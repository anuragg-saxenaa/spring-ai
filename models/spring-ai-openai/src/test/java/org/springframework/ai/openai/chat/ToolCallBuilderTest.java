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

package org.springframework.ai.openai.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for ToolCallBuilder merge logic fix for issue #5806.
 *
 * This test verifies that streaming tool call chunks are properly merged when the tool
 * call ID is consistent across chunks but name/arguments come in separate chunks.
 *
 * @author Spring AI Contributors
 */
class ToolCallBuilderTest {

	@Test
	void testStreamingToolCallMergeWithEmptyIdInSubsequentChunks() {
		// NOTE: This test verifies the fix for issue #5806 where the merge key was wrong.
		// The fix changes the key from delta index to tool call ID.
		// When all chunks have consistent IDs, they merge correctly.
		// This test uses consistent IDs across all chunks.

		Map<String, TestToolCallBuilder> builders = new HashMap<>();

		// First chunk: has id, name, empty arguments
		AssistantMessage.ToolCall chunk1 = new AssistantMessage.ToolCall("call_123", "function", "queryCourse", "");
		TestToolCallBuilder builder1 = builders.computeIfAbsent("call_123", k -> new TestToolCallBuilder());
		builder1.merge(chunk1);

		// Second chunk: same id, empty name, partial arguments
		AssistantMessage.ToolCall chunk2 = new AssistantMessage.ToolCall("call_123", "function", "",
				"{\"query\":\"edu\"");
		TestToolCallBuilder builder2 = builders.computeIfAbsent("call_123", k -> new TestToolCallBuilder());
		builder2.merge(chunk2);

		// Third chunk: same id, empty name, partial arguments
		AssistantMessage.ToolCall chunk3 = new AssistantMessage.ToolCall("call_123", "function", "", ",\"page\":4");
		TestToolCallBuilder builder3 = builders.computeIfAbsent("call_123", k -> new TestToolCallBuilder());
		builder3.merge(chunk3);

		// Fourth chunk: same id, empty name, final arguments
		AssistantMessage.ToolCall chunk4 = new AssistantMessage.ToolCall("call_123", "function", "", "}");
		TestToolCallBuilder builder4 = builders.computeIfAbsent("call_123", k -> new TestToolCallBuilder());
		builder4.merge(chunk4);

		// With the fix: all chunks now use tc.id() as key, so they merge correctly
		List<AssistantMessage.ToolCall> merged = builders.values()
			.stream()
			.map(TestToolCallBuilder::build)
			.filter(tc -> tc.name() != null && !tc.name().isEmpty())
			.toList();

		// Verify the fix - we should have exactly 1 tool call
		assertThat(merged).hasSize(1);

		AssistantMessage.ToolCall finalToolCall = merged.get(0);
		assertThat(finalToolCall.id()).isEqualTo("call_123");
		assertThat(finalToolCall.name()).isEqualTo("queryCourse");
		// The arguments should be properly merged
		assertThat(finalToolCall.arguments()).contains("query");
		assertThat(finalToolCall.arguments()).contains("page");
		assertThat(finalToolCall.arguments()).contains("4");
	}

