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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AssistantMessageReasoningExtractor}.
 */
class AssistantMessageReasoningExtractorTests {

	@Test
	void extractReturnsNullForNullMessage() {
		assertThat(AssistantMessageReasoningExtractor.extract(null)).isNull();
	}

	@Test
	void extractReturnsNullWhenNoReasoningPresent() {
		AssistantMessage message = AssistantMessage.builder().content("answer").build();
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isNull();
	}

	@Test
	void extractReturnsReasoningContentMetadataKey() {
		AssistantMessage message = AssistantMessage.builder()
			.content("answer")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "thought A"))
			.build();
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isEqualTo("thought A");
	}

	@Test
	void extractReturnsThinkingMetadataKey() {
		AssistantMessage message = AssistantMessage.builder()
			.content("answer")
			.properties(Map.of(AssistantMessageReasoningExtractor.THINKING_METADATA_KEY, "thought B"))
			.build();
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isEqualTo("thought B");
	}

	@Test
	void extractPrefersReasoningContentOverThinkingWhenBothPresent() {
		AssistantMessage message = AssistantMessage.builder()
			.content("answer")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "preferred",
					AssistantMessageReasoningExtractor.THINKING_METADATA_KEY, "fallback"))
			.build();
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isEqualTo("preferred");
	}

	@Test
	void extractTreatsBlankValuesAsAbsent() {
		AssistantMessage message = AssistantMessage.builder()
			.content("answer")
			.properties(Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "   "))
			.build();
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isNull();
	}

	@Test
	void extractUsesTypedAccessorWhenAvailable() {
		AssistantMessage message = new TestDeepSeekLikeMessage("typed field wins",
				Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "metadata loses"));
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isEqualTo("typed field wins");
	}

	@Test
	void extractFallsBackToMetadataWhenTypedAccessorReturnsNull() {
		AssistantMessage message = new TestDeepSeekLikeMessage(null,
				Map.of(AssistantMessageReasoningExtractor.REASONING_CONTENT_KEY, "from metadata"));
		assertThat(AssistantMessageReasoningExtractor.extract(message)).isEqualTo("from metadata");
	}

	/**
	 * Minimal stand-in for {@code DeepSeekAssistantMessage} that implements the accessor
	 * interface so the helper can be unit-tested without depending on the DeepSeek
	 * module.
	 */
	private static final class TestDeepSeekLikeMessage extends AssistantMessage
			implements AssistantMessageReasoningExtractor.DeepSeekReasoningAccessor {

		private final String reasoningContent;

		private TestDeepSeekLikeMessage(String reasoningContent, Map<String, Object> properties) {
			super("answer", properties, java.util.List.of(), java.util.List.of());
			this.reasoningContent = reasoningContent;
		}

		@Override
		public String getReasoningContent() {
			return this.reasoningContent;
		}

	}

}
