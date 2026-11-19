package com.today.telemetry;

import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.identity.IdentityService;
import jakarta.validation.Valid;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/v1/events")
public class ProductEventController {
  private final ProductEventMapper mapper;
  private final IdentityService identity;

  public ProductEventController(ProductEventMapper mapper, IdentityService identity) {
    this.mapper = mapper; this.identity = identity;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void ingest(@Valid @RequestBody List<@Valid ProductEventRequest> events) {
    if (events == null) return;
    if (events.size() > 50) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at most 50 product events per request");
    }
    String userId = identity.getCurrentUserId();
    List<ProductEventRequest> batch = events;
    for (ProductEventRequest input : batch) validate(input);
    for (ProductEventRequest input : batch) {
      String eventName = input.eventName().trim();
      ProductEventEntity event = new ProductEventEntity();
      Instant receivedAt = EntityMapper.now();
      event.setId(UUID.randomUUID().toString()); event.setUserId(userId);
      event.setEventName(eventName); event.setScreen(blank(input.screen()));
      event.setPlatform(normalizePlatform(input.platform()));
      event.setEventSchemaVersion(input.eventSchemaVersion() == null ? 1 : input.eventSchemaVersion());
      Instant occurredAt = input.occurredAt() == null ? receivedAt : input.occurredAt();
      if (occurredAt.isAfter(receivedAt.plus(Duration.ofMinutes(5)))) occurredAt = receivedAt;
      event.setOccurredAt(occurredAt);
      event.setReceivedAt(receivedAt);
      event.setEntityType(blank(input.entityType())); event.setEntityId(blank(input.entityId()));
      event.setSessionId(blank(input.sessionId())); event.setClientRequestId(blank(input.clientRequestId()));
      event.setAppVersion(blank(input.appVersion())); event.setPropertiesJson(blank(input.propertiesJson()));
      mapper.insert(event);
    }
  }

  private static void validate(ProductEventRequest input) {
    String eventName = input.eventName() == null ? "" : input.eventName().trim();
    if (!ProductEventCatalog.isKnown(eventName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown product event");
    }
    if (input.propertiesJson() != null && !input.propertiesJson().isBlank()
        && !JsonUtils.isJsonObject(input.propertiesJson())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "propertiesJson must be a JSON object");
    }
  }

  private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static String normalizePlatform(String value) {
    String platform = blank(value);
    return platform == null ? "unknown" : switch (platform.toLowerCase()) {
      case "android", "ios", "web" -> platform.toLowerCase();
      default -> "unknown";
    };
  }
}
