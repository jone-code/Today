package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import com.today.persistence.InboxItemEntity;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class AppointmentServiceTest {
  private final AppointmentMapper mapper = mock(AppointmentMapper.class);
  private final FollowupMapper inbox = mock(FollowupMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final AppointmentService service = new AppointmentService(mapper, inbox, identity);

  @Test
  void schedulesOneOffForConfirmedProposal() {
    Instant due = Instant.now().plus(Duration.ofDays(1));
    when(mapper.insert(any())).thenReturn(1);

    var saved = service.schedule("p1", "u1", "c1", "m1", "带文件", due, "Asia/Shanghai");

    assertEquals("p1", saved.getProposalId());
    assertEquals("pending", saved.getStatus());
    assertEquals(due, saved.getDueAt());
    verify(mapper).insert(saved);
    verify(inbox, never()).insertInbox(any());
  }

  @Test
  void dispatchCreatesSingleInboxItemAtOriginalDueTime() {
    Instant due = Instant.parse("2026-11-03T01:00:00Z");
    Instant now = due.plusSeconds(3600);
    AppointmentEntity appointment = appointment(due, "pending");
    when(mapper.listDue(now)).thenReturn(java.util.List.of(appointment));
    when(mapper.lockOwned("a1", "u1")).thenReturn(appointment);
    when(inbox.insertInbox(any())).thenReturn(1);
    when(mapper.markDelivered("a1", "u1", now)).thenReturn(1);

    assertEquals(1, service.dispatchDue(now));

    ArgumentCaptor<InboxItemEntity> item = ArgumentCaptor.forClass(InboxItemEntity.class);
    verify(inbox).insertInbox(item.capture());
    assertEquals(due, item.getValue().getScheduledAt());
    assertEquals("one_off_appointment", item.getValue().getSourceKind());
    assertEquals("约定提醒：带文件\n原定 2026-11-03 09:00（Asia/Shanghai）", item.getValue().getBody());

    appointment.setStatus("delivered");
    assertEquals(0, service.dispatchDue(now));
    verify(inbox, org.mockito.Mockito.times(1)).insertInbox(any());
  }

  @Test
  void canceledAppointmentCannotBeDispatched() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    AppointmentEntity appointment = appointment(Instant.now().plusSeconds(60), "pending");
    when(mapper.lockOwned("a1", "u1")).thenReturn(appointment);
    when(mapper.cancel("a1", "u1")).thenReturn(1);
    service.cancel("a1");
    appointment.setStatus("canceled");
    when(mapper.listDue(any())).thenReturn(java.util.List.of(appointment));

    assertEquals(0, service.dispatchDue(Instant.now().plusSeconds(120)));
    verify(inbox, never()).insertInbox(any());
  }

  @Test
  void expiredBeforeConfirmationIsRejected() {
    assertThrows(ResponseStatusException.class,
        () -> service.schedule("p1", "u1", "c1", "m1", "带文件",
            Instant.now().minusSeconds(1), "Asia/Shanghai"));
    verify(mapper, never()).insert(any());
  }

  private static AppointmentEntity appointment(Instant due, String status) {
    AppointmentEntity entity = new AppointmentEntity();
    entity.setId("a1"); entity.setUserId("u1"); entity.setTitle("带文件");
    entity.setDueAt(due); entity.setTimezone("Asia/Shanghai"); entity.setStatus(status);
    return entity;
  }
}
