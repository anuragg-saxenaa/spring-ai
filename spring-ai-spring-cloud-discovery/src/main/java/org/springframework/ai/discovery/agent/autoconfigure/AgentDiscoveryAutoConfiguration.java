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

package org.springframework.ai.discovery.agent.autoconfigure;

import reactor.core.publisher.Mono;

import org.springframework.ai.discovery.agent.AgentService;
import org.springframework.ai.discovery.properties.SpringCloudDiscoveryProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for {@link AgentService} for A2A communication via Spring Cloud
 * Service Discovery.
 *
 * <p>
 * This configuration activates when:
 * <ul>
 * <li>{@code spring.ai.discovery.enabled=true}
 * <li>{@link DiscoveryClient} is on the classpath
 * </ul>
 *
 * <p>
 * The {@link AgentService} discovers A2A agents by querying the {@link DiscoveryClient}
 * for services whose instances carry the metadata key/value pair configured via
 * {@code spring.ai.discovery.agent-metadata-key} and
 * {@code spring.ai.discovery.agent-metadata-value} (defaults:
 * {@code type=a2a-agent}).
 *
 * <p>
 * Agents are called via A2A HTTP protocol with load-balancing, optional circuit breaker
 * (Resilience4j), and optional retry support.
 *
 * @author Anurag Saxena
 * @see AgentService
 * @see SpringCloudDiscoveryProperties
 * @see DiscoveryClient
 */
@AutoConfiguration
@EnableConfigurationProperties(SpringCloudDiscoveryProperties.class)
@ConditionalOnProperty(prefix = SpringCloudDiscoveryProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(DiscoveryClient.class)
public class AgentDiscoveryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AgentService agentService(DiscoveryClient discoveryClient,
			SpringCloudDiscoveryProperties properties,
			ObjectProvider<reactor.core.publisher.Mono<org.springframework.web.reactive.function.client.WebClient.Builder>> webClientBuilderSupplierProvider) {
		return new AgentService(discoveryClient, properties,
				() -> webClientBuilderSupplierProvider.getIfAvailable(() -> Mono.just(WebClient.builder())));
	}

}
