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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Discovers MCP annotations on bean methods. Supports both regular beans and
 * {@link Proxy} instances wrapping HTTP Service interfaces annotated with
 * {@code @HttpExchange}.
 *
 * <p>
 * When a bean is a JDK proxy (e.g., created by Spring's {@code @ImportHttpServices}), the
 * {@code @McpTool} annotations on the proxied interface's methods are not directly
 * visible on the proxy class. This class detects such proxies and searches the
 * implemented interfaces (and their hierarchy) for {@code @McpTool}-annotated methods.
 *
 * @author Christian Tzolov
 * @author Josh Long
 * @author Anurag Saxena
 */
class AnnotatedMethodDiscovery {

	protected final Set<Class<? extends Annotation>> targetAnnotations;

	AnnotatedMethodDiscovery(Set<Class<? extends Annotation>> targetAnnotations) {
		this.targetAnnotations = targetAnnotations;
	}

	/**
	 * Scans a bean class for methods annotated with any of the target annotations. Also
	 * searches the interfaces implemented by the bean (including their hierarchy) to find
	 * annotations on proxied interface methods.
	 * @param beanClass the bean class to scan
	 * @return the set of annotation types found on the bean's methods
	 */
	protected Set<Class<? extends Annotation>> scan(Class<?> beanClass) {
		Set<Class<? extends Annotation>> foundAnnotations = new HashSet<>();

		// Scan all methods in the bean class (handles regular classes and non-proxy
		// interfaces)
		ReflectionUtils.doWithMethods(beanClass, method -> {
			this.targetAnnotations.forEach(annotationType -> {
				if (AnnotationUtils.findAnnotation(method, annotationType) != null) {
					foundAnnotations.add(annotationType);
				}
			});
		});

		// For JDK proxy beans, search the proxied interface hierarchy for @McpTool
		// annotations.
		// This is needed because @McpTool on a method of an @HttpExchange interface is
		// not
		// directly
		// visible on the proxy class — it exists on the interface method.
		if (Proxy.isProxyClass(beanClass)) {
			for (Class<?> iface : beanClass.getInterfaces()) {
				scanInterfaceHierarchy(iface, foundAnnotations);
			}
		}

		return foundAnnotations;
	}

	/**
	 * Recursively scans an interface and its super-interfaces for methods annotated with
	 * target annotations.
	 * @param iface the interface to scan
	 * @param foundAnnotations accumulator for found annotation types
	 */
	private void scanInterfaceHierarchy(Class<?> iface, Set<Class<? extends Annotation>> foundAnnotations) {
		if (iface == null || !iface.isInterface()) {
			return;
		}

		for (Method method : iface.getDeclaredMethods()) {
			for (Class<? extends Annotation> annotationType : this.targetAnnotations) {
				if (AnnotationUtils.findAnnotation(method, annotationType) != null) {
					foundAnnotations.add(annotationType);
				}
			}
		}

		// Recurse into super-interfaces
		for (Class<?> superIface : iface.getInterfaces()) {
			scanInterfaceHierarchy(superIface, foundAnnotations);
		}
	}

}
