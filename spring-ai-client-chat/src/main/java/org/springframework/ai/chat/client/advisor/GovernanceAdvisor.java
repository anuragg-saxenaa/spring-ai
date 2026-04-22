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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * An advisor that enforces governance policies for AI agents, integrating with
 * Microsoft AGT (Agent Governance Toolkit) concepts.
 *
 * <p>
 * GovernanceAdvisor evaluates each request against configurable governance rules before
 * allowing the call to proceed. It supports:
 * <ul>
 * <li>Tool-level allow/deny policies</li>
 * <li>Resource access restrictions</li>
 * <li>Audit logging of all governance decisions</li>
 * <li>AGT-specific governance modes (Azure Government, Commercial, DoD)</li>
 * <li>Custom policy evaluators</li>
 * </ul>
 *
 * <p>
 * This advisor enforces deterministic governance at the application layer, complementing
 * prompt-based safety. Unlike probabilistic prompt guards, GovernanceAdvisor provides
 * sub-millisecond, policy-driven enforcement.
 *
 * @author Anurag Saxena
 * @since 2.0.0
 * @see <a href="https://github.com/microsoft/agent-governance-toolkit">Microsoft Agent
 *      Governance Toolkit</a>
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5840">Issue #5840</a>
 */
public class GovernanceAdvisor implements CallAdvisor, StreamAdvisor {

	private static final String DEFAULT_FAILURE_RESPONSE = "I'm unable to process that request due to governance policy restrictions.";

	private static final int DEFAULT_ORDER = -100;

	/**
	 * AGT governance environment modes.
	 */
	public enum GovernanceMode {
		/**
		 * Standard commercial cloud governance.
		 */
		COMMERCIAL,
		/**
		 * Azure Government cloud governance with FedRAMP High requirements.
		 */
		AZURE_GOVERNMENT,
		/**
		 * Department of Defense IL5/IL6 governance requirements.
		 */
		DOD,
		/**
		 * Custom governance mode with user-defined rules.
		 */
		CUSTOM
	}

	/**
	 * Result of a governance policy evaluation.
	 */
	public enum GovernanceDecision {
		/** Action is allowed by governance policy. */
		ALLOWED,
		/** Action is denied by governance policy. */
		DENIED,
		/** Action requires additional human approval. */
		PENDING_APPROVAL,
		/** No matching policy rule found, uses default action. */
		NO_MATCH
	}

	/**
	 * Represents a single governance policy rule.
	 */
	public static final class GovernanceRule {
		private final String name;
		private final GovernanceDecision decision;
		private final int priority;
		private final @Nullable String description;
		private final Predicate<GovernanceContext> condition;

		public GovernanceRule(String name, GovernanceDecision decision, int priority,
				@Nullable String description, Predicate<GovernanceContext> condition) {
			this.name = name;
			this.decision = decision;
			this.priority = priority;
			this.description = description;
			this.condition = condition;
		}

		public String getName() {
			return name;
		}

		public GovernanceDecision getDecision() {
			return decision;
		}

		public int getPriority() {
			return priority;
		}

		public @Nullable String getDescription() {
			return description;
		}

		public GovernanceDecision evaluate(GovernanceContext context) {
			if (condition.test(context)) {
				return decision;
			}
			return null;
		}
	}

	/**
	 * Context passed to governance rule evaluation.
	 */
	public static final class GovernanceContext {
		private final String requestId;
		private final String toolName;
		private final Map<String, Object> toolParameters;
		private final List<String> availableTools;
		private final String promptContent;
		private final Instant timestamp;
		private final Map<String, Object> metadata;

		public GovernanceContext(String requestId, @Nullable String toolName,
				@Nullable Map<String, Object> toolParameters, List<String> availableTools,
				String promptContent) {
			this.requestId = requestId;
			this.toolName = toolName;
			this.toolParameters = toolParameters != null ? toolParameters : Map.of();
			this.availableTools = availableTools;
			this.promptContent = promptContent;
			this.timestamp = Instant.now();
			this.metadata = new ConcurrentHashMap<>();
		}

		public String getRequestId() {
			return requestId;
		}

		public @Nullable String getToolName() {
			return toolName;
		}

		public @Nullable Map<String, Object> getToolParameters() {
			return toolParameters;
		}

		public List<String> getAvailableTools() {
			return availableTools;
		}

		public String getPromptContent() {
			return promptContent;
		}

