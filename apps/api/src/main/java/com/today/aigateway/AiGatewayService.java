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
  private final AiCallObserver observer;

  public AiGatewayService(
      AiProperties properties,
      OpenAiCompatibleClient client,
      AiPromptTemplates prompts,
      AiCallObserver observer) {
    this.properties = properties;
    this.client = client;
    this.prompts = prompts;
    this.observer = observer;
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
      observer.recordSkippedComplete(task);
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
      long elapsed = System.currentTimeMillis() - start;
      log.info("ai complete ok task={} provider=llm elapsedMs={}", task, elapsed);
      observer.recordComplete(task, AiProvider.llm, "ok", elapsed, null);
      return new AiResult<>(parsed, AiProvider.llm);
    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - start;
      log.warn("ai complete fallback task={} elapsedMs={} reason={}", task, elapsed, e.toString());
      observer.recordComplete(task, AiProvider.heuristic, "fallback", elapsed, e.toString());
      return new AiResult<>(heuristic.get(), AiProvider.heuristic);
    }
  }

  /** 批量 embedding；无 key / 失败返回 empty（调用方走 strength 降级检索） */
  public Optional<List<float[]>> embed(List<String> texts) {
    if (!properties.isConfigured() || texts == null || texts.isEmpty()) {
      observer.recordSkippedEmbed();
      return Optional.empty();
    }
    long start = System.currentTimeMillis();
    try {
      List<float[]> vectors = client.embed(texts);
      long elapsed = System.currentTimeMillis() - start;
      log.info("ai embed ok count={} elapsedMs={}", texts.size(), elapsed);
      observer.recordEmbed(AiProvider.llm, "ok", elapsed, texts.size(), null);
      return Optional.of(vectors);
    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - start;
      log.warn(
          "ai embed failed count={} elapsedMs={} reason={}",
          texts.size(),
          elapsed,
          e.toString());
      observer.recordEmbed(AiProvider.llm, "failed", elapsed, texts.size(), e.toString());
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
