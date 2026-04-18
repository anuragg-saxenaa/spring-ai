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

package org.springframework.ai.discovery.mcp.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import reactor.core.publisher.Mono;

import org.springframework.ai.discovery.mcp.McpClientFactory;
import org.springframework.ai.discovery.properties.SpringCloudDiscoveryProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.McpSyncToolsChangeEventEmmiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerExtensions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for {@link McpClientFactory} with Spring Cloud Service Discovery.
 *
 * <p>
 * This configuration activates when:
 * <ul>
 * <li>{@code spring.ai.discovery.enabled=true}
 * <li>{@link DiscoveryClient} is on the classpath
 * <li>{@link McpSyncClient} is on the classpath
 * </ul>
 *
 * <p>
 * The factory discovers MCP servers by querying the {@link DiscoveryClient} for services
 * whose instances carry the metadata key/value pair configured via
 * {@code spring.ai.discovery.mcp-metadata-key} and
 * {@code spring.ai.discovery.mcp-metadata-value} (defaults: {@code type=mcp-server}).
 *
 * <p>
 * Discovered services are exposed as {@link McpSyncClient} beans that can be injected into
 * ChatClients or used directly. Tools are hot-pluggable: calling {@link McpClientFactory#refresh()}
 * will re-query the registry and publish a {@link McpClientFactory.McpServersChangedEvent}.
 *
 * @author Anurag Saxena
 * @see McpClientFactory
 * @see SpringCloudDiscoveryProperties
 * @see DiscoveryClient
 */
@AutoConfiguration
@EnableConfigurationProperties(SpringCloudDiscoveryProperties.class)
@ConditionalOnProperty(prefix = SpringCloudDiscoveryProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(McpSyncClient.class)
public class McpDiscoveryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public McpClientFactory mcpClientFactory(DiscoveryClient discoveryClient,
			ObjectProvider<LoadBalancerExtensions> loadBalancerExtensionsProvider,
			SpringCloudDiscoveryProperties properties, ApplicationEventPublisher eventPublisher,
			ObjectProvider<Supplier<Mono<WebClient.Builder>>> webClientBuilderSupplierProvider) {
		return new McpClientFactory(discoveryClient, loadBalancerExtensionsProvider.getIfAvailable(() -> null),
				properties, eventPublisher,
				() -> webClientBuilderSupplierProvider.getIfAvailable(() -> Mono.just(WebClient.builder())));
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = SpringCloudDiscoveryProperties.CONFIG_PREFIX + ".mcp", name = "type",
			havingValue = "SYNC", matchIfMissing = true)
	public List<McpSyncClient> discoveryMcpSyncClients(McpClientFactory factory) {
		return factory.createSyncClients();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = SpringCloudDiscoveryProperties.CONFIG_PREFIX + ".mcp", name = "type",
			havingValue = "ASYNC")
	public List<McpAsyncClient> discoveryMcpAsyncClients(McpClientFactory factory) {
		List<McpAsyncClient> clients = new ArrayList<>();
		for (String serviceName : factory.discoverMcpServerServices()) {
			clients.add(factory.createAsyncClientForService(serviceName));
		}
		return clients;
	}

}
