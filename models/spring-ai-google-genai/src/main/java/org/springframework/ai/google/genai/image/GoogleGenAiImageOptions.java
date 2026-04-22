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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.genai.types.SafetySetting;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.model.ModelOptionsUtils;

/**
 * Configuration options for Google GenAI image generation. Supports text-to-image
 * generation and multimodal image generation (text + input images).
 *
 * @author Anurag Saxena
 * @since 1.0.0
 */
public class GoogleGenAiImageOptions implements ImageOptions {

	/**
	 * Default model for image generation.
	 */
	public static final String DEFAULT_MODEL = "gemini-2.0-flash-preview-image-generation";

	/**
	 * Supported aspect ratios for image generation.
	 */
	public static final List<String> SUPPORTED_ASPECT_RATIOS = List.of("1:1", "16:9", "9:16", "4:3", "3:4", "2:3",
			"3:2");

	/**
	 * Supported resolution/quality presets for image generation.
	 */
	public static final List<String> SUPPORTED_RESOLUTIONS = List.of("1K", "2K", "1024x1024", "1024x1792", "1792x1024",
			"1536x1024", "1024x1536");

	/**
	 * The model to use for image generation. Default:
	 * gemini-2.0-flash-preview-image-generation
	 */
	private @Nullable String model;

	/**
	 * Number of images to generate (1-4). Default: 1.
	 */
	private @Nullable Integer n;

	/**
	 * Aspect ratio for the generated images. Supported values: "1:1", "16:9", "9:16",
	 * "4:3", "3:4", "2:3", "3:2". Default: "1:1".
	 */
	private @Nullable String aspectRatio;

	/**
	 * Resolution/quality preset for the generated images. Supported values: "1K", "2K",
	 * "1024x1024", "1024x1792", "1792x1024", "1536x1024", "1024x1536". Default: "1K".
	 */
	private @Nullable String resolution;

	/**
	 * Person name hint for the generation. Optional.
	 */
	private @nullab_le String person;

	/**
	 * Negative subjects to avoid. Optional.
	 */
	private @Nullable List<String> negativeSubjects;

	/**
	 * Safety settings for the generation. Optional.
	 */
	private @Nullable List<Map<String, String>> safetySettings;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable Integer getN() {
		return this.n;
	}

