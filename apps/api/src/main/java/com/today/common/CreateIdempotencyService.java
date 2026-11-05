package com.today.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.identity.IdentityService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreateIdempotencyService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final IdentityService identity;

  public CreateIdempotencyService(JdbcTemplate jdbc, ObjectMapper json, IdentityService identity) {
    this.jdbc = jdbc;
    this.json = json;
    this.identity = identity;
  }

  @Transactional
  public <T> T create(String scope, String key, Object body, Class<T> type, Supplier<T> action) {
    if (key == null) return action.get(); // Existing clients can still create without a key.
    if (key.isBlank() || key.length() > 128) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid Idempotency-Key");
    }
    String userId = identity.getCurrentUserId();
    String payloadHash = hash(encode(body));
    // The unique index serializes concurrent requests, including requests on different API nodes.
    int inserted = jdbc.update("INSERT IGNORE INTO create_requests "
        + "(user_id, scope, request_id, request_hash, response_json, created_at) "
        + "VALUES (?, ?, ?, ?, NULL, NOW(3))", userId, scope, key, payloadHash);
    if (inserted == 1) {
      T result = action.get();
      jdbc.update("UPDATE create_requests SET response_json = ? "
          + "WHERE user_id = ? AND scope = ? AND request_id = ?", encode(result), userId, scope, key);
      return result;
    }
    List<Stored> rows = jdbc.query("SELECT request_hash, response_json FROM create_requests "
        + "WHERE user_id = ? AND scope = ? AND request_id = ? FOR UPDATE",
        (rs, row) -> new Stored(rs.getString(1), rs.getString(2)), userId, scope, key);
    if (rows.size() != 1 || rows.get(0).response() == null) {
      throw new IllegalStateException("create request result unavailable");
    }
    if (!rows.get(0).requestHash().equals(payloadHash)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key reused with different content");
    }
    try {
      return json.readValue(rows.get(0).response(), type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("invalid stored create result", e);
    }
  }

  private String encode(Object value) {
    try { return json.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new IllegalArgumentException("invalid create request", e); }
  }

  private String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  private record Stored(String requestHash, String response) {}
}
