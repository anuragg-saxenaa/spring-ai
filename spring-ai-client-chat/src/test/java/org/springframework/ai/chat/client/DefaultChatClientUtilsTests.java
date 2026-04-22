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

package org.springframework.ai.chat.client;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultChatClientUtils}.
 *
 * @author Thomas Vitale
 * @author Sun Yuhan
 */
class DefaultChatClientUtilsTests {

	@Test
	void whenInputRequestIsNullThenThrows() {
		assertThatThrownBy(() -> DefaultChatClientUtils.toChatClientRequest(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputRequest cannot be null");
	}

	@Test
	void whenSystemTextIsProvidedThenSystemMessageIsAddedToPrompt() {
		String systemText = "System instructions";
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(systemText);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo(systemText);
	}

	@Test
	void whenSystemTextWithParamsIsProvidedThenSystemMessageIsRenderedAndAddedToPrompt() {
		String systemText = "System instructions for {name}";
		Map<String, Object> systemParams = Map.of("name", "Spring AI");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(s -> s.text(systemText).params(systemParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("System instructions for Spring AI");
	}

	@Test
	void whenMessagesAreProvidedThenTheyAreAddedToPrompt() {
		List<Message> messages = List.of(new SystemMessage("System message"), new UserMessage("User message"));
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.messages(messages);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).hasSize(2);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("System message");
		assertThat(result.prompt().getInstructions().get(1).getText()).isEqualTo("User message");
	}

	@Test
	void whenUserTextIsProvidedThenUserMessageIsAddedToPrompt() {
		String userText = "User question";
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(userText);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo(userText);
	}

	@Test
	void whenUserTextWithParamsIsProvidedThenUserMessageIsRenderedAndAddedToPrompt() {
		String userText = "Question about {topic}";
		Map<String, Object> userParams = Map.of("topic", "Spring AI");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(s -> s.text(userText).params(userParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("Question about Spring AI");
	}

	@Test
	void whenUserTextWithMediaIsProvidedThenUserMessageWithMediaIsAddedToPrompt() {
		String userText = "What's in this image?";
		Media media = mock(Media.class);
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(s -> s.text(userText).media(media));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(UserMessage.class);
		UserMessage userMessage = (UserMessage) result.prompt().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo(userText);
		assertThat(userMessage.getMedia()).contains(media);
	}

	@Test
	void whenSystemTextAndSystemMessageAreProvidedThenSystemTextIsFirst() {
		String systemText = "System instructions";
		List<Message> messages = List.of(new SystemMessage("System message"));
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(systemText)
			.messages(messages);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).hasSize(2);
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo(systemText);
	}

	@Test
	void whenUserTextAndUserMessageAreProvidedThenUserTextIsLast() {
		String userText = "User question";
		List<Message> messages = List.of(new UserMessage("User message"));
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(userText)
			.messages(messages);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).hasSize(2);
		assertThat(result.prompt().getInstructions()).last().isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions()).last().extracting(Message::getText).isEqualTo(userText);
	}

	@Test
	void whenToolCallingChatOptionsIsProvidedThenToolNamesAreSet() {
		var chatOptions = ToolCallingChatOptions.builder();
		List<String> toolNames = List.of("tool1", "tool2");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolNames(toolNames.toArray(new String[0]));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames);
	}

	@Test
	void whenToolCallingChatOptionsIsProvidedThenToolCallbacksAreSet() {
		var chatOptions = ToolCallingChatOptions.builder();
		ToolCallback toolCallback = new TestToolCallback("tool1");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolCallbacks(toolCallback);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolCallbacks()).contains(toolCallback);
	}

