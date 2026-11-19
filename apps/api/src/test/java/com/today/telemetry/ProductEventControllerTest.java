package com.today.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductEventControllerTest {
  @Test
  void recordsProvenanceAndClampsAnInvalidFutureClientClock() {
    ProductEventMapper mapper = mock(ProductEventMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    ProductEventController controller = new ProductEventController(mapper, identity);
    Instant future = Instant.now().plus(Duration.ofHours(2));

    controller.ingest(List.of(new ProductEventRequest(
        "today_viewed", "ANDROID", 1, future, null, null, null, null, null, null, null)));

    var captured = org.mockito.ArgumentCaptor.forClass(ProductEventEntity.class);
    verify(mapper).insert(captured.capture());
    ProductEventEntity event = captured.getValue();
    assertEquals("user-1", event.getUserId());
    assertEquals("android", event.getPlatform());
    assertEquals(1, event.getEventSchemaVersion());
    assertEquals(event.getReceivedAt(), event.getOccurredAt());
    assertTrue(!event.getOccurredAt().isAfter(event.getReceivedAt()));
  }

  @Test
  void validatesTheWholeBatchBeforeWritingAnyEvent() {
    ProductEventMapper mapper = mock(ProductEventMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    ProductEventController controller = new ProductEventController(mapper, identity);

    var valid = new ProductEventRequest("today_viewed", "web", 1, null,
        null, null, null, null, null, null, null);
    var invalid = new ProductEventRequest("unknown_event", "web", 1, null,
        null, null, null, null, null, null, null);

    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> controller.ingest(List.of(valid, invalid)));
    verifyNoInteractions(mapper);
  }

  @Test
  void rejectsBatchesLargerThanTheMetricIngestionLimit() {
    ProductEventMapper mapper = mock(ProductEventMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    ProductEventController controller = new ProductEventController(mapper, identity);
    var event = new ProductEventRequest("today_viewed", "web", 1, null,
        null, null, null, null, null, null, null);

    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> controller.ingest(java.util.Collections.nCopies(51, event)));
    verifyNoInteractions(mapper);
  }
}
