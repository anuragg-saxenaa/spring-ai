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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChatClientRequest} null context value validation.
 *
 * @author Research Agent
 * @since 1.0.0
 */
class ChatClientRequestNullContextTests {

	@Test
	void whenContextHasNullValueViaConstructorThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put("tenantId", null);
		context.put("otherKey", "value");

		assertThatThrownBy(() -> new ChatClientRequest(new Prompt(), context))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context value for key 'tenantId' cannot be null");
	}

	@Test
	void whenContextHasNullValueViaBuilderContextMethodThenThrow() {
		// Build will fail because the record constructor validates null values
		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context("tenantId", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context value for key 'tenantId' cannot be null");
	}

	@Test
	void whenContextHasNullValueViaBuilderMapThenThrow() {
		Map<String, Object> contextWithNull = new HashMap<>();
		contextWithNull.put("tenantId", null);
		contextWithNull.put("userId", "user123");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context(contextWithNull).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context value for key 'tenantId' cannot be null");
	}

	@Test
	void whenContextHasMultipleNullValuesThenReportFirst() {
		Map<String, Object> context = new HashMap<>();
		context.put("firstNull", null);
		context.put("secondNull", null);

		// The order depends on the map iteration order
		assertThatThrownBy(() -> new ChatClientRequest(new Prompt(), context))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("context value for key")
			.hasMessageContaining("cannot be null");
	}

	@Test
	void whenContextHasNoNullValuesThenSuccess() {
		Map<String, Object> context = Map.of("key1", "value1", "key2", "value2");

		// Should succeed without throwing
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt()).context(context).build();

		org.assertj.core.api.Assertions.assertThat(request.context()).hasSize(2);
	}

}
