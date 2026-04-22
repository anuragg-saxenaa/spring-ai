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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.ImageBase64;
import com.google.genai.types.Part;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.retry.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Google GenAI Image Model implementation that provides access to Google's Gemini image
 * generation models (gemini-2.0-flash-preview-image-generation,
 * gemini-3.0-pro-preview-image-generation, etc.).
 *
 * <p>
 * This model supports:
 * <ul>
 * <li>Text-to-image generation</li>
 * <li>Multimodal image generation (text + input images)</li>
 * <li>Configurable aspect ratios and resolutions</li>
 * <li>Person hints and negative subjects</li>
 * <li>Safety settings</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * GoogleGenAiImageModel imageModel = GoogleGenAiImageModel.builder()
 *     .genAiClient(genAiClient)
 *     .defaultOptions(GoogleGenAiImageOptions.builder()
 *         .model("gemini-2.0-flash-preview-image-generation")
 *         .aspectRatio("16:9")
 *         .resolution("2K")
 *         .build())
 *     .build();
 *
 * ImageResponse response = imageModel.call(new ImagePrompt("A sunset over the ocean"));
 * }</pre>
 *
 * @author Anurag Saxena
 * @since 1.0.0
 * @see ImageModel
 * @see GoogleGenAiImageOptions
 * @see GoogleGenAiImageApi
 */
