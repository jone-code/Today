package com.today.telemetry;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.Map;

class MetricsAuthorizationTest {
  @Test void missingOrIncorrectAdminConfigurationNeverReadsMetrics() {
    var mapper = mock(MetricsMapper.class);
    var audit = mock(MetricsAccessAuditMapper.class);
    var day = LocalDate.of(2026,11,10);
    assertThrows(ResponseStatusException.class, () -> new MetricsController(mapper, audit, "").activation(null, day, day, null, null, null));
    assertThrows(ResponseStatusException.class, () -> new MetricsController(mapper, audit, "secret").activation("wrong", day, day, null, null, null));
    verifyNoInteractions(mapper);
  }

  @Test void validTokenReturnsAggregatesAndUsesAnInclusiveEndDate() {
    var mapper = mock(MetricsMapper.class);
    var audit = mock(MetricsAccessAuditMapper.class);
    when(audit.insert(anyString(), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    var from = LocalDate.of(2026, 11, 1);
    var to = LocalDate.of(2026, 11, 7);
    when(mapper.activation(from, to.plusDays(1))).thenReturn(Map.of(
        "registeredUsers", 2, "activatedUsers", 1, "chatUsers", 1, "valueUsers", 1,
        "northStarUsers", 0, "checkinsSaved", 2, "checkinSavedEvents", 2,
        "checkinUsersMissingSavedEvent", 0, "savedEventUsersMissingCheckin", 0));
    when(mapper.eventCounts(from, to.plusDays(1), null, null, null)).thenReturn(List.of(Map.of("eventName", "checkin_saved")));
    when(mapper.feedbackQuality(from, to.plusDays(1))).thenReturn(List.of());

    var result = new MetricsController(mapper, audit, "secret").activation("secret", from, to, null, null, null);

    assertEquals(from, result.get("from"));
    assertEquals(to, result.get("to"));
    @SuppressWarnings("unchecked") var funnel = (Map<String, Object>) result.get("funnel");
    assertEquals(0, funnel.get("checkinUsersMissingSavedEvent"));
    assertEquals(0, funnel.get("savedEventUsersMissingCheckin"));
    @SuppressWarnings("unchecked") var filters = (Map<String, Object>) result.get("filters");
    assertNull(filters.get("platform"));
    assertNull(filters.get("appVersion"));
    assertNull(filters.get("eventSchemaVersion"));
    verify(mapper).activation(from, to.plusDays(1));
    verify(mapper).eventCounts(from, to.plusDays(1), null, null, null);
    verify(audit).insert(anyString(), eq("activation"), eq(from), eq(to), isNull(), isNull(), isNull(), any());
  }

  @Test void rejectsRangesLongerThan31DaysBeforeQuerying() {
    var mapper = mock(MetricsMapper.class);
    var audit = mock(MetricsAccessAuditMapper.class);
    var from = LocalDate.of(2026, 1, 1);
    var to = from.plusDays(31);

    assertThrows(ResponseStatusException.class,
        () -> new MetricsController(mapper, audit, "secret").retention("secret", from, to));
    verifyNoInteractions(mapper);
  }

  @Test void auditFailureBlocksMetricQuery() {
    var mapper = mock(MetricsMapper.class);
    var audit = mock(MetricsAccessAuditMapper.class);
    var day = LocalDate.of(2026, 11, 10);
    when(audit.insert(anyString(), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(0);

    assertThrows(IllegalStateException.class,
        () -> new MetricsController(mapper, audit, "secret").activation("secret", day, day, null, null, null));
    verifyNoInteractions(mapper);
  }

  @Test void forwardsOptionalPlatformAndVersionFiltersToEventAggregation() {
    var mapper = mock(MetricsMapper.class);
    var audit = mock(MetricsAccessAuditMapper.class);
    when(audit.insert(anyString(), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    var from = LocalDate.of(2026, 11, 1);
    var to = LocalDate.of(2026, 11, 7);
    when(mapper.activation(from, to.plusDays(1))).thenReturn(Map.of(
        "registeredUsers", 1, "activatedUsers", 1, "chatUsers", 0,
        "valueUsers", 0, "northStarUsers", 0, "checkinsSaved", 1,
        "checkinSavedEvents", 1));
    when(mapper.eventCounts(from, to.plusDays(1), "android", "0.1.0", 1))
        .thenReturn(List.of(Map.of("eventName", "checkin_saved")));

    new MetricsController(mapper, audit, "secret").activation(
        "secret", from, to, " android ", " 0.1.0 ", 1);

    verify(mapper).eventCounts(from, to.plusDays(1), "android", "0.1.0", 1);
  }

  @Test void rejectsUnsupportedMetricFilterBeforeQuerying() {
    var mapper = mock(MetricsMapper.class);
    var audit = mock(MetricsAccessAuditMapper.class);
    var day = LocalDate.of(2026, 11, 10);
    assertThrows(ResponseStatusException.class, () -> new MetricsController(mapper, audit, "secret")
        .activation("secret", day, day, "desktop", null, null));
    assertThrows(ResponseStatusException.class, () -> new MetricsController(mapper, audit, "secret")
        .activation("secret", day, day, "web", "x".repeat(33), null));
    assertThrows(ResponseStatusException.class, () -> new MetricsController(mapper, audit, "secret")
        .activation("secret", day, day, "web", null, 11));
    verifyNoInteractions(mapper);
  }
}
