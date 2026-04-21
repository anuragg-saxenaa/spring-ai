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

package org.springframework.ai.google.genai;

import java.util.List;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GoogleGenAiChatModel thought metadata propagation. Verifies that the
 * isThought flag from Gemini thinking parts is propagated to messageMetadata.
 *
 * @author Anurag Saxena
 * @since 1.1.0
 */
public class GoogleGenAiChatModelThoughtMetadataTest {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiGeminiChatModel chatModel;

	private RetryTemplate retryTemplate;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
			.model("gemini-2.0-flash-thinking-exp")
			.temperature(0.7)
			.build();

		this.chatModel = new TestGoogleGenAiGeminiChatModel(this.mockClient, defaultOptions, this.retryTemplate);
	}

	@Test
	void testIsThoughtTruePropagatedToMetadata() {
		// Create mock response with a thought part (thinking model)
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(50)
			.candidatesTokenCount(100)
			.totalTokenCount(150)
			.thoughtsTokenCount(30)
			.build();

		// Part with thought=true (thinking model intermediate step)
		Part thoughtPart = Part.builder().text("Let me think about this problem step by step...").thought(true).build();

		Content responseContent = Content.builder().parts(thoughtPart).build();
		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash-thinking-exp")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("Explain how machine learning works");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();

		Generation generation = response.getResults().get(0);
		AssistantMessage assistantMessage = (AssistantMessage) generation.getOutput();

		// Verify isThought=true is present in messageMetadata
		assertThat(assistantMessage.getMetadata()).containsKey("isThought");
		assertThat(assistantMessage.getMetadata().get("isThought")).isEqualTo(true);
	}

	@Test
	void testIsThoughtFalsePropagatedToMetadata() {
		// Create mock response with a regular text part (isThought=false)
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(50)
			.candidatesTokenCount(100)
			.totalTokenCount(150)
			.build();

		// Part without thought flag (regular response)
		Part textPart = Part.builder().text("Machine learning is a way for computers to learn from data.").build();

		Content responseContent = Content.builder().parts(textPart).build();
		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("What is machine learning?");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();

		Generation generation = response.getResults().get(0);
		AssistantMessage assistantMessage = (AssistantMessage) generation.getOutput();

		// Verify isThought=false is present in messageMetadata
		assertThat(assistantMessage.getMetadata()).containsKey("isThought");
		assertThat(assistantMessage.getMetadata().get("isThought")).isEqualTo(false);
	}

	@Test
	void testMultiplePartsWithMixedThoughtFlags() {
		// Create mock response with multiple parts: thought + regular text
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(50)
			.candidatesTokenCount(150)
			.totalTokenCount(200)
			.thoughtsTokenCount(50)
			.build();

		// First part is thought
		Part thoughtPart = Part.builder()
			.text("First, I need to understand what the user is asking about...")
			.thought(true)
			.build();

		// Second part is regular text (final answer)
		Part answerPart = Part.builder()
			.text("Machine learning enables computers to learn patterns from data without being explicitly programmed.")
			.thought(false)
			.build();

		Content responseContent = Content.builder().parts(List.of(thoughtPart, answerPart)).build();
		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash-thinking-exp")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("What is machine learning in simple terms?");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).isNotEmpty();

		// Each part should have its own generation with appropriate isThought flag
		List<Generation> generations = response.getResults();
		assertThat(generations).hasSize(2);

		Generation thoughtGeneration = generations.get(0);
		AssistantMessage thoughtMessage = (AssistantMessage) thoughtGeneration.getOutput();
		assertThat(thoughtMessage.getMetadata().get("isThought")).isEqualTo(true);
		assertThat(thoughtMessage.getText()).contains("First, I need to understand");

		Generation answerGeneration = generations.get(1);
		AssistantMessage answerMessage = (AssistantMessage) answerGeneration.getOutput();
		assertThat(answerMessage.getMetadata().get("isThought")).isEqualTo(false);
		assertThat(answerMessage.getText()).contains("Machine learning enables");
	}

	@Test
	void testCandidateMetadataAlsoContainsRelevantFields() {
		// Verify that candidate-level metadata is also present alongside isThought
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(50)
			.candidatesTokenCount(100)
			.totalTokenCount(150)
			.thoughtsTokenCount(30)
			.build();

		Part thoughtPart = Part.builder().text("Processing request...").thought(true).build();

		Content responseContent = Content.builder().parts(thoughtPart).build();
		Candidate candidate = Candidate.builder().content(responseContent).index(0).build();

		GenerateContentResponse mockResponse = GenerateContentResponse.builder()
			.candidates(List.of(candidate))
			.usageMetadata(usageMetadata)
			.modelVersion("gemini-2.0-flash-thinking-exp")
			.build();

		this.chatModel.setMockGenerateContentResponse(mockResponse);

		UserMessage userMessage = new UserMessage("Test request");
		Prompt prompt = new Prompt(List.of(userMessage));
		ChatResponse response = this.chatModel.call(prompt);

		Generation generation = response.getResults().get(0);
		AssistantMessage assistantMessage = (AssistantMessage) generation.getOutput();

		// Candidate-level metadata should include candidateIndex and finishReason
		assertThat(assistantMessage.getMetadata()).containsKey("candidateIndex");
		assertThat(assistantMessage.getMetadata()).containsKey("finishReason");
		// isThought is part-level metadata
		assertThat(assistantMessage.getMetadata()).containsKey("isThought");
	}

}
