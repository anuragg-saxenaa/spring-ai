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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Christian Tzolov
 */
public final class AnnotationProviderUtil {

	private AnnotationProviderUtil() {
	}

	/**
	 * Returns the declared methods of the given bean, sorted by method name and parameter
	 * types. This is useful for consistent method ordering in annotation processing.
	 * <p>
	 * For JDK dynamic proxies (e.g. those created by Spring's {@code HttpServiceClient}
	 * via {@code java.lang.reflect.Proxy}), the proxy class itself does not declare the
	 * interface methods — they are dispatched to the proxy's
	 * {@link java.lang.reflect.InvocationHandler}. Reflecting on the proxy class
	 * therefore only exposes internal proxy methods (such as
	 * {@code $Proxy$N.proxyClassLookup}) and not the user-declared methods where
	 * annotations like {@code @McpTool} live.
	 * <p>
	 * To make annotations on interface methods discoverable, when the bean is a JDK
	 * dynamic proxy we union the proxy's declared methods with the methods declared by
	 * all proxied interfaces. The interface {@link Method} objects carry the annotations
	 * and are usable for reflective invocation through the proxy (issue #5823).
	 * @param bean The bean instance to inspect
	 * @return An array of sorted methods
	 */
	public static Method[] beanMethods(Object bean) {

		Class<?> beanClass = AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();

		// Use a LinkedHashSet to deduplicate Method objects while preserving insertion
		// order. Method#equals is identity-based on the declaring class, so the same
		// logical method reached through the proxy class and through the interface
		// would otherwise appear as two distinct entries.
		Set<Method> uniqueMethods = new LinkedHashSet<>(
				Arrays.asList(ReflectionUtils.getUniqueDeclaredMethods(beanClass)));

		// Spring's AopUtils does not recognise raw java.lang.reflect.Proxy instances
		// (those not wrapped via Spring AOP's ProxyFactory), so isAopProxy may return
		// false even for a real JDK proxy. Check Proxy.isProxyClass explicitly to make
		// sure interface methods are surfaced regardless of how the proxy was created.
		if (Proxy.isProxyClass(beanClass)) {
			for (Class<?> iface : beanClass.getInterfaces()) {
				uniqueMethods.addAll(Arrays.asList(ReflectionUtils.getUniqueDeclaredMethods(iface)));
			}
		}

		Method[] methods = uniqueMethods.stream()
			.filter(ReflectionUtils.USER_DECLARED_METHODS::matches)
			.sorted(Comparator.comparing(Method::getName)
				.thenComparing(method -> Arrays.toString(method.getParameterTypes())))
			.toArray(Method[]::new);

		return methods;
	}

}
