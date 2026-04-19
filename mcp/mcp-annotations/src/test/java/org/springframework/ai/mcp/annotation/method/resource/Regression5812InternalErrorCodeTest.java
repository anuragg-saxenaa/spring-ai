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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.adapter.ResourceAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Regression tests for GitHub issue #5812.
 * 
 * Verifies that runtime exceptions thrown by @McpResource methods are wrapped
 * with ErrorCodes.INTERNAL_ERROR (-32603), not ErrorCodes.INVALID_PARAMS (-32602).
 * Per the MCP specification, -32603 is the correct code for internal/runtime errors,
 * while -32602 means bad method parameters.
 *
 * @author Anurag Saxena
 */
public class Regression5812InternalErrorCodeTest {

	@Test
	public void testRuntimeExceptionProducesInternalErrorCode() throws Exception {
		// Given: a resource method that throws a runtime exception
		FailingResourceProvider provider = new FailingResourceProvider();
		Method method = FailingResourceProvider.class.getMethod("getFailingResource", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(method.getAnnotation(McpResource.class)))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("failing://resource");

		// When: the resource method is invoked
		// Then: it should throw McpError with INTERNAL_ERROR code, not INVALID_PARAMS
		assertThatThrownBy(() -> callback.apply(exchange, request))
			.isInstanceOf(McpError.class)
			.satisfies(mcpError -> {
				McpError error = (McpError) mcpError;
				// The key assertion: code must be INTERNAL_ERROR (-32603), not INVALID_PARAMS (-32602)
				assertThat(error.getJsonRpcError().code()).isEqualTo(ErrorCodes.INTERNAL_ERROR);
			})
			.hasMessageContaining("Error invoking resource method");
	}

	@Test
	public void testNullPointerExceptionProducesInternalErrorCode() throws Exception {
		// Given: a resource method that throws NullPointerException
		FailingResourceProvider provider = new FailingResourceProvider();
		Method method = FailingResourceProvider.class.getMethod("getNullPointerResource", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(method.getAnnotation(McpResource.class)))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("npe://resource");

		// Then: it should throw McpError with INTERNAL_ERROR code
		assertThatThrownBy(() -> callback.apply(exchange, request))
			.isInstanceOf(McpError.class)
			.satisfies(mcpError -> {
				McpError error = (McpError) mcpError;
				assertThat(error.getJsonRpcError().code()).isEqualTo(ErrorCodes.INTERNAL_ERROR);
			});
	}

	@Test
	public void testSuccessfulResourceStillWorks() throws Exception {
		// Verify the fix doesn't break normal resource methods
		FailingResourceProvider provider = new FailingResourceProvider();
		Method method = FailingResourceProvider.class.getMethod("getValidResource", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(method.getAnnotation(McpResource.class)))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("valid://resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) result.contents().get(0)).text()).isEqualTo("Valid content");
	}

	private static class FailingResourceProvider {

		@McpResource(uri = "failing://resource", description = "A resource that throws RuntimeException")
		public ReadResourceResult getFailingResource(ReadResourceRequest request) {
			throw new RuntimeException("Simulated internal error");
		}

		@McpResource(uri = "npe://resource", description = "A resource that throws NullPointerException")
		public ReadResourceResult getNullPointerResource(ReadResourceRequest request) {
			throw new NullPointerException("Simulated NPE");
		}

		@McpResource(uri = "valid://resource", description = "A valid resource")
		public ReadResourceResult getValidResource(ReadResourceRequest request) {
			return new ReadResourceResult(
				List.of(new TextResourceContents(request.uri(), "text/plain", "Valid content")));
		}

	}

}
