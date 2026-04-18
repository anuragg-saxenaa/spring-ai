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
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

/**
 * Represents an A2A (Agent-to-Agent) communication request.
 *
 * @author Anurag Saxena
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aRequest {

	@JsonProperty("jsonrpc")
	private String jsonrpc = "2.0";

	@JsonProperty("id")
	@Nullable
	private String id;

	@JsonProperty("method")
	private String method;

	@JsonProperty("params")
	@Nullable
	private Map<String, Object> params;

	public A2aRequest() {
	}

	public A2aRequest(String method, @Nullable Map<String, Object> params) {
		this.method = method;
		this.params = params;
	}

	public A2aRequest(String method, @Nullable Map<String, Object> params, @Nullable String id) {
		this.method = method;
		this.params = params;
		this.id = id;
	}

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

	public String getMethod() {
		return this.method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	@Nullable
	public Map<String, Object> getParams() {
		return this.params;
	}

	public void setParams(@Nullable Map<String, Object> params) {
		this.params = params;
	}

	@Override
	public String toString() {
		return "A2aRequest{jsonrpc='" + this.jsonrpc + "', id='" + this.id + "', method='" + this.method
				+ "', params=" + this.params + "}";
	}

}
