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

package org.springframework.ai.mcp.client.webflux.transport;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for GitHub Issue #5780.
 * SSE frames without explicit `event:` field should be treated as "message" events
 * and parsed correctly (per SSE spec, omitting event: defaults to "message").
 *
 * @author ENG (RedOS)
 */
@Timeout(10)
public class WebClientStreamableHttpTransportSseEventTest {

	private String host;

	private HttpServer server;

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(0), 0);
		int port = ((java.net.InetSocketAddress) server.getAddress()).getPort();
		this.host = "http://localhost:" + port;
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	/**
	 * Test that SSE frames without `event:` field (null event type) are parsed correctly.
	 * This is the core regression test for issue #5780.
	 *
	 * Server sends: data: {...}  (no event: line)
	 * Expected: parse() should treat this as a message event and deserialize the JSON-RPC payload.
	 */
	@Test
	void testSseFrameWithoutEventFieldIsParsed() throws Exception {
		java.util.concurrent.CountDownLatch requestReceived = new java.util.concurrent.CountDownLatch(1);

		// Set up server that sends SSE without event: field
		server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("POST".equals(method)) {
				requestReceived.countDown();
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);
				// SSE frame WITHOUT event: field — this is the regression scenario for #5780
				String sseWithoutEvent = "data: {\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}}}\n\n";
				exchange.getResponseBody().write(sseWithoutEvent.getBytes());
				exchange.getResponseBody().close();
			}
			else if ("DELETE".equals(method)) {
				exchange.sendResponseHeaders(204, -1);
			}
			else {
				exchange.sendResponseHeaders(405, -1);
			}
		});

		var transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(this.host)).build();

		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_06_18,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("MCP Client", "0.3.1"));
		var testMessage = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE,
				"test-id", initializeRequest);

		// Assert: transport should successfully parse the SSE frame without event: field
		// Previously this would time out because parse() dropped null-event frames
		StepVerifier.create(transport.sendMessage(testMessage))
			.assertNext(msg -> {
				assertThat(msg).isInstanceOf(McpSchema.JSONRPCResponse.class);
				McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) msg;
				assertThat(response.id()).isEqualTo("test-id");
				assertThat(response.result()).isNotNull();
				McpSchema.InitializeResult result = (McpSchema.InitializeResult) response.result();
				assertThat(result.serverInfo().name()).isEqualTo("test");
			})
			.verifyComplete();

		StepVerifier.create(transport.closeGracefully()).verifyComplete();
	}

	/**
	 * Test that empty event type string is also treated as "message" (SSE spec compliance).
	 */
	@Test
	void testSseFrameWithEmptyEventFieldIsParsed() throws Exception {
		java.util.concurrent.CountDownLatch requestReceived = new java.util.concurrent.CountDownLatch(1);

		server.createContext("/mcp2", exchange -> {
			String method = exchange.getRequestMethod();

			if ("POST".equals(method)) {
				requestReceived.countDown();
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);
				// SSE frame with empty event: field
				String sseWithEmptyEvent = "event:\ndata: {\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"empty-event\",\"version\":\"1.0.0\"}}}\n\n";
				exchange.getResponseBody().write(sseWithEmptyEvent.getBytes());
				exchange.getResponseBody().close();
			}
			else if ("DELETE".equals(method)) {
				exchange.sendResponseHeaders(204, -1);
			}
			else {
				exchange.sendResponseHeaders(405, -1);
			}
		});

		var transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(this.host))
			.endpoint("/mcp2")
			.build();

		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("MCP Client", "0.3.1"));
		var testMessage = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE,
				"test-id-2", initializeRequest);

		StepVerifier.create(transport.sendMessage(testMessage))
			.assertNext(msg -> {
				assertThat(msg).isInstanceOf(McpSchema.JSONRPCResponse.class);
				McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) msg;
				assertThat(response.id()).isEqualTo("test-id-2");
			})
			.verifyComplete();

		StepVerifier.create(transport.closeGracefully()).verifyComplete();
	}

	/**
	 * Test that explicit "message" event still works (backward compatibility).
	 */
	@Test
	void testSseFrameWithExplicitMessageEventIsParsed() throws Exception {
		java.util.concurrent.CountDownLatch requestReceived = new java.util.concurrent.CountDownLatch(1);

		server.createContext("/mcp3", exchange -> {
			String method = exchange.getRequestMethod();

			if ("POST".equals(method)) {
				requestReceived.countDown();
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);
				// SSE frame with explicit event: message
				String sseWithMessageEvent = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":\"3\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"explicit-message\",\"version\":\"1.0.0\"}}}\n\n";
				exchange.getResponseBody().write(sseWithMessageEvent.getBytes());
				exchange.getResponseBody().close();
			}
			else if ("DELETE".equals(method)) {
				exchange.sendResponseHeaders(204, -1);
			}
			else {
				exchange.sendResponseHeaders(405, -1);
			}
		});

		var transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(this.host))
			.endpoint("/mcp3")
			.build();

		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("MCP Client", "0.3.1"));
		var testMessage = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE,
				"test-id-3", initializeRequest);

		StepVerifier.create(transport.sendMessage(testMessage))
			.assertNext(msg -> {
				assertThat(msg).isInstanceOf(McpSchema.JSONRPCResponse.class);
				McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) msg;
				assertThat(response.id()).isEqualTo("test-id-3");
			})
			.verifyComplete();

		StepVerifier.create(transport.closeGracefully()).verifyComplete();
	}
}