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

package org.springframework.ai.discovery.properties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Spring Cloud Service Discovery integration with MCP
 * clients.
 *
 * @author Anurag Saxena
 */
@ConfigurationProperties(prefix = SpringCloudDiscoveryProperties.CONFIG_PREFIX)
public class SpringCloudDiscoveryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.discovery";

	/**
	 * Whether service discovery is enabled.
	 */
	private boolean enabled = true;

	/**
	 * Metadata key used to identify MCP servers (default: type).
	 */
	private String mcpMetadataKey = "type";

	/**
	 * Metadata value that MCP servers register with (default: mcp-server).
	 */
	private String mcpMetadataValue = "mcp-server";

	/**
	 * Metadata key used to identify A2A agents (default: type).
	 */
	private String agentMetadataKey = "type";

	/**
	 * Metadata value that A2A agents register with (default: a2a-agent).
	 */
	private String agentMetadataValue = "a2a-agent";

	/**
	 * MCP endpoint path suffix appended to discovered service URLs.
	 */
	private String mcpEndpointPath = "/mcp";

	/**
	 * A2A endpoint path suffix appended to discovered agent URLs.
	 */
	private String a2aEndpointPath = "/a2a";

	/**
	 * Refresh interval for polling discovered services (0 = disabled).
	 */
	@Nullable
	private Duration refreshInterval;

	/**
	 * Circuit breaker configuration for A2A calls.
	 */
	private CircuitBreaker circuitBreaker = new CircuitBreaker();

	/**
	 * Load balancer configuration.
	 */
	private LoadBalancer loadBalancer = new LoadBalancer();

	/**
	 * Retry configuration for A2A calls.
	 */
	private Retry retry = new Retry();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getMcpMetadataKey() {
		return this.mcpMetadataKey;
	}

	public void setMcpMetadataKey(String mcpMetadataKey) {
		this.mcpMetadataKey = mcpMetadataKey;
	}

	public String getMcpMetadataValue() {
		return this.mcpMetadataValue;
	}

	public void setMcpMetadataValue(String mcpMetadataValue) {
		this.mcpMetadataValue = mcpMetadataValue;
	}

	public String getAgentMetadataKey() {
		return this.agentMetadataKey;
	}

	public void setAgentMetadataKey(String agentMetadataKey) {
		this.agentMetadataKey = agentMetadataKey;
	}

	public String getAgentMetadataValue() {
		return this.agentMetadataValue;
	}

	public void setAgentMetadataValue(String agentMetadataValue) {
		this.agentMetadataValue = agentMetadataValue;
	}

	public String getMcpEndpointPath() {
		return this.mcpEndpointPath;
	}

	public void setMcpEndpointPath(String mcpEndpointPath) {
		this.mcpEndpointPath = mcpEndpointPath;
	}

	public String getA2aEndpointPath() {
		return this.a2aEndpointPath;
	}

	public void setA2aEndpointPath(String a2aEndpointPath) {
		this.a2aEndpointPath = a2aEndpointPath;
	}

	@Nullable
	public Duration getRefreshInterval() {
		return this.refreshInterval;
	}

	public void setRefreshInterval(@Nullable Duration refreshInterval) {
		this.refreshInterval = refreshInterval;
	}

	public CircuitBreaker getCircuitBreaker() {
		return this.circuitBreaker;
	}

	public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
		this.circuitBreaker = circuitBreaker;
	}

	public LoadBalancer getLoadBalancer() {
		return this.loadBalancer;
	}

	public void setLoadBalancer(LoadBalancer loadBalancer) {
		this.loadBalancer = loadBalancer;
	}

	public Retry getRetry() {
		return this.retry;
	}

	public void setRetry(Retry retry) {
		this.retry = retry;
	}

	public static class CircuitBreaker {

		/**
		 * Enable circuit breaker for A2A calls.
		 */
		private boolean enabled = true;

		/**
		 * Failure rate threshold percentage.
		 */
		private int failureRateThreshold = 50;

		/**
		 * Sliding window size for failure rate calculation.
		 */
		private int slidingWindowSize = 10;

		/**
		 * Minimum number of calls before circuit breaker can open.
		 */
		private int minimumNumberOfCalls = 5;

		/**
		 * Wait duration in open state before attempting half-open.
		 */
		@Nullable
		private Duration waitDurationInOpenState;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getFailureRateThreshold() {
			return this.failureRateThreshold;
		}

		public void setFailureRateThreshold(int failureRateThreshold) {
			this.failureRateThreshold = failureRateThreshold;
		}

		public int getSlidingWindowSize() {
			return this.slidingWindowSize;
		}

		public void setSlidingWindowSize(int slidingWindowSize) {
			this.slidingWindowSize = slidingWindowSize;
		}

		public int getMinimumNumberOfCalls() {
			return this.minimumNumberOfCalls;
		}

		public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
			this.minimumNumberOfCalls = minimumNumberOfCalls;
		}

		@Nullable
		public Duration getWaitDurationInOpenState() {
			return this.waitDurationInOpenState;
		}

		public void setWaitDurationInOpenState(@Nullable Duration waitDurationInOpenState) {
			this.waitDurationInOpenState = waitDurationInOpenState;
		}

	}

	public static class LoadBalancer {

		/**
		 * Load balancer zone preference (null = any zone).
		 */
		@Nullable
		private String zone;

		public @Nullable String getZone() {
			return this.zone;
		}

		public void setZone(@Nullable String zone) {
			this.zone = zone;
		}

	}

	public static class Retry {

		/**
		 * Enable retry for A2A calls.
		 */
		private boolean enabled = true;

		/**
		 * Maximum number of retry attempts.
		 */
		private int maxAttempts = 3;

		/**
		 * Initial retry interval.
		 */
		@Nullable
		private Duration initialInterval;

		/**
		 * Multiplier for exponential backoff.
		 */
		private double multiplier = 2.0;

		/**
		 * Maximum retry interval cap.
		 */
		@Nullable
		private Duration maxInterval;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getMaxAttempts() {
			return this.maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		@Nullable
		public Duration getInitialInterval() {
			return this.initialInterval;
		}

		public void setInitialInterval(@Nullable Duration initialInterval) {
			this.initialInterval = initialInterval;
		}

		public double getMultiplier() {
			return this.multiplier;
		}

		public void setMultiplier(double multiplier) {
			this.multiplier = multiplier;
		}

		@Nullable
		public Duration getMaxInterval() {
			return this.maxInterval;
		}

		public void setMaxInterval(@Nullable Duration maxInterval) {
			this.maxInterval = maxInterval;
		}

	}

}
