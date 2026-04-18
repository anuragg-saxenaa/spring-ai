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

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpTransportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.ai.discovery.properties.SpringCloudDiscoveryProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link McpClientFactory}.
 */
class McpClientFactoryTests {

	private DiscoveryClient discoveryClient;

	private ApplicationEventPublisher eventPublisher;

	private SpringCloudDiscoveryProperties properties;

	@BeforeEach
	void setUp() {
		this.discoveryClient = mock(DiscoveryClient.class);
		this.eventPublisher = mock(ApplicationEventPublisher.class);
		this.properties = new SpringCloudDiscoveryProperties();
	}

	@Test
	void discoverMcpServerServices_shouldFilterByMetadata() {
		ServiceInstance mcpInstance = mock(ServiceInstance.class);
		when(mcpInstance.getMetadata()).thenReturn(Map.of("type", "mcp-server"));
		when(mcpInstance.getHost()).thenReturn("localhost");
		when(mcpInstance.getPort()).thenReturn(8080);
		when(mcpInstance.isSecure()).thenReturn(false);

		ServiceInstance otherInstance = mock(ServiceInstance.class);
		when(otherInstance.getMetadata()).thenReturn(Map.of("type", "other"));
		when(otherInstance.getHost()).thenReturn("localhost");
		when(otherInstance.getPort()).thenReturn(9090);

		when(this.discoveryClient.getServices()).thenReturn(List.of("weather-mcp", "inventory-mcp", "order-svc"));
		when(this.discoveryClient.getInstances("weather-mcp")).thenReturn(List.of(mcpInstance));
		when(this.discoveryClient.getInstances("inventory-mcp")).thenReturn(List.of(mcpInstance));
		when(this.discoveryClient.getInstances("order-svc")).thenReturn(List.of(otherInstance));

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		List<String> services = factory.discoverMcpServerServices();

		assertEquals(2, services.size());
		assertTrue(services.contains("weather-mcp"));
		assertTrue(services.contains("inventory-mcp"));
		assertFalse(services.contains("order-svc"));
	}

	@Test
	void discoverMcpServerServices_shouldReturnEmptyWhenNoMatches() {
		ServiceInstance otherInstance = mock(ServiceInstance.class);
		when(otherInstance.getMetadata()).thenReturn(Map.of("type", "other"));
		when(this.discoveryClient.getServices()).thenReturn(List.of("order-svc"));
		when(this.discoveryClient.getInstances("order-svc")).thenReturn(List.of(otherInstance));

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		List<String> services = factory.discoverMcpServerServices();

		assertTrue(services.isEmpty());
	}

	@Test
	void buildMcpUrl_shouldUseHttpsWhenSecure() {
		ServiceInstance instance = mock(ServiceInstance.class);
		when(instance.isSecure()).thenReturn(true);
		when(instance.getHost()).thenReturn("api.example.com");
		when(instance.getPort()).thenReturn(443);

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		String url = factory.buildMcpUrl(instance);

		assertEquals("https://api.example.com:443/mcp", url);
	}

	@Test
	void buildMcpUrl_shouldUseHttpWhenNotSecure() {
		ServiceInstance instance = mock(ServiceInstance.class);
		when(instance.isSecure()).thenReturn(false);
		when(instance.getHost()).thenReturn("localhost");
		when(instance.getPort()).thenReturn(8080);

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		String url = factory.buildMcpUrl(instance);

		assertEquals("http://localhost:8080/mcp", url);
	}

	@Test
	void buildMcpUrl_shouldAppendCustomEndpointPath() {
		this.properties.setMcpEndpointPath("/events");
		ServiceInstance instance = mock(ServiceInstance.class);
		when(instance.isSecure()).thenReturn(false);
		when(instance.getHost()).thenReturn("mcp-server");
		when(instance.getPort()).thenReturn(9000);

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		String url = factory.buildMcpUrl(instance);

		assertEquals("http://mcp-server:9000/events", url);
	}

	@Test
	void selectInstance_shouldThrowWhenNoInstancesFound() {
		when(this.discoveryClient.getServices()).thenReturn(List.of("unknown-svc"));
		when(this.discoveryClient.getInstances("unknown-svc")).thenReturn(List.of());

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		assertThrows(McpTransportException.class, () -> factory.selectInstance("unknown-svc"));
	}

	@Test
	void discoverMcpServerServices_shouldUseCustomMetadataKeyAndValue() {
		this.properties.setMcpMetadataKey("ai-type");
		this.properties.setMcpMetadataValue("mcp");

		ServiceInstance mcpInstance = mock(ServiceInstance.class);
		when(mcpInstance.getMetadata()).thenReturn(Map.of("ai-type", "mcp"));
		when(mcpInstance.getHost()).thenReturn("localhost");
		when(mcpInstance.getPort()).thenReturn(8080);

		when(this.discoveryClient.getServices()).thenReturn(List.of("my-agent"));
		when(this.discoveryClient.getInstances("my-agent")).thenReturn(List.of(mcpInstance));

		var factory = new McpClientFactory(this.discoveryClient, null, this.properties, this.eventPublisher,
				() -> reactor.core.publisher.Mono.just(WebClient.builder()));

		List<String> services = factory.discoverMcpServerServices();

		assertEquals(1, services.size());
		assertEquals("my-agent", services.get(0));
	}

}
