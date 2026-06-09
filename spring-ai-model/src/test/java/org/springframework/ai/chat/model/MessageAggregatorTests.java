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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the streaming tool-call aggregation path in
 * {@link MessageAggregator}. The original bug (#5806) was that
 * {@code addAll(outputMessage.getToolCalls())} kept each chunk's partial tool call as a
 * separate entry, leaving the first entry with an empty arguments string and causing
 * {@code IllegalArgumentException: toolInput cannot be null or empty} during tool
 * execution.
 */
class MessageAggregatorTests {

	@Test
	void mergeToolCallsShouldConcatenateArgumentsAcrossChunks() {
		// Reproduces the exact 4-chunk pattern from issue #5806.
		List<ToolCall> acc = new ArrayList<>();
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("call_xxx", "function", "queryCourse", "")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "", "{\"query\":\"edu\"")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "", ",\"page\":4")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "", "}")));

		assertThat(acc).hasSize(1);
		ToolCall merged = acc.get(0);
		assertThat(merged.id()).isEqualTo("call_xxx");
		assertThat(merged.name()).isEqualTo("queryCourse");
		assertThat(merged.arguments()).isEqualTo("{\"query\":\"edu\",\"page\":4}");
	}

	@Test
	void mergeToolCallsShouldIgnoreEmptyArgumentChunks() {
		// A heartbeat / keepalive chunk with no new args must not clobber accumulated
		// arguments or replace the name.
		List<ToolCall> acc = new ArrayList<>();
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("call_xxx", "function", "queryCourse", "{\"a\":")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "", "")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "", "1}")));

		assertThat(acc).hasSize(1);
		assertThat(acc.get(0).arguments()).isEqualTo("{\"a\":1}");
		assertThat(acc.get(0).name()).isEqualTo("queryCourse");
	}

	@Test
	void mergeToolCallsShouldAppendNewToolCallAfterExisting() {
		// Two sequential tool calls in the same stream. The second call has a fresh
		// id and should start a new entry, not merge into the first.
		List<ToolCall> acc = new ArrayList<>();
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("call_aaa", "function", "first", "{\"x\":1}")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("call_bbb", "function", "second", "{\"y\":2}")));

		assertThat(acc).hasSize(2);
		assertThat(acc.get(0).id()).isEqualTo("call_aaa");
		assertThat(acc.get(0).arguments()).isEqualTo("{\"x\":1}");
		assertThat(acc.get(1).id()).isEqualTo("call_bbb");
		assertThat(acc.get(1).arguments()).isEqualTo("{\"y\":2}");
	}

	@Test
	void mergeToolCallsShouldPreserveFirstNonBlankName() {
		// Some providers send the name on the first chunk, some on a later chunk.
		// The first non-blank value should win; later chunks must not overwrite.
		List<ToolCall> acc = new ArrayList<>();
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("call_xxx", "function", "", "{\"a\":")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "queryCourse", "")));
		MessageAggregator.mergeToolCalls(acc, List.of(new ToolCall("", "", "", "1}")));

		assertThat(acc).hasSize(1);
		assertThat(acc.get(0).name()).isEqualTo("queryCourse");
		assertThat(acc.get(0).arguments()).isEqualTo("{\"a\":1}");
	}

	@Test
	void mergeToolCallsShouldHandleEmptyIncoming() {
		List<ToolCall> acc = new ArrayList<>();
		MessageAggregator.mergeToolCalls(acc, List.of());
		assertThat(acc).isEmpty();
	}

	@Test
	void messageAggregatorStreamingShouldProduceSingleMergedToolCall() {
		// End-to-end: drive a Flux<ChatResponse> through the aggregator and verify
		// the emitted AssistantMessage carries a single merged tool call with the
		// expected id, name, and concatenated arguments. This is the regression
		// scenario from issue #5806 at the public API level.
		MessageAggregator aggregator = new MessageAggregator();

		ToolCall first = new ToolCall("call_xxx", "function", "queryCourse", "");
		ToolCall frag1 = new ToolCall("", "", "", "{\"query\":\"edu\"");
		ToolCall frag2 = new ToolCall("", "", "", ",\"page\":4");
		ToolCall frag3 = new ToolCall("", "", "", "}");

		ChatResponse chunk1 = new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(first)).build())));
		ChatResponse chunk2 = new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(frag1)).build())));
		ChatResponse chunk3 = new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(frag2)).build())));
		ChatResponse chunk4 = new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(frag3)).build())));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		aggregator.aggregate(Flux.just(chunk1, chunk2, chunk3, chunk4), aggregatedRef::set).blockLast();

		ChatResponse finalResponse = aggregatedRef.get();
		assertThat(finalResponse).isNotNull();
		AssistantMessage out = finalResponse.getResult().getOutput();
		assertThat(out).isNotNull();
		assertThat(out.hasToolCalls()).isTrue();
		assertThat(out.getToolCalls()).hasSize(1);
		ToolCall merged = out.getToolCalls().get(0);
		assertThat(merged.id()).isEqualTo("call_xxx");
		assertThat(merged.name()).isEqualTo("queryCourse");
		assertThat(merged.arguments()).isEqualTo("{\"query\":\"edu\",\"page\":4}");
	}

}
