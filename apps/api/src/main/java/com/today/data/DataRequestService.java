package com.today.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.vector.VectorStore;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DataRequestService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataRequestService.class);
  private final JdbcTemplate db; private final IdentityService identity; private final LocalMediaStorage storage;
  private final ObjectMapper json; private final VectorStore vectors; private final ExportEncryption encryption;
  public DataRequestService(DataSource dataSource, IdentityService identity, LocalMediaStorage storage, VectorStore vectors, ObjectMapper json, ExportEncryption encryption) { this.encryption = encryption; this.json = json; db = new JdbcTemplate(dataSource); this.identity = identity; this.storage = storage; this.vectors = vectors; }

  @Transactional
  public DataRequestDto createExport() {
    String user = identity.getCurrentUserId();
    // Serialize creation for this account, including requests with different keys.
    if (db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account unavailable");
    }
    if (isDeleting(user)) throw new ResponseStatusException(HttpStatus.CONFLICT, "account deletion in progress");
    List<String> active = db.queryForList("SELECT id FROM data_requests WHERE user_id=? AND type='export' "
        + "AND status IN ('queued','running') ORDER BY requested_at LIMIT 1", String.class, user);
    if (!active.isEmpty()) return find(active.get(0), user, null);
    String id = UUID.randomUUID().toString();
    db.update("INSERT INTO data_requests(id,user_id,type,status,requested_at,attempts) VALUES(?,?,'export','queued',?,0)",
        id, user, EntityMapper.now());
    return find(id, user, null);
  }

  /** One durable queue item per transaction. A process crash rolls back the claim for another worker. */
  @Transactional
  public boolean processNextExport() {
    List<Map<String,Object>> pending = db.queryForList("SELECT id,user_id FROM data_requests "
        + "WHERE type='export' AND status='queued' ORDER BY requested_at LIMIT 1 FOR UPDATE SKIP LOCKED");
    if (pending.isEmpty()) return false;
    String id = (String) pending.get(0).get("id");
    String user = (String) pending.get(0).get("user_id");
    db.update("UPDATE data_requests SET status='running',started_at=?,attempts=attempts+1 WHERE id=?", EntityMapper.now(), id);
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user);
    if (isDeleting(user)) {
      db.update("UPDATE data_requests SET status='cancelled',completed_at=? WHERE id=?", EntityMapper.now(), id);
      return true;
    }
    Path out = null;
    try {
      Path dir = storage.getRoot().resolve("exports").resolve(user).normalize();
      if (!dir.startsWith(storage.getRoot().resolve("exports"))) throw new IllegalStateException("invalid export owner");
      Files.createDirectories(dir);
      out = dir.resolve(id+".zip.enc");
      // A rolled-back worker may have left an unreferenced file with this task's exact name.
      try (OutputStream stream = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
          ZipOutputStream zip = new ZipOutputStream(encryption.encrypt(stream))) {
        var manifest = new java.util.ArrayList<Map<String,Object>>();
        for (String table : List.of("checkins","day_summaries","memories","memory_sources","todos","punch_habits","punch_logs","reminders","reminder_deliveries","conversations","conversation_messages","agent_proposals","agent_action_logs","agent_profiles","followup_topics","followup_preferences","one_off_appointments","inbox_items","weekly_reviews","user_privacy_settings","privacy_settings_audit","memory_access_audit")) {
          List<Map<String,Object>> rows = db.queryForList("SELECT * FROM `" + table + "` WHERE user_id=?", user);
          if (rows.stream().anyMatch(row -> row.containsKey("deleted_at"))) rows.removeIf(row -> row.get("deleted_at") != null);
          // Embeddings and internal model payloads are not part of the portable user data.
          for (var row : rows) { row.remove("embedding_json"); row.remove("request_json"); row.remove("response_json"); }
          byte[] data = json.writeValueAsBytes(rows);
          writeEntry(zip, table + ".json", data);
          byte[] csvData = csv(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);
          writeEntry(zip, table + ".csv", csvData);
          manifest.add(Map.of("file", table + ".csv", "rows", rows.size(), "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(csvData))));
          manifest.add(Map.of("file", table + ".json", "rows", rows.size(), "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))));
        }
        var media = new java.util.ArrayList<Map<String,Object>>();
        var references = db.queryForList("SELECT photo_path FROM punch_logs WHERE user_id=? AND photo_path IS NOT NULL UNION SELECT relative_path FROM agent_media WHERE user_id=? AND status='active'", String.class, user, user);
        for (String relative : references) {
          if (!relative.startsWith("punch/" + user + "/") && !relative.startsWith("agent/" + user + "/")) throw new IllegalStateException("invalid media owner");
          Path file = storage.resolveReadable(relative);
          byte[] data = Files.readAllBytes(file);
          writeEntry(zip, "media/" + relative, data);
          media.add(Map.of("file", "media/" + relative, "bytes", data.length, "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))));
        }
        writeEntry(zip, "media.json", json.writeValueAsBytes(media));
        writeEntry(zip, "manifest.json", json.writeValueAsBytes(Map.of("schemaVersion", 1, "userId", user, "generatedAt", EntityMapper.now().toString(), "tables", manifest, "media", media)));
      }
      Instant now = EntityMapper.now();
      db.update("UPDATE data_requests SET status='succeeded',completed_at=?,expires_at=?,output_path=?,error_code=NULL WHERE id=?",
          now, now.plus(24, ChronoUnit.HOURS), out.toString(), id);
      log.info("data export succeeded taskId={}", id);
    } catch (Exception e) {
      log.warn("data export failed taskId={} errorCode={}", id, e.getClass().getSimpleName());
      if (out != null) {
        try { Files.deleteIfExists(out); } catch (java.io.IOException cleanup) {
          db.update("UPDATE data_requests SET output_path=? WHERE id=?", out.toString(), id);
        }
      }
      db.update("UPDATE data_requests SET status='failed',error_code='export_failed',completed_at=? WHERE id=?", EntityMapper.now(), id);
    }
    return true;
  }

  @Transactional public DataRequestDto deleteAccount(String phrase) {
    if (!"DELETE MY ACCOUNT".equals(phrase)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"confirmation phrase required");
    String user = identity.getCurrentUserId();
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user);
    var existing = db.queryForList("SELECT id FROM data_requests WHERE user_id=? AND type='delete_account' ORDER BY requested_at DESC LIMIT 1", String.class, user);
    if (!existing.isEmpty()) return find(existing.get(0), user, null);
    String id = UUID.randomUUID().toString();
    db.update("INSERT INTO data_requests(id,user_id,type,status,requested_at,attempts) VALUES(?,?,'delete_account','queued',?,0)", id, user, EntityMapper.now());
    db.update("DELETE FROM checkin_ai_jobs WHERE user_id=?", user);
    db.update("DELETE FROM checkins WHERE user_id=?", user);
    db.update("UPDATE conversations SET deleted_at=?,status='deleted',version=version+1 WHERE user_id=?", EntityMapper.now(), user);
    db.update("UPDATE conversation_messages SET deleted_at=? WHERE user_id=?", EntityMapper.now(), user);
    db.update("UPDATE memories SET archived=1,chat_context_scope='disabled' WHERE user_id=?", user);
    db.update("UPDATE memory_sources SET active=0 WHERE user_id=?", user);
    db.update("UPDATE followup_preferences SET enabled=0 WHERE user_id=?", user);
    revokePrivacyForDeletion(user);
    db.update("UPDATE followup_topics SET state='stopped' WHERE user_id=?", user);
    db.update("UPDATE reminders SET enabled=0 WHERE user_id=?", user);
    db.update("UPDATE one_off_appointments SET status='cancelled' WHERE user_id=?", user);
    db.update("UPDATE data_requests SET status='cancelled',download_token_hash=NULL WHERE user_id=? AND type='export'", user);
    return find(id, user, null);
  }

  private void revokePrivacyForDeletion(String user) {
    Instant now = EntityMapper.now();
    db.update("INSERT INTO user_privacy_settings(user_id,chat_memory_enabled,voice_retention,followup_enabled,version,updated_at) "
        + "VALUES (?,1,'delete_on_success',1,1,?) ON DUPLICATE KEY UPDATE user_id=user_id", user, now);
    record PrivacySnapshot(boolean chat, boolean followup, int version) {}
    PrivacySnapshot current = db.queryForObject(
        "SELECT chat_memory_enabled,followup_enabled,version FROM user_privacy_settings WHERE user_id=? FOR UPDATE",
        (rs, rowNum) -> new PrivacySnapshot(rs.getBoolean("chat_memory_enabled"),
            rs.getBoolean("followup_enabled"), rs.getInt("version")), user);
    boolean chat = current.chat();
    boolean followup = current.followup();
    if (!chat && !followup) return;
    int version = current.version() + 1;
    db.update("UPDATE user_privacy_settings SET chat_memory_enabled=0,followup_enabled=0,version=?,updated_at=? WHERE user_id=?",
        version, now, user);
    if (chat) insertPrivacyAudit(user, "chat_memory_enabled", "true", "false", "account_deletion", now);
    if (followup) insertPrivacyAudit(user, "followup_enabled", "true", "false", "account_deletion", now);
  }

  private void insertPrivacyAudit(String user, String field, String oldValue, String newValue, String source, Instant at) {
    if (db.update("INSERT INTO privacy_settings_audit(id,user_id,field,old_value,new_value,source,occurred_at) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(), user, field, oldValue, newValue, source, at) != 1) {
      throw new IllegalStateException("privacy audit write failed");
    }
  }

  public boolean isDeleting(String user) {
    return !db.queryForList("SELECT id FROM data_requests WHERE user_id=? AND type='delete_account' LIMIT 1", String.class, user).isEmpty();
  }

  /** Persist one step at a time. External cleanup is idempotent and repeated after a crash. */
  @Transactional public boolean processNextDeletion() {
    var pending = db.queryForList("SELECT * FROM data_requests WHERE type='delete_account' AND status IN ('queued','running') ORDER BY requested_at LIMIT 1 FOR UPDATE SKIP LOCKED");
    if (pending.isEmpty()) return false;
    var task = pending.get(0); String id = (String) task.get("id"); String user = (String) task.get("user_id");
    int step = ((Number) task.get("step")).intValue();
    try {
      db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user);
      if (step == 0) {
        // A worker with an already-read vector must finish before final erasure.
        if (!db.queryForList("SELECT memory_id FROM vector_sync_jobs WHERE user_id=? AND lease_until>UTC_TIMESTAMP(3)", String.class, user).isEmpty()) return true;
        // Retain IDs durably until every external vector deletion succeeds.
        var ids = db.queryForList("SELECT id FROM memories WHERE user_id=?", String.class, user);
        for (String memory : ids) vectors.deleteStrict(memory);
      } else if (step == 1) {
        storage.deleteUserMedia(user);
      } else {
        storage.deleteUserMedia(user);
        for (String memory : db.queryForList("SELECT id FROM memories WHERE user_id=?", String.class, user)) vectors.deleteStrict(memory);
        var tables = db.queryForList("SELECT DISTINCT table_name FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='user_id'", String.class);
        for (String table : tables) if (!"data_requests".equals(table)) db.update("DELETE FROM `" + table.replace("`", "") + "` WHERE user_id=?", user);
        db.update("DELETE FROM data_requests WHERE user_id=? AND id<>?", user, id);
        db.update("DELETE FROM users WHERE id=?", user);
      }
      db.update("UPDATE data_requests SET step=?,status=?,started_at=COALESCE(started_at,?),completed_at=?,error_code=NULL WHERE id=?", step + 1, step == 2 ? "succeeded" : "running", EntityMapper.now(), step == 2 ? EntityMapper.now() : null, id);
      log.info("account deletion advanced taskId={} step={}", id, step);
    } catch (Exception failure) {
      log.warn("account deletion failed taskId={} step={} errorCode={}", id, step, failure.getClass().getSimpleName());
      db.update("UPDATE data_requests SET status='failed',attempts=attempts+1,error_code='deletion_step_failed',completed_at=? WHERE id=?", EntityMapper.now(), id);
    }
    return true;
  }

  public List<DataRequestDto> recent() {
    String user = identity.getCurrentUserId();
    return db.queryForList("SELECT id FROM data_requests WHERE user_id=? ORDER BY requested_at DESC LIMIT 10", String.class, user)
        .stream().map(id -> find(id, user, null)).toList();
  }

  public DataRequestDto get(String id) { return find(id, identity.getCurrentUserId(), null); }
  @Transactional
  public DataRequestDto retry(String id) {
    String user = identity.getCurrentUserId();
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user);
    Map<String,Object> row = owned(id, user);
    if ("export".equals(row.get("type")) && isDeleting(user)) throw new ResponseStatusException(HttpStatus.CONFLICT, "account deletion in progress");
    if (List.of("queued", "running").contains(row.get("status"))) return find(id, user, null);
    if (!List.of("failed", "expired").contains(row.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "request is not retryable");
    if (((Number) row.get("attempts")).intValue() >= 5) throw new ResponseStatusException(HttpStatus.CONFLICT, "retry limit reached");
    if (!db.queryForList("SELECT id FROM data_requests WHERE user_id=? AND type=? AND status IN ('queued','running')", String.class, user, row.get("type")).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "another request is active");
    }
    db.update("UPDATE data_requests SET status='queued',error_code=NULL,completed_at=NULL,download_token_hash=NULL WHERE id=? AND user_id=?", id, user);
    return find(id, user, null);
  }

  @Transactional
  public String issueDownloadToken(String id) {
    String user = identity.getCurrentUserId();
    String token = UUID.randomUUID().toString() + UUID.randomUUID();
    int changed = db.update("UPDATE data_requests SET download_token_hash=?,download_token_expires_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE) WHERE id=? AND user_id=? "
        + "AND type='export' AND status='succeeded' AND expires_at>UTC_TIMESTAMP(3)", hash(token), id, user);
    if (changed != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "download unavailable");
    return token;
  }

  private Map<String,Object> owned(String id, String user) {
    try { return db.queryForMap("SELECT * FROM data_requests WHERE id=? AND user_id=?", id, user); }
    catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found");
    }
  }

  @Transactional
  public Path download(String id, String token) {
    String user = identity.getCurrentUserId();
    Map<String,Object> row = owned(id, user);
    if (token == null || token.isBlank() || row.get("output_path") == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "download unavailable");
    }
    Path path = storage.resolveReadable(storage.getRoot().relativize(Path.of((String)row.get("output_path"))).toString());
    // Exactly one concurrent caller can consume the bearer token.
    int consumed = db.update("UPDATE data_requests SET download_token_hash=NULL WHERE id=? AND user_id=? "
        + "AND type='export' AND status='succeeded' AND expires_at>UTC_TIMESTAMP(3) AND download_token_hash=? AND download_token_expires_at>UTC_TIMESTAMP(3)", id, user, hash(token));
    if (consumed != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "download unavailable");
    return path;
  }
  private DataRequestDto find(String id, String user, String token) {
    Map<String,Object> r = owned(id, user);
    return new DataRequestDto(id, (String)r.get("type"), (String)r.get("status"),
        timestamp(r.get("requested_at")), timestamp(r.get("completed_at")), timestamp(r.get("expires_at")),
        List.of("failed", "expired").contains(r.get("status")) && ((Number)r.get("attempts")).intValue() < 5, token);
  }
  private static String timestamp(Object value) {
    if (value == null) return null;
    if (value instanceof java.sql.Timestamp t) return t.toInstant().toString();
    if (value instanceof java.time.LocalDateTime t) return t.toInstant(java.time.ZoneOffset.UTC).toString();
    throw new IllegalStateException("unsupported database timestamp");
  }

  @Transactional
  public void cleanupExpiredExports() {
    var rows = db.queryForList("SELECT id,output_path FROM data_requests WHERE type='export' "
        + "AND output_path IS NOT NULL AND (expires_at<=UTC_TIMESTAMP(3) OR status='failed') LIMIT 20 FOR UPDATE SKIP LOCKED");
    for (var row : rows) {
      Path path = Path.of((String)row.get("output_path")).toAbsolutePath().normalize();
      if (!path.startsWith(storage.getRoot().resolve("exports"))) throw new IllegalStateException("invalid export path");
      try { Files.deleteIfExists(path); }
      catch (java.io.IOException failure) { continue; } // Keep reference for the next cleanup attempt.
      db.update("UPDATE data_requests SET status=IF(status='failed','failed','expired'),output_path=NULL,download_token_hash=NULL WHERE id=?", row.get("id"));
    }
  }
  public java.io.InputStream openDownload(Path path) throws java.io.IOException {
    return encryption.decrypt(Files.newInputStream(path));
  }
  private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
    zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
  }
  private static String csv(List<Map<String,Object>> rows) {
    if (rows.isEmpty()) return "";
    var columns = new java.util.ArrayList<>(rows.get(0).keySet());
    StringBuilder out = new StringBuilder();
    out.append(columns.stream().map(DataRequestService::csvCell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
    for (var row : rows) out.append(columns.stream().map(c -> csvCell(row.get(c))).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
    return out.toString();
  }
  private static String csvCell(Object value) {
    String text = value == null ? "" : value.toString();
    if (!text.isEmpty() && "=+@-\t\r\n".indexOf(text.charAt(0)) >= 0) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
  private static String hash(String value){ try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
}
