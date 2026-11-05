package com.today.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RateLimitServiceTest {
  private final RateLimitMapper mapper = mock(RateLimitMapper.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T09:02:15Z"), ZoneOffset.UTC);
  private final RateLimitService service = new RateLimitService(mapper, "test-secret", clock);

  @Test
  void firstHitUsesHashedIdentityAndAlignedWindow() {
    when(mapper.insertFirst(eq("login_email"), any(), eq(Instant.parse("2026-11-03T09:00:00Z"))))
        .thenReturn(1);

    service.check("login_email", "someone@example.com", 10, Duration.ofMinutes(5));

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(mapper).insertFirst(eq("login_email"), hash.capture(),
        eq(Instant.parse("2026-11-03T09:00:00Z")));
    assertEquals(64, hash.getValue().length());
    assertNotEquals("someone@example.com", hash.getValue());
  }

  @Test
  void existingBucketIncrementsWithoutInserting() {
    when(mapper.increment(eq("conversation_hour"), any(), any(), eq(30))).thenReturn(1);
    service.check("conversation_hour", "u1", 30, Duration.ofHours(1));
    org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insertFirst(any(), any(), any());
  }

  @Test
  void fullBucketReturns429AndSecondsUntilNextWindow() {
    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> service.check("login_email", "someone@example.com", 10, Duration.ofMinutes(5)));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
    assertEquals("165", error.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void insertRaceRetriesIncrementWithoutExceedingMaximum() {
    when(mapper.increment(eq("avatar_day"), any(), any(), eq(20))).thenReturn(0, 1);
    service.check("avatar_day", "u1", 20, Duration.ofDays(1));
    org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(2))
        .increment(eq("avatar_day"), any(), any(), eq(20));
  }

  @Test
  void invalidLimitsFailClosed() {
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> service.check("conversation_hour", "u1", 0, Duration.ofHours(1)))
        .getMessage().contains("invalid"));
  }
}
