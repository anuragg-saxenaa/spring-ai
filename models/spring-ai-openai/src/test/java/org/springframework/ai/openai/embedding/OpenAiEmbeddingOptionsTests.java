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

import com.openai.models.embeddings.EmbeddingCreateParams;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiEmbeddingOptionsTests {

	@Test
	void defaultEncodingFormatIsNull() {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model("test-model").build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(options.getEncodingFormat()).isNull();
		assertThat(createParams.encodingFormat()).contains(EmbeddingCreateParams.EncodingFormat.BASE64);
	}

	@Test
	void encodingFormatCanBeConfigured() {
		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
			.build();

		EmbeddingCreateParams createParams = options.toOpenAiCreateParams(List.of("test input"));

		assertThat(createParams.encodingFormat()).contains(EmbeddingCreateParams.EncodingFormat.FLOAT);
	}

	@Test
	void encodingFormatIsCopiedAndMerged() {
		OpenAiEmbeddingOptions source = OpenAiEmbeddingOptions.builder()
			.model("test-model")
			.encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
			.build();

		OpenAiEmbeddingOptions copied = OpenAiEmbeddingOptions.builder().from(source).build();
		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder().model("other-model").merge(source).build();

		assertThat(copied.getEncodingFormat()).isEqualTo(OpenAiEmbeddingOptions.EncodingFormat.FLOAT);
		assertThat(merged.getEncodingFormat()).isEqualTo(OpenAiEmbeddingOptions.EncodingFormat.FLOAT);
	}

	// --- Issue #6042: portable EmbeddingOptions.model/dimensions must be merged ---

	@Test
	void mergePortableEmbeddingOptionsPropagatesModel() {
		// Portable EmbeddingOptions carrying only a model name.
		EmbeddingOptions portable = EmbeddingOptions.builder().model("bge-multilingual-gemma2").build();

		// Builder starts with a different model and no dimensions.
		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder()
			.model("text-embedding-ada-002")
			.merge(portable)
			.build();

		// Model is propagated from the portable options.
		assertThat(merged.getModel()).isEqualTo("bge-multilingual-gemma2");
		// Dimensions is left unchanged on the builder.
		assertThat(merged.getDimensions()).isNull();
	}

	@Test
	void mergePortableEmbeddingOptionsPropagatesDimensions() {
		// Portable EmbeddingOptions carrying only a dimensions value.
		EmbeddingOptions portable = EmbeddingOptions.builder().dimensions(768).build();

		// Builder starts with a model and no dimensions.
		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder()
			.model("text-embedding-ada-002")
			.merge(portable)
			.build();

		// Dimensions is propagated from the portable options.
		assertThat(merged.getDimensions()).isEqualTo(768);
		// Model is left unchanged on the builder.
		assertThat(merged.getModel()).isEqualTo("text-embedding-ada-002");
	}

	@Test
	void mergePortableEmbeddingOptionsOverridesBuilderWhenBothSet() {
		// Portable EmbeddingOptions with both fields populated. The portable options
		// are the "merge source" (i.e. applied later), so they must win — matching the
		// existing merge() semantics for OpenAiEmbeddingOptions. This is a regression
		// guard ensuring the new portable-merge branch does not silently drop overrides.
		EmbeddingOptions portable = EmbeddingOptions.builder().model("portable-model").dimensions(1024).build();

		OpenAiEmbeddingOptions merged = OpenAiEmbeddingOptions.builder()
			.model("builder-model")
			.dimensions(512)
			.merge(portable)
			.build();

		// Later values win: the portable (merge source) values override the builder.
		assertThat(merged.getModel()).isEqualTo("portable-model");
		assertThat(merged.getDimensions()).isEqualTo(1024);
	}

}
