package com.today.health;

import com.today.aigateway.AiGatewayService;
import com.today.vector.VectorStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final AiGatewayService aiGateway;
  private final VectorStore vectorStore;

  public HealthController(AiGatewayService aiGateway, VectorStore vectorStore) {
    this.aiGateway = aiGateway;
    this.vectorStore = vectorStore;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("service", "today-api");
    body.put("stack", "spring-boot");
    body.put("aiProvider", aiGateway.getActiveProvider().name());
    body.put("vectorProvider", vectorStore.provider());
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
            "punch"));
    return body;
  }
}
