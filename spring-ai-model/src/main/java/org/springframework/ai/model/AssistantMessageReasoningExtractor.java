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

package org.springframework.ai.model;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.util.StringUtils;

/**
 * Utility for reading reasoning / thinking content from an {@link AssistantMessage} that
 * was produced by a model which supports chain-of-thought style output (DeepSeek-R1,
 * Qwen3, DeepSeek-V4, OpenAI o1 / o3 via OpenAI-compat, Ollama thinking models, etc.).
 *
 * <p>
 * Different providers surface the reasoning trace in different shapes:
 * <ul>
 * <li>DeepSeek's {@code DeepSeekAssistantMessage} subclass exposes it as a typed field
 * ({@code getReasoningContent()}).</li>
 * <li>Ollama surfaces the trace as the {@code "thinking"} key on the assistant message's
 * metadata map.</li>
 * <li>OpenAI (and OpenAI-compatible) providers stash it under the
 * {@code "reasoningContent"} metadata key.</li>
 * </ul>
 *
 * <p>
 * When the request side of a multi-turn agentic loop serialises the conversation history
 * back into the next request, this helper extracts the reasoning trace so the provider
 * can continue the agent's thought process. DeepSeek in particular returns HTTP 400 if
 * {@code reasoning_content} is missing from a continuation turn.
 *
 * <p>
 * Precedence:
 * <ol>
 * <li>Subclass-specific typed field (e.g. {@code DeepSeekAssistantMessage}).</li>
 * <li>{@link AssistantMessage#getMetadata() metadata} key
 * {@value #REASONING_CONTENT_KEY}.</li>
 * <li>{@link AssistantMessage#getMetadata() metadata} key
 * {@value #THINKING_METADATA_KEY}.</li>
 * </ol>
 *
 * <p>
 * Blank or whitespace-only values are treated as absent.
 *
 * @author Anurag Saxena
 * @since 2.0.0
 */
public final class AssistantMessageReasoningExtractor {

	/**
	 * Metadata key for the OpenAI-style reasoning content. Used by the
	 * {@code OpenAiChatModel} when receiving responses and when replaying assistant
	 * history.
	 */
	public static final String REASONING_CONTENT_KEY = "reasoningContent";

	/**
	 * Metadata key for the Ollama-style thinking content. Used by the
	 * {@code OllamaChatModel} when receiving responses and when replaying assistant
	 * history.
	 */
	public static final String THINKING_METADATA_KEY = "thinking";

	private AssistantMessageReasoningExtractor() {
	}

	/**
	 * Extract the reasoning / thinking content for the given assistant message, or
	 * {@code null} if neither a typed field nor a recognised metadata key carries a
	 * non-blank value.
	 * @param message the assistant message
	 * @return the reasoning content, or {@code null} when absent
	 */
	public static @Nullable String extract(@Nullable AssistantMessage message) {
		if (message == null) {
			return null;
		}

		// 1. Subclass-specific typed field, e.g. DeepSeekAssistantMessage.
		if (message instanceof DeepSeekReasoningAccessor accessor) {
			String typed = accessor.getReasoningContent();
			if (StringUtils.hasText(typed)) {
				return typed;
			}
		}

		// 2. AssistantMessage metadata - OpenAI-style.
		String fromReasoningKey = readStringMetadata(message, REASONING_CONTENT_KEY);
		if (fromReasoningKey != null) {
			return fromReasoningKey;
		}

		// 3. AssistantMessage metadata - Ollama-style.
		String fromThinkingKey = readStringMetadata(message, THINKING_METADATA_KEY);
		if (fromThinkingKey != null) {
			return fromThinkingKey;
		}

		return null;
	}

	private static @Nullable String readStringMetadata(AssistantMessage message, String key) {
		Object value = message.getMetadata().get(key);
		if (value instanceof String s && StringUtils.hasText(s)) {
			return s;
		}
		return null;
	}

	/**
	 * Indirection for typed reasoning fields on vendor-specific {@link AssistantMessage}
	 * subclasses. Using a small accessor interface keeps the helper free of vendor
	 * imports and avoids a hard module dependency on the DeepSeek module, which lives
	 * outside {@code spring-ai-model}.
	 */
	public interface DeepSeekReasoningAccessor {

		/**
		 * Return the reasoning / chain-of-thought content, or {@code null} if the
		 * subclass did not capture any.
		 * @return the reasoning content
		 */
		@Nullable String getReasoningContent();

	}

}
