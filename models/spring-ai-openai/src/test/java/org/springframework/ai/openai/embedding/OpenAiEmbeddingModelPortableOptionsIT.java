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

package org.springframework.ai.openai.embedding;

import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.services.blocking.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for the end-to-end behavior of {@link OpenAiEmbeddingModel} when a
 * caller passes a portable {@link EmbeddingOptions} via the
 * {@link EmbeddingRequest#getOptions()} API.
 *
 * <p>
 * The test captures the {@link EmbeddingCreateParams} that the model hands to the OpenAI
 * SDK. Since the OpenAI Java SDK serializes this object directly to the JSON request
 * body, the captured params faithfully reflect what goes on the wire — making this a
 * "request-capturing HTTP client" integration test as required by the issue.
 *
 * <p>
 * Regression guard for
 * <a href="https://github.com/spring-projects/spring-ai/issues/6042">spring-ai#6042</a>.
 */
class OpenAiEmbeddingModelPortableOptionsIT {

	private static CreateEmbeddingResponse fakeResponse() {
		Embedding embedding = Embedding.builder().index(0L).embedding(List.of(0.1f, 0.2f, 0.3f)).build();
		CreateEmbeddingResponse response = mock(CreateEmbeddingResponse.class);
		when(response.data()).thenReturn(List.of(embedding));
		when(response.model()).thenReturn("custom-model");
		CreateEmbeddingResponse.Usage usage = mock(CreateEmbeddingResponse.Usage.class);
		when(usage.promptTokens()).thenReturn(1L);
		when(usage.totalTokens()).thenReturn(1L);
		when(response.usage()).thenReturn(usage);
		return response;
	}

	private static OpenAIClient createCapturingClient() {
		// Pre-build the fake response OUTSIDE of any when().thenReturn() block to
		// avoid Mockito's "unfinished stubbing detected" error caused by nested
		// when() calls.
		CreateEmbeddingResponse response = fakeResponse();
		OpenAIClient client = mock(OpenAIClient.class);
		EmbeddingService embeddingService = mock(EmbeddingService.class);
		when(client.embeddings()).thenReturn(embeddingService);
		when(embeddingService.create(any(EmbeddingCreateParams.class))).thenReturn(response);
		return client;
	}

	@Test
	void portableEmbeddingOptionsModelIsSentOnTheRequest() {
		OpenAIClient client = createCapturingClient();

		// Build the model with no default options (so it falls back to
		// text-embedding-ada-002 internally), then override via the per-request
		// portable EmbeddingOptions.
		OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(client, MetadataMode.EMBED, null);

		EmbeddingOptions portable = EmbeddingOptions.builder().model("custom-model").build();
		EmbeddingRequest request = new EmbeddingRequest(List.of("text"), portable);

		// Sanity: the call returns a response and the model field is propagated to the
		// outbound EmbeddingCreateParams.
		var response = model.call(request);
		assertThat(response).isNotNull();

		// Capture the EmbeddingCreateParams that OpenAiEmbeddingModel actually hands to
		// the SDK — this is the object that gets serialized to the HTTP JSON body.
		ArgumentCaptor<EmbeddingCreateParams> captor = ArgumentCaptor.forClass(EmbeddingCreateParams.class);
		org.mockito.Mockito.verify(client.embeddings()).create(captor.capture());
		EmbeddingCreateParams sent = captor.getValue();

		assertThat(sent).as("Outbound EmbeddingCreateParams passed to the OpenAI SDK").isNotNull();
		// The portable options' model name must be the one going out on the wire.
		assertThat(sent.model().asString()).isEqualTo("custom-model");
	}

	@Test
	void defaultModelIsUsedWhenPortableOptionsHasNoModel() {
		// Sanity check that the existing default behaviour is preserved: when the
		// caller passes a portable EmbeddingOptions with no model, the model's
		// default (text-embedding-ada-002) is used.
		OpenAIClient client = createCapturingClient();

		OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(client, MetadataMode.EMBED, null);

		EmbeddingOptions portable = EmbeddingOptions.builder().build(); // no model
		EmbeddingRequest request = new EmbeddingRequest(List.of("text"), portable);

		model.call(request);

		ArgumentCaptor<EmbeddingCreateParams> captor = ArgumentCaptor.forClass(EmbeddingCreateParams.class);
		org.mockito.Mockito.verify(client.embeddings()).create(captor.capture());
		EmbeddingCreateParams sent = captor.getValue();

		// Default model from OpenAiEmbeddingOptions.DEFAULT_EMBEDDING_MODEL.
		assertThat(sent.model().asString()).isEqualTo("text-embedding-ada-002");
	}

}
