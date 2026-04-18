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

/**
 * Spring Cloud Service Discovery integration for Spring AI MCP clients and A2A agents.
 *
 * <p>
 * This module provides dynamic service discovery integration for the Spring AI ecosystem,
 * enabling:
 * <ul>
 * <li><strong>MCP Client Factory</strong>: Automatically discovers MCP servers registered
 * in Spring Cloud service registries (Eureka, Nacos, Consul, Kubernetes) by metadata,
 * enabling hot-pluggable MCP tool discovery without hardcoded URLs.</li>
 * <li><strong>Agent Service</strong>: Resolves agent service names to endpoints via the
 * service registry and makes A2A (Agent-to-Agent) HTTP calls with load balancing,
 * circuit breaker, and retry support.</li>
 * </ul>
 *
 * <h3>Quick Start</h3>
 *
 * <p>
 * Register an MCP server in Eureka/Nacos with metadata:
 * <pre>
 * eureka:
 *   instance:
 *     metadata:
 *       type: mcp-server
 *       domain: weather
 * </pre>
 *
 * <p>
 * Then inject the factory in your Spring AI application:
 * <pre>{@code
 * @Autowired McpClientFactory factory;
 * List<McpSyncClient> clients = factory.createSyncClients();
 * }</pre>
 *
 * <h3>Configuration</h3>
 *
 * <pre>{@code
 * spring.ai.discovery:
 *   enabled: true
 *   mcp-metadata-key: type
 *   mcp-metadata-value: mcp-server
 *   agent-metadata-key: type
 *   agent-metadata-value: a2a-agent
 *   refresh-interval: 30s
 * }</pre>
 *
 * @author Anurag Saxena
 */
package org.springframework.ai.discovery;
