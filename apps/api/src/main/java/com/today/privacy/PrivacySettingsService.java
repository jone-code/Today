package com.today.privacy;

import com.today.agent.FollowupMapper;
import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PrivacySettingsService {
  private static final Set<String> RETENTION = Set.of("delete_on_success");
  private final PrivacySettingsMapper mapper; private final IdentityService identity; private final FollowupMapper followups;
  private final PrivacySettingsAuditMapper audit;
  public PrivacySettingsService(PrivacySettingsMapper mapper, IdentityService identity, FollowupMapper followups,
      PrivacySettingsAuditMapper audit) {
    this.mapper = mapper; this.identity = identity; this.followups = followups; this.audit = audit;
  }
  @Transactional public PrivacySettingsDto get() { return dto(load(identity.getCurrentUserId())); }
  @Transactional public PrivacySettingsDto update(PrivacySettingsRequest input) {
    String userId = identity.getCurrentUserId();
    requireWritableAccount(userId);
    PrivacySettingsEntity current = load(userId);
    String retention = input.getVoiceRetention() == null ? current.getVoiceRetention() : input.getVoiceRetention();
    if (!RETENTION.contains(retention)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid voice retention");
    if (input.getVersion() == null || input.getVersion() != current.getVersion()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "privacy settings changed; reload before saving");
    }
    int version = current.getVersion() + 1; var now = EntityMapper.now();
    if (mapper.update(userId, input.getChatMemoryEnabled(), retention, input.getFollowupEnabled(), version, now) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "privacy settings changed; reload before saving");
    }
    boolean oldChatMemory = current.isChatMemoryEnabled();
    String oldRetention = current.getVoiceRetention();
    boolean oldFollowup = current.isFollowupEnabled();
    current.setChatMemoryEnabled(input.getChatMemoryEnabled()); current.setVoiceRetention(retention);
    current.setFollowupEnabled(input.getFollowupEnabled()); current.setVersion(version); current.setUpdatedAt(now);
    recordChanges(userId, oldChatMemory, current.isChatMemoryEnabled(), oldRetention, retention,
        oldFollowup, current.isFollowupEnabled(), "profile_privacy", now);
    followups.ensurePreference(userId, now);
    followups.updatePreferenceEnabled(userId, input.getFollowupEnabled(), now);
    return dto(current);
  }
  /** Keeps the privacy snapshot aligned when follow-up preferences are changed through their dedicated API. */
  @Transactional public void syncFollowupEnabled(String userId, boolean enabled) {
    requireWritableAccount(userId);
    PrivacySettingsEntity current = load(userId);
    if (current.isFollowupEnabled() == enabled) return;
    int version = current.getVersion() + 1;
    Instant now = EntityMapper.now();
    if (mapper.update(userId, current.isChatMemoryEnabled(), current.getVoiceRetention(), enabled,
        version, now) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "privacy settings changed; reload before saving");
    }
    record(userId, "followup_enabled", Boolean.toString(current.isFollowupEnabled()), Boolean.toString(enabled),
        "followup_preferences", now);
  }
  @Transactional public boolean chatMemoryEnabled(String userId) { return load(userId).isChatMemoryEnabled(); }
  /** Serialize settings writes with deletion, including requests already past the HTTP filter. */
  @Transactional public void requireWritableAccount(String userId) {
    if (mapper.lockUser(userId) == null || mapper.lockDeletionRequest(userId) != null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account unavailable for settings changes");
    }
  }
  private PrivacySettingsEntity load(String userId) { PrivacySettingsEntity e=mapper.find(userId); if(e!=null)return e; requireWritableAccount(userId); e=new PrivacySettingsEntity(); e.setUserId(userId); e.setChatMemoryEnabled(true); e.setVoiceRetention("delete_on_success"); e.setFollowupEnabled(true); e.setVersion(1); e.setUpdatedAt(EntityMapper.now()); mapper.insert(e); return mapper.find(userId); }
  private PrivacySettingsDto dto(PrivacySettingsEntity e) { return new PrivacySettingsDto(e.getUserId(),e.isChatMemoryEnabled(),e.getVoiceRetention(),e.isFollowupEnabled(),e.getVersion(),e.getUpdatedAt().toString()); }
  private void recordChanges(String userId, boolean oldChat, boolean newChat, String oldRetention, String newRetention,
      boolean oldFollowup, boolean newFollowup, String source, java.time.Instant at) {
    if (oldChat != newChat) record(userId, "chat_memory_enabled", Boolean.toString(oldChat), Boolean.toString(newChat), source, at);
    if (!oldRetention.equals(newRetention)) record(userId, "voice_retention", oldRetention, newRetention, source, at);
    if (oldFollowup != newFollowup) record(userId, "followup_enabled", Boolean.toString(oldFollowup), Boolean.toString(newFollowup), source, at);
  }
  private void record(String userId, String field, String oldValue, String newValue, String source, java.time.Instant at) {
    if (oldValue.equals(newValue)) return;
    if (audit.insert(java.util.UUID.randomUUID().toString(), userId, field, oldValue, newValue, source, at) != 1) {
      throw new IllegalStateException("privacy audit write failed");
    }
  }
}
