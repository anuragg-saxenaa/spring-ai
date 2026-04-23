/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.ai.chat.client.advisor.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the OAuth 2.0 scopes required by a tool for authorization via RFC 8693 token
 * exchange.
 * <p>
 * When a tool method is annotated with {@code @ToolScope}, the
 * {@link ToolTokenExchangeAdvisor} will perform a token exchange before tool dispatch to
 * obtain a downscoped delegation token with the specified scopes. This enables
 * least-privilege enforcement for AI tool calls in enterprise environments.
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * @Tool(name = "read-user-profile")
 * @ToolScope(scopes = {"profile:read", "email:read"})
 * public UserProfile getUserProfile(ToolIdentityContext context) {
 *     // context.getDelegationToken() contains the downscoped token
 *     // with profile:read and email:read scopes
 *     return profileService.getProfile(context.subject());
 * }
 * }
 * </pre>
 *
 * @author Spring AI Team
 * @since 2.0.0
 * @see ToolTokenExchangeAdvisor
 * @see ToolIdentityContext
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
public @interface ToolScope {

	/**
	 * The OAuth 2.0 scopes required for tool execution. These scopes are used to obtain
	 * a downscoped delegation token via RFC 8693 token exchange.
	 * @return the required scopes
	 */
	String[] value();

	/**
	 * The resource indicator (audience) for the token request. Optional. If not
	 * specified, the token exchange will use the default resource configuration.
	 * @return the resource indicator URI
	 */
	String resource() default "";

	/**
	 * The registration ID of the {@code OAuth2AuthorizedClient} to use for token
	 * exchange. If not specified, uses the default authorized client manager.
	 * @return the client registration ID
	 */
	String clientRegistrationId() default "";

}