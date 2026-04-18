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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for issue #5775: WebClientStreamableHttpTransport silently drops
 * body-level errors.
 *
 * <p>
 * Before the fix, when a body-level error occurred (malformed JSON, SSE parse error,
 * DataBufferLimitException), the {@code onErrorComplete} handler would swallow the error
 * after calling {@code sink.error(t)}. The sink was notified, but no message reached the
 * handler, so {@code McpClientSession.pendingResponses} was never resolved — causing the
 * caller to hang for the full requestTimeout (300 seconds).
 *
 * <p>
 * After the fix, body-level errors for requests emit a synthetic {@code JSONRPCResponse}
 * with {@code INTERNAL_ERROR} code so pendingResponses resolve immediately. Notifications
 * are silently dropped as per the spec.
 *
 * @author Anurag Saxena
 */
@Timeout(15)
public class WebClientStreamableHttpBodyErrorIT {

	private String host;

	private HttpServer server;

	private AtomicReference<String> currentServerSessionId = new AtomicReference<>(null);

	private McpClientTransport transport;

	private CountDownLatch postLatch;

	@BeforeEach
	void startServer() throws IOException {
		this.postLatch = new CountDownLatch(1);
		this.server = HttpServer.create(new InetSocketAddress(0), 0);

		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("GET".equals(method)) {
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(405, 0);
				exchange.close();
				return;
			}

			String requestSessionId = exchange.getRequestHeaders().getFirst(HttpHeaders.MCP_SESSION_ID);
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

			this.postLatch.countDown();

			String responseSessionId = this.currentServerSessionId.get();
			if (responseSessionId != null) {
				exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, responseSessionId);
			}

			if ("POST".equals(method) && "application/json".equals(contentType)) {
				byte[] requestBody = exchange.getRequestBody().readAllBytes();
				String requestBodyStr = new String(requestBody, StandardCharsets.UTF_8);

				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");

				// Send a session ID header if we have one
				if (responseSessionId != null) {
					exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, responseSessionId);
				}

				// Send malformed SSE that would cause a parse error in the client
				// This simulates a body-level error like malformed JSON, SSE parse
				// errors, DataBufferLimitException
				String malformedSse = "id: 1\nevent: message\ndata: {invalid json here\n\n";
				byte[] responseBytes = malformedSse.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, responseBytes.length);
				exchange.getResponseBody().write(responseBytes);
			}
			else {
				exchange.sendResponseHeaders(400, 0);
			}
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
	 * Regression test: body-level error should resolve pending response with
	 * INTERNAL_ERROR, not hang indefinitely.
	 *
	 * <p>
	 * Before fix: caller hangs for requestTimeout (300s) because pendingResponses is
	 * never resolved.
	 *
	 * <p>
	 * After fix: pendingResponses resolves with a synthetic JSONRPCResponse containing
	 * INTERNAL_ERROR, and the stream completes immediately.
	 */
	@Test
	void testBodyLevelErrorResolvesPendingResponseWithError() throws InterruptedException {
		// Establish a session first
		this.currentServerSessionId.set("test-session-body-error");
		StepVerifier.create(this.transport.connect(msg -> msg)).verifyComplete();

		// Send a request message that will receive a malformed SSE response
		McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_INITIALIZE, "req-001",
				new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
						McpSchema.ClientCapabilities.builder().roots(true).build(),
						new McpSchema.Implementation("Test", "1.0")));

		AtomicReference<McpSchema.JSONRPCMessage> receivedMessage = new AtomicReference<>();

		// Connect handler to capture received messages
		StepVerifier.create(this.transport.connect(msg -> Flux.just(msg).doOnNext(receivedMessage::set)))
			.verifyComplete();

		// Send the request - should NOT hang even though body is malformed
		// Before fix: hangs for 300s (requestTimeout)
		// After fix: resolves with INTERNAL_ERROR response
		StepVerifier.create(this.transport.sendMessage(request), 5).expectError();

		// Verify the POST was made
		assertThat(this.postLatch.await(5, TimeUnit.SECONDS)).isTrue();
	}

	/**
	 * Verify that notifications (no request ID) are silently dropped on body-level
	 * errors, per the spec.
	 */
	@Test
	void testNotificationBodyErrorSilentlyDropped() throws InterruptedException {
		this.currentServerSessionId.set("test-session-notif");

		AtomicReference<McpSchema.JSONRPCMessage> receivedMessage = new AtomicReference<>();

		StepVerifier.create(this.transport.connect(msg -> Flux.just(msg).doOnNext(receivedMessage::set)))
			.verifyComplete();

		// Send a notification (no ID)
		McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification("notifications/game", null);

		// Should complete successfully (not hang, not error)
		// After fix: notification body errors are silently dropped
		StepVerifier.create(this.transport.sendMessage(notification)).expectError();

		assertThat(this.postLatch.await(5, TimeUnit.SECONDS)).isTrue();
	}

}
