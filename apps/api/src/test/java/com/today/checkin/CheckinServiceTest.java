package com.today.checkin;

import com.today.agent.FollowupMapper;
import com.today.persistence.FollowupPreferenceEntity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.CheckinEntity;
import com.today.summary.SummaryMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.Test;

class CheckinServiceTest {
  @Test
  void recordDetailRequiresCurrentAccount() {
    CheckinMapper mapper = mock(CheckinMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    CheckinEntity own = new CheckinEntity();
    own.setId("c1"); own.setUserId("u1"); own.setCheckinDate(LocalDate.of(2026, 11, 3));
    own.setRawText("只属于我的记录"); own.setCreatedAt(Instant.now()); own.setUpdatedAt(Instant.now());
    when(mapper.findOwnedById("c1", "u1")).thenReturn(own);
    CheckinService service = new CheckinService(mapper, mock(CheckinMemoryCandidateMapper.class),
        mock(SummaryMapper.class), mock(MemoryService.class), identity, mock(FollowupMapper.class));

    assertEquals("只属于我的记录", service.getById("c1").rawText());
    assertEquals(404, assertThrows(ResponseStatusException.class,
        () -> service.getById("another-account-record")).getStatusCode().value());
    verify(mapper).findOwnedById("another-account-record", "u1");
  }
  @Test
  void changingCheckinInvalidatesPendingCandidates() {
    CheckinMapper mapper = mock(CheckinMapper.class);
    CheckinMemoryCandidateMapper candidates = mock(CheckinMemoryCandidateMapper.class);
    SummaryMapper summaries = mock(SummaryMapper.class);
    MemoryService memories = mock(MemoryService.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    CheckinEntity existing = new CheckinEntity();
    existing.setId("c1"); existing.setUserId("u1"); existing.setRawText("old");
    existing.setCheckinDate(LocalDate.of(2026, 11, 2));
    existing.setCreatedAt(java.time.Instant.now());
    when(mapper.lockByUserIdAndDate("u1", LocalDate.of(2026, 11, 2))).thenReturn(existing);

    new CheckinService(mapper, candidates, summaries, memories, identity, mock(FollowupMapper.class))
        .upsert(new CheckinCreateInput("new", "2026-11-02"));

    verify(candidates).invalidatePending("c1", "u1");
    verify(memories).deactivateCheckinSource("u1", "c1");
    verify(summaries).deleteByCheckinId("c1", "u1");
    verify(mapper).update(existing);
  }

  @Test
  void unchangedCheckinKeepsPendingCandidates() {
    CheckinMapper mapper = mock(CheckinMapper.class);
    CheckinMemoryCandidateMapper candidates = mock(CheckinMemoryCandidateMapper.class);
    SummaryMapper summaries = mock(SummaryMapper.class);
    MemoryService memories = mock(MemoryService.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    CheckinEntity existing = new CheckinEntity();
    existing.setId("c1"); existing.setUserId("u1"); existing.setRawText("same");
    existing.setCheckinDate(LocalDate.of(2026, 11, 2));
    existing.setCreatedAt(java.time.Instant.now());
    when(mapper.lockByUserIdAndDate("u1", LocalDate.of(2026, 11, 2))).thenReturn(existing);

    new CheckinService(mapper, candidates, summaries, memories, identity, mock(FollowupMapper.class))
        .upsert(new CheckinCreateInput("same", "2026-11-02"));

    verifyNoInteractions(candidates);
    verifyNoInteractions(summaries);
    verifyNoInteractions(memories);
    verify(mapper).update(any(CheckinEntity.class));
  }

  @Test
  void agentCannotOverwriteRecordChangedAfterPreview() {
    CheckinMapper mapper = mock(CheckinMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    LocalDate date = LocalDate.of(2026, 11, 3);
    CheckinEntity changed = new CheckinEntity();
    changed.setUpdatedAt(Instant.parse("2026-11-03T10:00:00Z"));
    changed.setRawText("new content");
    when(mapper.lockByUserIdAndDate("u1", date)).thenReturn(changed);
    CheckinService service = new CheckinService(mapper, mock(CheckinMemoryCandidateMapper.class),
        mock(SummaryMapper.class), mock(MemoryService.class), identity, mock(FollowupMapper.class));

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> service.upsertFromAgent("u1", date, "old preview", "2026-11-03T09:00:00Z", "old content"));
    assertEquals(409, failure.getStatusCode().value());
    ResponseStatusException sameMillisecond = assertThrows(ResponseStatusException.class,
        () -> service.upsertFromAgent("u1", date, "old preview", "2026-11-03T10:00:00Z", "old content"));
    assertEquals(409, sameMillisecond.getStatusCode().value());
    verify(mapper, org.mockito.Mockito.never()).update(any());
  }

  @Test
  void agentCreateRaceReturnsConflictInsteadOfDatabaseError() {
    CheckinMapper mapper = mock(CheckinMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    LocalDate date = LocalDate.of(2026, 11, 3);
    org.mockito.Mockito.doThrow(new DuplicateKeyException("concurrent insert"))
        .when(mapper).insert(any(CheckinEntity.class));
    CheckinService service = new CheckinService(mapper, mock(CheckinMemoryCandidateMapper.class),
        mock(SummaryMapper.class), mock(MemoryService.class), identity, mock(FollowupMapper.class));

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> service.upsertFromAgent("u1", date, "new record", null, null));
    assertEquals(409, failure.getStatusCode().value());
  }

  @Test
  void todayAndManualCheckinUseUserTimezone() {
    CheckinMapper mapper = mock(CheckinMapper.class);
    FollowupMapper followups = mock(FollowupMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    FollowupPreferenceEntity preference = new FollowupPreferenceEntity();
    preference.setTimezone("Pacific/Honolulu");
    when(followups.findPreference("u1")).thenReturn(preference);
    LocalDate today = LocalDate.now(ZoneId.of("Pacific/Honolulu"));
    CheckinService service = new CheckinService(mapper, mock(CheckinMemoryCandidateMapper.class),
        mock(SummaryMapper.class), mock(MemoryService.class), identity, followups);

    assertEquals(today.toString(), service.todayDate());
    service.getToday();
    verify(mapper).findByUserIdAndDate("u1", today);
    service.upsert(new CheckinCreateInput("today", null));
    org.mockito.ArgumentCaptor<CheckinEntity> inserted = org.mockito.ArgumentCaptor.forClass(CheckinEntity.class);
    verify(mapper).insert(inserted.capture());
    assertEquals(today, inserted.getValue().getCheckinDate());
  }
}
