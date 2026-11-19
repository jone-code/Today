package com.today.telemetry;

import java.time.LocalDate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MetricsController {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetricsController.class);
  private final MetricsMapper mapper;
  private final MetricsAccessAuditMapper audit;
  private final String adminToken;

  public MetricsController(MetricsMapper mapper, MetricsAccessAuditMapper audit,
      @Value("${today.metrics.admin-token:}") String adminToken) {
    this.mapper = mapper;
    this.audit = audit;
    this.adminToken = adminToken == null ? "" : adminToken;
  }

  @GetMapping("/v1/admin/metrics/activation")
  public Map<String, Object> activation(
      @RequestHeader(value = "X-Today-Admin-Token", required = false) String token,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String platform,
      @RequestParam(required = false) String appVersion,
      @RequestParam(required = false) Integer eventSchemaVersion) {
    assertAdmin(token); validateRange(from, to);
    String eventPlatform = cleanPlatform(platform);
    String eventVersion = cleanVersion(appVersion);
    Integer eventSchema = cleanSchemaVersion(eventSchemaVersion);
    recordAccess("activation", from, to, eventPlatform, eventVersion, eventSchema);
    log.info("aggregate metrics accessed from={} to={}", from, to);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", from); result.put("to", to);
    result.put("filters", filters(eventPlatform, eventVersion, eventSchema));
    result.put("funnel", mapper.activation(from, to.plusDays(1)));
    result.put("events", mapper.eventCounts(from, to.plusDays(1), eventPlatform, eventVersion, eventSchema));
    return result;
  }

  @GetMapping("/v1/admin/metrics/retention")
  public Map<String, Object> retention(
      @RequestHeader(value = "X-Today-Admin-Token", required = false) String token,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    assertAdmin(token); validateRange(from, to);
    recordAccess("retention", from, to, null, null, null);
    log.info("aggregate metrics accessed from={} to={}", from, to);
    return Map.of("from", from, "to", to, "cohort", mapper.retention(from, to.plusDays(1)));
  }

  @GetMapping("/v1/admin/metrics/quality")
  public Map<String, Object> quality(
      @RequestHeader(value = "X-Today-Admin-Token", required = false) String token,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String platform,
      @RequestParam(required = false) String appVersion,
      @RequestParam(required = false) Integer eventSchemaVersion) {
    assertAdmin(token); validateRange(from, to);
    String eventPlatform = cleanPlatform(platform);
    String eventVersion = cleanVersion(appVersion);
    Integer eventSchema = cleanSchemaVersion(eventSchemaVersion);
    recordAccess("quality", from, to, eventPlatform, eventVersion, eventSchema);
    log.info("aggregate metrics accessed from={} to={}", from, to);
    List<Map<String, Object>> events = mapper.eventCounts(from, to.plusDays(1), eventPlatform, eventVersion, eventSchema);
    return Map.of("from", from, "to", to, "filters", filters(eventPlatform, eventVersion, eventSchema), "events", events, "dataRequests", mapper.dataQuality(from, to.plusDays(1)), "voiceTranscriptions", mapper.voiceQuality(from, to.plusDays(1)), "feedback", mapper.feedbackQuality(from, to.plusDays(1)));
  }

  private void assertAdmin(String token) {
    if (adminToken.isBlank() || !adminToken.equals(token)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid admin token");
    }
  }

  private void recordAccess(String endpoint, LocalDate from, LocalDate to, String platform,
      String appVersion, Integer eventSchemaVersion) {
    if (audit.insert(java.util.UUID.randomUUID().toString(), endpoint, from, to, platform, appVersion,
        eventSchemaVersion, Instant.now()) != 1) {
      throw new IllegalStateException("metrics access audit write failed");
    }
  }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to) || to.isAfter(from.plusDays(30))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date range must be 1-31 days");
    }
  }

  private static String cleanPlatform(String value) {
    String cleaned = clean(value);
    if (cleaned != null && !switch (cleaned.toLowerCase()) {
      case "android", "ios", "web", "unknown" -> true;
      default -> false;
    }) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform must be android, ios, web or unknown");
    }
    return cleaned == null ? null : cleaned.toLowerCase();
  }

  private static String cleanVersion(String value) {
    String cleaned = clean(value);
    if (cleaned != null && cleaned.length() > 32) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appVersion must be at most 32 characters");
    }
    return cleaned;
  }

  private static Integer cleanSchemaVersion(Integer value) {
    if (value != null && (value < 1 || value > 10)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventSchemaVersion must be between 1 and 10");
    }
    return value;
  }

  private static Map<String, Object> filters(String platform, String appVersion, Integer eventSchemaVersion) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("platform", platform);
    result.put("appVersion", appVersion);
    result.put("eventSchemaVersion", eventSchemaVersion);
    return result;
  }

  private static String clean(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
