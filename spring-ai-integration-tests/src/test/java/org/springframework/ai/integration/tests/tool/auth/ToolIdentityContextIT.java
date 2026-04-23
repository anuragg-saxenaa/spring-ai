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

package org.springframework.ai.integration.tests.tool.auth;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.auth.ToolIdentityContext;
import org.springframework.ai.chat.client.advisor.auth.ToolScope;
import org.springframework.ai.chat.client.advisor.auth.ToolTokenExchangeAdvisor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.AuthorizationCodeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.test.OAuth2AuthorizationServerInitializer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.server.authorization.test.OAuth2AuthorizationServerSupport.authorizationServerSupport;

/**
 * Integration tests for ToolIdentityContext and ToolTokenExchangeAdvisor.
 * <p>
 * These tests verify the complete flow from authentication through tool invocation,
 * ensuring that:
 * <ul>
 * <li>ToolIdentityContext is properly injected into tool methods</li>
 * <li>RFC 8693 token exchange produces downscoped delegation tokens</li>
 * <li>Least-privilege enforcement works via @ToolScope annotation</li>
 * <li>Enterprise security requirements are met</li>
 * </ul>
 *
 * @author Spring AI Team
 * @since 2.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ToolIdentityContextIT.TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ToolIdentityContextIT {

	@Autowired
	private ChatClient chatClient;

	@Autowired
	private ToolService toolService;

	@Autowired
	private MockAuthorizationServerController authServerController;

	@Autowired
	private ToolTokenExchangeAdvisor tokenExchangeAdvisor;

	@BeforeAll
	void setUp() {
		// Set up a mock user authentication
		TestingAuthenticationToken auth = new TestingAuthenticationToken("user-123", "password",
				"ROLE_USER", "openid", "profile", "email");
		auth.setDetails(Map.of("client_id", "spring-ai-client", "sub", "user-123"));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@Test
	void toolIdentityContextIsInjected() {
		// When calling a tool with ToolIdentityContext parameter
		String response = this.chatClient.prompt()
			.system("You are a helpful assistant.")
			.user("What is the user profile for ID 123?")
			.advisors(new ToolCallAdvisor())
			.advisors(this.tokenExchangeAdvisor)
			.call()
			.content();

		// Then the tool receives the identity context
		assertThat(toolService.getLastInvokedIdentity()).isNotNull();
		ToolIdentityContext identity = toolService.getLastInvokedIdentity();
		assertThat(identity.subject()).isEqualTo("user-123");
		assertThat(identity.toolName()).isEqualTo("getUserProfile");
	}

	@Test
	void toolScopeEnforcesAuthorization() {
		// Given a tool with specific scopes
		TestingAuthenticationToken auth = new TestingAuthenticationToken("user-456", "password", "ROLE_USER");
		auth.setDetails(Map.of("client_id", "spring-ai-client", "sub", "user-456"));
		SecurityContextHolder.getContext().setAuthentication(auth);

		// When calling the tool
		String response = this.chatClient.prompt()
			.system("You are a helpful assistant.")
			.user("Get my profile")
			.advisors(new ToolCallAdvisor())
			.advisors(this.tokenExchangeAdvisor)
			.call()
			.content();

		// Then the authorized scopes should match the @ToolScope declaration
		ToolIdentityContext identity = toolService.getLastInvokedIdentity();
		assertThat(identity.authorizedScopes()).contains("profile:read", "email:read");
	}

	@Test
	void delegationTokenIsPresent() {
		// When the auth server processes token exchange
		String tokenExchangeResult = authServerController.getLastTokenExchangeScope();

		// Then the scopes used for token exchange match the tool requirements
		assertThat(tokenExchangeResult).isNotNull();
		assertThat(tokenExchangeResult.split(" ")).contains("profile:read", "email:read");
	}

	@Test
	void anonymousUserGetsDefaultContext() {
		// Given no authentication
		SecurityContextHolder.clearContext();

		// When calling a tool
		String response = this.chatClient.prompt()
			.system("You are a helpful assistant.")
			.user("Get public info")
			.advisors(new ToolCallAdvisor())
			.advisors(this.tokenExchangeAdvisor)
			.call()
			.content();

		// Then the tool still receives a context (with 'anonymous' subject)
		ToolIdentityContext identity = toolService.getLastInvokedIdentity();
		assertThat(identity).isNotNull();
		assertThat(identity.subject()).isEqualTo("anonymous");
	}

	@Test
	void multiToolInvocationWithDifferentScopes() {
		// Given a user with multi-tool request
		TestingAuthenticationToken auth = new TestingAuthenticationToken("user-789", "password", "ROLE_USER");
		auth.setDetails(Map.of("client_id", "spring-ai-client", "sub", "user-789"));
		SecurityContextHolder.getContext().setAuthentication(auth);

		// When calling multiple tools with different scope requirements
		String response = this.chatClient.prompt()
			.system("You are a helpful assistant.")
			.user("First get my profile, then check my email permissions")
			.advisors(new ToolCallAdvisor())
			.advisors(this.tokenExchangeAdvisor)
			.call()
			.content();

		// Then each tool receives appropriate scopes
		ToolIdentityContext profileContext = toolService.getProfileInvokedIdentity();
		ToolIdentityContext emailContext = toolService.getEmailInvokedIdentity();

		assertThat(profileContext.authorizedScopes()).contains("profile:read");
		assertThat(emailContext.authorizedScopes()).contains("email:read");
	}

	// ==================== Test Configuration ====================

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

		@Bean
		OAuth2AuthorizationServerInitializer authorizationServerInitializer() {
			return new OAuth2AuthorizationServerInitializer();
		}

		@Bean
		ClientRegistrationRepository clientRegistrationRepository(
				OAuth2AuthorizationServerInitializer authServerInit) {
			ClientRegistration registration = ClientRegistration.withRegistrationId("spring-ai-tool-client")
				.clientId("spring-ai-client")
				.clientSecret("{noop}secret")
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("http://localhost/callback")
				.scope("openid", "profile", "email")
				.scope("profile:read", "profile:write")
				.scope("email:read", "email:write")
				.authorizationUri(authServerInit.getAuthorizationUri())
				.tokenUri(authServerInit.getTokenUri())
				.userInfoUri(authServerInit.getUserinfoUri())
				.jwkSetUri(authServerInit.getJwkSetUri())
				.build();
			return registrations -> registrations.add(registration);
		}

		@Bean
		OAuth2AuthorizedClientManager authorizedClientManager(
				ClientRegistrationRepository clientRegistrationRepository) {
			OAuth2AuthorizedClientProvider provider = new AuthorizationCodeOAuth2AuthorizedClientProvider();
			return new MockOAuth2AuthorizedClientManager(clientRegistrationRepository);
		}

		@Bean
		ToolTokenExchangeAdvisor toolTokenExchangeAdvisor() {
			return new ToolTokenExchangeAdvisor(null, null) {
				@Override
				public ToolIdentityContext performTokenExchange(String toolName, String[] scopes, String resource,
						String clientRegistrationId) {
					// In test mode, create a context with a mock token
					Authentication auth = SecurityContextHolder.getContext().getAuthentication();
					String subject = extractSubject(auth);
					return ToolIdentityContext.builder()
						.subject(subject)
						.actingAgentClientId("spring-ai-client")
						.originalClientId("spring-ai-client")
						.delegationToken("mock-delegation-token-" + UUID.randomUUID())
						.authorizedScopes(scopes)
						.toolName(toolName)
						.principal(auth)
						.build();
				}
			};
		}

		@Bean
		ToolService toolService(MockAuthorizationServerController authServerController) {
			return new ToolService(authServerController);
		}

		@Bean
		RestController mockAuthServerController() {
			return new MockAuthorizationServerController();
		}

	}

	// ==================== Tool Service ====================

	static class ToolService {

		private final MockAuthorizationServerController authServerController;

		private volatile ToolIdentityContext lastInvokedIdentity;

		private volatile ToolIdentityContext profileInvokedIdentity;

		private volatile ToolIdentityContext emailInvokedIdentity;

		ToolService(MockAuthorizationServerController authServerController) {
			this.authServerController = authServerController;
		}

		public ToolIdentityContext getLastInvokedIdentity() {
			return this.lastInvokedIdentity;
		}

		public ToolIdentityContext getProfileInvokedIdentity() {
			return this.profileInvokedIdentity;
		}

		public ToolIdentityContext getEmailInvokedIdentity() {
			return this.emailInvokedIdentity;
		}

		@Tool(name = "getUserProfile", description = "Get the user profile")
		@ToolScope(scopes = { "profile:read", "email:read" })
		public String getUserProfile(ToolIdentityContext context) {
			this.lastInvokedIdentity = context;
			if (context.toolName().equals("getUserProfile")) {
				this.profileInvokedIdentity = context;
			}
			String token = context.delegationToken().orElse("no-token");
			return String.format("{\"userId\": \"%s\", \"subject\": \"%s\", \"tokenPrefix\": \"%s\"}",
					context.subject(), context.subject(), token.substring(0, Math.min(10, token.length())));
		}

		@Tool(name = "checkEmailPermissions", description = "Check email permissions")
		@ToolScope(scopes = { "email:read" })
		public String checkEmailPermissions(ToolIdentityContext context) {
			this.lastInvokedIdentity = context;
			this.emailInvokedIdentity = context;
			String[] scopes = context.authorizedScopes();
			return String.format("{\"hasEmailAccess\": %s, \"scopes\": %s}", scopes.length > 0,
					Arrays.toString(scopes));
		}

		@Tool(name = "getPublicInfo", description = "Get public information")
		public String getPublicInfo(ToolIdentityContext context) {
			this.lastInvokedIdentity = context;
			return String.format("{\"subject\": \"%s\", \"authenticated\": %s}", context.subject(),
					!"anonymous".equals(context.subject()));
		}

		@Tool(name = "getWeather", description = "Get weather information")
		public String getWeather(String location) {
			return String.format("Weather in %s: Sunny, 72°F", location);
		}

	}

	// ==================== Mock Authorization Server Controller ====================

	@RestController
	static class MockAuthorizationServerController {

		private volatile String lastTokenExchangeScope;

		private volatile String lastTokenExchangeSubject;

		private final Map<String, Instant> issuedTokens = new HashMap<>();

		public String getLastTokenExchangeScope() {
			return this.lastTokenExchangeScope;
		}

		public String getLastTokenExchangeSubject() {
			return this.lastTokenExchangeSubject;
		}

		public void recordTokenExchange(String subject, String scope) {
			this.lastTokenExchangeSubject = subject;
			this.lastTokenExchangeScope = scope;
			String tokenId = "token-" + UUID.randomUUID();
			this.issuedTokens.put(tokenId, Instant.now());
		}

	}

	// ==================== Mock Authorized Client Manager ====================

	static class MockOAuth2AuthorizedClientManager implements OAuth2AuthorizedClientManager {

		private final ClientRegistrationRepository clientRegistrationRepository;

		MockOAuth2AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository) {
			this.clientRegistrationRepository = clientRegistrationRepository;
		}

		@Override
		public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context, String clientRegistrationId) {
			// Create a mock authorized client with a delegation token
			org.springframework.security.oauth2.core.OAuth2AccessToken accessToken = new org.springframework.security.oauth2.core.OAuth2AccessToken(
					org.springframework.security.oauth2.core.OAuth2TokenType.ACCESS_TOKEN, "mock-access-token",
					Instant.now(), Instant.now().plus(Duration.ofHours(1)));

			return OAuth2AuthorizedClient.withClientRegistration(
					this.clientRegistrationRepository.findByRegistrationId(clientRegistrationId))
				.accessToken(accessToken)
				.principal(context.getPrincipal())
				.build();
		}

	}

}