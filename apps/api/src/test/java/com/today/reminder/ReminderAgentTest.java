package com.today.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.today.identity.IdentityService;
import com.today.persistence.ReminderEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ReminderAgentTest {
  private final ReminderMapper mapper = mock(ReminderMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final ReminderService service = new ReminderService(mapper, identity);

  @Test
  void staleReminderCannotOverwriteNewerContent() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    ReminderEntity current = reminder();
    current.setMessage("手动改过的内容");
    when(mapper.lockOwned("r1", "u1")).thenReturn(current);
    assertThrows(ResponseStatusException.class, () -> service.updateFromAgent(snapshot(),
        new ReminderUpdateRequest("喝水", "喝水休息", "10:30", "Asia/Shanghai", true)));
    verify(mapper, never()).update(any());
  }

  @Test
  void reminderMustBelongToCurrentUser() {
    when(identity.getCurrentUserId()).thenReturn("u2");
    when(mapper.lockOwned("r1", "u2")).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.updateFromAgent(snapshot(),
        new ReminderUpdateRequest("喝水", "喝水休息", "10:30", "Asia/Shanghai", true)));
    verify(mapper, never()).update(any());
  }

  @Test
  void confirmedReminderEditPreservesOtherFields() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    ReminderEntity current = reminder();
    when(mapper.lockOwned("r1", "u1")).thenReturn(current);
    when(mapper.update(current)).thenReturn(1);
    ReminderDto result = service.updateFromAgent(snapshot(),
        new ReminderUpdateRequest("喝水", "喝水休息", "10:30", "Asia/Shanghai", true));
    assertEquals("10:30", result.remindTime());
    assertEquals("喝水休息", result.message());
    assertEquals("Asia/Shanghai", result.timezone());
    verify(mapper).update(current);
  }

  private ReminderEntity reminder() {
    ReminderEntity r = new ReminderEntity();
    r.setId("r1"); r.setUserId("u1"); r.setTitle("喝水"); r.setMessage("喝水休息");
    r.setRemindTime("09:00"); r.setTimezone("Asia/Shanghai"); r.setEnabled(true);
    r.setCreatedAt(Instant.parse("2026-11-03T09:00:00Z"));
    r.setUpdatedAt(Instant.parse("2026-11-03T09:00:00Z"));
    return r;
  }

  private ReminderDto snapshot() {
    return new ReminderDto("r1", "u1", "喝水", "喝水休息", "09:00", "Asia/Shanghai",
        true, "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z");
  }
}
