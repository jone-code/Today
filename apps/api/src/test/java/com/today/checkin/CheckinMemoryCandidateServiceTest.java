package com.today.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.CheckinEntity;
import com.today.persistence.CheckinMemoryCandidateEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class CheckinMemoryCandidateServiceTest {
  private final CheckinMapper checkins = mock(CheckinMapper.class);
  private final CheckinMemoryCandidateMapper candidates = mock(CheckinMemoryCandidateMapper.class);
  private final MemoryService memories = mock(MemoryService.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final CheckinMemoryCandidateService service =
      new CheckinMemoryCandidateService(checkins, candidates, memories, identity);

  @BeforeEach
  void user() { when(identity.getCurrentUserId()).thenReturn("u1"); }

  @Test
  void staleJobCannotCreateCandidates() {
    when(memories.extractCandidatesFromCheckin("old"))
        .thenReturn(List.of(new MemoryService.MemoryCandidate(MemoryCategory.life, "old memory")));
    when(checkins.lockById("c1", "u1")).thenReturn(checkin("new"));

    service.generate("u1", "c1", "old");

    verifyNoInteractions(candidates);
    verify(memories, never()).confirmFromProposal(any(), any(), any(), any(), any());
  }

  @Test
  void generationOnlyInsertsPendingCandidate() throws Exception {
    when(memories.extractCandidatesFromCheckin("today"))
        .thenReturn(List.of(new MemoryService.MemoryCandidate(MemoryCategory.health, "running")));
    when(checkins.lockById("c1", "u1")).thenReturn(checkin("today"));

    service.generate("u1", "c1", "today");

    ArgumentCaptor<CheckinMemoryCandidateEntity> saved = ArgumentCaptor.forClass(CheckinMemoryCandidateEntity.class);
    verify(candidates).insertIfAbsent(saved.capture());
    assertEquals("pending", saved.getValue().getStatus());
    assertEquals("c1", saved.getValue().getCheckinId());
    assertEquals(hash("today"), saved.getValue().getSourceHash());
    verify(memories, never()).confirmFromProposal(any(), any(), any(), any(), any());
  }

  @Test
  void generationSkipsBlankCandidate() {
    when(memories.extractCandidatesFromCheckin("today"))
        .thenReturn(List.of(new MemoryService.MemoryCandidate(MemoryCategory.life, "  ")));
    when(checkins.lockById("c1", "u1")).thenReturn(checkin("today"));

    service.generate("u1", "c1", "today");

    verifyNoInteractions(candidates);
  }

  @Test
  void confirmationRejectsChangedCheckin() throws Exception {
    when(candidates.findOwned("p1", "u1")).thenReturn(candidate(hash("old")));
    when(checkins.lockById("c1", "u1")).thenReturn(checkin("new"));

    assertThrows(ResponseStatusException.class, () -> service.confirm("p1", null));

    verify(candidates, never()).updatePendingStatus(any(), any(), any(), any(), any());
    verify(memories, never()).confirmFromProposal(any(), any(), any(), any(), any());
  }

  @Test
  void confirmationPersistsMemoryOnlyAfterStatusTransition() throws Exception {
    CheckinMemoryCandidateEntity pending = candidate(hash("today"));
    CheckinMemoryCandidateEntity confirmed = candidate(hash("today"));
    confirmed.setStatus("confirmed");
    confirmed.setCreatedAt(java.time.Instant.now());
    when(candidates.findOwned("p1", "u1")).thenReturn(pending, confirmed);
    when(checkins.lockById("c1", "u1")).thenReturn(checkin("today"));
    when(candidates.updatePendingStatus(org.mockito.ArgumentMatchers.eq("p1"),
        org.mockito.ArgumentMatchers.eq("u1"), org.mockito.ArgumentMatchers.eq("confirmed"), any(),
        org.mockito.ArgumentMatchers.eq("old memory")))
        .thenReturn(1);

    assertEquals("confirmed", service.confirm("p1", null).status());

    var ordered = org.mockito.Mockito.inOrder(candidates, memories);
    ordered.verify(candidates).updatePendingStatus(org.mockito.ArgumentMatchers.eq("p1"),
        org.mockito.ArgumentMatchers.eq("u1"), org.mockito.ArgumentMatchers.eq("confirmed"), any(),
        org.mockito.ArgumentMatchers.eq("old memory"));
    ordered.verify(memories).confirmFromProposal("u1", MemoryCategory.life,
        "old memory", "checkin", "c1");
  }

  @Test
  void editedConfirmationPersistsOnlyReviewedText() throws Exception {
    CheckinMemoryCandidateEntity pending = candidate(hash("today"));
    CheckinMemoryCandidateEntity confirmed = candidate(hash("today"));
    confirmed.setMemoryText("corrected memory");
    confirmed.setStatus("confirmed");
    confirmed.setCreatedAt(java.time.Instant.now());
    when(candidates.findOwned("p1", "u1")).thenReturn(pending, confirmed);
    when(checkins.lockById("c1", "u1")).thenReturn(checkin("today"));
    when(candidates.updatePendingStatus(org.mockito.ArgumentMatchers.eq("p1"),
        org.mockito.ArgumentMatchers.eq("u1"), org.mockito.ArgumentMatchers.eq("confirmed"), any(),
        org.mockito.ArgumentMatchers.eq("corrected memory"))).thenReturn(1);

    assertEquals("corrected memory", service.confirm("p1", "  corrected memory  ").text());
    verify(memories).confirmFromProposal("u1", MemoryCategory.life, "corrected memory", "checkin", "c1");
  }

  @Test
  void emptyEditCannotConfirmCandidate() throws Exception {
    when(candidates.findOwned("p1", "u1")).thenReturn(candidate(hash("today")));

    assertThrows(ResponseStatusException.class, () -> service.confirm("p1", "  "));

    verify(candidates, never()).updatePendingStatus(any(), any(), any(), any(), any());
    verifyNoInteractions(checkins, memories);
  }

  private static CheckinEntity checkin(String text) {
    CheckinEntity entity = new CheckinEntity();
    entity.setId("c1"); entity.setUserId("u1"); entity.setRawText(text);
    return entity;
  }

  private static CheckinMemoryCandidateEntity candidate(String sourceHash) {
    CheckinMemoryCandidateEntity entity = new CheckinMemoryCandidateEntity();
    entity.setId("p1"); entity.setCheckinId("c1"); entity.setSourceHash(sourceHash);
    entity.setStatus("pending"); entity.setCategory("life"); entity.setMemoryText("old memory");
    return entity;
  }

  private static String hash(String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(text.getBytes(StandardCharsets.UTF_8)));
  }
}
