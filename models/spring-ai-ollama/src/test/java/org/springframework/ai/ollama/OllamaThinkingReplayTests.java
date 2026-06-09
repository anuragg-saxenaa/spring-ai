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

package org.springframework.ai.ollama;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.AssistantMessageReasoningExtractor;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the thinking-trace replay fix in {@link OllamaChatModel}.
 *
 * <p>
 * Ollama surfaces chain-of-thought style output as the {@code thinking} field on
 * assistant messages. Multi-turn Qwen3 and other thinking-model workflows silently break
 * unless that field is replayed when the conversation history is sent back to the model.
 * These tests verify the three contract states: baseline (no assistant history), thinking
 * present, and thinking absent.
 */
class OllamaThinkingReplayTests {

	private final OllamaChatModel chatModel = OllamaChatModel.builder()
		.ollamaApi(OllamaApi.builder().build())
		.defaultOptions(OllamaChatOptions.builder().model("qwen3").build())
		.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
		.build();

	private static OllamaApi.Message findAssistantMessage(List<OllamaApi.Message> messages) {
		return messages.stream().filter(m -> m.role() == OllamaApi.Message.Role.ASSISTANT).findFirst().orElseThrow();
	}

	@Test
	void singleTurnBaselineHasNoAssistantMessage() {
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("hello"));
		var request = this.chatModel.ollamaChatRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.messages().get(0).role()).isEqualTo(OllamaApi.Message.Role.USER);
	}

	@Test
	void thinkingFieldIsReplayedWhenAssistantMessageMetadataCarriesIt() {
		// Simulate the receive side having stored the thinking trace on
		// AssistantMessage.properties under THINKING_METADATA_KEY (the same key the
		// receive side of OllamaChatModel uses upstream).
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("The answer is 42.")
			.properties(Map.of(AssistantMessageReasoningExtractor.THINKING_METADATA_KEY, "Step 1: ..."))
			.build();
		var prompt = this.chatModel
			.buildRequestPrompt(new Prompt(List.of(new UserMessage("What is the answer?"), assistantMessage)));

		var request = this.chatModel.ollamaChatRequest(prompt, false);

		OllamaApi.Message assistantRequest = findAssistantMessage(request.messages());
		assertThat(assistantRequest.thinking()).isEqualTo("Step 1: ...");
		assertThat(assistantRequest.content()).isEqualTo("The answer is 42.");
	}

	@Test
	void thinkingFieldIsReplayedFromReasoningContentKey() {
		// OpenAI-style metadata should also be honoured: a downstream ChatModel might
		// hand back a generic AssistantMessage whose reasoning trace lives under
		// REASONING_CONTENT_KEY.
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("answer")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "reasoning content"))
			.build();
		var prompt = this.chatModel.buildRequestPrompt(
				new Prompt(List.of(new UserMessage("Q"), assistantMessage, new UserMessage("follow up"))));

		var request = this.chatModel.ollamaChatRequest(prompt, false);

		OllamaApi.Message assistantRequest = findAssistantMessage(request.messages());
		assertThat(assistantRequest.thinking()).isEqualTo("reasoning content");
	}

	@Test
	void thinkingFieldRemainsNullWhenAbsentFromAssistantHistory() {
		AssistantMessage assistantMessage = AssistantMessage.builder().content("answer").build();
		var prompt = this.chatModel.buildRequestPrompt(
				new Prompt(List.of(new UserMessage("Q"), assistantMessage, new UserMessage("follow up"))));

		var request = this.chatModel.ollamaChatRequest(prompt, false);

		OllamaApi.Message assistantRequest = findAssistantMessage(request.messages());
		assertThat(assistantRequest.thinking()).isNull();
	}

	@Test
	void thinkingFieldRemainsNullWhenAssistantMessageHasOnlyBlankMetadataValue() {
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("answer")
			.properties(Map.of(AssistantMessageReasoningExtractor.THINKING_METADATA_KEY, "   "))
			.build();
		var prompt = this.chatModel.buildRequestPrompt(
				new Prompt(List.of(new UserMessage("Q"), assistantMessage, new UserMessage("follow up"))));

		var request = this.chatModel.ollamaChatRequest(prompt, false);

		OllamaApi.Message assistantRequest = findAssistantMessage(request.messages());
		assertThat(assistantRequest.thinking()).isNull();
	}

}
