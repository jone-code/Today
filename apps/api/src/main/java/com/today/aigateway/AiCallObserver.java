package com.today.aigateway;

import com.today.common.AiProvider;
import com.today.common.EntityMapper;
import com.today.persistence.AiCallLogEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** AI 调用审计：落库 + 进程内计数，供 /health 与管理查询 */
@Service
public class AiCallObserver {

  private static final Logger log = LoggerFactory.getLogger(AiCallObserver.class);

  private final AiCallLogMapper logs;
  private final AiProperties properties;

  private final AtomicLong ok = new AtomicLong();
  private final AtomicLong fallback = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong skipped = new AtomicLong();
  private final AtomicLong totalElapsedMs = new AtomicLong();
  private final AtomicLong timedCalls = new AtomicLong();

  public AiCallObserver(AiCallLogMapper logs, AiProperties properties) {
    this.logs = logs;
    this.properties = properties;
  }

  public void recordComplete(
      AiGatewayService.AiTask task,
      AiProvider provider,
      String outcome,
      long elapsedMs,
      String error) {
    bump(outcome, elapsedMs);
    persist("complete", task.name(), provider.name(), outcome, elapsedMs, 1, error);
  }

  public void recordEmbed(
      AiProvider provider, String outcome, long elapsedMs, int inputUnits, String error) {
    bump(outcome, elapsedMs);
    persist("embed", "embed", provider.name(), outcome, elapsedMs, inputUnits, error);
  }

  public void recordSkippedComplete(AiGatewayService.AiTask task) {
    bump("skipped", 0);
    persist("complete", task.name(), AiProvider.heuristic.name(), "skipped", 0, 1, "no api key");
  }

  public void recordSkippedEmbed() {
    bump("skipped", 0);
    persist("embed", "embed", AiProvider.heuristic.name(), "skipped", 0, 0, "no api key");
  }

  public Map<String, Object> processStats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ok", ok.get());
    m.put("fallback", fallback.get());
    m.put("failed", failed.get());
    m.put("skipped", skipped.get());
    long n = timedCalls.get();
    m.put("avgElapsedMs", n == 0 ? 0 : totalElapsedMs.get() / n);
    return m;
  }

  public Map<String, Object> statsSinceHours(int hours) {
    Instant since = EntityMapper.now().minus(Math.max(1, hours), ChronoUnit.HOURS);
    List<Map<String, Object>> rows = logs.aggregateSince(since);
    List<Map<String, Object>> buckets = new ArrayList<>();
    long total = 0;
    long fallbacks = 0;
    long fails = 0;
    for (Map<String, Object> row : rows) {
      Map<String, Object> b = new LinkedHashMap<>();
      b.put("kind", row.get("kind"));
      b.put("task", row.get("task"));
      b.put("outcome", row.get("outcome"));
      long cnt = ((Number) row.get("cnt")).longValue();
      b.put("count", cnt);
      b.put("avgElapsedMs", row.get("avgElapsedMs"));
      b.put("maxElapsedMs", row.get("maxElapsedMs"));
      buckets.add(b);
      total += cnt;
      String outcome = String.valueOf(row.get("outcome"));
      if ("fallback".equals(outcome)) fallbacks += cnt;
      if ("failed".equals(outcome)) fails += cnt;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("windowHours", hours);
    out.put("total", total);
    out.put("fallback", fallbacks);
    out.put("failed", fails);
    Double avgOk = logs.avgElapsedSince(since, "ok");
    out.put("avgOkElapsedMs", avgOk == null ? 0 : avgOk);
    out.put("buckets", buckets);
    out.put("process", processStats());
    return out;
  }

  public List<AiCallLogDto> recent(int limit) {
    return logs.listRecent(Math.min(200, Math.max(1, limit))).stream().map(this::toDto).toList();
  }

  @Scheduled(cron = "0 20 3 * * *")
  public void retain() {
    int days = Math.max(1, properties.getLogRetainDays());
    Instant before = EntityMapper.now().minus(days, ChronoUnit.DAYS);
    int deleted = logs.deleteOlderThan(before);
    if (deleted > 0) {
      log.info("ai_call_logs retained deleted={} olderThanDays={}", deleted, days);
    }
  }

  private void bump(String outcome, long elapsedMs) {
    switch (outcome) {
      case "ok" -> ok.incrementAndGet();
      case "fallback" -> fallback.incrementAndGet();
      case "failed" -> failed.incrementAndGet();
      case "skipped" -> skipped.incrementAndGet();
      default -> {}
    }
    if (elapsedMs > 0) {
      totalElapsedMs.addAndGet(elapsedMs);
      timedCalls.incrementAndGet();
    }
  }

  private void persist(
      String kind,
      String task,
      String provider,
      String outcome,
      long elapsedMs,
      int inputUnits,
      String error) {
    try {
      AiCallLogEntity entity = new AiCallLogEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setKind(kind);
      entity.setTask(task);
      entity.setProvider(provider);
      entity.setOutcome(outcome);
      entity.setElapsedMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, elapsedMs)));
      entity.setInputUnits(Math.max(0, inputUnits));
      if (error != null && !error.isBlank()) {
        entity.setErrorMessage(error.length() > 512 ? error.substring(0, 512) : error);
      }
      entity.setCreatedAt(EntityMapper.now());
      logs.insert(entity);
    } catch (Exception e) {
      log.warn("ai call log persist failed reason={}", e.toString());
    }
  }

  private AiCallLogDto toDto(AiCallLogEntity e) {
    return new AiCallLogDto(
        e.getId(),
        e.getKind(),
        e.getTask(),
        e.getProvider(),
        e.getOutcome(),
        e.getElapsedMs(),
        e.getInputUnits(),
        e.getErrorMessage(),
        e.getCreatedAt().toString());
  }

  public record AiCallLogDto(
      String id,
      String kind,
      String task,
      String provider,
      String outcome,
      int elapsedMs,
      int inputUnits,
      String errorMessage,
      String createdAt) {}
}
