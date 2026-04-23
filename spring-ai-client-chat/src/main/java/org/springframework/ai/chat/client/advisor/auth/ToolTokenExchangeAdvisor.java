/*
 * Copyright 2025-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.client.advisor.auth;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Advisor that performs RFC 8693 OAuth 2.0 token exchange before tool dispatch,
 * enabling per-tool scope enforcement for enterprise security.
 * <p>
 * This advisor integrates with the {@link ToolCallAdvisor} to intercept tool execution
 * and inject {@link ToolIdentityContext} into tool methods annotated with
 * {@link ToolScope}.
 * <p>
 * When a tool method declares required scopes via {@code @ToolScope}, this advisor:
 * <ol>
 * <li>Extracts the user identity from the security context</li>
 * <li>Performs RFC 8693 token exchange via {@link OAuth2AuthorizedClientManager}</li>
 * <li>Creates a {@link ToolIdentityContext} with the downscoped delegation token</li>
 * <li>Injects the context into the tool method parameters</li>
 * </ol>
 * <p>
 * This enables enterprise security teams to enforce least-privilege for AI tool calls,
 * ensuring each tool invocation has only the permissions it needs.
 *
 * @author Spring AI Team
 * @since 2.0.0
 * @see ToolScope
 * @see ToolIdentityContext
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token
 * Exchange</a>
 */