	public void setN(@Nullable Integer n) {
		this.n = n;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	@Override
	public @Nullable Integer getWidth() {
		return null; // Not directly supported, use aspectRatio or resolution
	}

	@Override
	public @Nullable Integer getHeight() {
		return null; // Not directly supported, use aspectRatio or resolution
	}

	@Override
	public @Nullable String getResponseFormat() {
		return null; // Not directly applicable
	}

	@Override
	public @Nullable String getStyle() {
		return null; // Not directly applicable
	}

	/**
	 * Get the aspect ratio.
	 * @return the aspect ratio
	 */
	public @Nullable String getAspectRatio() {
		return this.aspectRatio;
	}

	/**
	 * Set the aspect ratio.
	 * @param aspectRatio the aspect ratio
	 */
	public void setAspectRatio(@Nullable String aspectRatio) {
		this.aspectRatio = aspectRatio;
	}

	/**
	 * Get the resolution.
	 * @return the resolution
	 */
	public @Nullable String getResolution() {
		return this.resolution;
	}

	/**
	 * Set the resolution.
	 * @param resolution the resolution
	 */
	public void setResolution(@Nullable String resolution) {
		this.resolution = resolution;
	}

	/**
	 * Get the person name hint.
	 * @return the person name
	 */
	public @Nullable String getPerson() {
		return this.person;
	}

	/**
	 * Set the person name hint.
	 * @param person the person name
	 */
	public void setPerson(@Nullable String person) {
		this.person = person;
	}

	/**
	 * Get the negative subjects.
	 * @return the negative subjects
	 */
	public @Nullable List<String> getNegativeSubjects() {
		return this.negativeSubjects;
	}

	/**
	 * Set the negative subjects.
	 * @param negativeSubjects the negative subjects
	 */
	public void setNegativeSubjects(@Nullable List<String> negativeSubjects) {
		this.negativeSubjects = negativeSubjects;
	}

	/**
	 * Get the safety settings.
	 * @return the safety settings
	 */
	public @Nullable List<Map<String, String>> getSafetySettings() {
		return this.safetySettings;
	}

	/**
	 * Set the safety settings.
	 * @param safetySettings the safety settings
	 */
	public void setSafetySettings(@Nullable List<Map<String, String>> safetySettings) {
		this.safetySettings = safetySettings;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		GoogleGenAiImageOptions that = (GoogleGenAiImageOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.n, that.n)
				&& Objects.equals(this.aspectRatio, that.aspectRatio)
				&& Objects.equals(this.resolution, that.resolution) && Objects.equals(this.person, that.person)
				&& Objects.equals(this.negativeSubjects, that.negativeSubjects)
				&& Objects.equals(this.safetySettings, that.safetySettings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.n, this.aspectRatio, this.resolution, this.person, this.negativeSubjects,
				this.safetySettings);
	}

	@Override
	public String toString() {
		return "GoogleGenAiImageOptions{" + "model='" + this.model + '\'' + ", n=" + this.n + ", aspectRatio='"
				+ this.aspectRatio + '\'' + ", resolution='" + this.resolution + '\'' + ", person='" + this.person
				+ '\'' + ", negativeSubjects=" + this.negativeSubjects + ", safetySettings=" + this.safetySettings + '}';
	}

	public static final class Builder {

		private final GoogleGenAiImageOptions options;

		private Builder() {
			this.options = new GoogleGenAiImageOptions();
		}

		public Builder from(GoogleGenAiImageOptions fromOptions) {
			this.options.setModel(fromOptions.getModel());
			this.options.setN(fromOptions.getN());
			this.options.setAspectRatio(fromOptions.getAspectRatio());
			this.options.setResolution(fromOptions.getResolution());
			this.options.setPerson(fromOptions.getPerson());
			this.options.setNegativeSubjects(fromOptions.getNegativeSubjects());
			this.options.setSafetySettings(fromOptions.getSafetySettings());
			return this;
		}

		public Builder merge(@Nullable ImageOptions from) {
			if (from == null) {
				return this;
			}
			if (from instanceof GoogleGenAiImageOptions castFrom) {
				if (castFrom.getModel() != null) {
					this.options.setModel(castFrom.getModel());
				}
				if (castFrom.getN() != null) {
					this.options.setN(castFrom.getN());
				}
				if (castFrom.getAspectRatio() != null) {
					this.options.setAspectRatio(castFrom.getAspectRatio());
				}
				if (castFrom.getResolution() != null) {
					this.options.setResolution(castFrom.getResolution());
				}
				if (castFrom.getPerson() != null) {
					this.options.setPerson(castFrom.getPerson());
				}
				if (castFrom.getNegativeSubjects() != null) {
					this.options.setNegativeSubjects(castFrom.getNegativeSubjects());
				}
				if (castFrom.getSafetySettings() != null) {
					this.options.setSafetySettings(castFrom.getSafetySettings());
				}
			}
			// Handle portable ImageOptions
			if (from.getModel() != null) {
				this.options.setModel(from.getModel());
			}
			if (from.getN() != null) {
				this.options.setN(from.getN());
			}
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder n(Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder aspectRatio(String aspectRatio) {
			this.options.setAspectRatio(aspectRatio);
			return this;
		}

		public Builder resolution(String resolution) {
			this.options.setResolution(resolution);
			return this;
		}

		public Builder person(String person) {
			this.options.setPerson(person);
			return this;
		}

		public Builder negativeSubjects(List<String> negativeSubjects) {
			this.options.setNegativeSubjects(negativeSubjects);
			return this;
		}

		public Builder safetySettings(List<Map<String, String>> safetySettings) {
			this.options.setSafetySettings(safetySettings);
			return this;
		}

		public GoogleGenAiImageOptions build() {
			return this.options;
		}

	}

}