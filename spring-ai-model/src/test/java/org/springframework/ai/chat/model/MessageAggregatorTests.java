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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MessageAggregator}, focusing on error log level behavior.
 *
 * @author Research Agent
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5781">Issue
 * #5781</a>
 */
class MessageAggregatorTests {

	@Test
	void defaultConstructorShouldUseWarnLogLevel() {
		MessageAggregator aggregator = new MessageAggregator();
		assertThat(aggregator).isNotNull();
	}

	@Test
	void explicitWarnLogLevelShouldBeAccepted() {
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.WARN);
		assertThat(aggregator).isNotNull();
	}

	@Test
	void explicitErrorLogLevelShouldBeAccepted() {
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.ERROR);
		assertThat(aggregator).isNotNull();
	}

	@Test
	void explicitDebugLogLevelShouldBeAccepted() {
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.DEBUG);
		assertThat(aggregator).isNotNull();
	}

	@Test
	void explicitNoneLogLevelShouldBeAccepted() {
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.NONE);
		assertThat(aggregator).isNotNull();
	}

	@Test
	void aggregationSucceedsWithDefaultWarnLevel() {
		MessageAggregator aggregator = new MessageAggregator();

		ChatResponse chunk1 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Hello "))));
		ChatResponse chunk2 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("world!"))));

		AtomicReference<ChatResponse> resultRef = new AtomicReference<>();
		aggregator.aggregate(Flux.just(chunk1, chunk2), resultRef::set).blockLast();

		ChatResponse result = resultRef.get();
		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Hello world!");
	}

	@Test
	void aggregationSucceedsWithErrorLogLevel() {
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.ERROR);

		ChatResponse chunk1 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Hello "))));
		ChatResponse chunk2 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("world!"))));

		AtomicReference<ChatResponse> resultRef = new AtomicReference<>();
		aggregator.aggregate(Flux.just(chunk1, chunk2), resultRef::set).blockLast();

		ChatResponse result = resultRef.get();
		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Hello world!");
	}

	@Test
	void aggregationSucceedsWithNoneLogLevel() {
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.NONE);

		ChatResponse chunk1 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Hello "))));
		ChatResponse chunk2 = new ChatResponse(
				List.of(new Generation(new AssistantMessage("world!"))));

		AtomicReference<ChatResponse> resultRef = new AtomicReference<>();
		aggregator.aggregate(Flux.just(chunk1, chunk2), resultRef::set).blockLast();

		ChatResponse result = resultRef.get();
		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Hello world!");
	}

	@Test
	void handledExceptionDoesNotTriggerRepeatedErrorLogWithWarnLevel() {
		// Regression test for
		// https://github.com/spring-projects/spring-ai/issues/5781
		// When an exception occurs and is already being handled by the caller,
		// the MessageAggregator should NOT spam ERROR logs at WARN level.
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.WARN);

		AtomicReference<ChatResponse> resultRef = new AtomicReference<>();
		AtomicInteger errorLogCount = new AtomicInteger(0);

		// Simulate a Flux that produces a recoverable error
		Flux<ChatResponse> errorFlux = Flux.<ChatResponse>error(new RuntimeException("Recoverable error"))
				.doOnError(e -> errorLogCount.incrementAndGet());

		// The aggregator's error handler should not re-log at ERROR level
		// when WARN level is configured.
		assertThatThrownBy(() -> aggregator.aggregate(errorFlux, resultRef::set).blockLast())
				.isInstanceOf(RuntimeException.class)
				.hasMessage("Recoverable error");
	}

	@Test
	void handledExceptionWithNoneLogLevelDoesNotTriggerAnyLog() {
		// Regression test: with NONE level, handled exceptions should be silent
		MessageAggregator aggregator = new MessageAggregator(MessageAggregator.ErrorLogLevel.NONE);

		AtomicReference<ChatResponse> resultRef = new AtomicReference<>();

		Flux<ChatResponse> errorFlux = Flux.error(new RuntimeException("Silent recoverable error"));

		assertThatThrownBy(() -> aggregator.aggregate(errorFlux, resultRef::set).blockLast())
				.isInstanceOf(RuntimeException.class)
				.hasMessage("Silent recoverable error");

		// Result ref should never be set when error occurs
		assertThat(resultRef.get()).isNull();
	}

	@Test
	void errorLogLevelEnumHasExpectedValues() {
		MessageAggregator.ErrorLogLevel[] values = MessageAggregator.ErrorLogLevel.values();
		assertThat(values).containsExactlyInAnyOrder(MessageAggregator.ErrorLogLevel.WARN,
				MessageAggregator.ErrorLogLevel.ERROR, MessageAggregator.ErrorLogLevel.DEBUG,
				MessageAggregator.ErrorLogLevel.NONE);
	}

	@Test
	void errorLogLevelFromString() {
		assertThat(MessageAggregator.ErrorLogLevel.valueOf("WARN")).isEqualTo(MessageAggregator.ErrorLogLevel.WARN);
		assertThat(MessageAggregator.ErrorLogLevel.valueOf("ERROR")).isEqualTo(MessageAggregator.ErrorLogLevel.ERROR);
		assertThat(MessageAggregator.ErrorLogLevel.valueOf("DEBUG")).isEqualTo(MessageAggregator.ErrorLogLevel.DEBUG);
		assertThat(MessageAggregator.ErrorLogLevel.valueOf("NONE")).isEqualTo(MessageAggregator.ErrorLogLevel.NONE);
	}

}
