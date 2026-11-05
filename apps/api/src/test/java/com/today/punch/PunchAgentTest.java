package com.today.punch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.persistence.PunchHabitEntity;
import com.today.persistence.PunchLogEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PunchAgentTest {
  private final PunchMapper mapper = mock(PunchMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final LocalMediaStorage media = mock(LocalMediaStorage.class);
  private final PunchPhotoCleanupMapper cleanup = mock(PunchPhotoCleanupMapper.class);
  private final PunchService service = new PunchService(mapper, identity, media, cleanup,
      mock(org.springframework.transaction.PlatformTransactionManager.class));
  private final LocalDate date = LocalDate.of(2026, 11, 3);

  @Test
  void staleLogNeverDeletesPhotoOrData() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockHabitById("h1", "u1")).thenReturn(habit());
    PunchLogEntity log = log();
    when(mapper.lockLog("h1", "u1", date)).thenReturn(log);
    when(mapper.findHabitById("h1")).thenReturn(habit());
    when(mapper.findLog("h1", date)).thenReturn(log);
    var snapshot = service.snapshotForAgent("h1", date);
    assertEquals(true, snapshot.hasPhoto());
    log.setNote("后来修改的备注");
    assertThrows(ResponseStatusException.class, () -> service.applyAgentPunch(snapshot, true));
    verify(mapper, never()).deleteLog("h1", "u1", date);
    verifyNoInteractions(media);
  }

  @Test
  void cancelQueuesPhotoCleanupInSameTransaction() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockHabitById("h1", "u1")).thenReturn(habit());
    PunchLogEntity log = log();
    when(mapper.findHabitById("h1")).thenReturn(habit());
    when(mapper.findLog("h1", date)).thenReturn(log);
    when(mapper.lockLog("h1", "u1", date)).thenReturn(log);
    when(mapper.deleteLog("h1", "u1", date)).thenReturn(1);
    var snapshot = service.snapshotForAgent("h1", date);
    assertEquals("h1", service.applyAgentPunch(snapshot, true));
    verify(cleanup).enqueue(eq("private/photo.jpg"), eq("u1"), any());
    verifyNoInteractions(media);
  }

  @Test
  void punchCannotBeRepeatedUsingStaleSnapshot() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockHabitById("h1", "u1")).thenReturn(habit());
    when(mapper.lockLog("h1", "u1", date)).thenReturn(log());
    var snapshot = new PunchService.PunchAgentSnapshot("h1", "跑步", "2026-11-03T09:00:00Z",
        true, date.toString(), null, null, false);
    assertThrows(ResponseStatusException.class, () -> service.applyAgentPunch(snapshot, false));
    verify(mapper, never()).insertLog(any());
  }

  @Test
  void editingHabitRejectsStaleSnapshotAndKeepsNewerValues() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    PunchHabitEntity changed = habit();
    changed.setDescription("后来更新的描述");
    when(mapper.lockHabitById("h1", "u1")).thenReturn(changed);
    PunchHabitDto before = new PunchHabitDto("h1", "u1", "跑步", null, true,
        "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z", false, 0, null);
    assertThrows(ResponseStatusException.class, () -> service.updateHabitFromAgent(before,
        new PunchHabitUpdateRequest("晨跑", null, false)));
    assertEquals("跑步", changed.getTitle());
    verify(mapper, never()).updateHabit(any());
  }

  @Test
  void editingHabitUpdatesOnlyMatchedOwnedHabit() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    PunchHabitEntity existing = habit();
    when(mapper.lockHabitById("h1", "u1")).thenReturn(existing);
    when(mapper.updateHabit(existing)).thenReturn(1);
    PunchHabitDto before = new PunchHabitDto("h1", "u1", "跑步", null, true,
        "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z", false, 0, null);
    var changed = service.updateHabitFromAgent(before,
        new PunchHabitUpdateRequest("晨跑", "每天十分钟", false));
    assertEquals("晨跑", changed.title());
    assertEquals("每天十分钟", changed.description());
    assertEquals(false, changed.enabled());
    verify(mapper).updateHabit(existing);
    verifyNoInteractions(media);
  }

  @Test
  void deletingHabitRemovesLogsAndQueuesPhotos() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockHabitById("h1", "u1")).thenReturn(habit());
    when(mapper.listAllLogsByHabit("h1", "u1")).thenReturn(List.of(log()));
    when(mapper.deleteHabit("h1", "u1")).thenReturn(1);
    service.deleteHabit("h1");
    verify(mapper).deleteLogsByHabit("h1", "u1");
    verify(cleanup).enqueue(eq("private/photo.jpg"), eq("u1"), any());
    verifyNoInteractions(media);
  }

  private PunchHabitEntity habit() {
    PunchHabitEntity habit = new PunchHabitEntity();
    habit.setId("h1"); habit.setUserId("u1"); habit.setTitle("跑步");
    habit.setEnabled(true); habit.setCreatedAt(Instant.parse("2026-11-03T09:00:00Z"));
    habit.setUpdatedAt(Instant.parse("2026-11-03T09:00:00Z"));
    return habit;
  }

  private PunchLogEntity log() {
    PunchLogEntity log = new PunchLogEntity();
    log.setId("l1"); log.setHabitId("h1"); log.setUserId("u1");
    log.setPunchDate(date); log.setPhotoPath("private/photo.jpg");
    return log;
  }
}
