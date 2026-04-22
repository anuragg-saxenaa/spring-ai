/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GovernanceAdvisor} demonstrating AGT (Microsoft Agent Governance
 * Toolkit) integration.
 *
 * @author Anurag Saxena
 * @since 2.0.0
 * @see GovernanceAdvisor
 * @see <a href="https://github.com/microsoft/agent-governance-toolkit">Microsoft Agent
 *      Governance Toolkit</a>
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5840">Issue #5840</a>
 */
class GovernanceAdvisorTests {

	private static final String DEFAULT_FAILURE = "I'm unable to process that request due to governance policy restrictions.";

	@Test
	void shouldAllowByDefault() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder().build();

		ChatClientRequest request = createMockRequest("Hello, how are you?");
		ChatClientResponse expectedResponse = createMockResponse("Hello!");

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(expectedResponse);

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(response).isEqualTo(expectedResponse);
		assertThat(advisor.getAuditLog()).isEmpty(); // No denial, no audit entry
	}

	@Test
	void shouldDenyDangerousTools() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder().denyTool("execute_code").denyTool("delete_file").build();

		ChatClientRequest request = createMockRequestWithTool("execute_code with malicious payload", "execute_code",
				Map.of("code", "rm -rf /"));

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(response).isNotNull();
		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
		assertThat(advisor.getAuditLog()).hasSize(1);
		assertThat(advisor.getAuditLog().get(0).getDecision())
				.isEqualTo(GovernanceAdvisor.GovernanceDecision.DENIED);
	}

	@Test
	void shouldAllowSafeTools() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.allowedTools(Set.of("web_search", "calculator"))
				.build();

		ChatClientRequest request = createMockRequestWithTool("Search the web", "web_search", Map.of("query", "weather"));

		ChatClientResponse expectedResponse = createMockResponse("Sunny with 72F");

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(expectedResponse);

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(response).isEqualTo(expectedResponse);
	}

	@Test
	void shouldDenyUnlistedToolWhenAllowedListSet() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.allowedTools(Set.of("web_search", "calculator"))
				.build();

		ChatClientRequest request = createMockRequestWithTool("Delete a file", "delete_file", Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
	}

	@Test
	void shouldRestrictToolParameters() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.restrictToolParameters("database_query", Set.of("DROP", "DELETE", "TRUNCATE"))
				.build();

		ChatClientRequest request = createMockRequestWithTool("Query database", "database_query",
				Map.of("query", "SELECT * FROM users", "table", "users"));

		ChatClientResponse expectedResponse = createMockResponse("Query results");
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(expectedResponse);

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(response).isEqualTo(expectedResponse);
	}

	@Test
	void shouldDenyRestrictedParameters() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.restrictToolParameters("database_query", Set.of("DROP", "DELETE", "TRUNCATE"))
				.build();

		ChatClientRequest request = createMockRequestWithTool("Drop database", "database_query",
				Map.of("query", "DROP DATABASE production"));

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
		assertThat(advisor.getAuditLog().get(0).getRuleName()).isEqualTo("param-restriction");
	}

	@Test
	void shouldEvaluateRulesByPriority() {
		GovernanceAdvisor.GovernanceRule lowPriorityAllow = new GovernanceAdvisor.GovernanceRule("allow-db-read",
				GovernanceAdvisor.GovernanceDecision.ALLOWED, 10, "Allow database read operations",
				ctx -> ctx.getToolName() != null && ctx.getToolName().equals("database_read"));

		GovernanceAdvisor.GovernanceRule highPriorityDeny = new GovernanceAdvisor.GovernanceRule("deny-all-db",
				GovernanceAdvisor.GovernanceDecision.DENIED, 100, "Deny all database operations",
				ctx -> ctx.getToolName() != null && ctx.getToolName().startsWith("database_"));

		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.addRules(List.of(lowPriorityAllow, highPriorityDeny))
				.build();

		ChatClientRequest request = createMockRequestWithTool("Read from database", "database_read", Map.of());
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
		assertThat(advisor.getAuditLog().get(0).getRuleName()).isEqualTo("deny-all-db");
	}

	@Test
	void shouldRequireApprovalForSensitiveTools() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder().requireApprovalFor("send_email").build();

		ChatClientRequest request = createMockRequestWithTool("Send an email", "send_email",
				Map.of("to", "boss@company.com", "body", "Important message"));

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
		assertThat(advisor.getAuditLog().get(0).getDecision())
				.isEqualTo(GovernanceAdvisor.GovernanceDecision.PENDING_APPROVAL);
	}

	@Test
	void shouldUseCustomAgtEvaluator() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.customAgtEvaluator(ctx -> {
					// Custom AGT logic: block if request contains "confidential"
					if (ctx.getPromptContent().toLowerCase().contains("confidential")) {
						return GovernanceAdvisor.GovernanceDecision.DENIED;
					}
					return null; // Fall through to other rules
				})
				.build();

		ChatClientRequest request = createMockRequest("Please access the confidential financial data");
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
		assertThat(advisor.getAuditLog().get(0).getRuleName()).isEqualTo("custom-evaluator");
	}

	@Test
	void shouldEnforceAzureGovernmentMode() {
		GovernanceAdvisor advisor = GovernanceAdvisor.defaultAzureGovernmentAdvisor();

		// Add a data export tool attempt - should be blocked in Azure Government mode
		ChatClientRequest request = createMockRequestWithTool("Export all customer data", "data_export",
				Map.of("format", "csv"));
		request = withContextMetadata(request, "region", "usgovtexas"); // Valid AGT region

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Allowed in valid region"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		// With valid region metadata, data export should be allowed
		assertThat(advisor.getAuditLog()).isEmpty();
	}

	@Test
	void shouldBlockDangerousToolsHelper() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder().blockDangerousTools().build();

		ChatClientRequest request = createMockRequestWithTool("Execute malicious code", "execute_code", Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
	}

	@Test
	void shouldSupportStreamAdvising() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder().denyTool("delete_all").build();

		ChatClientRequest request = createMockRequestWithTool("Delete everything", "delete_all", Map.of());

		reactor.core.publisher.Flux<ChatClientResponse> expectedFlux = reactor.core.publisher.Flux
				.just(createMockResponse("Response"));

		org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain streamChain = mock(
				org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain.class);
		when(streamChain.nextStream(any())).thenReturn(expectedFlux);

		reactor.core.publisher.Flux<ChatClientResponse> flux = advisor.adviseStream(request, streamChain);

		ChatClientResponse response = flux.blockFirst();
		assertThat(response).isNotNull();
		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
	}

	@Test
	void shouldProvideDetailedAuditLog() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.auditEnabled(true)
				.denyTool("sensitive_operation")
				.build();

		ChatClientRequest request = createMockRequestWithTool("Perform sensitive operation", "sensitive_operation",
				Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		advisor.adviseCall(request, chain);

		List<GovernanceAdvisor.AuditEntry> auditLog = advisor.getAuditLog();
		assertThat(auditLog).hasSize(1);

		GovernanceAdvisor.AuditEntry entry = auditLog.get(0);
		assertThat(entry.getRequestId()).isNotEmpty();
		assertThat(entry.getRuleName()).isEqualTo("denied-tools");
		assertThat(entry.getDecision()).isEqualTo(GovernanceAdvisor.GovernanceDecision.DENIED);
		assertThat(entry.getToolName()).isEqualTo("sensitive_operation");
		assertThat(entry.getTimestamp()).isBeforeOrEqualTo(Instant.now());
		assertThat(entry.getEvaluationTimeNanos()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void shouldClearAuditLog() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.auditEnabled(true)
				.denyTool("blocked")
				.build();

		ChatClientRequest request = createMockRequestWithTool("Test", "blocked", Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Response"));

		advisor.adviseCall(request, chain);
		assertThat(advisor.getAuditLog()).hasSize(1);

		advisor.clearAuditLog();
		assertThat(advisor.getAuditLog()).isEmpty();
	}

	@Test
	void shouldReturnCorrectGovernanceMode() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.governanceMode(GovernanceAdvisor.GovernanceMode.AZURE_GOVERNMENT)
				.build();

		assertThat(advisor.getGovernanceMode())
				.isEqualTo(GovernanceAdvisor.GovernanceMode.AZURE_GOVERNMENT);
	}

	@Test
	void shouldUseCustomFailureMessage() {
		String customMessage = "Custom: This action is not allowed by our governance policies.";
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.denyTool("forbidden")
				.failureResponse(customMessage)
				.build();

		ChatClientRequest request = createMockRequestWithTool("Try forbidden action", "forbidden", Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(customMessage);
	}

	@Test
	void shouldEnforceDenyAllByDefault() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.denyAllByDefault(true)
				.allowTool("safe_tool")
				.build();

		ChatClientRequest request = createMockRequestWithTool("Try safe tool", "safe_tool", Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Success"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(response).isNotNull();
		assertThat(getResponseText(response)).isEqualTo("Success");
	}

	@Test
	void shouldEnforceToolPatternMatching() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
				.blockTool("admin") // Block anything with "admin" in name
				.build();

		ChatClientRequest request = createMockRequestWithTool("Admin action", "system_admin", Map.of());

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
		assertThat(advisor.getAuditLog().get(0).getRuleName()).isEqualTo("block-system_admin");
	}

	@Test
	void shouldEvaluateContextMetadata() {
		GovernanceAdvisor.GovernanceRule clearanceRule = new GovernanceAdvisor.GovernanceRule("clearance-check",
				GovernanceAdvisor.GovernanceDecision.DENIED, 100, "Require security clearance",
				ctx -> ctx.getMetadata("clearanceLevel") == null);

		GovernanceAdvisor advisor = GovernanceAdvisor.builder().addRule(clearanceRule).build();

		// Request without clearance metadata
		ChatClientRequest request = createMockRequest("Access classified resource");
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Should not reach here"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo(DEFAULT_FAILURE);
	}

	@Test
	void shouldAllowToolWithCorrectMetadata() {
		GovernanceAdvisor.GovernanceRule clearanceRule = new GovernanceAdvisor.GovernanceRule("clearance-check",
				GovernanceAdvisor.GovernanceDecision.DENIED, 100, "Require security clearance",
				ctx -> ctx.getMetadata("clearanceLevel") == null);

		GovernanceAdvisor advisor = GovernanceAdvisor.builder().addRule(clearanceRule).build();

		// Request with clearance metadata
		ChatClientRequest request = createMockRequest("Access classified resource");
		request = withContextMetadata(request, "clearanceLevel", "5");

		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(createMockResponse("Access granted"));

		ChatClientResponse response = advisor.adviseCall(request, chain);

		assertThat(getResponseText(response)).isEqualTo("Access granted");
	}

	// Helper methods

	private ChatClientRequest createMockRequest(String content) {
		return createMockRequestWithTool(content, null, null);
	}

	private ChatClientRequest createMockRequestWithTool(String content, @Nullable String toolName,
			@Nullable Map<String, Object> toolParams) {
		ChatClientRequest request = mock(ChatClientRequest.class);
		org.springframework.ai.chat.client.Prompt prompt = mock(org.springframework.ai.chat.client.Prompt.class);
		when(request.prompt()).thenReturn(prompt);
		when(prompt.getContents()).thenReturn(content);

		java.util.HashMap<String, Object> context = new java.util.HashMap<>();
		context.put("requestId", UUID.randomUUID().toString());
		if (toolName != null) {
			context.put("toolName", toolName);
		}
		if (toolParams != null) {
			context.put("toolParameters", toolParams);
		}
		when(request.context()).thenReturn(context);

		return request;
	}

	private ChatClientRequest withContextMetadata(ChatClientRequest request, String key, Object value) {
		java.util.HashMap<String, Object> context = new java.util.HashMap<>(request.context());
		context.put(key, value);
		when(request.context()).thenReturn(context);
		return request;
	}

	private ChatClientResponse createMockResponse(String content) {
		return ChatClientResponse.builder()
				.chatResponse(ChatResponse.builder()
						.generations(List.of(new Generation(new AssistantMessage(content))))
						.build())
				.context(Map.of())
				.build();
	}

	private String getResponseText(ChatClientResponse response) {
		return response.chatResponse().generations().get(0).getOutput().getText();
	}
}
