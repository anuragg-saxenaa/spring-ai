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
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for GitHub issue #5780 / Spring AI MCP.
 * 
 * Problem: WebClientStreamableHttpTransport drops valid SSE frames when the server
 * sends `data: {...}` without the `event:` field. Per SSE spec, missing event:
 * defaults to "message", but Spring AI only parses frames where event == "message"
 * explicitly. Frames with event == null are silently dropped, causing MCP client
 * initialize() responses to be ignored and "Did not observe any item" timeouts.
 * 
 * This test verifies that SSE frames without an explicit event: field are properly
 * parsed as message events (SSE spec compliance).
 *
 * @author Spring AI Team
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5780">Issue #5780</a>
 * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">SSE Spec</a>
 */
@Timeout(30)
public class WebClientStreamableHttpTransportSseNullEventIT {

	private String host;

	private HttpServer server;

	private AtomicBoolean useNullEvent = new AtomicBoolean(true);

	private AtomicReference<String> lastReceivedSessionId = new AtomicReference<>(null);

	private WebClientStreamableHttpTransport transport;

	private CountDownLatch initializeResponseReceived;

	@BeforeEach
	void startServer() throws IOException {
		this.initializeResponseReceived = new CountDownLatch(1);

		this.server = HttpServer.create(new InetSocketAddress(0), 0);

		// Configure SSE endpoint that sends responses without explicit event: field
		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("GET".equals(method)) {
				// SSE stream endpoint
				String requestSessionId = exchange.getRequestHeaders().getFirst(HttpHeaders.MCP_SESSION_ID);
				this.lastReceivedSessionId.set(requestSessionId);

				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.getResponseHeaders().set("Cache-Control", "no-cache");
				exchange.sendResponseHeaders(200, 0);

				// Send initial session id via SSE comment
				String sessionId = "test-session-" + System.currentTimeMillis();
				exchange.getResponseBody().write((":" + sessionId + "\n\n").getBytes());

				// Send initialize response WITHOUT event: field (defaults to "message")
				// This is the key test case - data without event:
				if (this.useNullEvent.get()) {
					// Per SSE spec, missing event: field means event type is "message"
					String sseResponse = "data: {\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}},\"id\":\"1\"}\n\n";
					exchange.getResponseBody().write(sseResponse.getBytes());
				}
				else {
					// Explicit event: message
					String sseResponse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}},\"id\":\"1\"}\n\n";
					exchange.getResponseBody().write(sseResponse.getBytes());
				}

