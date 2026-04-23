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

package org.springframework.ai.chat.client.advisor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetrievalAugmentationAdvisor} skipDuringToolExecution feature.
 *
 * @author Research Agent
 * @since 1.1.0
 */
class RetrievalAugmentationAdvisorSkipDuringToolExecutionTests {

	@Test
	void whenSkipDuringToolExecutionEnabledAndToolExecutionContextSetThenAdvisorSkips() {
		// Chat Model
		var chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture())).willReturn(ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"))))
			.build());

		// Document Retriever - track calls
		var retrieveCallCount = new AtomicInteger(0);
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		when(documentRetriever.retrieve(Mockito.any())).thenAnswer(invocation -> {
			retrieveCallCount.incrementAndGet();
			return List.of(Document.builder().id("1").text("doc1").build());
		});

		// Advisor with skipDuringToolExecution enabled
		var advisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(documentRetriever)
			.skipDuringToolExecution(true)
			.build();

		// Chat Client
		var chatClient = ChatClient.builder(chatModel)
			.defaultAdvisors(advisor)
			.build();

		// First call - normal request (should process RAG)
		chatClient.prompt()
			.user("What is the document about?")
			.call()
			.chatResponse();

		assertThat(retrieveCallCount.get()).isEqualTo(1);

		// Simulate tool-execution sub-call by setting context marker
		// (In real usage, this would be set by the tool execution infrastructure)
		var toolExecutionRequest = org.springframework.ai.chat.client.ChatClientRequest.builder()
			.prompt(new Prompt(org.springframework.ai.chat.messages.UserMessage.of("Tool sub-call")))
			.context(java.util.Map.of(RetrievalAugmentationAdvisor.TOOL_EXECUTION_CALL, true))
			.build();

		// Simulate calling advisor.before directly with tool execution context
		advisor.before(toolExecutionRequest, null);

		// Verify that document retriever was NOT called again (skipped)
		assertThat(retrieveCallCount.get()).isEqualTo(1);
	}

	@Test
	void whenSkipDuringToolExecutionDisabledThenAdvisorProcessesNormally() {
		// Document Retriever - track calls
		var retrieveCallCount = new AtomicInteger(0);
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		when(documentRetriever.retrieve(Mockito.any())).thenAnswer(invocation -> {
			retrieveCallCount.incrementAndGet();
			return List.of(Document.builder().id("1").text("doc1").build());
		});

		// Advisor with skipDuringToolExecution disabled (default)
		var advisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(documentRetriever)
			.skipDuringToolExecution(false)
			.build();

		// Simulate tool-execution sub-call by setting context marker
		var toolExecutionRequest = org.springframework.ai.chat.client.ChatClientRequest.builder()
			.prompt(new Prompt(org.springframework.ai.chat.messages.UserMessage.of("Tool sub-call")))
			.context(java.util.Map.of(RetrievalAugmentationAdvisor.TOOL_EXECUTION_CALL, true))
			.build();

		// Simulate calling advisor.before with tool execution context
		advisor.before(toolExecutionRequest, null);

		// Verify that document retriever WAS called (not skipped)
		assertThat(retrieveCallCount.get()).isEqualTo(1);
	}

	@Test
	void whenNoToolExecutionContextAndSkipDuringToolExecutionEnabledThenAdvisorProcesses() {
		// Chat Model
		var chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		given(chatModel.call(Mockito.any())).willReturn(ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Result"))))
			.build());

		// Document Retriever - track calls
		var retrieveCallCount = new AtomicInteger(0);
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		when(documentRetriever.retrieve(Mockito.any())).thenAnswer(invocation -> {
			retrieveCallCount.incrementAndGet();
			return List.of(Document.builder().id("1").text("doc1").build());
		});

		// Advisor with skipDuringToolExecution enabled
		var advisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(documentRetriever)
			.skipDuringToolExecution(true)
			.build();

		// Chat Client
		var chatClient = ChatClient.builder(chatModel)
			.defaultAdvisors(advisor)
			.build();

		// Call without tool execution context (normal request)
		chatClient.prompt()
			.user("What is the document about?")
			.call()
			.chatResponse();

		// Verify that document retriever WAS called (not skipped because no tool execution context)
		assertThat(retrieveCallCount.get()).isEqualTo(1);
	}

}
