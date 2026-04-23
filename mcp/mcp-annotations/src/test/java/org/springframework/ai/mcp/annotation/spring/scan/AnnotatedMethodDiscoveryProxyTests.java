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

package org.springframework.ai.mcp.annotation.spring.scan;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnnotatedMethodDiscovery} — specifically the JDK proxy / HTTP Service
 * interface scenario from GitHub issue #5823.
 *
 * <p>
 * When a Spring {@code @ImportHttpServices}-created proxy wraps an interface whose
 * methods are annotated with both {@code @HttpExchange} and {@code @McpTool}, the
 * {@code @McpTool} annotations live on the <b>interface method</b>, not the proxy class.
 * The bean post-processor must search the interface hierarchy to find them.
 *
 * @author Anurag Saxena
 */
class AnnotatedMethodDiscoveryProxyTests {

	// --- HTTP Service interface (simulates @HttpExchange + @McpTool combo) ---

	interface RemoteService {

		@McpTool(name = "list-elements", description = "Lists all available elements.")
		List<String> getElements();

		@McpTool(name = "get-element", description = "Gets a single element by ID.", generateOutputSchema = true)
		Map<String, Object> getElement(@McpToolParam(description = "The element ID") String id);

		String nonAnnotatedMethod();

	}

	interface SubRemoteService extends RemoteService {

		@McpTool(name = "sub-method", description = "A method from a sub-interface.")
		void subMethod();

	}

	// --- JDK proxy factory for tests ---

	private static Object newProxyInstance(Class<?> iface, Map<String, Object> methodReturns) {
		InvocationHandler handler = (proxy, method, args) -> {
			Object ret = methodReturns.get(method.getName());
			if (ret instanceof RuntimeException ex) {
				throw ex;
			}
			if (ret == null && !methodReturns.containsKey(method.getName()) && !method.getName().equals("toString")
					&& !method.getName().equals("equals") && !method.getName().equals("hashCode")) {
				return null;
			}
			return ret;
		};
		// Multi-interface: RemoteService + SubRemoteService
		return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[] { iface },
				handler);
	}

	// --- Tests ---

	@Test
	void testScanFindsMcpToolOnRegularBean() {
		// Plain (non-proxy) bean — annotations are directly on the class methods
		Object bean = new RegularToolBean();
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(bean.getClass());

		assertThat(found).contains(McpTool.class);
	}

	@Test
	void testScanFindsMcpToolOnJdkProxyBean() {
		// JDK proxy wrapping RemoteService — @McpTool is on the INTERFACE method,
		// not directly on the proxy class.
		Object proxyBean = newProxyInstance(RemoteService.class, Map.of("getElements", List.of("a", "b")));
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(proxyBean.getClass());

		assertThat(found).contains(McpTool.class);
	}

	@Test
	void testScanFindsMcpToolOnMultiInterfaceProxy() {
		// Proxy implementing multiple interfaces (RemoteService + SubRemoteService)
		Object proxyBean = newProxyInstance(SubRemoteService.class, Map.of("getElements", List.of("x")));
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(proxyBean.getClass());

		assertThat(found).contains(McpTool.class);
	}

	@Test
	void testScanFindsMcpToolInSubInterfaceHierarchy() {
		// The @McpTool on SubRemoteService.subMethod() should be found
		Object proxyBean = newProxyInstance(SubRemoteService.class, Map.of());
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(proxyBean.getClass());

		assertThat(found).contains(McpTool.class);
	}

	@Test
	void testScanFindsMcpToolOnRegularInterface() {
		// Even a plain (non-proxy) interface should be scanned correctly
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(RemoteService.class);

		assertThat(found).contains(McpTool.class);
	}

	@Test
	void testScanReturnsEmptyForNonAnnotatedBean() {
		Object bean = new Object();
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(bean.getClass());

		assertThat(found).isEmpty();
	}

	@Test
	void testScanWithMultipleTargetAnnotations() {
		// Mix @McpTool and @Deprecated as target annotations
		Object proxyBean = newProxyInstance(RemoteService.class, Map.of("getElements", List.of()));
		Set<Class<? extends Annotation>> targetAnnotations = Set.of(McpTool.class, Deprecated.class);
		AnnotatedMethodDiscovery discovery = new AnnotatedMethodDiscovery(targetAnnotations);

		Set<Class<? extends Annotation>> found = discovery.scan(proxyBean.getClass());

		// Should find @McpTool (on interface), @Deprecated may also be found on
		// Object.class
		assertThat(found).contains(McpTool.class);
	}

	// --- Helper bean: regular (non-proxy) class with @McpTool ---

	static class RegularToolBean {

		@McpTool(name = "regular-tool", description = "A regular @McpTool method.")
		public void regularTool() {
		}

	}

}
