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

import java.util.List;

import com.google.genai.Client;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.ImageBase64;
import com.google.genai.types.Part;

/**
 * Low-level SDK adapter for Gemini image generation models. This class wraps the Google
 * GenAI client to provide image generation capabilities.
 *
 * @author Anurag Saxena
 * @since 1.0.0
 */
public class GoogleGenAiImageApi {

	private final Client genAiClient;

	private final String defaultModel;

	/**
	 * Creates a new GoogleGenAiImageApi.
	 * @param genAiClient the GenAI client
	 * @param defaultModel the default model to use for image generation
	 */
	public GoogleGenAiImageApi(Client genAiClient, String defaultModel) {
		this.genAiClient = genAiClient;
		this.defaultModel = defaultModel;
	}

	/**
	 * Generate images from a text prompt.
	 * @param prompt the text prompt for image generation
	 * @param config the generation configuration (can be null for defaults)
	 * @return the generated images response
	 */
	public GenerateImagesResponse generate(String prompt, GenerateImagesConfig config) {
		String model = this.defaultModel;
		List<Part> parts = List.of(Part.builder().text(prompt).build());
		return this.genAiClient.models.generateImages(model, parts, config);
	}

	/**
	 * Generate images with multimodal input (text + base64 images).
	 * @param prompt the text prompt for image generation
	 * @param inputImages list of base64 encoded images (can be null or empty)
	 * @param config the generation configuration (can be null for defaults)
	 * @return the generated images response
	 */
	public GenerateImagesResponse generateWithImages(String prompt, List<ImageBase64> inputImages,
			GenerateImagesConfig config) {
		String model = this.defaultModel;
		java.util.List<Part> parts = new java.util.ArrayList<>();
		parts.add(Part.builder().text(prompt).build());
		if (inputImages != null) {
			for (ImageBase64 img : inputImages) {
				parts.add(Part.builder().image(img).build());
			}
		}
		return this.genAiClient.models.generateImages(model, parts, config);
	}

	/**
	 * Get the default model name.
	 * @return the default model name
	 */
	public String getDefaultModel() {
		return this.defaultModel;
	}

}