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

package org.springframework.ai.chat.client;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for issue #4952: ChatClient advisors NPE when context contains null
 * values.
 *
 * @author Thomas Vitale
 */
class ChatClientRequestNullContextTests {

	@Test
	void whenContextHasNullValueViaBuilderThenThrow() {
		assertThatThrownBy(() -> ChatClientRequest.builder()
			.prompt(new Prompt("hi"))
			.context("tenantId", null)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context values cannot be null");
	}

	@Test
	void whenContextHasNullValueViaMapConstructorThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put("tenantId", null);
		assertThatThrownBy(() -> new ChatClientRequest(new Prompt("hi"), context))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context values cannot be null");
	}

	@Test
	void whenContextHasNullValueViaMapBuilderThenThrow() {
		assertThatThrownBy(
				() -> ChatClientRequest.builder().prompt(new Prompt("hi")).context(Map.of("tenantId", null)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context values cannot be null");
	}

}