	@Test
	void whenToolCallingChatOptionsIsProvidedThenToolContextIsSet() {
		var chatOptions = ToolCallingChatOptions.builder();
		Map<String, Object> toolContext = Map.of("key", "value");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolContext(toolContext);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext);
	}

	@Test
	void whenToolNamesAndChatOptionsAreProvidedThenTheToolNamesOverride() {
		Set<String> toolNames1 = Set.of("toolA", "toolB");
		var chatOptions = ToolCallingChatOptions.builder().toolNames(toolNames1);
		List<String> toolNames2 = List.of("tool1", "tool2");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolNames(toolNames2.toArray(new String[0]));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames2);
	}

	@Test
	void whenToolCallbacksAndChatOptionsAreProvidedThenTheToolCallbacksOverride() {
		ToolCallback toolCallback1 = new TestToolCallback("tool1");
		var chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallback1);
		ToolCallback toolCallback2 = new TestToolCallback("tool2");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolCallbacks(toolCallback2);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolCallbacks()).containsExactlyInAnyOrder(toolCallback2);
	}

	@Test
	void whenToolContextAndChatOptionsAreProvidedThenTheValuesAreMerged() {
		Map<String, Object> toolContext1 = Map.of("key1", "value1");
		Map<String, Object> toolContext2 = Map.of("key2", "value2");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(ToolCallingChatOptions.builder().toolContext(toolContext1))
			.toolContext(toolContext2);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext1)
			.containsAllEntriesOf(toolContext2);
	}

	@Test
	void whenToolNamesAndChatOptionsAreDefaultChatOptions() {
		Set<String> toolNames1 = Set.of("toolA", "toolB");
		var chatOptions = ChatOptions.builder();
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolNames(toolNames1.toArray(new String[0]));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames1);
	}

	@Test
	void whenToolCallbacksAndChatOptionsAreDefaultChatOptions() {
		ToolCallback toolCallback1 = new TestToolCallback("tool1");
		var chatOptions = ChatOptions.builder();
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolCallbacks(toolCallback1);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolCallbacks()).containsExactlyInAnyOrder(toolCallback1);
	}

	@Test
	void whenToolContextAndChatOptionsAreDefaultChatOptions() {
		Map<String, Object> toolContext1 = Map.of("key1", "value1");
		var chatOptions = ChatOptions.builder();
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolContext(toolContext1);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext1);
	}

	@Test
	void whenAdvisorParamsAreProvidedThenTheyAreAddedToContext() {
		Map<String, Object> advisorParams = Map.of("key1", "value1", "key2", "value2");
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.advisors(a -> a.params(advisorParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.context()).containsAllEntriesOf(advisorParams);
	}

	@Test
	void whenCustomTemplateRendererIsProvidedThenItIsUsedForRendering() {
		String systemText = "Instructions <name>";
		Map<String, Object> systemParams = Map.of("name", "Spring AI");
		TemplateRenderer customRenderer = StTemplateRenderer.builder()
			.startDelimiterToken('<')
			.endDelimiterToken('>')
			.build();
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(s -> s.text(systemText).params(systemParams))
			.templateRenderer(customRenderer);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("Instructions Spring AI");
	}

	@Test
	void whenAllComponentsAreProvidedThenCompleteRequestIsCreated() {
		String systemText = "System instructions for {name}";
		Map<String, Object> systemParams = Map.of("name", "Spring AI");

		String userText = "Question about {topic}";
		Map<String, Object> userParams = Map.of("topic", "Spring AI");
		Media media = mock(Media.class);

		List<Message> messages = List.of(new UserMessage("Intermediate message"));

		var chatOptions = ToolCallingChatOptions.builder();
		List<String> toolNames = List.of("tool1", "tool2");
		ToolCallback toolCallback = new TestToolCallback("tool3");
		Map<String, Object> toolContext = Map.of("toolKey", "toolValue");

		Map<String, Object> advisorParams = Map.of("advisorKey", "advisorValue");

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(s -> s.text(systemText).params(systemParams))
			.user(u -> u.text(userText).params(userParams).media(media))
			.messages(messages)
			.toolNames(toolNames.toArray(new String[0]))
			.toolCallbacks(toolCallback)
			.toolContext(toolContext)
			.options(chatOptions)
			.advisors(a -> a.params(advisorParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();

		assertThat(result.prompt().getInstructions()).hasSize(3);
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("System instructions for Spring AI");
		assertThat(result.prompt().getInstructions().get(1).getText()).isEqualTo("Intermediate message");
		assertThat(result.prompt().getInstructions().get(2)).isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions().get(2).getText()).isEqualTo("Question about Spring AI");
		UserMessage userMessage = (UserMessage) result.prompt().getInstructions().get(2);
		assertThat(userMessage.getMedia()).contains(media);

		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames);
		assertThat(resultOptions.getToolCallbacks()).contains(toolCallback);
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext);

		assertThat(result.context()).containsAllEntriesOf(advisorParams);
	}

	@Test
	void whenDefaultOptionsAndCallTimeOptionsAreProvidedThenCallTimeOverrides() {
		// Model default: temperature=0.5, maxTokens=100
		ChatOptions modelDefaults = ChatOptions.builder().temperature(0.5).maxTokens(100).build();

		// Call-time override: temperature=0.8 (maxTokens should inherit from defaults)
		ChatOptions callTimeOptions = ChatOptions.builder().temperature(0.8).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions()).isNotNull();
		// Call-time temperature should win
		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(0.8);
		// Default maxTokens should be inherited
		assertThat(result.prompt().getOptions().getMaxTokens()).isEqualTo(100);
	}

	@Test
	void whenDefaultOptionsAndCallTimeOptionsPartialOverrideThenBothApply() {
		// Model default: temperature=0.5, maxTokens=100, model="gpt-4"
		ChatOptions modelDefaults = ChatOptions.builder().temperature(0.5).maxTokens(100).model("gpt-4").build();

		// Call-time override: only maxTokens=200 (temperature and model should inherit)
		ChatOptions callTimeOptions = ChatOptions.builder().maxTokens(200).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		// Call-time maxTokens should win
		assertThat(result.prompt().getOptions().getMaxTokens()).isEqualTo(200);
		// Default temperature and model should be inherited
		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(0.5);
		assertThat(result.prompt().getOptions().getModel()).isEqualTo("gpt-4");
	}

	@Test
	void whenOnlyDefaultOptionsExistThenTheyAreUsed() {
		ChatOptions modelDefaults = ChatOptions.builder().temperature(0.7).maxTokens(50).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello");

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(0.7);
		assertThat(result.prompt().getOptions().getMaxTokens()).isEqualTo(50);
	}

	@Test
	void whenOnlyCallTimeOptionsExistThenTheyAreUsed() {
		ChatOptions callTimeOptions = ChatOptions.builder().temperature(0.9).topP(0.95).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(0.9);
		assertThat(result.prompt().getOptions().getTopP()).isEqualTo(0.95);
	}

	@Test
	void whenNeitherOptionsExistThenResultIsEmpty() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello");

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions()).isNotNull();
	}

	@Test
	void whenCallTimeOptionsNullOutDefaultThenNullWins() {
		// Model default has temperature=0.5
		ChatOptions modelDefaults = ChatOptions.builder().temperature(0.5).build();
		// Call-time customizer does not set temperature (null), so default should apply
		ChatOptions callTimeOptions = ChatOptions.builder().maxTokens(200).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		// Default temperature should be inherited when call-time doesn't set it
		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(0.5);
		assertThat(result.prompt().getOptions().getMaxTokens()).isEqualTo(200);
	}

	@Test
	void whenToolCallingOptionsWithDefaultsAndCallTimeOverrideThenMerged() {
		// Model default ToolCallingChatOptions with toolName defaultTool
		ToolCallingChatOptions modelDefaults = ToolCallingChatOptions.builder()
			.temperature(0.5)
			.toolNames(Set.of("defaultTool"))
			.build();

		// Call-time ToolCallingChatOptions overrides toolName and temperature
		ToolCallingChatOptions callTimeOptions = ToolCallingChatOptions.builder()
			.temperature(0.8)
			.toolNames(Set.of("runtimeTool"))
			.build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		// Call-time values override defaults
		assertThat(resultOptions.getTemperature()).isEqualTo(0.8);
		assertThat(resultOptions.getToolNames()).containsExactly("runtimeTool");
	}

	@Test
	void whenDefaultOptionsBeanAndCallTimeOptionsBothSetSameFieldThenCallTimeWins() {
		// This is the classic bug scenario: bean sets temperature=0.1,
		// call-time sets temperature=0.9. Call-time MUST win.
		ChatOptions modelDefaults = ChatOptions.builder().temperature(0.1).maxTokens(10).build();
		ChatOptions callTimeOptions = ChatOptions.builder().temperature(0.9).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(0.9);
		// Unset fields from call-time should still inherit from defaults
		assertThat(result.prompt().getOptions().getMaxTokens()).isEqualTo(10);
	}

	@Test
	void whenDefaultOptionsBeanSetsAllFieldsAndCallTimeSetsPartialThenPartialOverrides() {
		// Model bean configures full defaults
		ChatOptions modelDefaults = ChatOptions.builder()
			.model("gpt-4o")
			.temperature(0.3)
			.maxTokens(500)
			.topP(0.9)
			.frequencyPenalty(0.5)
			.presencePenalty(0.5)
			.stopSequences(List.of("STOP"))
			.topK(10)
			.build();

		// User only overrides temperature at call time
		ChatOptions callTimeOptions = ChatOptions.builder().temperature(1.0).build();

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getDefaultOptions()).thenReturn(modelDefaults);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user("Hello")
			.options(callTimeOptions.mutate());

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result.prompt().getOptions().getTemperature()).isEqualTo(1.0); // overridden
		assertThat(result.prompt().getOptions().getModel()).isEqualTo("gpt-4o"); // inherited
		assertThat(result.prompt().getOptions().getMaxTokens()).isEqualTo(500); // inherited
		assertThat(result.prompt().getOptions().getTopP()).isEqualTo(0.9); // inherited
		assertThat(result.prompt().getOptions().getFrequencyPenalty()).isEqualTo(0.5); // inherited
		assertThat(result.prompt().getOptions().getPresencePenalty()).isEqualTo(0.5); // inherited
		assertThat(result.prompt().getOptions().getStopSequences()).containsExactly("STOP"); // inherited
		assertThat(result.prompt().getOptions().getTopK()).isEqualTo(10); // inherited
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
		}

		TestToolCallback(String name, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
