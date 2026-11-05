package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.today.common.RateLimitService;
import com.today.common.CreateIdempotencyService;
import com.today.identity.IdentityService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InboxSnoozeControllerTest {
  private final FollowupService followups = mock(FollowupService.class);
  private final AgentController controller = new AgentController(mock(AgentProfileService.class),
      mock(ConversationService.class), mock(ProposalService.class), followups,
      mock(AppointmentService.class), mock(RateLimitService.class), mock(IdentityService.class),
      mock(CreateIdempotencyService.class));

  @Test
  void dateAndLegacyUntilAreMutuallyExclusive() {
    for (InboxSnoozeRequest invalid : new InboxSnoozeRequest[] {
        new InboxSnoozeRequest(null, null),
        new InboxSnoozeRequest("2026-11-10", Instant.parse("2026-11-10T01:00:00Z")) }) {
      ResponseStatusException failure = assertThrows(ResponseStatusException.class,
          () -> controller.snoozeInbox("item-1", invalid));
      assertEquals(400, failure.getStatusCode().value());
    }
    verifyNoInteractions(followups);
  }

  @Test
  void dateAndLegacyUntilUseTheirOwnValidationPaths() {
    Instant instant = Instant.parse("2026-11-10T01:00:00Z");
    controller.snoozeInbox("item-1", new InboxSnoozeRequest("2026-11-10", null));
    controller.snoozeInbox("item-2", new InboxSnoozeRequest(null, instant));

    verify(followups).snoozeOnDate("item-1", "2026-11-10");
    verify(followups).snooze("item-2", instant);
  }
}
