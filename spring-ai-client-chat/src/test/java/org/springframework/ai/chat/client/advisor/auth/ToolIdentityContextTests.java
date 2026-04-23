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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ToolIdentityContext.
 *
 * @author Spring AI Team
 * @since 2.0.0
 */
class ToolIdentityContextTests {

	@Test
	void builderRequiresSubject() {
		assertThatThrownBy(() -> ToolIdentityContext.builder().build())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("subject");
	}

	@Test
	void builderCreatesValidContext() {
		ToolIdentityContext context = ToolIdentityContext.builder().subject("user-123")
			.actingAgentClientId("agent-client")
			.originalClientId("original-client")
			.delegationToken("delegation-token-value")
			.authorizedScopes(new String[] { "scope1", "scope2" })
			.toolName("myTool")
			.build();

		assertThat(context.subject()).isEqualTo("user-123");
		assertThat(context.actingAgentClientId()).isEqualTo(Optional.of("agent-client"));
		assertThat(context.originalClientId()).isEqualTo(Optional.of("original-client"));
		assertThat(context.delegationToken()).isEqualTo(Optional.of("delegation-token-value"));
		assertThat(context.authorizedScopes()).containsExactly("scope1", "scope2");
		assertThat(context.toolName()).isEqualTo("myTool");
		assertThat(context.principal()).isEmpty();
	}

	@Test
	void optionalFieldsCanBeNull() {
		ToolIdentityContext context = ToolIdentityContext.builder()
			.subject("user-456")
			.toolName("simpleTool")
			.build();

		assertThat(context.subject()).isEqualTo("user-456");
		assertThat(context.actingAgentClientId()).isEmpty();
		assertThat(context.originalClientId()).isEmpty();
		assertThat(context.delegationToken()).isEmpty();
		assertThat(context.authorizedScopes()).isEmpty();
		assertThat(context.toolName()).isEqualTo("simpleTool");
	}

	@Test
	void authorizedScopesIsDefensiveCopy() {
		String[] originalScopes = { "scope1", "scope2" };
		ToolIdentityContext context = ToolIdentityContext.builder().subject("user")
			.authorizedScopes(originalScopes)
			.toolName("tool")
			.build();

		// Modify the original array
		originalScopes[0] = "modified";

		// Context should not be affected
		assertThat(context.authorizedScopes()[0]).isEqualTo("scope1");
	}

	@Test
	void toBuilderCopiesAllFields() {
		ToolIdentityContext original = ToolIdentityContext.builder().subject("user-789")
			.actingAgentClientId("agent")
			.originalClientId("original")
			.delegationToken("token")
			.authorizedScopes(new String[] { "s1" })
			.toolName("tool")
			.build();

		ToolIdentityContext copy = original.toBuilder().subject("modified-user").build();

		assertThat(copy.subject()).isEqualTo("modified-user");
		assertThat(copy.actingAgentClientId()).isEqualTo(Optional.of("agent"));
		assertThat(copy.originalClientId()).isEqualTo(Optional.of("original"));
		assertThat(copy.delegationToken()).isEqualTo(Optional.of("token"));
		assertThat(copy.toolName()).isEqualTo("tool");
	}

	@Test
	void toStringShowsMaskedDelegationToken() {
		ToolIdentityContext context = ToolIdentityContext.builder()
			.subject("user")
			.delegationToken("secret-token-value")
			.toolName("tool")
			.build();

		String str = context.toString();
		assertThat(str).contains("subject='user'");
		assertThat(str).contains("[PRESENT]"); // Masked token
		assertThat(str).doesNotContain("secret-token-value");
	}

	@Test
	void staticBuilderFactoryMethod() {
		ToolIdentityContext context = ToolIdentityContext.builder().subject("factory-method-user").build();
		assertThat(context.subject()).isEqualTo("factory-method-user");
	}

	@Test
	void subjectCannotBeNullInBuiltContext() {
		// The subject should be non-null in the built object
		ToolIdentityContext context = ToolIdentityContext.builder().subject("valid-subject").build();
		assertThat(context.subject()).isEqualTo("valid-subject");
	}

}