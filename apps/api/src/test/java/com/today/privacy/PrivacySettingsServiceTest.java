package com.today.privacy;

import com.today.agent.FollowupMapper;
import com.today.identity.IdentityService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PrivacySettingsServiceTest {
  private final PrivacySettingsMapper mapper = mock(PrivacySettingsMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final FollowupMapper followups = mock(FollowupMapper.class);
  private final PrivacySettingsAuditMapper audit = mock(PrivacySettingsAuditMapper.class);
  private final PrivacySettingsService service = new PrivacySettingsService(mapper, identity, followups, audit);

  PrivacySettingsServiceTest() {
    when(mapper.lockUser("owner")).thenReturn("owner");
    when(audit.insert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
  }

  private PrivacySettingsEntity existing() {
    when(identity.getCurrentUserId()).thenReturn("owner");
    var row = new PrivacySettingsEntity();
    row.setUserId("owner"); row.setVersion(2); row.setChatMemoryEnabled(false);
    row.setVoiceRetention("delete_on_success"); row.setFollowupEnabled(true); row.setUpdatedAt(Instant.now());
    when(mapper.find("owner")).thenReturn(row);
    return row;
  }
  private PrivacySettingsRequest request(int version) {
    var input = new PrivacySettingsRequest(); input.setVersion(version);
    input.setChatMemoryEnabled(true); input.setFollowupEnabled(false); return input;
  }
  @Test void staleClientCannotReenableMemory() {
    existing();
    assertEquals(409, assertThrows(ResponseStatusException.class,
        () -> service.update(request(1))).getStatusCode().value());
    verify(mapper, never()).update(anyString(), anyBoolean(), anyString(), anyBoolean(), anyInt(), any());
  }
  @Test void concurrentUpdateIsConflictRatherThanFalseSuccess() {
    existing();
    when(mapper.update(eq("owner"), anyBoolean(), anyString(), anyBoolean(), eq(3), any())).thenReturn(0);
    assertEquals(409, assertThrows(ResponseStatusException.class,
        () -> service.update(request(2))).getStatusCode().value());
  }
  @Test void ownerUpdateAdvancesVersion() {
    existing();
    when(mapper.update(eq("owner"), eq(true), eq("delete_on_success"), eq(false), eq(3), any())).thenReturn(1);
    var result = service.update(request(2));
    assertEquals(3, result.version()); assertTrue(result.chatMemoryEnabled());
    assertEquals("owner", result.userId());
    verify(followups).ensurePreference(eq("owner"), any());
    verify(followups).updatePreferenceEnabled(eq("owner"), eq(false), any());
    verify(audit).insert(anyString(), eq("owner"), eq("chat_memory_enabled"), eq("false"), eq("true"),
        eq("profile_privacy"), any());
    verify(audit).insert(anyString(), eq("owner"), eq("followup_enabled"), eq("true"), eq("false"),
        eq("profile_privacy"), any());
  }
  @Test void initializationRereadsWinningRowRatherThanReturningPermissiveDefault() {
    when(identity.getCurrentUserId()).thenReturn("owner");
    var winner = new PrivacySettingsEntity(); winner.setUserId("owner"); winner.setVersion(2);
    winner.setChatMemoryEnabled(false); winner.setVoiceRetention("delete_on_success"); winner.setUpdatedAt(Instant.now());
    when(mapper.find("owner")).thenReturn(null, winner);
    assertFalse(service.get().chatMemoryEnabled());
  }
  @Test void cannotEnableUnimplementedAudioRetention() {
    existing(); var input = request(2); input.setVoiceRetention("短期保留");
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> service.update(input)).getStatusCode().value());
  }
  @Test void syncFollowupEnabledAdvancesPrivacySnapshot() {
    var row = existing();
    row.setFollowupEnabled(false);
    when(mapper.update(eq("owner"), eq(false), eq("delete_on_success"), eq(true), eq(3), any())).thenReturn(1);
    service.syncFollowupEnabled("owner", true);
    verify(mapper).update(eq("owner"), eq(false), eq("delete_on_success"), eq(true), eq(3), any());
    verify(audit).insert(anyString(), eq("owner"), eq("followup_enabled"), eq("false"), eq("true"),
        eq("followup_preferences"), any());
  }
  @Test void deletionRejectsEvenASettingsRequestThatAlreadyPassedAuthentication() {
    existing();
    when(mapper.lockDeletionRequest("owner")).thenReturn("deletion");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> service.update(request(2))).getStatusCode().value());
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> service.syncFollowupEnabled("owner", true)).getStatusCode().value());
    verify(mapper, never()).update(anyString(), anyBoolean(), anyString(), anyBoolean(), anyInt(), any());
    verifyNoInteractions(followups, audit);
  }
  @Test void deletedAccountCannotRecreateDefaultConsent() {
    when(identity.getCurrentUserId()).thenReturn("owner");
    when(mapper.lockUser("owner")).thenReturn(null);
    assertEquals(403, assertThrows(ResponseStatusException.class, service::get).getStatusCode().value());
    verify(mapper, never()).insert(any());
  }
}