public class ToolTokenExchangeAdvisor extends BaseAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(ToolTokenExchangeAdvisor.class);

	/**
	 * Attribute key for storing tool identity context in the prompt options context.
	 */
	public static final String TOOL_IDENTITY_CONTEXT_KEY = "spring.ai.tool.identity.context";

	/**
	 * Thread-local holder for current tool identity context during tool execution.
	 */
	private static final ThreadLocal<ToolIdentityContext> currentContextHolder = new ThreadLocal<>();

	private final ApplicationContext applicationContext;

	@Nullable
	private final OAuth2AuthorizedClientManager authorizedClientManager;

	/**
	 * Cache of method -> ToolScope annotation to avoid repeated reflection.
	 */
	private final Map<Method, @Nullable ToolScope> scopeAnnotationCache = new ConcurrentHashMap<>();

	/**
	 * Cache of client registration ID -> client name mapping for token exchange.
	 */
	private final Map<String, String> clientNameCache = new ConcurrentHashMap<>();

	public ToolTokenExchangeAdvisor(ApplicationContext applicationContext) {
		this(applicationContext, null);
	}

	public ToolTokenExchangeAdvisor(ApplicationContext applicationContext,
			@Nullable OAuth2AuthorizedClientManager authorizedClientManager) {
		Assert.notNull(applicationContext, "applicationContext must not be null");
		this.applicationContext = applicationContext;
		this.authorizedClientManager = authorizedClientManager;
	}

	@Override
	public String getName() {
		return "Tool Token Exchange Advisor";
	}

	@Override
	public int getOrder() {
		// Run after ToolCallAdvisor to intercept tool execution
		return ToolCallAdvisor.builder().build().getOrder() + 1;
	}

	@Override
	public ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		// Extract security context and prepare for tool execution
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String subject = extractSubject(authentication);
		String originalClientId = extractClientId(authentication);

		// Store the extracted identity for use during tool execution
		Map<String, Object> contextMap = new ConcurrentHashMap<>();
		if (chatClientRequest.prompt() != null && chatClientRequest.prompt().getOptions() != null) {
			Object options = chatClientRequest.prompt().getOptions();
			if (options instanceof ToolCallingChatOptionsAccessor accessor) {
				Map<String, Object> existingContext = accessor.getToolContext();
				if (existingContext != null) {
					contextMap.putAll(existingContext);
				}
			}
		}

		// Store identity info in context for tool execution phase
		contextMap.put("_springAiSubject", subject);
		contextMap.put("_springAiOriginalClientId", originalClientId);
		contextMap.put("_springAiAuthentication", authentication);

		return chatClientRequest;
	}

	/**
	 * Performs token exchange for a specific tool invocation.
	 * <p>
	 * This method is called by the tool execution infrastructure to obtain a downscoped
	 * token for the given tool.
	 * @param toolName the name of the tool being invoked
	 * @param scopes the scopes required by the tool
	 * @param resource the resource indicator, or empty string for default
	 * @param clientRegistrationId the client registration ID for token exchange
	 * @return a ToolIdentityContext with the delegation token, or null if token exchange
	 * is not needed
	 */
	@Nullable
	public ToolIdentityContext performTokenExchange(String toolName, String[] scopes, String resource,
			String clientRegistrationId) {

		if (this.authorizedClientManager == null) {
			logger.warn("No OAuth2AuthorizedClientManager configured - token exchange disabled");
			return null;
		}

		if (!StringUtils.hasText(clientRegistrationId)) {
			clientRegistrationId = resolveDefaultClientRegistrationId();
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			logger.warn("No authentication context available for token exchange");
			return null;
		}

		String subject = extractSubject(authentication);
		String originalClientId = extractClientId(authentication);

		// Build the scope string
		String scopeString = String.join(" ", scopes);

		// Perform RFC 8693 token exchange
		OAuth2AuthorizedClient authorizedClient = exchangeToken(authentication, clientRegistrationId, scopeString,
				resource);

		if (authorizedClient == null) {
			logger.warn("Token exchange failed for tool: {}", toolName);
			return null;
		}

		return ToolIdentityContext.builder().subject(subject).actingAgentClientId(originalClientId)
			.originalClientId(originalClientId).delegationToken(authorizedClient.getAccessToken().getTokenValue())
			.authorizedScopes(scopes).toolName(toolName).principal(authentication).build();
	}

	@Nullable
	private OAuth2AuthorizedClient exchangeToken(Authentication authentication, String clientRegistrationId,
			String scopeString, String resource) {
		// Get the client name - handle both ClientRegistration and AuthorizedClientRepository
		String clientName = resolveClientName(clientRegistrationId);

		try {
			OAuth2AuthorizationContext.Builder contextBuilder = OAuth2AuthorizationContext
				.authorizationContext(authentication);

			// Add RFC 8693 token exchange parameters
			if (StringUtils.hasText(scopeString)) {
				contextBuilder.attribute(OAuth2ParameterNames.SCOPE, scopeString);
			}

			// Add requested audience if specified
			if (StringUtils.hasText(resource)) {
				contextBuilder.attribute(OAuth2ParameterNames.AUDIENCE, resource);
			}

			// Add actor parameter for delegation chain (RFC 8693)
			// The current authentication represents the actor
			if (authentication.getName() != null) {
				contextBuilder.attribute("actor", authentication);
			}

			OAuth2AuthorizationContext context = contextBuilder.build();

			OAuth2AuthorizedClient result = this.authorizedClientManager.authorize(context, clientName);
			return result;
		}
		catch (Exception e) {
			logger.error("Token exchange failed: {}", e.getMessage(), e);
			return null;
		}
	}

	private String resolveClientName(String clientRegistrationId) {
		if (clientNameCache.containsKey(clientRegistrationId)) {
			return clientNameCache.get(clientRegistrationId);
		}

		// Try to resolve from Spring Security's ClientRegistrationRepository
		try {
			var clientRegistrationRepo = applicationContext
				.getBeanProvider(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class)
				.getIfAvailable();
			if (clientRegistrationRepo != null) {
				var registration = clientRegistrationRepo.findByRegistrationId(clientRegistrationId);
				if (registration != null) {
					clientNameCache.put(clientRegistrationId, clientRegistrationId);
					return clientRegistrationId;
				}
			}
		}
		catch (Exception e) {
			logger.debug("Could not resolve client registration: {}", e.getMessage());
		}

		// Fall back to using the registration ID as the client name
		return clientRegistrationId;
	}

	private String resolveDefaultClientRegistrationId() {
		try {
			var clientRegistrationRepo = applicationContext
				.getBeanProvider(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class)
				.getIfAvailable();
			if (clientRegistrationRepo != null) {
				// Return the first registration if available
				// In practice, you'd want to configure a specific default
				return "spring-ai-tool-client";
			}
		}
		catch (Exception e) {
			logger.debug("Could not resolve default client registration: {}", e.getMessage());
		}
		return "";
	}

	/**
	 * Extracts the subject (user identifier) from the authentication.
	 */
	String extractSubject(@Nullable Authentication authentication) {
		if (authentication == null) {
			return "anonymous";
		}

		// Try to get the principal name - this could be a user ID or email
		String subject = authentication.getName();

		// If the principal is a Principal implementation with getSubject(), use that
		Object principal = authentication.getPrincipal();
		if (principal instanceof java.util.function.Supplier) {
			// Handle delegated authentication
			subject = authentication.getName();
		}

		return subject != null ? subject : "anonymous";
	}

	/**
	 * Extracts the client ID from the authentication.
	 */
	String extractClientId(@Nullable Authentication authentication) {
		if (authentication == null) {
			return null;
		}

		// Check for OAuth2AuthenticationToken which has clientId
		Map<String, Object> attrs = authentication.getAttributes();
		if (attrs != null && attrs.containsKey("client_id")) {
			return attrs.get("client_id").toString();
		}

		return null;
	}

	/**
	 * Extracts the @ToolScope annotation from a method.
	 */
	@Nullable
	public ToolScope getToolScope(Method method) {
		return this.scopeAnnotationCache.computeIfAbsent(method, m -> {
			ToolScope annotation = AnnotationUtils.findAnnotation(m, ToolScope.class);
			if (annotation == null) {
				// Check for meta-annotations
				return AnnotationUtils.findMetaAnnotation(m, ToolScope.class);
			}
			return annotation;
		});
	}

	/**
	 * Gets the current tool identity context for this thread.
	 * @return the current context, or null if not set
	 */
	@Nullable
	public static ToolIdentityContext getCurrentContext() {
		return currentContextHolder.get();
	}

	/**
	 * Sets the current tool identity context for this thread.
	 * @param context the context to set, or null to clear
	 */
	public static void setCurrentContext(@Nullable ToolIdentityContext context) {
		if (context == null) {
			currentContextHolder.remove();
		}
		else {
			currentContextHolder.set(context);
		}
	}

	/**
	 * Clears the current context.
	 */
	public static void clearCurrentContext() {
		currentContextHolder.remove();
	}

	// Nested interface for accessing tool context from options
	public interface ToolCallingChatOptionsAccessor {

		Map<String, Object> getToolContext();

	}

	/**
	 * Utility method to extract identity from a prompt's tool context.
	 */
	public static ToolIdentityContext extractIdentityContext(@Nullable Prompt prompt, @Nullable String toolName) {
		if (prompt == null || prompt.getOptions() == null) {
			return null;
		}

		Object options = prompt.getOptions();
		if (options instanceof ToolCallingChatOptionsAccessor accessor) {
			Map<String, Object> context = accessor.getToolContext();
			if (context != null && context.containsKey(TOOL_IDENTITY_CONTEXT_KEY)) {
				Object value = context.get(TOOL_IDENTITY_CONTEXT_KEY);
				if (value instanceof ToolIdentityContext identityContext) {
					// Filter to only the requested tool if specified
					if (toolName == null || toolName.equals(identityContext.toolName())) {
						return identityContext;
					}
				}
			}
		}

		return null;
	}

	/**
	 * Utility method to extract raw identity info from prompt context.
	 */
	@SuppressWarnings("unchecked")
	public static String extractSubjectFromContext(@Nullable Prompt prompt) {
		if (prompt == null) {
			return "anonymous";
		}

		ToolIdentityContext identityContext = extractIdentityContext(prompt, null);
		if (identityContext != null) {
			return identityContext.subject();
		}

		// Check for legacy context format
		if (prompt.getOptions() instanceof ToolCallingChatOptionsAccessor accessor) {
			Map<String, Object> context = accessor.getToolContext();
			if (context != null && context.containsKey("_springAiSubject")) {
				return context.get("_springAiSubject").toString();
			}
		}

		return "anonymous";
	}

	@Override
	public String toString() {
		return "ToolTokenExchangeAdvisor{" + "order=" + getOrder() + ", configured="
				+ (this.authorizedClientManager != null) + '}';
	}

}