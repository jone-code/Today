package com.today.aigateway;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AiObservabilityController {

  private final AiCallObserver observer;
  private final AiProperties properties;

  public AiObservabilityController(AiCallObserver observer, AiProperties properties) {
    this.observer = observer;
    this.properties = properties;
  }

  @GetMapping("/v1/admin/ai/stats")
  public Map<String, Object> stats(
      @RequestHeader(value = "X-Today-Admin-Token", required = false) String adminToken,
      @RequestParam(name = "hours", defaultValue = "24") int hours) {
    assertAdmin(adminToken);
    return observer.statsSinceHours(hours);
  }

  @GetMapping("/v1/admin/ai/calls")
  public Map<String, Object> calls(
      @RequestHeader(value = "X-Today-Admin-Token", required = false) String adminToken,
      @RequestParam(name = "limit", defaultValue = "50") int limit) {
    assertAdmin(adminToken);
    List<AiCallObserver.AiCallLogDto> items = observer.recent(limit);
    return Map.of("items", items);
  }

  private void assertAdmin(String adminToken) {
    if (!properties.hasAdminToken()) {
      return;
    }
    if (adminToken == null || !properties.getAdminToken().equals(adminToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid admin token");
    }
  }
}
