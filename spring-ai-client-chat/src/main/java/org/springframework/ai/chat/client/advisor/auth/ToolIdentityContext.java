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

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Context object carrying identity and authorization information for tool execution.
 * <p>
 * This context is auto-injected into {@code @Tool} method parameters when present and
 * carries the user identity, acting agent client ID, and downscoped delegation token
 * via RFC 8693 token exchange.
 *
 * @author Spring AI Team
 * @since 2.0.0
 */
public final class ToolIdentityContext {

	/**
	 * The authenticated user identity (subject claim). Never null.
	 */
	private final String subject;

	/**
	 * The acting agent/client that is making the tool call on behalf of the subject.
	 * This is the client_id of the agent performing the delegation.
	 */
	private final @Nullable String actingAgentClientId;

	/**
	 * The original client_id that initiated the request chain. May be null if the
	 * request originated directly from the user.
	 */
	private final @Nullable String originalClientId;

	/**
	 * The downscoped delegation token (RFC 8693 token exchange) scoped to the
	 * specific tool's required permissions. Null if no token exchange was performed.
	 */
	private final @Nullable String delegationToken;

	/**
	 * The scopes that were used to obtain the delegation token. Empty if no token
	 * exchange was performed.
	 */
	private final String[] authorizedScopes;

	/**
	 * The tool name this context applies to.
	 */
	private final String toolName;

	/**
	 * The principal from the current security context, if available.
	 */
	private final @Nullable Principal principal;

	private ToolIdentityContext(Builder builder) {
		this.subject = Objects.requireNonNull(builder.subject, "subject must not be null");
		this.actingAgentClientId = builder.actingAgentClientId;
		this.originalClientId = builder.originalClientId;
		this.delegationToken = builder.delegationToken;
		this.authorizedScopes = builder.authorizedScopes != null ? builder.authorizedScopes.clone()
				: new String[0];
		this.toolName = builder.toolName;
		this.principal = builder.principal;
	}

	/**
	 * Returns the authenticated user identity (subject claim).
	 * @return the subject identifier
	 */
	public String subject() {
		return this.subject;
	}

	/**
	 * Returns the acting agent/client making the tool call.
	 * @return the acting agent client ID, or empty if not applicable
	 */
	public Optional<String> actingAgentClientId() {
		return Optional.ofNullable(this.actingAgentClientId);
	}

	/**
	 * Returns the original client_id that initiated the request chain.
	 * @return the original client ID, or empty if not applicable
	 */
	public Optional<String> originalClientId() {
		return Optional.ofNullable(this.originalClientId);
	}

	/**
	 * Returns the downscoped delegation token from RFC 8693 token exchange.
	 * <p>
	 * This token is scoped to the specific tool's required permissions as declared by
	 * {@code @ToolScope}.
	 * @return the delegation token, or empty if no token exchange was performed
	 */
	public Optional<String> delegationToken() {
		return Optional.ofNullable(this.delegationToken);
	}

	/**
	 * Returns the authorized scopes used to obtain the delegation token.
	 * @return array of authorized scope strings
	 */
	public String[] authorizedScopes() {
		return this.authorizedScopes.clone();
	}

	/**
	 * Returns the tool name this context applies to.
	 * @return the tool name
	 */
	public String toolName() {
		return this.toolName;
	}

	/**
	 * Returns the security principal from the current context.
	 * @return the principal, or empty if not available
	 */
	public Optional<Principal> principal() {
		return Optional.ofNullable(this.principal);
	}

	/**
	 * Creates a new builder copying the current context.
	 * @return a new builder with the current values
	 */
	public Builder toBuilder() {
		return new Builder().subject(this.subject).actingAgentClientId(this.actingAgentClientId)
			.originalClientId(this.originalClientId).delegationToken(this.delegationToken)
			.authorizedScopes(this.authorizedScopes).toolName(this.toolName).principal(this.principal);
	}

	/**
	 * Creates a builder for constructing a new ToolIdentityContext.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String toString() {
		return "ToolIdentityContext{" + "subject='" + this.subject + '\'' + ", actingAgentClientId='"
				+ this.actingAgentClientId + '\'' + ", originalClientId='" + this.originalClientId + '\''
				+ ", delegationToken=" + (this.delegationToken != null ? "[PRESENT]" : "null") + ", authorizedScopes="
				+ java.util.Arrays.toString(this.authorizedScopes) + ", toolName='" + this.toolName + '\'' + '}';
	}

	public static final class Builder {

		private String subject;

		private @Nullable String actingAgentClientId;

		private @Nullable String originalClientId;

		private @Nullable String delegationToken;

		private String[] authorizedScopes;

		private String toolName;

		private @Nullable Principal principal;

		private Builder() {
		}

		public Builder subject(String subject) {
			this.subject = subject;
			return this;
		}

		public Builder actingAgentClientId(@Nullable String actingAgentClientId) {
			this.actingAgentClientId = actingAgentClientId;
			return this;
		}

		public Builder originalClientId(@Nullable String originalClientId) {
			this.originalClientId = originalClientId;
			return this;
		}

		public Builder delegationToken(@Nullable String delegationToken) {
			this.delegationToken = delegationToken;
			return this;
		}

		public Builder authorizedScopes(String[] authorizedScopes) {
			this.authorizedScopes = authorizedScopes;
			return this;
		}

		public Builder toolName(String toolName) {
			this.toolName = toolName;
			return this;
		}

		public Builder principal(@Nullable Principal principal) {
			this.principal = principal;
			return this;
		}

		public ToolIdentityContext build() {
			return new ToolIdentityContext(this);
		}

	}

}