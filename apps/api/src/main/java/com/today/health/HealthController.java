package com.today.health;

import com.today.aigateway.AiCallObserver;
import com.today.aigateway.AiGatewayService;
import com.today.media.LocalMediaStorage;
import com.today.vector.VectorHealth;
import com.today.vector.VectorReindexService;
import com.today.vector.VectorStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final AiGatewayService aiGateway;
  private final AiCallObserver aiCallObserver;
  private final VectorStore vectorStore;
  private final VectorReindexService vectorReindex;
  private final LocalMediaStorage mediaStorage;

  public HealthController(
      AiGatewayService aiGateway,
      AiCallObserver aiCallObserver,
      VectorStore vectorStore,
      VectorReindexService vectorReindex,
      LocalMediaStorage mediaStorage) {
    this.aiGateway = aiGateway;
    this.aiCallObserver = aiCallObserver;
    this.vectorStore = vectorStore;
    this.vectorReindex = vectorReindex;
    this.mediaStorage = mediaStorage;
  }

  /** Liveness for Docker healthcheck — no DB / vector calls. */
  @GetMapping({"/health/live", "/healthz"})
  public Map<String, Object> live() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("service", "today-api");
    return body;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("service", "today-api");
    body.put("stack", "spring-boot");
    body.put("aiProvider", aiGateway.getActiveProvider().name());
    body.put("ai", aiCallObserver.processStats());
    body.put("vectorProvider", vectorStore.provider());
    try {
      VectorHealth vh = vectorReindex.health();
      body.put("vector", vh.toMap());
    } catch (Exception e) {
      Map<String, Object> vector = new LinkedHashMap<>();
      vector.put("ok", false);
      vector.put("detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      body.put("vector", vector);
    }
    Map<String, Object> media = new LinkedHashMap<>();
    media.put("root", mediaStorage.getRoot().toString());
    body.put("media", media);
    body.put(
        "modules",
        List.of(
            "checkin",
            "summary",
            "memory",
            "timeline",
            "proactive",
            "ai-gateway",
            "vector",
            "identity",
            "reminder",
            "todo",
            "punch",
            "media"));
    return body;
  }
}
