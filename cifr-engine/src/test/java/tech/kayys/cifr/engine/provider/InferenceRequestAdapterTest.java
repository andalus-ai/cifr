package tech.kayys.cifr.engine.provider;

import org.junit.jupiter.api.Test;
import tech.kayys.andalus.agent.spi.InferenceResponse;
import tech.kayys.andalus.agent.spi.InferenceTypes.AssistantMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.ToolCall;
import tech.kayys.andalus.inference.Choice;
import tech.kayys.andalus.inference.CompletionResult;
import tech.kayys.andalus.inference.Message;
import tech.kayys.andalus.inference.Usage;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InferenceRequestAdapterTest {

    @Test
    void testGemma4ParenthesisToolCall() {
        String raw = "<|tool_call>call:list_directory({\"path\": \".\"})<tool_call|>";
        CompletionResult result = new CompletionResult("req-1", "gemma",
                List.of(Choice.of(Message.assistant(raw), "stop")),
                new Usage(10, 20, 30, 0.0), Collections.emptyMap(), 100);

        InferenceResponse response = InferenceRequestAdapter.toInferenceResponse("req-1", "gemma", result);

        assertNotNull(response);
        assertEquals("tool_calls", response.finishReason());
        assertTrue(response.message() instanceof AssistantMessage);
        AssistantMessage msg = (AssistantMessage) response.message();
        assertEquals(1, msg.toolCalls().size());
        ToolCall tc = msg.toolCalls().get(0);
        assertEquals("list_directory", tc.name());
        assertTrue(tc.arguments().contains("\"path\""));
    }

    @Test
    void testGemma4BracedToolCall() {
        String raw = "<|tool_call>call:default_tools:read_file{path: \"pom.xml\"}<tool_call|>";
        CompletionResult result = new CompletionResult("req-2", "gemma",
                List.of(Choice.of(Message.assistant(raw), "stop")),
                new Usage(10, 20, 30, 0.0), Collections.emptyMap(), 100);

        InferenceResponse response = InferenceRequestAdapter.toInferenceResponse("req-2", "gemma", result);

        assertNotNull(response);
        assertEquals("tool_calls", response.finishReason());
        AssistantMessage msg = (AssistantMessage) response.message();
        assertEquals(1, msg.toolCalls().size());
        ToolCall tc = msg.toolCalls().get(0);
        assertEquals("read_file", tc.name());
        assertTrue(tc.arguments().contains("\"path\""));
    }

    @Test
    void testStandardActionToolCall() {
        String raw = "Action: list_directory({\"path\": \".\"})";
        CompletionResult result = new CompletionResult("req-3", "gemma",
                List.of(Choice.of(Message.assistant(raw), "stop")),
                new Usage(10, 20, 30, 0.0), Collections.emptyMap(), 100);

        InferenceResponse response = InferenceRequestAdapter.toInferenceResponse("req-3", "gemma", result);

        assertNotNull(response);
        assertEquals("tool_calls", response.finishReason());
        AssistantMessage msg = (AssistantMessage) response.message();
        assertEquals(1, msg.toolCalls().size());
        ToolCall tc = msg.toolCalls().get(0);
        assertEquals("list_directory", tc.name());
    }

    @Test
    void testPlainAssistantAnswer() {
        String raw = "I have inspected the project and here are the details.";
        CompletionResult result = new CompletionResult("req-4", "gemma",
                List.of(Choice.of(Message.assistant(raw), "stop")),
                new Usage(10, 20, 30, 0.0), Collections.emptyMap(), 100);

        InferenceResponse response = InferenceRequestAdapter.toInferenceResponse("req-4", "gemma", result);

        assertNotNull(response);
        assertNotEquals("tool_calls", response.finishReason());
        AssistantMessage msg = (AssistantMessage) response.message();
        assertTrue(msg.toolCalls() == null || msg.toolCalls().isEmpty());
        assertEquals(raw, msg.content());
    }
}