public class GoogleGenAiImageModel implements ImageModel {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiImageModel.class);

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	/**
	 * The default model for image generation.
	 */
	public static final String DEFAULT_MODEL = GoogleGenAiImageOptions.DEFAULT_MODEL;

	private final GoogleGenAiImageApi imageApi;

	private final GoogleGenAiImageOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new GoogleGenAiImageModel with required parameters.
	 * @param imageApi the Google GenAI Image API
	 * @param defaultOptions the default options
	 * @param retryTemplate the retry template
	 * @param observationRegistry the observation registry
	 */
	public GoogleGenAiImageModel(GoogleGenAiImageApi imageApi, GoogleGenAiImageOptions defaultOptions,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(imageApi, "GoogleGenAiImageApi must not be null");
		Assert.notNull(defaultOptions, "GoogleGenAiImageOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		this.imageApi = imageApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
	}

	/**
	 * Creates a new GoogleGenAiImageModel with default retry template and observation
	 * registry.
	 * @param imageApi the Google GenAI Image API
	 * @param defaultOptions the default options
	 */
	public GoogleGenAiImageModel(GoogleGenAiImageApi imageApi, GoogleGenAiImageOptions defaultOptions) {
		this(imageApi, defaultOptions, org.springframework.ai.retry.RetryUtils.DEFAULT_RETRY_TEMPLATE,
				ObservationRegistry.NOOP);
	}

	/**
	 * Creates a new GoogleGenAiImageModel with builder.
	 * @param builder the builder
	 */
	private GoogleGenAiImageModel(Builder builder) {
		this.imageApi = new GoogleGenAiImageApi(builder.genAiClient,
				builder.defaultOptions.getModel() != null ? builder.defaultOptions.getModel() : DEFAULT_MODEL);
		this.defaultOptions = builder.defaultOptions;
		this.retryTemplate = builder.retryTemplate;
		this.observationRegistry = builder.observationRegistry;
	}

	/**
	 * Get the default options.
	 * @return the default options
	 */
	public GoogleGenAiImageOptions getOptions() {
		return this.defaultOptions;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		Assert.notNull(imagePrompt, "ImagePrompt must not be null");
		Assert.notEmpty(imagePrompt.getInstructions(), "ImagePrompt instructions must not be empty");

		// Merge runtime options with default options
		GoogleGenAiImageOptions requestOptions = mergeOptions(imagePrompt.getOptions(), this.defaultOptions);

		// Extract prompt text from the first message
		String promptText = imagePrompt.getInstructions().get(0).getText();

		// Build GenerateImagesConfig
		GenerateImagesConfig config = buildGenerateImagesConfig(requestOptions);

		// Check for multimodal input (base64 images)
		List<ImageBase64> inputImages = extractInputImages(imagePrompt);

		logger.debug("Calling Google GenAI image generation with prompt: {}", promptText);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				com.google.genai.types.GenerateImagesResponse response;
				if (!inputImages.isEmpty()) {
					response = this.imageApi.generateWithImages(promptText, inputImages, config);
				}
				else {
					response = this.imageApi.generate(promptText, config);
				}

				ImageResponse imageResponse = convertToImageResponse(response, requestOptions.getN());
				observationContext.setResponse(imageResponse);
				return imageResponse;
			});
	}

	/**
	 * Build the GenerateImagesConfig from options.
	 * @param options the options
	 * @return the config
	 */
	private GenerateImagesConfig buildGenerateImagesConfig(GoogleGenAiImageOptions options) {
		GenerateImagesConfig.Builder configBuilder = GenerateImagesConfig.builder();

		// Set number of images
		if (options.getN() != null) {
			configBuilder.numberOfImages(options.getN());
		}

		// Set aspect ratio if provided
		if (options.getAspectRatio() != null) {
			configBuilder.aspectRatio(options.getAspectRatio());
		}

		// Set resolution if provided
		if (options.getResolution() != null) {
			configBuilder.resolution(options.getResolution());
		}

		// Set person hint if provided
		if (options.getPerson() != null) {
			configBuilder.person(options.getPerson());
		}

		// Set negative subjects if provided
		if (options.getNegativeSubjects() != null && !options.getNegativeSubjects().isEmpty()) {
			configBuilder.negativeSubjects(options.getNegativeSubjects());
		}

		// Set safety settings if provided
		if (options.getSafetySettings() != null && !options.getSafetySettings().isEmpty()) {
			List<com.google.genai.types.SafetySetting> safetySettings = convertSafetySettings(
					options.getSafetySettings());
			configBuilder.safetySettings(safetySettings);
		}

		return configBuilder.build();
	}

	/**
	 * Convert safety settings from Map format to GenAI SafetySetting format.
	 * @param safetySettings the safety settings as maps
	 * @return the GenAI safety settings
	 */
	private List<com.google.genai.types.SafetySetting> convertSafetySettings(
			List<Map<String, String>> safetySettings) {
		List<com.google.genai.types.SafetySetting> result = new ArrayList<>();
		for (Map<String, String> setting : safetySettings) {
			String categoryStr = setting.get("category");
			String thresholdStr = setting.get("threshold");
			if (categoryStr != null && thresholdStr != null) {
				com.google.genai.types.HarmCategory category = new com.google.genai.types.HarmCategory(
						com.google.genai.types.HarmCategory.Known.valueOf(categoryStr));
				com.google.genai.types.HarmBlockThreshold threshold = new com.google.genai.types.HarmBlockThreshold(
						com.google.genai.types.HarmBlockThreshold.Known.valueOf(thresholdStr));
				result.add(com.google.genai.types.SafetySetting.builder().category(category).threshold(threshold)
					.build());
			}
		}
		return result;
	}

	/**
	 * Extract base64 encoded images from the image prompt (multimodal support).
	 * @param imagePrompt the image prompt
	 * @return list of base64 images
	 */
	private List<ImageBase64> extractInputImages(ImagePrompt imagePrompt) {
		List<ImageBase64> images = new ArrayList<>();

		// Check if prompt contains image data in the message
		for (var message : imagePrompt.getInstructions()) {
			// Check message media content if available
			if (message instanceof org.springframework.ai.chat.messages.UserMessage userMessage) {
				var media = userMessage.getMedia();
				if (media != null) {
					for (var m : media) {
						Object data = m.getData();
						if (data instanceof byte[] bytes) {
							String base64 = Base64.getEncoder().encodeToString(bytes);
							ImageBase64 img = ImageBase64.builder().base64(base64).build();
							images.add(img);
						}
						else if (data instanceof String strData) {
							// Assume it's base64 encoded string if it looks like it
							if (strData.length() > 100 && !strData.startsWith("http")) {
								ImageBase64 img = ImageBase64.builder().base64(strData).build();
								images.add(img);
							}
						}
					}
				}
			}
		}

		return images;
	}

	/**
	 * Convert the GenAI response to Spring AI ImageResponse.
	 * @param response the GenAI response
	 * @param n the number of images requested (for metadata)
	 * @return the ImageResponse
	 */
	private ImageResponse convertToImageResponse(com.google.genai.types.GenerateImagesResponse response, Integer n) {
		List<ImageGeneration> generations = new ArrayList<>();

		if (response.generatedImages().isPresent()) {
			List<com.google.genai.types.GeneratedImage> generatedImages = response.generatedImages().get();
			for (com.google.genai.types.GeneratedImage genImage : generatedImages) {
				Image image = null;

				// Get image data from the response
				if (genImage.image().isPresent()) {
					com.google.genai.types.Image imageData = genImage.image().get();
					if (imageData.imageBytes().isPresent()) {
						String base64 = imageData.imageBytes().get().base64();
						image = new Image(null, base64);
					}
				}

				if (image == null) {
					throw new RuntimeException("Failed to extract image data from response");
				}

				// Create generation metadata
				GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata();
				genImage.finishReason().ifPresent(reason -> metadata.setFinishReason(reason.toString()));
				genImage.usageMetadata().ifPresent(usage -> {
					metadata.setPromptTokens(usage.promptTokenCount().orElse(null));
					metadata.setCompletionTokens(usage.candidatesTokenCount().orElse(null));
					metadata.setTotalTokens(usage.totalTokenCount().orElse(null));
				});

				generations.add(new ImageGeneration(image, metadata));
			}
		}

		if (generations.isEmpty()) {
			throw new RuntimeException("No images generated in the response");
		}

		return new ImageResponse(generations, new ImageResponseMetadata());
	}

	/**
	 * Merge runtime options with default options.
	 * @param runtimeOptions the runtime options (can be null)
	 * @param defaultOptions the default options
	 * @return the merged options
	 */
	private GoogleGenAiImageOptions mergeOptions(@Nullable ImageOptions runtimeOptions,
			GoogleGenAiImageOptions defaultOptions) {
		GoogleGenAiImageOptions.Builder builder = GoogleGenAiImageOptions.builder()
			// Start with defaults
			.from(defaultOptions);

		if (runtimeOptions != null && runtimeOptions instanceof GoogleGenAiImageOptions imageOptions) {
			// Merge runtime options
			builder.merge(runtimeOptions);
		}
		else if (runtimeOptions != null) {
			// Handle portable ImageOptions interface
			if (runtimeOptions.getModel() != null) {
				builder.model(runtimeOptions.getModel());
			}
			if (runtimeOptions.getN() != null) {
				builder.n(runtimeOptions.getN());
			}
		}

		return builder.build();
	}

	/**
	 * Set the observation convention.
	 * @param observationConvention the observation convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Builder for GoogleGenAiImageModel.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for creating GoogleGenAiImageModel instances.
	 */
	public static final class Builder {

		private com.google.genai.Client genAiClient;

		private GoogleGenAiImageOptions defaultOptions = GoogleGenAiImageOptions.builder()
			.model(DEFAULT_MODEL)
			.n(1)
			.aspectRatio("1:1")
			.build();

		private RetryTemplate retryTemplate = org.springframework.ai.retry.RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		/**
		 * Set the GenAI client.
		 * @param genAiClient the client
		 * @return this builder
		 */
		public Builder genAiClient(com.google.genai.Client genAiClient) {
			this.genAiClient = genAiClient;
			return this;
		}

		/**
		 * Set the default options.
		 * @param defaultOptions the options
		 * @return this builder
		 */
		public Builder defaultOptions(GoogleGenAiImageOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		/**
		 * Set the retry template.
		 * @param retryTemplate the template
		 * @return this builder
		 */
		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		/**
		 * Set the observation registry.
		 * @param observationRegistry the registry
		 * @return this builder
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Build the GoogleGenAiImageModel.
		 * @return the model
		 */
		public GoogleGenAiImageModel build() {
			Assert.notNull(this.genAiClient, "genAiClient must not be null");
			return new GoogleGenAiImageModel(this);
		}

	}

}