package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.persistence.AgentProfileEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AgentProfileServiceTest {
  @Test
  void rollbackCleansNewlyWrittenAvatar() {
    AgentProfileMapper profiles = mock(AgentProfileMapper.class);
    AgentMediaMapper media = mock(AgentMediaMapper.class);
    IdentityService identity = mock(IdentityService.class);
    LocalMediaStorage storage = mock(LocalMediaStorage.class);
    AgentProfileEntity profile = new AgentProfileEntity();
    profile.setUserId("u1"); profile.setName("Today"); profile.setTone("温和");
    profile.setVersion(1); profile.setCreatedAt(Instant.now()); profile.setUpdatedAt(Instant.now());
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(profiles.findByUserId("u1")).thenReturn(profile);
    when(profiles.lockByUserId("u1")).thenReturn(profile);
    when(storage.storeAgentAvatar(any(), any())).thenReturn(
        new LocalMediaStorage.StoredAvatar("agent/u1/new.jpg", 301, "image/jpeg"));
    when(media.insert(any())).thenThrow(new IllegalStateException("database unavailable"));

    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThrows(IllegalStateException.class, () -> new AgentProfileService(profiles, identity, media, storage)
          .uploadAvatar(new MockMultipartFile("avatar", "a.jpg", "image/jpeg", new byte[] { 1 })));
      for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
        callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
      }
      verify(storage).deleteIfPresent("agent/u1/new.jpg");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void uploadReplacesAvatarFromLockedProfile() {
    AgentProfileMapper profiles = mock(AgentProfileMapper.class);
    AgentMediaMapper media = mock(AgentMediaMapper.class);
    IdentityService identity = mock(IdentityService.class);
    LocalMediaStorage storage = mock(LocalMediaStorage.class);
    AgentProfileEntity profile = new AgentProfileEntity();
    profile.setUserId("u1"); profile.setName("Today"); profile.setTone("温和");
    profile.setAvatarMediaId("previous"); profile.setVersion(1);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(profiles.findByUserId("u1")).thenReturn(profile);
    when(profiles.lockByUserId("u1")).thenReturn(profile);
    when(storage.storeAgentAvatar(any(), any())).thenReturn(
        new LocalMediaStorage.StoredAvatar("agent/u1/new.jpg", 301, "image/jpeg"));

    new AgentProfileService(profiles, identity, media, storage)
        .uploadAvatar(new MockMultipartFile("avatar", "a.jpg", "image/jpeg", new byte[] { 1 }));

    verify(profiles).lockByUserId("u1");
    verify(media).markReplaced(org.mockito.ArgumentMatchers.eq("previous"),
        org.mockito.ArgumentMatchers.eq("u1"), any());
    verify(profiles).update(profile);
  }

  @Test
  void resetLocksProfileBeforeReplacingCurrentMedia() {
    AgentProfileMapper profiles = mock(AgentProfileMapper.class);
    AgentMediaMapper media = mock(AgentMediaMapper.class);
    IdentityService identity = mock(IdentityService.class);
    AgentProfileEntity profile = new AgentProfileEntity();
    profile.setUserId("u1"); profile.setName("Today"); profile.setTone("温和");
    profile.setAvatarMediaId("current"); profile.setVersion(4);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(profiles.findByUserId("u1")).thenReturn(profile);
    when(profiles.lockByUserId("u1")).thenReturn(profile);

    AgentProfileDto result = new AgentProfileService(profiles, identity, media,
        mock(LocalMediaStorage.class)).resetAvatar();

    verify(profiles).lockByUserId("u1");
    verify(media).markReplaced(org.mockito.ArgumentMatchers.eq("current"),
        org.mockito.ArgumentMatchers.eq("u1"), any());
    verify(profiles).update(profile);
    org.junit.jupiter.api.Assertions.assertNull(result.avatarMediaId());
    org.junit.jupiter.api.Assertions.assertEquals(5, result.version());
  }

  @Test
  void newlyCreatedProfileUsesCurrentReadAfterConcurrentInsert() {
    AgentProfileMapper profiles = mock(AgentProfileMapper.class);
    IdentityService identity = mock(IdentityService.class);
    AgentProfileEntity committed = new AgentProfileEntity();
    committed.setUserId("u1"); committed.setName("Custom"); committed.setTone("Brief");
    committed.setVersion(3);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(profiles.lockByUserId("u1")).thenReturn(committed);

    AgentProfileDto result = new AgentProfileService(profiles, identity,
        mock(AgentMediaMapper.class), mock(LocalMediaStorage.class)).getOrCreate();

    verify(profiles).insert(any());
    verify(profiles).lockByUserId("u1");
    org.junit.jupiter.api.Assertions.assertEquals("Custom", result.name());
  }
}