		public Instant getTimestamp() {
			return timestamp;
		}

		public Map<String, Object> getMetadata() {
			return metadata;
		}

		public Object getMetadata(String key) {
			return metadata.get(key);
		}

		public void setMetadata(String key, Object value) {
			metadata.put(key, value);
		}
	}

	/**
	 * Audit log entry for governance decisions.
	 */
	public static final class AuditEntry {
		private final String requestId;
		private final String ruleName;
		private final GovernanceDecision decision;
		private final @Nullable String toolName;
		private final Instant timestamp;
		private final String reason;
		private final long evaluationTimeNanos;

		public AuditEntry(String requestId, @Nullable String ruleName, GovernanceDecision decision,
				@Nullable String toolName, Instant timestamp, String reason, long evaluationTimeNanos) {
			this.requestId = requestId;
			this.ruleName = ruleName;
			this.decision = decision;
			this.toolName = toolName;
			this.timestamp = timestamp;
			this.reason = reason;
			this.evaluationTimeNanos = evaluationTimeNanos;
		}

		public String getRequestId() {
			return requestId;
		}

		public @Nullable String getRuleName() {
			return ruleName;
		}

		public GovernanceDecision getDecision() {
			return decision;
		}

		public @Nullable String getToolName() {
			return toolName;
		}

		public Instant getTimestamp() {
			return timestamp;
		}

		public String getReason() {
			return reason;
		}

		public long getEvaluationTimeNanos() {
			return evaluationTimeNanos;
		}

		@Override
		public String toString() {
			return String.format(
					"AuditEntry{requestId='%s', rule='%s', decision=%s, tool='%s', time=%dns, reason='%s'}",
					requestId, ruleName, decision, toolName, evaluationTimeNanos, reason);
		}
	}

	/**
	 * Interface for custom AGT policy evaluation integration.
	 */
	@FunctionalInterface
	public interface AgtPolicyEvaluator {
		GovernanceDecision evaluate(GovernanceContext context);
	}

	private final GovernanceMode governanceMode;
	private final List<GovernanceRule> rules;
	private final GovernanceDecision defaultDecision;
	private final String failureResponse;
	private final int order;
	private final boolean auditEnabled;
	private final List<AuditEntry> auditLog;
	private final AgtPolicyEvaluator customAgtEvaluator;
	private final Set<String> deniedTools;
	private final Set<String> allowedTools;
	private final Map<String, Set<String>> toolParameterRestrictions;

	public GovernanceAdvisor(GovernanceMode governanceMode, List<GovernanceRule> rules,
			GovernanceDecision defaultDecision, String failureResponse, int order, boolean auditEnabled,
			AgtPolicyEvaluator customAgtEvaluator, Set<String> deniedTools, Set<String> allowedTools,
			Map<String, Set<String>> toolParameterRestrictions) {
		this.governanceMode = governanceMode;
		this.rules = rules != null ? new CopyOnWriteArrayList<>(rules) : new CopyOnWriteArrayList<>();
		this.defaultDecision = defaultDecision;
		this.failureResponse = failureResponse;
		this.order = order;
		this.auditEnabled = auditEnabled;
		this.auditLog = auditEnabled ? new CopyOnWriteArrayList<>() : null;
		this.customAgtEvaluator = customAgtEvaluator;
		this.deniedTools = deniedTools;
		this.allowedTools = allowedTools;
		this.toolParameterRestrictions = toolParameterRestrictions;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static GovernanceAdvisor defaultAzureGovernmentAdvisor() {
		return builder().governanceMode(GovernanceMode.AZURE_GOVERNMENT).build();
	}

	public static GovernanceAdvisor strictAdvisor() {
		return builder().denyAllByDefault(true).build();
	}

	public String getName() {
		return "GovernanceAdvisor";
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		GovernanceContext context = buildContext(chatClientRequest);
		GovernanceDecision decision = evaluateGovernance(context);

		if (decision == GovernanceDecision.DENIED) {
			logAudit(context, null, decision, "Policy enforcement denied the request");
			return createFailureResponse(chatClientRequest);
		}

		if (decision == GovernanceDecision.PENDING_APPROVAL) {
			logAudit(context, null, decision, "Request requires human approval");
			return createFailureResponse(chatClientRequest);
		}

		return callAdvisorChain.nextCall(chatClientRequest);
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		GovernanceContext context = buildContext(chatClientRequest);
		GovernanceDecision decision = evaluateGovernance(context);

		if (decision == GovernanceDecision.DENIED) {
			logAudit(context, null, decision, "Policy enforcement denied the request");
			return Flux.just(createFailureResponse(chatClientRequest));
		}

		if (decision == GovernanceDecision.PENDING_APPROVAL) {
			logAudit(context, null, decision, "Request requires human approval");
			return Flux.just(createFailureResponse(chatClientRequest));
		}

		return streamAdvisorChain.nextStream(chatClientRequest);
	}

	private GovernanceContext buildContext(ChatClientRequest request) {
		String requestId = request.context().getOrDefault("requestId", java.util.UUID.randomUUID().toString())
				.toString();

		String toolName = extractToolName(request);
		Map<String, Object> toolParams = extractToolParameters(request);
		List<String> availableTools = extractAvailableTools(request);
		String content = request.prompt().getContents();

		return new GovernanceContext(requestId, toolName, toolParams, availableTools, content);
	}

	@Nullable
	private String extractToolName(ChatClientRequest request) {
		Object toolName = request.context().get("toolName");
		if (toolName != null) {
			return toolName.toString();
		}
		return null;
	}

	@Nullable
	private Map<String, Object> extractToolParameters(ChatClientRequest request) {
		Object params = request.context().get("toolParameters");
		if (params instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) params;
			return map;
		}
		return null;
	}

