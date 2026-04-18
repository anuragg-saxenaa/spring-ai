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

package org.springframework.ai.discovery.agent;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

/**
 * Represents an A2A (Agent-to-Agent) communication response.
 *
 * @author Anurag Saxena
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aResponse {

	@JsonProperty("jsonrpc")
	private String jsonrpc = "2.0";

	@JsonProperty("id")
	@Nullable
	private String id;

	@JsonProperty("result")
	@Nullable
	private Map<String, Object> result;

	@JsonProperty("error")
	@Nullable
	private A2aError error;

	public A2aResponse() {
	}

	public A2aResponse(@Nullable Map<String, Object> result) {
		this.result = result;
	}

	@Nullable
	public String getJsonrpc() {
		return this.jsonrpc;
	}

	public void setJsonrpc(String jsonrpc) {
		this.jsonrpc = jsonrpc;
	}

	@Nullable
	public String getId() {
		return this.id;
	}

	public void setId(@Nullable String id) {
		this.id = id;
	}

	@Nullable
	public Map<String, Object> getResult() {
		return this.result;
	}

	public void setResult(@Nullable Map<String, Object> result) {
		this.result = result;
	}

	@Nullable
	public A2aError getError() {
		return this.error;
	}

	public void setError(@Nullable A2aError error) {
		this.error = error;
	}

	public boolean isSuccess() {
		return this.error == null;
	}

	@Override
	public String toString() {
		return "A2aResponse{jsonrpc='" + this.jsonrpc + "', id='" + this.id + "', result=" + this.result
				+ ", error=" + this.error + "}";
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class A2aError {

		@JsonProperty("code")
		private int code;

		@JsonProperty("message")
		private String message;

		@JsonProperty("data")
		@Nullable
		private Object data;

		public A2aError() {
		}

		public A2aError(int code, String message) {
			this.code = code;
			this.message = message;
		}

		public int getCode() {
			return this.code;
		}

		public void setCode(int code) {
			this.code = code;
		}

		public String getMessage() {
			return this.message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		@Nullable
		public Object getData() {
			return this.data;
		}

		public void setData(@Nullable Object data) {
			this.data = data;
		}

	}

}
