package com.today.aigateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.common.AiProvider;
import com.today.common.JsonUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 唯一允许接触模型 SDK 的模块；无 key / 失败时降级 Heuristic */
@Service
public class AiGatewayService {

  private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

  private final AiProperties properties;
  private final OpenAiCompatibleClient client;
  private final AiPromptTemplates prompts;

  public AiGatewayService(
      AiProperties properties, OpenAiCompatibleClient client, AiPromptTemplates prompts) {
    this.properties = properties;
    this.client = client;
    this.prompts = prompts;
  }

  public AiProvider getActiveProvider() {
    return properties.isConfigured() ? AiProvider.llm : AiProvider.heuristic;
  }

  public int retrieveTopK() {
    return Math.max(1, properties.getRetrieveTopK());
  }

  /**
   * 调用 LLM 并将 JSON 解析为 {@code type}；无 key、超时、解析失败时走 {@code heuristic}。
   */
  public <T> AiResult<T> complete(
      AiTask task, Object input, TypeReference<T> type, Supplier<T> heuristic) {
    if (!properties.isConfigured()) {
      return new AiResult<>(heuristic.get(), AiProvider.heuristic);
    }

    long start = System.currentTimeMillis();
    try {
      String system = prompts.system(task);
      String user = prompts.user(task, input);
      String content = client.chatJson(system, user);
      String json = JsonUtils.extractJson(content);
      T parsed = JsonUtils.fromJson(json, type);
      if (parsed == null) {
        throw new IllegalStateException("parsed LLM payload is null");
      }
      log.info(
          "ai complete ok task={} provider=llm elapsedMs={}",
          task,
          System.currentTimeMillis() - start);
      return new AiResult<>(parsed, AiProvider.llm);
    } catch (Exception e) {
      log.warn(
          "ai complete fallback task={} elapsedMs={} reason={}",
          task,
          System.currentTimeMillis() - start,
          e.toString());
      return new AiResult<>(heuristic.get(), AiProvider.heuristic);
    }
  }

  /** 批量 embedding；无 key / 失败返回 empty（调用方走 strength 降级检索） */
  public Optional<List<float[]>> embed(List<String> texts) {
    if (!properties.isConfigured() || texts == null || texts.isEmpty()) {
      return Optional.empty();
    }
    long start = System.currentTimeMillis();
    try {
      List<float[]> vectors = client.embed(texts);
      log.info(
          "ai embed ok count={} elapsedMs={}", texts.size(), System.currentTimeMillis() - start);
      return Optional.of(vectors);
    } catch (Exception e) {
      log.warn(
          "ai embed failed count={} elapsedMs={} reason={}",
          texts.size(),
          System.currentTimeMillis() - start,
          e.toString());
      return Optional.empty();
    }
  }

  public enum AiTask {
    summary,
    memory_extract,
    proactive
  }

  public record AiResult<T>(T data, AiProvider provider) {}
}
