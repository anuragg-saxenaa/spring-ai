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

package org.springframework.ai.google.genai.image;

import org.springframework.ai.image.ImageGenerationMetadata;

/**
 * Metadata specific to Google GenAI image generation responses.
 *
 * @author Anurag Saxena
 * @since 1.0.0
 */
public class GoogleGenAiImageGenerationMetadata implements ImageGenerationMetadata {

	private String finishReason;

	private Integer promptTokens;

	private Integer completionTokens;

	private Integer totalTokens;

	public GoogleGenAiImageGenerationMetadata() {
	}

	/**
	 * Get the finish reason.
	 * @return the finish reason
	 */
	public String getFinishReason() {
		return this.finishReason;
	}

	/**
	 * Set the finish reason.
	 * @param finishReason the finish reason
	 */
	public void setFinishReason(String finishReason) {
		this.finishReason = finishReason;
	}

	/**
	 * Get the prompt tokens count.
	 * @return the prompt tokens
	 */
	public Integer getPromptTokens() {
		return this.promptTokens;
	}

	/**
	 * Set the prompt tokens count.
	 * @param promptTokens the prompt tokens
	 */
	public void setPromptTokens(Integer promptTokens) {
		this.promptTokens = promptTokens;
	}

	/**
	 * Get the completion tokens count.
	 * @return the completion tokens
	 */
	public Integer getCompletionTokens() {
		return this.completionTokens;
	}

	/**
	 * Set the completion tokens count.
	 * @param completionTokens the completion tokens
	 */
	public void setCompletionTokens(Integer completionTokens) {
		this.completionTokens = completionTokens;
	}

	/**
	 * Get the total tokens count.
	 * @return the total tokens
	 */
	public Integer getTotalTokens() {
		return this.totalTokens;
	}

	/**
	 * Set the total tokens count.
	 * @param totalTokens the total tokens
	 */
	public void setTotalTokens(Integer totalTokens) {
		this.totalTokens = totalTokens;
	}

	@Override
	public String toString() {
		return "GoogleGenAiImageGenerationMetadata{" + "finishReason='" + this.finishReason + '\'' + ", promptTokens="
				+ this.promptTokens + ", completionTokens=" + this.completionTokens + ", totalTokens="
				+ this.totalTokens + '}';
	}

}