	@Nullable
	private List<String> extractAvailableTools(ChatClientRequest request) {
		Object tools = request.context().get("availableTools");
		if (tools instanceof List) {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) tools;
			return list;
		}
		return List.of();
	}

	private GovernanceDecision evaluateGovernance(GovernanceContext context) {
		long startNanos = System.nanoTime();

		// 1. Check custom AGT evaluator first
		if (customAgtEvaluator != null) {
			GovernanceDecision decision = customAgtEvaluator.evaluate(context);
			if (decision != null) {
				long elapsedNanos = System.nanoTime() - startNanos;
				logAudit(context, "custom-evaluator", decision, "Custom AGT evaluator", elapsedNanos);
				return decision;
			}
		}

		// 2. Check denied/allowed tool lists
		if (context.getToolName() != null) {
			if (!CollectionUtils.isEmpty(deniedTools) && deniedTools.contains(context.getToolName())) {
				long elapsedNanos = System.nanoTime() - startNanos;
				logAudit(context, "denied-tools", GovernanceDecision.DENIED, "Tool is in denied list", elapsedNanos);
				return GovernanceDecision.DENIED;
			}

			if (!CollectionUtils.isEmpty(allowedTools) && !allowedTools.contains(context.getToolName())) {
				long elapsedNanos = System.nanoTime() - startNanos;
				logAudit(context, "allowed-tools", GovernanceDecision.DENIED, "Tool not in allowed list", elapsedNanos);
				return GovernanceDecision.DENIED;
			}

			// 3. Check parameter restrictions
			Set<String> restrictedParams = toolParameterRestrictions.get(context.getToolName());
			if (restrictedParams != null && context.getToolParameters() != null) {
				for (String key : context.getToolParameters().keySet()) {
					if (restrictedParams.contains(key)) {
						long elapsedNanos = System.nanoTime() - startNanos;
						logAudit(context, "param-restriction", GovernanceDecision.DENIED,
								"Parameter '" + key + "' is restricted for tool '" + context.getToolName() + "'",
								elapsedNanos);
						return GovernanceDecision.DENIED;
					}
				}
			}
		}

		// 4. Evaluate rules by priority (higher priority first)
		List<GovernanceRule> sortedRules = rules.stream().sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
				.toList();

		for (GovernanceRule rule : sortedRules) {
			GovernanceDecision ruleDecision = rule.evaluate(context);
			if (ruleDecision != null) {
				long elapsedNanos = System.nanoTime() - startNanos;
				logAudit(context, rule.getName(), ruleDecision,
						rule.getDescription() != null ? rule.getDescription() : "Rule matched", elapsedNanos);
				return ruleDecision;
			}
		}

		// 5. No matching rule found - use default
		long elapsedNanos = System.nanoTime() - startNanos;
		logAudit(context, "default", defaultDecision, "No matching rule, using default", elapsedNanos);
		return defaultDecision;
	}

	private void logAudit(GovernanceContext context, @Nullable String ruleName, GovernanceDecision decision,
			String reason) {
		logAudit(context, ruleName, decision, reason, 0);
	}

	private void logAudit(GovernanceContext context, @Nullable String ruleName, GovernanceDecision decision,
			String reason, long evaluationTimeNanos) {
		if (!auditEnabled || auditLog == null) {
			return;
		}
		AuditEntry entry = new AuditEntry(context.getRequestId(), ruleName, decision, context.getToolName(),
				context.getTimestamp(), reason, evaluationTimeNanos);
		auditLog.add(entry);
	}

	private ChatClientResponse createFailureResponse(ChatClientRequest chatClientRequest) {
		return ChatClientResponse.builder()
				.chatResponse(ChatResponse.builder()
						.generations(List.of(new Generation(new AssistantMessage(this.failureResponse))))
						.build())
				.context(Map.copyOf(chatClientRequest.context()))
				.build();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Get audit log entries.
	 */
	public List<AuditEntry> getAuditLog() {
		if (!auditEnabled) {
			return List.of();
		}
		return new ArrayList<>(auditLog);
	}

	/**
	 * Clear the audit log.
	 */
	public void clearAuditLog() {
		if (auditLog != null) {
			auditLog.clear();
		}
	}

	/**
	 * Get current governance mode.
	 */
	public GovernanceMode getGovernanceMode() {
		return governanceMode;
	}

	public static final class Builder {

		private GovernanceMode governanceMode = GovernanceMode.COMMERCIAL;

		private List<GovernanceRule> rules = new CopyOnWriteArrayList<>();

		private GovernanceDecision defaultDecision = GovernanceDecision.ALLOWED;

		private String failureResponse = DEFAULT_FAILURE_RESPONSE;

		private int order = DEFAULT_ORDER;

		private boolean auditEnabled = true;

		private AgtPolicyEvaluator customAgtEvaluator;

		private Set<String> deniedTools = Set.of();

		private Set<String> allowedTools = Set.of();

		private Map<String, Set<String>> toolParameterRestrictions = new ConcurrentHashMap<>();

		private Builder() {
		}

		/**
		 * Set the AGT governance mode.
		 */
		public Builder governanceMode(GovernanceMode governanceMode) {
			this.governanceMode = governanceMode;
			applyGovernanceModeDefaults(governanceMode);
			return this;
		}

		/**
		 * Add a governance rule.
		 */
		public Builder addRule(GovernanceRule rule) {
			this.rules.add(rule);
			return this;
		}

		/**
		 * Add multiple governance rules.
		 */
		public Builder addRules(List<GovernanceRule> rules) {
			this.rules.addAll(rules);
			return this;
		}

		/**
		 * Set the governance rules.
		 */
		public Builder rules(List<GovernanceRule> rules) {
			this.rules = new CopyOnWriteArrayList<>(rules);
			return this;
		}

		/**
		 * Set the default decision when no rule matches.
		 */
		public Builder defaultDecision(GovernanceDecision defaultDecision) {
			this.defaultDecision = defaultDecision;
			return this;
		}

		/**
		 * If true, deny all actions by default (strict mode).
		 */
		public Builder denyAllByDefault(boolean denyAllByDefault) {
			this.defaultDecision = denyAllByDefault ? GovernanceDecision.DENIED : GovernanceDecision.ALLOWED;
			return this;
		}

		/**
		 * Set the failure response message.
		 */
		public Builder failureResponse(String failureResponse) {
			this.failureResponse = failureResponse;
			return this;
		}

		/**
		 * Set the advisor order.
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Enable or disable audit logging.
		 */
		public Builder auditEnabled(boolean auditEnabled) {
			this.auditEnabled = auditEnabled;
			return this;
		}

		/**
		 * Set a custom AGT policy evaluator for advanced integration.
		 */
		public Builder customAgtEvaluator(AgtPolicyEvaluator customAgtEvaluator) {
			this.customAgtEvaluator = customAgtEvaluator;
			return this;
		}

		/**
		 * Add tools that should always be denied.
		 */
		public Builder deniedTools(Set<String> deniedTools) {
			this.deniedTools = deniedTools;
			return this;
		}

		/**
		 * Add a tool that should always be denied.
		 */
		public Builder denyTool(String toolName) {
			this.deniedTools = new java.util.HashSet<>(this.deniedTools);
			this.deniedTools.add(toolName);
			return this;
		}

		/**
		 * Set tools that are allowed (all others denied if non-empty).
		 */
		public Builder allowedTools(Set<String> allowedTools) {
			this.allowedTools = allowedTools;
			return this;
		}

		/**
		 * Add a tool that is allowed.
		 */
		public Builder allowTool(String toolName) {
			this.allowedTools = new java.util.HashSet<>(this.allowedTools);
			this.allowedTools.add(toolName);
			return this;
		}

		/**
		 * Restrict specific parameters for a tool.
		 */
		public Builder restrictToolParameters(String toolName, Set<String> parameterNames) {
			this.toolParameterRestrictions.put(toolName, parameterNames);
			return this;
		}

		/**
		 * Add a dangerous tool blocking rule.
		 */
		public Builder blockDangerousTools() {
			this.deniedTools = new java.util.HashSet<>(Set.of("execute_code", "delete_file", "execute_shell",
					"system_admin", "database_drop", "file_delete", "format_disk"));
			return this;
		}

		/**
		 * Add a rule to block tool by name pattern.
		 */
		public Builder blockTool(String toolNamePattern) {
			this.rules.add(new GovernanceRule("block-" + toolNamePattern, GovernanceDecision.DENIED, 100,
					"Block tool matching pattern: " + toolNamePattern,
					ctx -> ctx.getToolName() != null && ctx.getToolName().contains(toolNamePattern)));
			return this;
		}

		/**
		 * Add a rule to allow tool by name pattern.
		 */
		public Builder allowToolPattern(String toolNamePattern) {
			this.rules.add(new GovernanceRule("allow-" + toolNamePattern, GovernanceDecision.ALLOWED, 50,
					"Allow tool matching pattern: " + toolNamePattern,
					ctx -> ctx.getToolName() != null && ctx.getToolName().contains(toolNamePattern)));
			return this;
		}

		/**
		 * Add rule requiring approval for specific tools.
		 */
		public Builder requireApprovalFor(String toolName) {
			this.rules.add(new GovernanceRule("approval-" + toolName, GovernanceDecision.PENDING_APPROVAL, 90,
					"Require human approval for tool: " + toolName,
					ctx -> ctx.getToolName() != null && ctx.getToolName().equals(toolName)));
			return this;
		}

		private void applyGovernanceModeDefaults(GovernanceMode mode) {
			switch (mode) {
				case AZURE_GOVERNMENT -> {
					// Azure Government requires FedRAMP High - stricter defaults
					if (this.rules.isEmpty()) {
						this.deniedTools = new java.util.HashSet<>(Set.of("execute_code", "delete_file",
								"execute_shell", "format_disk", "system_admin"));
						this.rules.add(new GovernanceRule("az-gov-data-residency", GovernanceDecision.DENIED, 150,
								"Azure Government data residency enforcement",
								ctx -> ctx.getMetadata("region") != null
										&& !"usgovtexas".equals(ctx.getMetadata("region").toString())
										&& ctx.getToolName() != null && ctx.getToolName().contains("data_export")));
					}
				}
				case DOD -> {
					// DoD IL5/IL6 requires even stricter controls
					this.deniedTools = new java.util.HashSet<>(Set.of("execute_code", "delete_file", "execute_shell",
							"format_disk", "system_admin", "network_scan", "port_scan"));
					this.rules.add(new GovernanceRule("dod-classified-check", GovernanceDecision.DENIED, 200,
							"DoD classified data access check",
							ctx -> ctx.getMetadata("clearanceLevel") != null
									&& Integer.parseInt(ctx.getMetadata("clearanceLevel").toString()) < 5));
					this.rules.add(new GovernanceRule("dod-audit-required", GovernanceDecision.PENDING_APPROVAL, 180,
							"DoD requires audit trail for all tool calls",
							ctx -> true));
				}
				case COMMERCIAL, CUSTOM -> {
					// Commercial mode - default to blocking dangerous tools only
				}
			}
		}

		public GovernanceAdvisor build() {
			if (defaultDecision == GovernanceDecision.DENIED && allowedTools.isEmpty() && deniedTools.isEmpty()
					&& rules.isEmpty()) {
				Assert.state(false, "Strict (deny-all) mode requires at least one allowTool or custom rule");
			}
			return new GovernanceAdvisor(governanceMode, rules, defaultDecision, failureResponse, order,
					auditEnabled, customAgtEvaluator, deniedTools, allowedTools, toolParameterRestrictions);
		}
	}
}
