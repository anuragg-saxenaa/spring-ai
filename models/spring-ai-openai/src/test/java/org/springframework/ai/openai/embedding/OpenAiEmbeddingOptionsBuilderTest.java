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

package org.springframework.ai.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiEmbeddingOptions.Builder}.
 *
 * @author ENG / RedOS
 */
class OpenAiEmbeddingOptionsBuilderTest {

	@Test
	void mergeEmbeddingOptionsModelCopiesModel() {
		// Simulate framework-agnostic EmbeddingOptions set via EmbeddingOptions.builder().model()
		var genericOptions = new org.springframework.ai.embedding.EmbeddingOptions() {
			@Override
			public String getModel() {
				return "text-embedding-3-small";
			}

			@Override
			public Integer getDimensions() {
				return null;
			}
		};

		var openAiOptions = OpenAiEmbeddingOptions.builder()
				.merge(genericOptions)
				.build();

		assertThat(openAiOptions.getModel()).isEqualTo("text-embedding-3-small");
	}

	@Test
	void mergeEmbeddingOptionsDimensionsCopiesDimensions() {
		var genericOptions = new org.springframework.ai.embedding.EmbeddingOptions() {
			@Override
			public String getModel() {
				return null;
			}

			@Override
			public Integer getDimensions() {
				return 512;
			}
		};

		var openAiOptions = OpenAiEmbeddingOptions.builder()
				.merge(genericOptions)
				.build();

		assertThat(openAiOptions.getDimensions()).isEqualTo(512);
	}

	@Test
	void mergePreservesOpenAiSpecificFields() {
		// Start with a fully configured OpenAiEmbeddingOptions
		var openAiOptions = OpenAiEmbeddingOptions.builder()
				.model("text-embedding-3-large")
				.deploymentName("my-deployment")
				.user("test-user")
				.build();

		// Now merge a generic EmbeddingOptions that only overrides model
		var genericOptions = new org.springframework.ai.embedding.EmbeddingOptions() {
			@Override
			public String getModel() {
				return "text-embedding-3-small";
			}

			@Override
			public Integer getDimensions() {
				return null;
			}
		};

		var merged = OpenAiEmbeddingOptions.builder()
				.from(openAiOptions)
				.merge(genericOptions)
				.build();

		// Model is overridden by generic
		assertThat(merged.getModel()).isEqualTo("text-embedding-3-small");
		// Deployment name preserved from original
		assertThat(merged.getDeploymentName()).isEqualTo("my-deployment");
		// User preserved from original
		assertThat(merged.getUser()).isEqualTo("test-user");
	}

	@Test
	void mergeWithBothModelAndDimensions() {
		var genericOptions = new org.springframework.ai.embedding.EmbeddingOptions() {
			@Override
			public String getModel() {
				return "text-embedding-3-large";
			}

			@Override
			public Integer getDimensions() {
				return 1024;
			}
		};

		var openAiOptions = OpenAiEmbeddingOptions.builder()
				.merge(genericOptions)
				.build();

		assertThat(openAiOptions.getModel()).isEqualTo("text-embedding-3-large");
		assertThat(openAiOptions.getDimensions()).isEqualTo(1024);
	}

	@Test
	void mergeNullDoesNothing() {
		var openAiOptions = OpenAiEmbeddingOptions.builder()
				.model("text-embedding-3-large")
				.deploymentName("my-deployment")
				.build();

		var merged = OpenAiEmbeddingOptions.builder()
				.from(openAiOptions)
				.merge(null)
				.build();

		assertThat(merged.getModel()).isEqualTo("text-embedding-3-large");
		assertThat(merged.getDeploymentName()).isEqualTo("my-deployment");
	}

}
