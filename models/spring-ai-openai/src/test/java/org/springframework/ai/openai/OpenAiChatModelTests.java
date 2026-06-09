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

package org.springframework.ai.openai;

import java.util.List;
import java.util.Map;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.AssistantMessageReasoningExtractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenAiChatModel}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiChatModelTests {

	@Mock
	OpenAIClient openAiClient;

	@Mock
	OpenAIClientAsync openAiClientAsync;

	@Test
	void toolChoiceAuto() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("auto").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isAuto()).isTrue();
	}

	@Test
	void toolChoiceNone() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("none").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("SDK version does not support typed 'none' toolChoice");
	}

	@Test
	void toolChoiceRequired() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("required").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("SDK version does not support typed 'required' toolChoice");
	}

	@Test
	void toolChoiceFunction() {
		String json = """
				{
					"type": "function",
					"function": {
						"name": "my_function"
					}
				}
				""";
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice(json).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		ChatCompletionCreateParams request = chatModel.createRequest(new Prompt("test", options), false);
		assertThat(request.toolChoice()).isPresent();
		assertThat(request.toolChoice().get().isNamedToolChoice()).isTrue();
		assertThat(request.toolChoice().get().asNamedToolChoice().function().name()).isEqualTo("my_function");
	}

	@Test
	void toolChoiceInvalidJson() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").toolChoice("invalid-json").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		assertThatThrownBy(() -> chatModel.createRequest(new Prompt("test", options), false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Failed to parse toolChoice JSON");
	}

	@Test
	void reasoningContentReplayedWhenPresentInAssistantHistory() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("100")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "25 * 4 = 100."))
			.build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				options);

		ChatCompletionCreateParams request = chatModel.createRequest(prompt, false);

		ChatCompletionAssistantMessageParam assistantParam = request.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
		assertThat(assistantParam._additionalProperties()).containsEntry("reasoning_content",
				JsonValue.from("25 * 4 = 100."));
	}

	@Test
	void reasoningContentOmittedWhenAbsentFromAssistantHistory() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		AssistantMessage assistantMessage = AssistantMessage.builder().content("100").build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				options);

		ChatCompletionCreateParams request = chatModel.createRequest(prompt, false);

		ChatCompletionAssistantMessageParam assistantParam = request.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
		assertThat(assistantParam._additionalProperties()).doesNotContainKey("reasoning_content");
	}

	@Test
	void reasoningContentOmittedWhenAssistantMessageHasOnlyBlankMetadataValue() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("test-model").build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();

		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("100")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "   "))
			.build();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				options);

		ChatCompletionCreateParams request = chatModel.createRequest(prompt, false);

		ChatCompletionAssistantMessageParam assistantParam = request.messages()
			.stream()
			.filter(ChatCompletionMessageParam::isAssistant)
			.map(ChatCompletionMessageParam::asAssistant)
			.findFirst()
			.orElseThrow();
		assertThat(assistantParam._additionalProperties()).doesNotContainKey("reasoning_content");
	}

}
