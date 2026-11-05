package com.today.checkin;

import com.today.common.EntityMapper;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.CheckinEntity;
import com.today.persistence.CheckinMemoryCandidateEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckinMemoryCandidateService {
  private final CheckinMapper checkins;
  private final CheckinMemoryCandidateMapper candidates;
  private final MemoryService memories;
  private final IdentityService identity;

  public CheckinMemoryCandidateService(
      CheckinMapper checkins, CheckinMemoryCandidateMapper candidates,
      MemoryService memories, IdentityService identity) {
    this.checkins = checkins;
    this.candidates = candidates;
    this.memories = memories;
    this.identity = identity;
  }

  @Transactional
  public void generate(String userId, String checkinId, String rawText) {
    List<MemoryService.MemoryCandidate> extracted = memories.extractCandidatesFromCheckin(rawText);
    CheckinEntity checkin = checkins.lockById(checkinId, userId);
    if (checkin == null || !checkin.getRawText().equals(rawText)) return;

    String sourceHash = hash(rawText);
    for (int i = 0; i < extracted.size(); i++) {
      var suggestion = extracted.get(i);
      String text = suggestion.text().trim();
      if (text.isEmpty() || text.length() > 512) continue;
      CheckinMemoryCandidateEntity entity = new CheckinMemoryCandidateEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setCheckinId(checkinId);
      entity.setSourceHash(sourceHash);
      entity.setOrdinalIndex(i);
      entity.setCategory(suggestion.category().name());
      entity.setMemoryText(text);
      entity.setStatus("pending");
      entity.setCreatedAt(EntityMapper.now());
      candidates.insertIfAbsent(entity);
    }
  }

  public List<CheckinMemoryCandidateDto> list(String checkinId) {
    String userId = identity.getCurrentUserId();
    CheckinEntity checkin = checkins.findById(checkinId);
    if (checkin == null || !userId.equals(checkin.getUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "checkin not found");
    }
    return candidates.listByCheckin(checkinId, userId).stream()
        .filter(e -> hash(checkin.getRawText()).equals(e.getSourceHash()))
        .map(this::toDto).toList();
  }

  @Transactional
  public CheckinMemoryCandidateDto confirm(String id, String editedText) {
    String userId = identity.getCurrentUserId();
    CheckinMemoryCandidateEntity candidate = requirePending(id, userId);
    String text = editedText == null ? candidate.getMemoryText() : editedText.trim();
    if (text.isEmpty() || text.length() > 512) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory text must be 1 to 512 characters");
    }
    CheckinEntity checkin = checkins.lockById(candidate.getCheckinId(), userId);
    if (checkin == null || !hash(checkin.getRawText()).equals(candidate.getSourceHash())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "checkin changed; review new suggestions");
    }
    if (candidates.updatePendingStatus(id, userId, "confirmed", EntityMapper.now(), text) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "candidate already handled");
    }
    memories.confirmFromProposal(userId, MemoryCategory.valueOf(candidate.getCategory()),
        text, "checkin", candidate.getCheckinId());
    return toDto(candidates.findOwned(id, userId));
  }

  @Transactional
  public CheckinMemoryCandidateDto reject(String id) {
    String userId = identity.getCurrentUserId();
    requirePending(id, userId);
    if (candidates.updatePendingStatus(id, userId, "rejected", null, null) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "candidate already handled");
    }
    return toDto(candidates.findOwned(id, userId));
  }

  private CheckinMemoryCandidateEntity requirePending(String id, String userId) {
    CheckinMemoryCandidateEntity candidate = candidates.findOwned(id, userId);
    if (candidate == null || !"pending".equals(candidate.getStatus())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "candidate not found or already handled");
    }
    return candidate;
  }

  private CheckinMemoryCandidateDto toDto(CheckinMemoryCandidateEntity e) {
    Instant confirmedAt = e.getConfirmedAt();
    return new CheckinMemoryCandidateDto(e.getId(), e.getCheckinId(), e.getCategory(),
        e.getMemoryText(), e.getStatus(), e.getCreatedAt().toString(),
        confirmedAt == null ? null : confirmedAt.toString());
  }

  private static String hash(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
