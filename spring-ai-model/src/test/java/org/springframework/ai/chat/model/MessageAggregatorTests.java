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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MessageAggregator}.
 *
 * @author RESEARCH
 * @since 1.0.0
 */
public class MessageAggregatorTests {

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUp() {
		Logger logger = (Logger) LoggerFactory.getLogger(MessageAggregator.class);
		this.logAppender = new ListAppender<>();
		this.logAppender.start();
		logger.addAppender(this.logAppender);
	}

	@Test
	void doOnError_logsAtWarnLevel_notErrorLevel() {
		// Given: a Flux that errors partway through streaming, an aggregator
		MessageAggregator aggregator = new MessageAggregator();
		AtomicBoolean onCompleteCalled = new AtomicBoolean(false);
		AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();

		Exception testError = new RuntimeException("Test aggregation failure");

		// When: we aggregate a flux that errors
		aggregator.aggregate(Flux.just(
				new ChatResponse(List.of(new Generation(new AssistantMessage("partial"), ChatGenerationMetadata.NULL))),
				new ChatResponse(
						List.of(new Generation(new AssistantMessage("complete"), ChatGenerationMetadata.NULL))))
			.concatWith(Flux.error(testError)), response -> {
				aggregatedResponse.set(response);
				onCompleteCalled.set(true);
			}).subscribe();

		// Then: verify no ERROR-level logs were emitted for handled/recoverable errors
		// The aggregator's doOnError handler should use WARN, not ERROR, for handled
		// errors
		List<ILoggingEvent> errorLogs = this.logAppender.list.stream()
			.filter(e -> "ERROR".equals(e.getLevel().levelStr))
			.toList();

		assertThat(errorLogs)
			.describedAs("MessageAggregator should not emit ERROR-level logs for handled exceptions; "
					+ "handled errors should be logged at WARN level to reduce log noise")
			.isEmpty();
	}

}
