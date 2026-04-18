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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.discovery.agent.A2aRequest;
import org.springframework.ai.discovery.agent.A2aResponse;
import org.springframework.ai.discovery.agent.AgentService;
import org.springframework.ai.discovery.properties.SpringCloudDiscoveryProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AgentService}.
 */
class AgentServiceTests {

	private DiscoveryClient discoveryClient;

	private SpringCloudDiscoveryProperties properties;

	@BeforeEach
	void setUp() {
		this.discoveryClient = mock(DiscoveryClient.class);
		this.properties = new SpringCloudDiscoveryProperties();
	}

	@Test
	void discoverAgentServices_shouldFilterByMetadata() {
		ServiceInstance agentInstance = mock(ServiceInstance.class);
		when(agentInstance.getMetadata()).thenReturn(Map.of("type", "a2a-agent"));
		when(agentInstance.getHost()).thenReturn("localhost");
		when(agentInstance.getPort()).thenReturn(8080);
		when(agentInstance.isSecure()).thenReturn(false);

		ServiceInstance otherInstance = mock(ServiceInstance.class);
		when(otherInstance.getMetadata()).thenReturn(Map.of("type", "other"));

		when(this.discoveryClient.getServices()).thenReturn(List.of("order-agent", "inventory-svc"));
		when(this.discoveryClient.getInstances("order-agent")).thenReturn(List.of(agentInstance));
		when(this.discoveryClient.getInstances("inventory-svc")).thenReturn(List.of(otherInstance));

		var service = new AgentService(this.discoveryClient, this.properties,
				() -> Mono.just(WebClient.builder()));

		List<String> agents = service.discoverAgentServices();

		assertEquals(1, agents.size());
		assertEquals("order-agent", agents.get(0));
	}

	@Test
	void discoverAgentServices_shouldReturnEmptyWhenNoMatches() {
		ServiceInstance otherInstance = mock(ServiceInstance.class);
		when(otherInstance.getMetadata()).thenReturn(Map.of("type", "other"));
		when(this.discoveryClient.getServices()).thenReturn(List.of("order-svc"));
		when(this.discoveryClient.getInstances("order-svc")).thenReturn(List.of(otherInstance));

		var service = new AgentService(this.discoveryClient, this.properties,
				() -> Mono.just(WebClient.builder()));

		List<String> agents = service.discoverAgentServices();

		assertTrue(agents.isEmpty());
	}

	@Test
	void discoverAgentServices_shouldUseCustomMetadataKeyAndValue() {
		this.properties.setAgentMetadataKey("ai-role");
		this.properties.setAgentMetadataValue("agent");

		ServiceInstance agentInstance = mock(ServiceInstance.class);
		when(agentInstance.getMetadata()).thenReturn(Map.of("ai-role", "agent"));
		when(agentInstance.getHost()).thenReturn("localhost");
		when(agentInstance.getPort()).thenReturn(8080);

		when(this.discoveryClient.getServices()).thenReturn(List.of("my-agent"));
		when(this.discoveryClient.getInstances("my-agent")).thenReturn(List.of(agentInstance));

		var service = new AgentService(this.discoveryClient, this.properties,
				() -> Mono.just(WebClient.builder()));

		List<String> agents = service.discoverAgentServices();

		assertEquals(1, agents.size());
		assertEquals("my-agent", agents.get(0));
	}

	@Test
	void buildAgentUrl_shouldUseHttpsWhenSecure() {
		ServiceInstance instance = mock(ServiceInstance.class);
		when(instance.isSecure()).thenReturn(true);
		when(instance.getHost()).thenReturn("api.example.com");
		when(instance.getPort()).thenReturn(443);

		var service = new AgentService(this.discoveryClient, this.properties,
				() -> Mono.just(WebClient.builder()));

		String url = service.buildAgentUrl(instance);

		assertEquals("https://api.example.com:443/a2a", url);
	}

	@Test
	void buildAgentUrl_shouldAppendCustomEndpointPath() {
		this.properties.setA2aEndpointPath("/agent/api");
		ServiceInstance instance = mock(ServiceInstance.class);
		when(instance.isSecure()).thenReturn(false);
		when(instance.getHost()).thenReturn("localhost");
		when(instance.getPort()).thenReturn(9000);

		var service = new AgentService(this.discoveryClient, this.properties,
				() -> Mono.just(WebClient.builder()));

		String url = service.buildAgentUrl(instance);

		assertEquals("http://localhost:9000/agent/api", url);
	}

	@Test
	void selectInstance_shouldThrowWhenNoInstancesFound() {
		when(this.discoveryClient.getServices()).thenReturn(List.of("unknown-agent"));
		when(this.discoveryClient.getInstances("unknown-agent")).thenReturn(List.of());

		var service = new AgentService(this.discoveryClient, this.properties,
				() -> Mono.just(WebClient.builder()));

		assertThrows(RuntimeException.class, () -> service.selectInstance("unknown-agent"));
	}

	@Test
	void a2aRequest_shouldConstructCorrectly() {
		var request = new A2aRequest("invoke", Map.of("prompt", "hello"), "1");
		assertEquals("2.0", request.getJsonrpc());
		assertEquals("invoke", request.getMethod());
		assertEquals("1", request.getId());
		assertEquals("hello", request.getParams().get("prompt"));
	}

	@Test
	void a2aResponse_shouldIndicateSuccess() {
		var response = new A2aResponse(Map.of("answer", "42"));
		assertTrue(response.isSuccess());
		assertEquals("42", response.getResult().get("answer"));
	}

	@Test
	void a2aResponse_shouldIndicateError() {
		var response = new A2aResponse();
		response.setError(new A2aResponse.A2aError(-32600, "Invalid Request"));
		assertFalse(response.isSuccess());
		assertEquals(-32600, response.getError().getCode());
	}

}
