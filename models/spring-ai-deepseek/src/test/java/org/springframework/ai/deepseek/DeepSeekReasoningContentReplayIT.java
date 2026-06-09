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

package org.springframework.ai.deepseek;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the {@code reasoning_content} replay fix in
 * {@link DeepSeekChatModel} (issue #6016).
 *
 * <p>
 * The test stands up a {@link MockWebServer} that mimics an OpenAI-compatible DeepSeek
 * endpoint. On the first call it returns a response carrying {@code reasoning_content}.
 * On the second call it inspects the request body:
 * <ul>
 * <li>If the assistant turn carries {@code reasoning_content} back to the server, the
 * server returns HTTP 200 and the test asserts the body is intact.</li>
 * <li>If {@code reasoning_content} is missing from the second turn, the server returns
 * HTTP 400 (mirroring DeepSeek's real behaviour) and the test asserts the body omits the
 * field.</li>
 * </ul>
 */
class DeepSeekReasoningContentReplayIT {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final String FIRST_RESPONSE = "{" + "\"id\":\"cmpl-1\"," + "\"object\":\"chat.completion\","
			+ "\"created\":1700000000," + "\"model\":\"deepseek-reasoner\"," + "\"choices\":["
			+ "{\"index\":0,\"finish_reason\":\"stop\","
			+ "\"message\":{\"role\":\"assistant\",\"content\":\"100\",\"reasoning_content\":\"25 * 4 = 100.\"}}" + "],"
			+ "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}" + "}";

	private static final String SECOND_RESPONSE = "{" + "\"id\":\"cmpl-2\"," + "\"object\":\"chat.completion\","
			+ "\"created\":1700000001," + "\"model\":\"deepseek-reasoner\"," + "\"choices\":["
			+ "{\"index\":0,\"finish_reason\":\"stop\"," + "\"message\":{\"role\":\"assistant\",\"content\":\"20\"}}"
			+ "]," + "\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":5,\"total_tokens\":35}" + "}";

	private static final String ERROR_RESPONSE = "{"
			+ "\"error\":{\"message\":\"Missing reasoning_content\",\"type\":\"invalid_request_error\"}" + "}";

	private MockWebServer mockWebServer;

	private DeepSeekChatModel chatModel;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();

		DeepSeekApi api = DeepSeekApi.builder().baseUrl(this.mockWebServer.url("/").toString()).apiKey("TEST").build();
		this.chatModel = DeepSeekChatModel.builder().deepSeekApi(api).build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	@Test
	void secondTurnReplaysReasoningContentAndServerReturns200() throws InterruptedException, IOException {
		// First response: a typical DeepSeek-R1 response with reasoning_content
		// alongside a final answer.
		this.mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody(FIRST_RESPONSE));

		// Make the first call and capture the assistant message.
		Prompt firstPrompt = new Prompt(new UserMessage("What's 25 * 4?"));
		ChatResponse firstResponse = this.chatModel.call(firstPrompt);
		AssistantMessage firstAssistant = firstResponse.getResult().getOutput();
		// DeepSeek stores reasoning_content as a typed field on
		// DeepSeekAssistantMessage.
		assertThat(firstAssistant).isInstanceOf(DeepSeekAssistantMessage.class);
		assertThat(((DeepSeekAssistantMessage) firstAssistant).getReasoningContent())
			.as("receive side must capture reasoning_content")
			.isEqualTo("25 * 4 = 100.");

		// Build a follow-up prompt that includes the assistant message.
		AssistantMessage assistantMessage = firstAssistant;
		Prompt secondPrompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")));

		// Drain the first recorded request so the second enqueue hooks the second
		// call.
		this.mockWebServer.takeRequest(5, TimeUnit.SECONDS);

		// Make the second call in a background thread; the test thread will inspect
		// the recorded request body and enqueue the second response based on
		// whether reasoning_content is present.
		AtomicReference<Integer> secondStatus = new AtomicReference<>();
		AtomicReference<JsonNode> secondRequestBody = new AtomicReference<>();
		AtomicReference<ChatResponse> secondResponseHolder = new AtomicReference<>();
		AtomicReference<RuntimeException> secondError = new AtomicReference<>();
		Thread caller = new Thread(() -> {
			try {
				secondResponseHolder.set(this.chatModel.call(secondPrompt));
			}
			catch (RuntimeException ex) {
				secondError.set(ex);
			}
		});
		Thread enqueuer = new Thread(() -> {
			try {
				RecordedRequest recorded = this.mockWebServer.takeRequest(5, TimeUnit.SECONDS);
				if (recorded == null) {
					return;
				}
				JsonNode body = OBJECT_MAPPER.readTree(recorded.getBody().readUtf8());
				secondRequestBody.set(body);
				boolean hasReasoning = false;
				for (JsonNode msg : body.get("messages")) {
					if ("assistant".equals(msg.path("role").asText()) && msg.hasNonNull("reasoning_content")) {
						hasReasoning = true;
						break;
					}
				}
				int status = hasReasoning ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value();
				secondStatus.set(status);
				String responseBody = hasReasoning ? SECOND_RESPONSE : ERROR_RESPONSE;
				this.mockWebServer.enqueue(new MockResponse().setResponseCode(status)
					.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.setBody(responseBody));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
		caller.start();
		enqueuer.start();
		caller.join();
		enqueuer.join();

		ChatResponse secondResponse = secondResponseHolder.get();
		if (secondResponse == null) {
			// Allow the error to propagate; the assertion below will fail loudly
			// and we want a useful message.
			RuntimeException ex = secondError.get();
			throw new AssertionError("Second call failed unexpectedly", ex);
		}

		assertThat(secondStatus.get()).as("the mock server should have inspected the second request")
			.isEqualTo(HttpStatus.OK.value());
		JsonNode body = secondRequestBody.get();
		assertThat(body).as("second request body should be captured").isNotNull();

		// Find the assistant turn and assert reasoning_content is present.
		boolean assistantHasReasoning = false;
		for (JsonNode msg : body.get("messages")) {
			if ("assistant".equals(msg.path("role").asText())) {
				assertThat(msg.path("content").asText()).isEqualTo("100");
				if (msg.hasNonNull("reasoning_content")) {
					assistantHasReasoning = true;
					assertThat(msg.path("reasoning_content").asText()).isEqualTo("25 * 4 = 100.");
				}
			}
		}
		assertThat(assistantHasReasoning)
			.as("second request must include reasoning_content in the assistant turn (issue #6016)")
			.isTrue();

		assertThat(secondResponse.getResult().getOutput().getText()).isEqualTo("20");
	}

	@Test
	void secondTurnWithoutReasoningContentReturnsHttp400() throws InterruptedException, IOException {
		this.mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value())
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody(FIRST_RESPONSE));

		// Use a plain AssistantMessage without any reasoning anywhere, so the
		// createRequest() path omits reasoning_content from the second turn.
		AssistantMessage plainAssistant = AssistantMessage.builder().content("100").build();
		Prompt secondPrompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), plainAssistant, new UserMessage("Now divide that by 5")));

		// Drain the first request.
		this.mockWebServer.takeRequest(5, TimeUnit.SECONDS);

		AtomicReference<Integer> secondStatus = new AtomicReference<>();
		Thread caller = new Thread(() -> {
			try {
				this.chatModel.call(secondPrompt);
			}
			catch (RuntimeException expected) {
				// Expected: HTTP 400 surfaces as a client exception.
			}
		});
		Thread enqueuer = new Thread(() -> {
			try {
				RecordedRequest recorded = this.mockWebServer.takeRequest(5, TimeUnit.SECONDS);
				if (recorded == null) {
					return;
				}
				JsonNode body = OBJECT_MAPPER.readTree(recorded.getBody().readUtf8());
				boolean hasReasoning = false;
				for (JsonNode msg : body.get("messages")) {
					if ("assistant".equals(msg.path("role").asText()) && msg.hasNonNull("reasoning_content")) {
						hasReasoning = true;
						break;
					}
				}
				int status = hasReasoning ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value();
				secondStatus.set(status);
				String responseBody = hasReasoning ? "{\"choices\":[]}" : ERROR_RESPONSE;
				this.mockWebServer.enqueue(new MockResponse().setResponseCode(status)
					.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.setBody(responseBody));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
		caller.start();
		enqueuer.start();
		caller.join();
		enqueuer.join();

		assertThat(secondStatus.get()).as("server should return 400 when the assistant turn omits reasoning_content")
			.isEqualTo(HttpStatus.BAD_REQUEST.value());
	}

}
