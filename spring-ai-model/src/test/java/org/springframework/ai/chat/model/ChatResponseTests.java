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

package org.springframework.ai.chat.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

/**
 * Unit tests for {@link ChatResponse}.
 *
 * @author Thomas Vitale
 * @author Heonwoo Kim
 */
class ChatResponseTests {

	@Test
	void whenToolCallsArePresentThenReturnTrue() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(List.of(new ToolCall("toolA", "function", "toolA", "{}")))
				.build())))
			.build();
		assertThat(chatResponse.hasToolCalls()).isTrue();
	}

	@Test
	void whenNoToolCallsArePresentThenReturnFalse() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"))))
			.build();
		assertThat(chatResponse.hasToolCalls()).isFalse();
	}

	@Test
	void whenFinishReasonIsNullThenThrow() {
		var chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"),
					ChatGenerationMetadata.builder().finishReason("completed").build())))
			.build();
		assertThatThrownBy(() -> chatResponse.hasFinishReasons(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("finishReasons cannot be null");
	}

	@Test
	void whenFinishReasonIsPresent() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"),
					ChatGenerationMetadata.builder().finishReason("completed").build())))
			.build();
		assertThat(chatResponse.hasFinishReasons(Set.of("completed"))).isTrue();
	}

	@Test
	void whenFinishReasonIsNotPresent() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"),
					ChatGenerationMetadata.builder().finishReason("failed").build())))
			.build();
		assertThat(chatResponse.hasFinishReasons(Set.of("completed"))).isFalse();
	}

	@Test
	void messageAggregatorShouldCorrectlyAggregateToolCallsFromStream() {

		MessageAggregator aggregator = new MessageAggregator();

		ChatResponse chunk1 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Thinking about the weather... "))));

		ToolCall weatherToolCall = new ToolCall("tool-id-123", "function", "getCurrentWeather",
				"{\"location\": \"Seoul\"}");

		Map<String, Object> metadataWithToolCall = Map.of("toolCalls", List.of(weatherToolCall));
		ChatResponseMetadata responseMetadataForChunk2 = ChatResponseMetadata.builder()
			.metadata(metadataWithToolCall)
			.build();

		ChatResponse chunk2 = new ChatResponse(List.of(new Generation(new AssistantMessage(""))),
				responseMetadataForChunk2);

		Flux<ChatResponse> streamingResponse = Flux.just(chunk1, chunk2);

		AtomicReference<ChatResponse> aggregatedResponseRef = new AtomicReference<>();

		aggregator.aggregate(streamingResponse, aggregatedResponseRef::set).blockLast();

		ChatResponse finalResponse = aggregatedResponseRef.get();
		assertThat(finalResponse).isNotNull();

		AssistantMessage finalAssistantMessage = finalResponse.getResult().getOutput();

		assertThat(finalAssistantMessage).isNotNull();
		assertThat(finalAssistantMessage.getText()).isEqualTo("Thinking about the weather... ");
		assertThat(finalAssistantMessage.hasToolCalls()).isTrue();
		assertThat(finalAssistantMessage.getToolCalls()).hasSize(1);

		ToolCall resultToolCall = finalAssistantMessage.getToolCalls().get(0);
		assertThat(resultToolCall.id()).isEqualTo("tool-id-123");
		assertThat(resultToolCall.name()).isEqualTo("getCurrentWeather");
		assertThat(resultToolCall.arguments()).isEqualTo("{\"location\": \"Seoul\"}");
	}

	@Test
	void whenEmptyGenerationsListThenReturnFalse() {
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of()).build();
		assertThat(chatResponse.hasToolCalls()).isFalse();
	}

	@Test
	void whenMultipleGenerationsWithToolCallsThenReturnTrue() {
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("First response")),
					new Generation(AssistantMessage.builder()
						.content("")
						.properties(Map.of())
						.toolCalls(List.of(new ToolCall("toolB", "function", "toolB", "{}")))
						.build())))
			.build();
		assertThat(chatResponse.hasToolCalls()).isTrue();
	}

	// Issue #5167: Stream mode toolCall information loss in tool calling loops
	@Test
	void messageAggregatorShouldEmitSeparateObservationsForEachToolCallLoop() {
		// Simulate two LLM calls in a streaming tool-calling loop.
		// Call 1 emits text + toolCall; Call 2 emits final text.
		// After the fix, MessageAggregator should emit TWO observations:
		// Observation 1: text from Call 1, toolCalls from Call 1
		// Observation 2: text from Call 2 only (NOT cumulative)
		MessageAggregator aggregator = new MessageAggregator();

		// Call 1, chunk 1: text without toolCall
		ChatResponse call1Chunk1 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Checking weather... "))));

		// Call 1, final chunk: text + toolCall
		ToolCall weatherCall = new ToolCall("call_001", "function", "getWeather", "{\"city\":\"Beijing\"}");
		AssistantMessage call1FinalMsg = AssistantMessage.builder()
			.content("I will check the weather.")
			.toolCalls(List.of(weatherCall))
			.build();
		ChatResponse call1Final = new ChatResponse(List.of(new Generation(call1FinalMsg)));

		// Call 2, chunk 1: text without toolCall
		ChatResponse call2Chunk1 = new ChatResponse(List.of(new Generation(new AssistantMessage("Based on "))));

		// Call 2, final chunk: final text (no toolCall)
		AssistantMessage call2FinalMsg = AssistantMessage.builder()
			.content("Based on the sunny weather, wear a jacket.")
			.toolCalls(List.of())
			.build();
		ChatResponse call2Final = new ChatResponse(List.of(new Generation(call2FinalMsg)));

		// Stream: Call 1 chunks -> Call 2 chunks (mimics Flux.concat behavior after fix)
		Flux<ChatResponse> streamingResponse = Flux.just(call1Chunk1, call1Final, call2Chunk1, call2Final);

		List<ChatResponse> emittedObservations = new java.util.ArrayList<>();
		aggregator.aggregate(streamingResponse, response -> emittedObservations.add(response)).blockLast();

		// After fix: should have exactly 2 observations (one per LLM call)
		assertThat(emittedObservations).hasSize(2);

		// Observation 1: should have text from Call 1 and the toolCall
		ChatResponse observation1 = emittedObservations.get(0);
		AssistantMessage msg1 = observation1.getResult().getOutput();
		assertThat(msg1.getText()).isEqualTo("Checking weather... I will check the weather.");
		assertThat(msg1.hasToolCalls()).isTrue();
		assertThat(msg1.getToolCalls()).hasSize(1);
		assertThat(msg1.getToolCalls().get(0).name()).isEqualTo("getWeather");

		// Observation 2: should have ONLY Call 2's text (NOT cumulative with Call 1)
		ChatResponse observation2 = emittedObservations.get(1);
		AssistantMessage msg2 = observation2.getResult().getOutput();
		assertThat(msg2.getText()).isEqualTo("Based on the sunny weather, wear a jacket.");
		assertThat(msg2.hasToolCalls()).isFalse();
	}

}
