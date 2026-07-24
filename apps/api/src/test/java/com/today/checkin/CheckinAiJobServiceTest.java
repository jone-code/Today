package com.today.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckinAiJobServiceTest {

  @Test
  void backoffGrowsWithAttempts() {
    assertEquals(Duration.ofSeconds(5), CheckinAiJobService.backoff(1));
    assertEquals(Duration.ofSeconds(15), CheckinAiJobService.backoff(2));
    assertEquals(Duration.ofSeconds(45), CheckinAiJobService.backoff(3));
    assertEquals(Duration.ofSeconds(120), CheckinAiJobService.backoff(4));
    assertEquals(Duration.ofSeconds(300), CheckinAiJobService.backoff(5));
  }
}
