/*
 * Copyright 2025 the original author or authors.
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
 * Regression tests for {@link ChatClientRequest} null-value handling in context.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4952">Issue #4952</a>
 */
class ChatClientRequestNullContextTests {

	@Test
	void whenContextHasNullValueThenThrowViaBuilder() {
		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context("tenantId", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values must not be null");
	}

	@Test
	void whenContextMapHasNullValueThenThrowViaBuilder() {
		Map<String, Object> contextWithNull = new HashMap<>();
		contextWithNull.put("tenantId", null);
		contextWithNull.put("userId", "user-123");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context(contextWithNull).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values must not be null");
	}

	@Test
	void whenContextHasNullKeyAlreadyThrows() {
		Map<String, Object> contextWithNullKey = new HashMap<>();
		contextWithNullKey.put(null, "value");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context(contextWithNullKey).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context keys cannot be null");
	}

	@Test
	void whenContextHasOnlyValidEntriesThenBuildSucceeds() {
		Prompt prompt = new Prompt("test");
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context("key1", "value1")
			.context("key2", 42)
			.build();

		assertThat(request.context()).hasSize(2);
		assertThat(request.context().get("key1")).isEqualTo("value1");
		assertThat(request.context().get("key2")).isEqualTo(42);
	}

	@Test
	void whenContextMapHasOnlyValidEntriesThenBuildSucceeds() {
		Prompt prompt = new Prompt("test");
		Map<String, Object> context = Map.of("key1", "value1", "key2", 42);

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(context).build();

		assertThat(request.context()).hasSize(2);
		assertThat(request.context().get("key1")).isEqualTo("value1");
		assertThat(request.context().get("key2")).isEqualTo(42);
	}

	@Test
	void whenMutateAddsNullValueThenBuildThrows() {
		ChatClientRequest original = ChatClientRequest.builder()
			.prompt(new Prompt())
			.context("existing", "value")
			.build();

		assertThatThrownBy(() -> original.mutate().context("newKey", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values must not be null");
	}

	@Test
	void whenMutateMergesMapWithNullValueThenBuildThrows() {
		ChatClientRequest original = ChatClientRequest.builder()
			.prompt(new Prompt())
			.context("existing", "value")
			.build();

		Map<String, Object> badMap = new HashMap<>();
		badMap.put("goodKey", "goodValue");
		badMap.put("badKey", null);

		assertThatThrownBy(() -> original.mutate().context(badMap).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values must not be null");
	}

}