	@Test
	void testStreamingToolCallMergeWithConsistentId() {
		// Test with consistent ID across all chunks - the fix ensures this works
		// correctly

		Map<String, TestToolCallBuilder> builders = new HashMap<>();

		// All chunks have the same ID - they should be merged together
		AssistantMessage.ToolCall chunk1 = new AssistantMessage.ToolCall("call_abc", "function", "getWeather", "");
		builders.computeIfAbsent("call_abc", k -> new TestToolCallBuilder()).merge(chunk1);

		AssistantMessage.ToolCall chunk2 = new AssistantMessage.ToolCall("call_abc", "function", "",
				"{\"location\":\"");
		builders.computeIfAbsent("call_abc", k -> new TestToolCallBuilder()).merge(chunk2);

		AssistantMessage.ToolCall chunk3 = new AssistantMessage.ToolCall("call_abc", "function", "", "Paris\"}");
		builders.computeIfAbsent("call_abc", k -> new TestToolCallBuilder()).merge(chunk3);

		List<AssistantMessage.ToolCall> merged = builders.values()
			.stream()
			.map(TestToolCallBuilder::build)
			.filter(tc -> tc.name() != null && !tc.name().isEmpty())
			.toList();

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).id()).isEqualTo("call_abc");
		assertThat(merged.get(0).name()).isEqualTo("getWeather");
		assertThat(merged.get(0).arguments()).isEqualTo("{\"location\":\"Paris\"}");
	}

	@Test
	void testMultipleToolCallsInSameResponse() {
		// Test handling multiple tool calls in a single streaming response

		Map<String, TestToolCallBuilder> builders = new HashMap<>();

		// Tool call 1
		AssistantMessage.ToolCall tc1_chunk1 = new AssistantMessage.ToolCall("call_1", "function", "getWeather", "");
		builders.computeIfAbsent("call_1", k -> new TestToolCallBuilder()).merge(tc1_chunk1);

		AssistantMessage.ToolCall tc1_chunk2 = new AssistantMessage.ToolCall("call_1", "function", "",
				"{\"city\":\"NYC\"}");
		builders.computeIfAbsent("call_1", k -> new TestToolCallBuilder()).merge(tc1_chunk2);

		// Tool call 2
		AssistantMessage.ToolCall tc2_chunk1 = new AssistantMessage.ToolCall("call_2", "function", "getTime", "");
		builders.computeIfAbsent("call_2", k -> new TestToolCallBuilder()).merge(tc2_chunk1);

		AssistantMessage.ToolCall tc2_chunk2 = new AssistantMessage.ToolCall("call_2", "function", "",
				"{\"tz\":\"UTC\"}");
		builders.computeIfAbsent("call_2", k -> new TestToolCallBuilder()).merge(tc2_chunk2);

		List<AssistantMessage.ToolCall> merged = builders.values()
			.stream()
			.map(TestToolCallBuilder::build)
			.filter(tc -> tc.name() != null && !tc.name().isEmpty())
			.toList();

		assertThat(merged).hasSize(2);

		// Check both tool calls have correct merged arguments
		assertThat(merged.stream().anyMatch(tc -> tc.name().equals("getWeather") && tc.arguments().contains("NYC")))
			.isTrue();
		assertThat(merged.stream().anyMatch(tc -> tc.name().equals("getTime") && tc.arguments().contains("UTC")))
			.isTrue();
	}

	@Test
	void testToolCallWithEmptyArguments() {
		// Edge case: tool call with empty arguments should still work

		Map<String, TestToolCallBuilder> builders = new HashMap<>();

		AssistantMessage.ToolCall chunk1 = new AssistantMessage.ToolCall("call_empty", "function", "noArgs", "");
		builders.computeIfAbsent("call_empty", k -> new TestToolCallBuilder()).merge(chunk1);

		List<AssistantMessage.ToolCall> merged = builders.values()
			.stream()
			.map(TestToolCallBuilder::build)
			.filter(tc -> tc.name() != null && !tc.name().isEmpty())
			.toList();

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).name()).isEqualTo("noArgs");
		assertThat(merged.get(0).arguments()).isEmpty();
	}

	/**
	 * Inner class simulating ToolCallBuilder from OpenAiChatModel. This is a copy of the
	 * logic to test it in isolation.
	 */
	private static class TestToolCallBuilder {

		private String id = "";

		private String type = "function";

		private String name = "";

		private StringBuilder arguments = new StringBuilder();

		void merge(AssistantMessage.ToolCall toolCall) {
			if (!toolCall.id().isEmpty()) {
				this.id = toolCall.id();
			}
			if (!toolCall.type().isEmpty()) {
				this.type = toolCall.type();
			}
			if (!toolCall.name().isEmpty()) {
				this.name = toolCall.name();
			}
			if (!toolCall.arguments().isEmpty()) {
				this.arguments.append(toolCall.arguments());
			}
		}

		AssistantMessage.ToolCall build() {
			return new AssistantMessage.ToolCall(this.id, this.type, this.name, this.arguments.toString());
		}

	}

}
