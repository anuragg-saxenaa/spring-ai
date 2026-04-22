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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OpenAI HTTP client selection.
 *
 * <p>
 * This allows choosing between different HTTP client implementations:
 * <ul>
 * <li>{@code jdk} - Uses JDK HttpClient (requires manual client setup, no OkHttp
 * dependency)</li>
 * <li>{@code okhttp} - Uses OkHttp (default, requires okhttp dependency)</li>
 * <li>{@code custom} - Use a custom HttpClient bean provided by the application</li>
 * </ul>
 *
 * @author Spring AI Contributors
 */
@ConfigurationProperties("spring.ai.openai.http-client")
public class OpenAiHttpClientProperties {

	/**
	 * The HTTP client type to use.
	 */
	public enum HttpClientType {

		/**
		 * Use JDK HttpClient. Requires application to provide a
		 * {@link java.net.http.HttpClient} bean.
		 */
		JDK,

		/**
		 * Use OkHttp (default). Requires okhttp dependency.
		 */
		OKHTTP,

		/**
		 * Use a custom HttpClient. Requires application to provide the appropriate client
		 * bean.
		 */
		CUSTOM

	}

	/**
	 * Default HTTP client type.
	 */
	public static final HttpClientType DEFAULT_TYPE = HttpClientType.OKHTTP;

	private HttpClientType type = DEFAULT_TYPE;

	/**
	 * Bean name of custom HttpClient to use when type is CUSTOM.
	 */
	private String customBeanName;

	public HttpClientType getType() {
		return this.type;
	}

	public void setType(HttpClientType type) {
		this.type = type;
	}

	public String getCustomBeanName() {
		return this.customBeanName;
	}

	public void setCustomBeanName(String customBeanName) {
		this.customBeanName = customBeanName;
	}

}