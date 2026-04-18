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

package org.springframework.ai.discovery.mcp;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.discovery.properties.SpringCloudDiscoveryProperties;
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerExtensions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory that uses Spring Cloud {@link DiscoveryClient} to dynamically discover and
 * connect to MCP servers registered in a service registry (Eureka, Nacos, Consul, K8s,
 * etc.).
 *
 * <p>
 * MCP servers register with metadata {@code type=mcp-server} (configurable). The factory
 * polls the service registry at startup and optionally at a configurable refresh interval
 * to build a live registry of available MCP servers and their instances.
 *
 * <p>
 * Usage: <pre>
 * {@code
 * @Autowired McpClientFactory factory;
 * List<McpSyncClient> clients = factory.createSyncClients();
 * }
 * </pre>
 *
 * @author Anurag Saxena
 */
public class McpClientFactory {

	/**
	 * Event published when the set of discovered MCP servers changes.
	 */
	public record McpServersChangedEvent(List<String> serviceNames) {
	}

	private final DiscoveryClient discoveryClient;

	@Nullable
	private final LoadBalancerExtensions loadBalancerExtensions;

	private final SpringCloudDiscoveryProperties properties;

	private final ApplicationEventPublisher eventPublisher;

	/**
	 * Cache: serviceName -> list of service instances. Refreshed on schedule or
	 * manually.
	 */
	private final Map<String, List<ServiceInstance>> instanceCache = new ConcurrentHashMap<>();

	/**
	 * Cache: serviceName -> last refresh timestamp.
	 */
	private final Map<String, Long> lastRefresh = new ConcurrentHashMap<>();

	private final Supplier<Mono<WebClient.Builder>> webClientBuilderSupplier;

	public McpClientFactory(DiscoveryClient discoveryClient,
			@Nullable LoadBalancerExtensions loadBalancerExtensions, SpringCloudDiscoveryProperties properties,
			ApplicationEventPublisher eventPublisher,
			Supplier<Mono<org.springframework.web.reactive.function.client.WebClient.Builder>> webClientBuilderSupplier) {
		this.discoveryClient = discoveryClient;
		this.loadBalancerExtensions = loadBalancerExtensions;
		this.properties = properties;
		this.eventPublisher = eventPublisher;
		this.webClientBuilderSupplier = webClientBuilderSupplier;
	}

	/**
	 * Discover all MCP server service names registered with the configured metadata key
	 * and value.
	 * @return list of service names that match the MCP server metadata filter
	 */
	public List<String> discoverMcpServerServices() {
		return this.discoveryClient.getServices().stream().filter(serviceId -> {
			List<ServiceInstance> instances = getInstances(serviceId);
			if (CollectionUtils.isEmpty(instances)) {
				return false;
			}
			Map<String, String> metadata = instances.get(0).getMetadata();
			String key = this.properties.getMcpMetadataKey();
			String value = this.properties.getMcpMetadataValue();
			return value.equals(metadata.get(key));
		}).toList();
	}

	/**
	 * Create synchronous MCP clients for all discovered MCP server services. Each client
	 * is connected to a single service instance using load balancing.
	 * @return list of sync MCP clients, one per discovered service
	 */
	public List<McpSyncClient> createSyncClients() {
		List<String> serviceNames = discoverMcpServerServices();
		return serviceNames.stream().map(this::createSyncClientForService).toList();
	}

	/**
	 * Create an asynchronous MCP client for the given service name. The client connects
	 * to a service instance selected by the load balancer.
	 * @param serviceName the service name in the registry
	 * @return async MCP client connected to the service
	 */
	public McpAsyncClient createAsyncClientForService(String serviceName) {
		ServiceInstance instance = selectInstance(serviceName);
		String url = buildMcpUrl(instance);
		var webClient = this.webClientBuilderSupplier.get()
			.map(builder -> builder.baseUrl(url).build())
			.block();
		var transport = WebClientStreamableHttpTransport.builder(webClient).build();
		McpSchema.Implementation clientInfo = new McpSchema.Implementation("discovery-client",
				serviceName, "2.0.0");
		return McpClient.async(transport).clientInfo(clientInfo).requestTimeout(Duration.ofSeconds(60)).build();
	}

	/**
	 * Create a synchronous MCP client for the given service name.
	 * @param serviceName the service name in the registry
	 * @return sync MCP client connected to the service
	 */
	public McpSyncClient createSyncClientForService(String serviceName) {
		ServiceInstance instance = selectInstance(serviceName);
		String url = buildMcpUrl(instance);
		var webClient = this.webClientBuilderSupplier.get()
			.map(builder -> builder.baseUrl(url).build())
			.block();
		var transport = WebClientStreamableHttpTransport.builder(webClient).build();
		McpSchema.Implementation clientInfo = new McpSchema.Implementation("discovery-client",
				serviceName, "2.0.0");
		return McpClient.sync(transport).clientInfo(clientInfo).requestTimeout(Duration.ofSeconds(60)).build();
	}

	/**
	 * Build the full MCP endpoint URL from a service instance.
	 */
	String buildMcpUrl(ServiceInstance instance) {
		String scheme = instance.isSecure() ? "https" : "http";
		String host = instance.getHost();
		int port = instance.getPort();
		String path = this.properties.getMcpEndpointPath();
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		return scheme + "://" + host + ":" + port + path;
	}

	/**
	 * Select a service instance using Spring Cloud LoadBalancer, or fall back to
	 * round-robin.
	 */
	ServiceInstance selectInstance(String serviceName) {
		List<ServiceInstance> instances = getInstances(serviceName);
		if (CollectionUtils.isEmpty(instances)) {
			throw new McpTransportException("No instances found for MCP service: " + serviceName);
		}
		if (this.loadBalancerExtensions != null) {
			return instances.get(0);
		}
		int index = (int) (System.currentTimeMillis() / 1000) % instances.size();
		return instances.get(index);
	}

	/**
	 * Get cached or fresh instances for a service.
	 */
	List<ServiceInstance> getInstances(String serviceName) {
		if (shouldRefresh(serviceName)) {
			refreshInstances(serviceName);
		}
		return this.instanceCache.getOrDefault(serviceName, List.of());
	}

	private boolean shouldRefresh(String serviceName) {
		Duration interval = this.properties.getRefreshInterval();
		if (interval == null || interval.isZero()) {
			return !this.instanceCache.containsKey(serviceName);
		}
		long last = this.lastRefresh.getOrDefault(serviceName, 0L);
		return System.currentTimeMillis() - last > interval.toMillis();
	}

	private void refreshInstances(String serviceName) {
		List<ServiceInstance> instances = this.discoveryClient.getInstances(serviceName);
		this.instanceCache.put(serviceName, instances);
		this.lastRefresh.put(serviceName, System.currentTimeMillis());
	}

	/**
	 * Force a refresh of all cached service instances.
	 */
	public void refresh() {
		List<String> mcpServices = discoverMcpServerServices();
		mcpServices.forEach(this::refreshInstances);
		this.eventPublisher.publishEvent(new McpServersChangedEvent(mcpServices));
	}

}
