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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.model.AssistantMessageReasoningExtractor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the reasoning_content replay fix in {@link DeepSeekChatModel}.
 *
 * <p>
 * DeepSeek returns HTTP 400 if {@code reasoning_content} is missing from a continuation
 * turn. The {@code createRequest(...)} serialiser must therefore forward the reasoning
 * trace from the previous turn. These tests verify the three contract states: baseline
 * (no assistant history), reasoning present, and reasoning absent.
 */
class DeepSeekReasoningContentReplayTests {

	private static DeepSeekChatModel newModel() {
		return DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();
	}

	private static DeepSeekChatOptions defaultOptions() {
		return DeepSeekChatOptions.builder().model("deepseek-reasoner").build();
	}

	private static ChatCompletionMessage findAssistantMessage(List<ChatCompletionMessage> messages) {
		return messages.stream()
			.filter(m -> m.role() == ChatCompletionMessage.Role.ASSISTANT)
			.findFirst()
			.orElseThrow();
	}

	@Test
	void singleTurnBaselineLeavesReasoningContentNull() {
		DeepSeekChatModel model = newModel();
		Prompt prompt = new Prompt(new UserMessage("hello"), defaultOptions());

		List<ChatCompletionMessage> messages = model.createRequest(prompt, false).messages();

		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).role()).isEqualTo(ChatCompletionMessage.Role.USER);
	}

	@Test
	void reasoningContentIsReplayedWhenAssistantMessageCarriesTypedField() {
		DeepSeekChatModel model = newModel();

		// Simulate the response from DeepSeek: DeepSeekAssistantMessage with a typed
		// reasoningContent field.
		AssistantMessage assistantMessage = DeepSeekAssistantMessage.builder()
			.content("100")
			.reasoningContent("25 * 4 = 100.")
			.build();

		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				defaultOptions());
		List<ChatCompletionMessage> messages = model.createRequest(prompt, false).messages();

		ChatCompletionMessage assistantRequest = findAssistantMessage(messages);
		assertThat(assistantRequest.reasoningContent()).isEqualTo("25 * 4 = 100.");
		assertThat(assistantRequest.content()).isEqualTo("100");
	}

	@Test
	void reasoningContentIsReplayedFromMetadataKeyWhenTypedFieldAbsent() {
		DeepSeekChatModel model = newModel();

		// Plain AssistantMessage (no subclass) carrying the reasoning on metadata,
		// e.g. forwarded from another ChatModel's history.
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("100")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "from metadata"))
			.build();

		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				defaultOptions());
		List<ChatCompletionMessage> messages = model.createRequest(prompt, false).messages();

		ChatCompletionMessage assistantRequest = findAssistantMessage(messages);
		assertThat(assistantRequest.reasoningContent()).isEqualTo("from metadata");
	}

	@Test
	void reasoningContentRemainsNullWhenAbsentFromAssistantHistory() {
		DeepSeekChatModel model = newModel();

		AssistantMessage assistantMessage = AssistantMessage.builder().content("100").build();

		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's 25 * 4?"), assistantMessage, new UserMessage("Now divide that by 5")),
				defaultOptions());
		List<ChatCompletionMessage> messages = model.createRequest(prompt, false).messages();

		ChatCompletionMessage assistantRequest = findAssistantMessage(messages);
		assertThat(assistantRequest.reasoningContent()).isNull();
	}

}
