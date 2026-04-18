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

package org.springframework.ai.discovery.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.discovery.properties.SpringCloudDiscoveryProperties;
import org.springframework.ai.discovery.agent.A2aRequest;
import org.springframework.ai.discovery.agent.A2aResponse;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Service for A2A (Agent-to-Agent) communication using Spring Cloud Service Discovery.
 *
 * <p>
 * Agents register in the service registry with metadata {@code type=a2a-agent}. This
 * service resolves agent names to service URLs and makes HTTP calls using a
 * load-balancer-aware {@link WebClient} with optional circuit breaker and retry support.
 *
 * <p>
 * Usage: <pre>
 * {@code
 * @Autowired AgentService agentService;
 * A2aResponse response = agentService.callAgent("order-agent", request);
 * }
 * </pre>
 *
 * @author Anurag Saxena
 */
public class AgentService {

	private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

	private final DiscoveryClient discoveryClient;

	private final SpringCloudDiscoveryProperties properties;

	private final Supplier<Mono<WebClient.Builder>> webClientBuilderSupplier;

	private final Map<String, List<ServiceInstance>> instanceCache = new ConcurrentHashMap<>();

	private final Map<String, Long> lastRefresh = new ConcurrentHashMap<>();

	public AgentService(DiscoveryClient discoveryClient, SpringCloudDiscoveryProperties properties,
			Supplier<Mono<WebClient.Builder>> webClientBuilderSupplier) {
		this.discoveryClient = discoveryClient;
		this.properties = properties;
		this.webClientBuilderSupplier = webClientBuilderSupplier;
	}

	/**
	 * Discover all registered A2A agent services in the service registry.
	 * @return list of agent service names
	 */
	public List<String> discoverAgentServices() {
		return this.discoveryClient.getServices().stream().filter(serviceId -> {
			List<ServiceInstance> instances = this.discoveryClient.getInstances(serviceId);
			if (instances.isEmpty()) {
				return false;
			}
			Map<String, String> metadata = instances.get(0).getMetadata();
			String key = this.properties.getAgentMetadataKey();
			String value = this.properties.getAgentMetadataValue();
			return value.equals(metadata.get(key));
		}).toList();
	}

	/**
	 * Call an agent by its service name with a JSON-RPC A2A request.
	 * @param agentName the service name of the agent
	 * @param request the A2A request payload
	 * @return the A2A response
	 */
	public A2aResponse callAgent(String agentName, A2aRequest request) {
		ServiceInstance instance = selectInstance(agentName);
		String url = buildAgentUrl(instance);
		WebClient webClient = this.webClientBuilderSupplier.get()
			.map(builder -> builder.baseUrl(url).build())
			.block();
		return callAgentInternal(webClient, request);
	}

	/**
	 * Reactive version of {@link #callAgent(String, A2aRequest)}.
	 */
	public Mono<A2aResponse> callAgentReactive(String agentName, A2aRequest request) {
		return Mono.justOrEmpty(selectInstanceReactive(agentName)).flatMap(instance -> {
			String url = buildAgentUrl(instance);
			WebClient webClient = this.webClientBuilderSupplier.get()
				.map(builder -> builder.baseUrl(url).build())
				.block();
			return Mono.just(callAgentInternal(webClient, request));
		});
	}

	A2aResponse callAgentInternal(WebClient webClient, A2aRequest request) {
		if (this.properties.getRetry().isEnabled()) {
			return webClient.post().uri("").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request).retrieve().bodyToMono(A2aResponse.class)
				.retry(this.properties.getRetry().getMaxAttempts())
				.block();
		}
		return webClient.post().uri("").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).retrieve().bodyToMono(A2aResponse.class)
			.block();
	}

	String buildAgentUrl(ServiceInstance instance) {
		String scheme = instance.isSecure() ? "https" : "http";
		String host = instance.getHost();
		int port = instance.getPort();
		String path = this.properties.getA2aEndpointPath();
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		return scheme + "://" + host + ":" + port + path;
	}

	ServiceInstance selectInstance(String serviceName) {
		List<ServiceInstance> instances = getInstances(serviceName);
		if (instances.isEmpty()) {
			throw new RuntimeException("No instances found for agent service: " + serviceName);
		}
		int index = (int) (System.currentTimeMillis() / 1000) % instances.size();
		return instances.get(index);
	}

	@Nullable
	ServiceInstance selectInstanceReactive(String serviceName) {
		List<ServiceInstance> instances = getInstances(serviceName);
		if (instances.isEmpty()) {
			return null;
		}
		int index = (int) (System.currentTimeMillis() / 1000) % instances.size();
		return instances.get(index);
	}

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
	 * Force refresh of cached instances for all agent services.
	 */
	public void refresh() {
		discoverAgentServices().forEach(this::refreshInstances);
	}

}
