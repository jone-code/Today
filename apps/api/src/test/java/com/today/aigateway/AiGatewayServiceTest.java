package com.today.aigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.common.AiProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

  @Mock AiProperties properties;
  @Mock OpenAiChatClient chatClient;
  @Mock AiPromptTemplates prompts;

  @InjectMocks AiGatewayService gateway;

  @Test
  void withoutKeyUsesHeuristicAndSkipsClient() {
    when(properties.isConfigured()).thenReturn(false);

    var result =
        gateway.complete(
            AiGatewayService.AiTask.summary,
            Map.of("rawText", "今天很累"),
            new TypeReference<Map<String, String>>() {},
            () -> Map.of("oneLiner", "heuristic"));

    assertEquals(AiProvider.heuristic, result.provider());
    assertEquals("heuristic", result.data().get("oneLiner"));
    verify(chatClient, never()).chatJson(anyString(), anyString());
  }

  @Test
  void llmSuccessParsesJson() {
    when(properties.isConfigured()).thenReturn(true);
    when(prompts.system(any())).thenReturn("sys");
    when(prompts.user(any(), any())).thenReturn("user");
    when(chatClient.chatJson("sys", "user"))
        .thenReturn("{\"oneLiner\":\"来自模型\"}");

    var result =
        gateway.complete(
            AiGatewayService.AiTask.summary,
            Map.of("rawText", "今天顺利"),
            new TypeReference<Map<String, String>>() {},
            () -> Map.of("oneLiner", "heuristic"));

    assertEquals(AiProvider.llm, result.provider());
    assertEquals("来自模型", result.data().get("oneLiner"));
  }

  @Test
  void llmFailureFallsBack() {
    when(properties.isConfigured()).thenReturn(true);
    when(prompts.system(any())).thenReturn("sys");
    when(prompts.user(any(), any())).thenReturn("user");
    when(chatClient.chatJson("sys", "user")).thenThrow(new RuntimeException("timeout"));

    var result =
        gateway.complete(
            AiGatewayService.AiTask.summary,
            Map.of("rawText", "x"),
            new TypeReference<Map<String, String>>() {},
            () -> Map.of("oneLiner", "fallback"));

    assertEquals(AiProvider.heuristic, result.provider());
    assertEquals("fallback", result.data().get("oneLiner"));
  }
}