				// Keep connection open for a bit
				Thread.sleep(500);
				exchange.close();
				return;
			}

			if ("POST".equals(method)) {
				String requestSessionId = exchange.getRequestHeaders().getFirst(HttpHeaders.MCP_SESSION_ID);
				this.lastReceivedSessionId.set(requestSessionId);

				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				String sessionId = "test-session-" + System.currentTimeMillis();
				exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, sessionId);
				exchange.sendResponseHeaders(200, 0);

				// Send response WITHOUT event: field (the bug case)
				String sseResponse;
				if (this.useNullEvent.get()) {
					sseResponse = "data: {\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}},\"id\":\"1\"}\n\n";
				}
				else {
					sseResponse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}},\"id\":\"1\"}\n\n";
				}
				exchange.getResponseBody().write(sseResponse.getBytes());

				Thread.sleep(100);
				exchange.close();
				return;
			}

			exchange.sendResponseHeaders(405, 0);
			exchange.close();
		});

		this.server.setExecutor(null);
		this.server.start();
		this.host = "http://localhost:" + this.server.getAddress().getPort();

		this.transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(this.host)).build();
	}

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
		StepVerifier.create(this.transport.closeGracefully()).verifyComplete();
	}

	/**
	 * Test that SSE frames with null event type (no event: field) are properly parsed.
	 * This is the regression test for issue #5780.
	 */
	@Test
	void testSseFramesWithNullEventTypeAreParsed() throws InterruptedException {
		// Ensure we're testing the null event case
		this.useNullEvent.set(true);

		AtomicReference<McpSchema.JSONRPCMessage> receivedMessage = new AtomicReference<>();

		// Connect and listen for messages
		Mono<Void> connect = this.transport.connect(msg -> {
			receivedMessage.set(msg.block());
			this.initializeResponseReceived.countDown();
			return Mono.empty();
		});

		// Create initialize request
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().build(), new McpSchema.Implementation("TestClient", "1.0.0"));
		var request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "1",
				initializeRequest);

		// Send initialize request
		StepVerifier.create(connect.then(this.transport.sendMessage(request)))
			.verifyComplete();

		// Wait for response or timeout
		boolean received = this.initializeResponseReceived.await(5, TimeUnit.SECONDS);

		// The key assertion: we should have received the message even though
		// the SSE frame had no event: field
		assertThat(received).as("Should have received initialize response from SSE frame with null event type")
			.isTrue();
		assertThat(receivedMessage.get()).isNotNull();
		assertThat(receivedMessage.get()).isInstanceOf(McpSchema.JSONRPCResponse.class);

		var response = (McpSchema.JSONRPCResponse) receivedMessage.get();
		assertThat(response.result()).isNotNull();
		assertThat(response.result().serverInfo()).isNotNull();
		assertThat(response.result().serverInfo().name()).isEqualTo("test");
	}

	/**
	 * Test that SSE frames with explicit event: message are still parsed correctly.
	 * This ensures the fix doesn't break the existing behavior.
	 */
	@Test
	void testSseFramesWithExplicitMessageEventAreParsed() throws InterruptedException {
		// Test with explicit event: message
		this.useNullEvent.set(false);

		AtomicReference<McpSchema.JSONRPCMessage> receivedMessage = new AtomicReference<>();

		// Connect and listen for messages
		Mono<Void> connect = this.transport.connect(msg -> {
			receivedMessage.set(msg.block());
			this.initializeResponseReceived.countDown();
			return Mono.empty();
		});

		// Create initialize request
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().build(), new McpSchema.Implementation("TestClient", "1.0.0"));
		var request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "1",
				initializeRequest);

		// Send initialize request
		StepVerifier.create(connect.then(this.transport.sendMessage(request)))
			.verifyComplete();

		// Wait for response or timeout
		boolean received = this.initializeResponseReceived.await(5, TimeUnit.SECONDS);

		// Should also work with explicit event: message
		assertThat(received).as("Should have received initialize response from SSE frame with explicit event: message")
			.isTrue();
		assertThat(receivedMessage.get()).isNotNull();
		assertThat(receivedMessage.get()).isInstanceOf(McpSchema.JSONRPCResponse.class);

		var response = (McpSchema.JSONRPCResponse) receivedMessage.get();
		assertThat(response.result()).isNotNull();
	}

	/**
	 * Test that empty string event type is treated as "message" (SSE spec compliance).
	 */
	@Test
	void testSseFramesWithEmptyEventTypeAreParsed() throws InterruptedException {
		// Override server behavior for this test
		this.server.removeContext("/mcp");
		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("GET".equals(method)) {
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);

				// Send response with empty event type
				String sseResponse = "event:\ndata: {\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}},\"id\":\"1\"}\n\n";
				exchange.getResponseBody().write(sseResponse.getBytes());

				Thread.sleep(500);
				exchange.close();
				return;
			}

			if ("POST".equals(method)) {
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				String sessionId = "test-session-" + System.currentTimeMillis();
				exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, sessionId);
				exchange.sendResponseHeaders(200, 0);

				// Empty event type (event:\n)
				String sseResponse = "event:\ndata: {\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}},\"id\":\"1\"}\n\n";
				exchange.getResponseBody().write(sseResponse.getBytes());

				Thread.sleep(100);
				exchange.close();
				return;
			}

			exchange.sendResponseHeaders(405, 0);
			exchange.close();
		});

		AtomicReference<McpSchema.JSONRPCMessage> receivedMessage = new AtomicReference<>();
		CountDownLatch responseLatch = new CountDownLatch(1);

		Mono<Void> connect = this.transport.connect(msg -> {
			receivedMessage.set(msg.block());
			responseLatch.countDown();
			return Mono.empty();
		});

		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().build(), new McpSchema.Implementation("TestClient", "1.0.0"));
		var request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "1",
				initializeRequest);

		StepVerifier.create(connect.then(this.transport.sendMessage(request)))
			.verifyComplete();

		boolean received = responseLatch.await(5, TimeUnit.SECONDS);

		assertThat(received).as("Should have received initialize response from SSE frame with empty event type")
			.isTrue();
		assertThat(receivedMessage.get()).isNotNull();
		assertThat(receivedMessage.get()).isInstanceOf(McpSchema.JSONRPCResponse.class);
	}
}