package com.today.aigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.common.AiProvider;
import java.util.Map;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

  @Mock AiProperties properties;
  @Mock OpenAiCompatibleClient client;
  @Mock AiPromptTemplates prompts;
  @Mock AiCallObserver observer;

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
    verify(client, never()).chatJson(anyString(), anyString());
    verify(observer).recordSkippedComplete(AiGatewayService.AiTask.summary);
  }

  @Test
  void llmSuccessParsesJson() {
    when(properties.isConfigured()).thenReturn(true);
    when(prompts.system(any())).thenReturn("sys");
    when(prompts.user(any(), any())).thenReturn("user");
    when(client.chatJson("sys", "user")).thenReturn("{\"oneLiner\":\"来自模型\"}");

    var result =
        gateway.complete(
            AiGatewayService.AiTask.summary,
            Map.of("rawText", "今天顺利"),
            new TypeReference<Map<String, String>>() {},
            () -> Map.of("oneLiner", "heuristic"));

    assertEquals(AiProvider.llm, result.provider());
    assertEquals("来自模型", result.data().get("oneLiner"));
    verify(observer)
        .recordComplete(
            eq(AiGatewayService.AiTask.summary),
            eq(AiProvider.llm),
            eq("ok"),
            anyLong(),
            isNull());
  }

  @Test
  void llmFailureFallsBack() {
    when(properties.isConfigured()).thenReturn(true);
    when(prompts.system(any())).thenReturn("sys");
    when(prompts.user(any(), any())).thenReturn("user");
    when(client.chatJson("sys", "user")).thenThrow(new RuntimeException("private checkin text"));

    var result =
        gateway.complete(
            AiGatewayService.AiTask.summary,
            Map.of("rawText", "x"),
            new TypeReference<Map<String, String>>() {},
            () -> Map.of("oneLiner", "fallback"));

    assertEquals(AiProvider.heuristic, result.provider());
    assertEquals("fallback", result.data().get("oneLiner"));
    verify(observer)
        .recordComplete(
            eq(AiGatewayService.AiTask.summary),
            eq(AiProvider.heuristic),
            eq("fallback"),
            anyLong(),
            eq("RuntimeException"));
  }

  @Test
  void requiredFailureStoresOnlyStatusCodeNotVendorResponseBody() {
    when(properties.isConfigured()).thenReturn(true);
    when(prompts.system(any())).thenReturn("sys");
    when(prompts.user(any(), any())).thenReturn("user");
    when(client.chatJson("sys", "user")).thenThrow(HttpServerErrorException.create(
        HttpStatus.SERVICE_UNAVAILABLE, "unavailable", HttpHeaders.EMPTY,
        "private conversation echoed by vendor".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

    var error = assertThrows(ResponseStatusException.class, () -> gateway.completeRequired(
        AiGatewayService.AiTask.conversation, Map.of("message", "private conversation"),
        new TypeReference<Map<String, String>>() {}));

    assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    verify(observer).recordComplete(eq(AiGatewayService.AiTask.conversation), eq(AiProvider.llm),
        eq("failed"), anyLong(), eq("http_503"));
  }

  @Test
  void embeddingFailureStoresOnlyExceptionType() {
    when(properties.isConfigured()).thenReturn(true);
    when(client.embed(List.of("private memory"))).thenThrow(new RuntimeException("private memory"));

    assertEquals(java.util.Optional.empty(), gateway.embed(List.of("private memory")));
    verify(observer).recordEmbed(eq(AiProvider.llm), eq("failed"), anyLong(), eq(1),
        eq("RuntimeException"));
  }
}
