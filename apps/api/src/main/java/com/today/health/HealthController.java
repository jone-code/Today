package com.today.health;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "ok", true,
        "service", "today-api",
        "stack", "spring-boot",
        "modules",
            List.of(
                "checkin",
                "summary",
                "memory",
                "timeline",
                "proactive",
                "ai-gateway",
                "identity",
                "reminder",
                "todo",
                "punch"));
  }
}
