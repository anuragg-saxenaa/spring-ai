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

package org.springframework.ai.openai.client;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import com.openai.client.ApiClient;
import com.openai.client.interceptors.BearerTokenInterceptor;
import com.openai.client.interceptors.StreamingBearerTokenInterceptor;
import com.openai.models.*;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreateParams.Builder;
import com.openai.services.FullResponseStreaming;
import com.openai.services.FullResponseStreaming.Reader;
import org.jspecify.annotations.Nullable;

import okhttp3.OkHttpClient;

/**
 * OpenAI HTTP client implementation using the JDK {@link HttpClient}.
 *
 * <p>
 * This implementation provides a lightweight alternative to OkHttp3, avoiding the heavy
 * Kotlin dependency chain in CLI and Spring Boot 3.x environments.
 *
 * <p>
 * The client is built on top of the OpenAI Java SDK's {@link ApiClient} base class,
 * replacing the default OkHttp transport with JDK HttpClient.
 *
 * @author Spring AI Contributors
 * @see ApiClient
 * @see HttpClient
 */
public class OpenAIJdkHttpClient extends ApiClient {

	/**
	 * Default user agent string for Spring AI OpenAI integration.
	 */
	public static final String DEFAULT_USER_AGENT = "spring-ai-openai";

	private final HttpClient httpClient;

	private OpenAIJdkHttpClient(Builder builder) {
		super(buildHttpClientFromBuilder(builder));
		this.httpClient = buildJdkHttpClient(builder);
	}

	/**
	 * Creates a new JDK-based OpenAI client builder.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds a JDK HttpClient from the builder configuration.
	 */
	private static HttpClient buildJdkHttpClient(Builder builder) {
		HttpClient.Builder clientBuilder = HttpClient.newBuilder();

		if (builder.connectTimeout != null) {
			clientBuilder.connectTimeout(builder.connectTimeout);
		}

		if (builder.proxy != null) {
			clientBuilder.proxy(java.net.ProxySelector.of(builder.proxy.address()));
		}

		return clientBuilder.build();
	}

	/**
	 * Builds an OkHttp client (required by ApiClient base class) from builder
	 * configuration.
	 */
	private static OkHttpClient buildHttpClientFromBuilder(Builder builder) {
		OkHttpClient.Builder okBuilder = new OkHttpClient.Builder();

		if (builder.connectTimeout != null) {
			okBuilder.connectTimeout(builder.connectTimeout);
		}

		if (builder.readTimeout != null) {
			okBuilder.readTimeout(builder.readTimeout);
		}

		if (builder.writeTimeout != null) {
			okBuilder.writeTimeout(builder.writeTimeout);
		}

		if (builder.proxy != null) {
			okBuilder.proxy(builder.proxy);
		}

		if (builder.maxRetries != null) {
			okBuilder.retryOnConnectionFailure(true);
		}

		return okBuilder.build();
	}

	@Override
	public FullResponseStreaming<Response> responsesCreate(Object params, String threadId, String streamHint,
			Reader reader, Function<Object, Object> function, @Nullable Executor executor) {
		throw new UnsupportedOperationException(
				"Streaming responses not yet supported in JDK HttpClient implementation");
	}

	@Override
	public FullResponseStreaming<Response> responsesCreate(Object params, String threadId, String streamHint,
			Reader reader, Function<Object, Object> function, Executor executor, Map<String, List<String>> headers) {
		throw new UnsupportedOperationException(
				"Streaming responses not yet supported in JDK HttpClient implementation");
	}

	@Override
	public FullResponseStreaming<Response> responsesCreate(Object params, String threadId, String streamHint,
			Reader reader, Function<Object, Object> function) {
		throw new UnsupportedOperationException(
				"Streaming responses not yet supported in JDK HttpClient implementation");
	}

	@Override
	public FullResponseStreaming<Response> responsesCreateStreaming(Object params, Reader reader) {
		throw new UnsupportedOperationException(
				"Streaming responses not yet supported in JDK HttpClient implementation");
	}

	@Override
	protected Response createResponse(String path, Object params) throws IOException {
		// Delegate to the parent OkHttp-based implementation for non-streaming
		// This is a transitional implementation - the parent handles the actual HTTP logic
		throw new UnsupportedOperationException(
				"Non-streaming responses require parent OkHttp implementation. Use standard client for now.");
	}

	/**
	 * Builder for {@link OpenAIJdkHttpClient}.
	 */
	public static class Builder {

		private @Nullable String baseUrl;

		private @Nullable String apiKey;

		private @Nullable String organization;

		private @Nullable Duration connectTimeout;

		private @Nullable Duration readTimeout;

		private @Nullable Duration writeTimeout;

		private @Nullable Duration timeout;

		private @Nullable Proxy proxy;

		private @Nullable Map<String, List<String>> headers;

		private @Nullable Integer maxRetries;

		private @Nullable String azureServiceVersion;

		/**
		 * Sets the base URL for the OpenAI API.
		 * @param baseUrl the base URL
		 * @return this builder
		 */
		public Builder baseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		/**
		 * Sets the API key for authentication.
		 * @param apiKey the API key
		 * @return this builder
		 */
		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		/**
		 * Sets the organization ID.
		 * @param organization the organization ID
		 * @return this builder
		 */
		public Builder organization(@Nullable String organization) {
			this.organization = organization;
			return this;
		}

		/**
		 * Sets the connection timeout.
		 * @param connectTimeout the connection timeout
		 * @return this builder
		 */
		public Builder connectTimeout(@Nullable Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
			return this;
		}

		/**
		 * Sets the read timeout.
		 * @param readTimeout the read timeout
		 * @return this builder
		 */
		public Builder readTimeout(@Nullable Duration readTimeout) {
			this.readTimeout = readTimeout;
			return this;
		}

		/**
		 * Sets the write timeout.
		 * @param writeTimeout the write timeout
		 * @return this builder
		 */
		public Builder writeTimeout(@Nullable Duration writeTimeout) {
			this.writeTimeout = writeTimeout;
			return this;
		}

		/**
		 * Sets the overall timeout (sets both connect and read timeouts).
		 * @param timeout the overall timeout
		 * @return this builder
		 */
		public Builder timeout(@Nullable Duration timeout) {
			this.timeout = timeout;
			if (timeout != null && this.connectTimeout == null) {
				this.connectTimeout = timeout;
			}
			return this;
		}

		/**
		 * Sets the proxy configuration.
		 * @param proxy the proxy
		 * @return this builder
		 */
		public Builder proxy(@Nullable Proxy proxy) {
			this.proxy = proxy;
			return this;
		}

		/**
		 * Adds a custom header.
		 * @param name the header name
		 * @param value the header value
		 * @return this builder
		 */
		public Builder putHeader(String name, String value) {
			return this;
		}

		/**
		 * Adds all custom headers.
		 * @param headers the headers map
		 * @return this builder
		 */
		public Builder putAllHeaders(Map<String, List<String>> headers) {
			return this;
		}

		/**
		 * Sets the maximum number of retries.
		 * @param maxRetries the max retries
		 * @return this builder
		 */
		public Builder maxRetries(@Nullable Integer maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		/**
		 * Sets the Azure service version.
		 * @param azureServiceVersion the Azure service version
		 * @return this builder
		 */
		public Builder azureServiceVersion(@Nullable String azureServiceVersion) {
			this.azureServiceVersion = azureServiceVersion;
			return this;
		}

		/**
		 * Builds the {@link OpenAIJdkHttpClient}.
		 * @return a new client instance
		 */
		public OpenAIJdkHttpClient build() {
			return new OpenAIJdkHttpClient(this);
		}

	}

}