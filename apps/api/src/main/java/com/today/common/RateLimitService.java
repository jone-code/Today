package com.today.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimitService {
  private final RateLimitMapper mapper;
  private final String pepper;
  private final Clock clock;

  @Autowired
  public RateLimitService(RateLimitMapper mapper, @Value("${today.jwt.secret}") String pepper) {
    this(mapper, pepper, Clock.systemUTC());
  }

  RateLimitService(RateLimitMapper mapper, String pepper, Clock clock) {
    this.mapper = mapper;
    this.pepper = pepper;
    this.clock = clock;
  }

  public void check(String scope, String subject, int maxHits, Duration window) {
    if (maxHits < 1 || window.isZero() || window.isNegative() || window.getSeconds() < 1) {
      throw new IllegalArgumentException("invalid rate limit configuration");
    }
    Instant now = clock.instant();
    long seconds = window.getSeconds();
    long start = Math.floorDiv(now.getEpochSecond(), seconds) * seconds;
    Instant windowStart = Instant.ofEpochSecond(start);
    String subjectHash = hash(scope + ":" + subject);

    if (mapper.increment(scope, subjectHash, windowStart, maxHits) == 1
        || mapper.insertFirst(scope, subjectHash, windowStart) == 1
        || mapper.increment(scope, subjectHash, windowStart, maxHits) == 1) return;

    throw new RateLimitExceeded(Math.max(1, start + seconds - now.getEpochSecond()));
  }

  @Scheduled(cron = "0 5 3 * * *", zone = "UTC")
  public void deleteExpiredBuckets() {
    mapper.deleteOlderThan(clock.instant().minus(Duration.ofDays(3)));
  }

  private String hash(String subject) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest((pepper + ":" + subject).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static final class RateLimitExceeded extends ResponseStatusException {
    private final HttpHeaders headers = new HttpHeaders();

    private RateLimitExceeded(long retryAfterSeconds) {
      super(HttpStatus.TOO_MANY_REQUESTS, "too many requests; try again later");
      headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }
  }
}
