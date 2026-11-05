package com.today.checkin;

import com.today.agent.FollowupMapper;
import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.persistence.CheckinEntity;
import com.today.persistence.FollowupPreferenceEntity;
import com.today.summary.SummaryMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckinService {

  private final CheckinMapper checkinMapper;
  private final CheckinMemoryCandidateMapper candidates;
  private final SummaryMapper summaries;
  private final MemoryService memories;
  private final IdentityService identity;
  private final FollowupMapper followups;

  public CheckinService(CheckinMapper checkinMapper, CheckinMemoryCandidateMapper candidates,
      SummaryMapper summaries, MemoryService memories, IdentityService identity, FollowupMapper followups) {
    this.checkinMapper = checkinMapper;
    this.candidates = candidates;
    this.summaries = summaries;
    this.memories = memories;
    this.identity = identity;
    this.followups = followups;
  }

  public String todayDate() {
    FollowupPreferenceEntity preference = followups.findPreference(identity.getCurrentUserId());
    try {
      return LocalDate.now(ZoneId.of(preference == null ? "Asia/Shanghai" : preference.getTimezone())).toString();
    } catch (DateTimeException e) {
      return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    }
  }

  public CheckinDto getToday() {
    CheckinEntity entity =
        checkinMapper.findByUserIdAndDate(identity.getCurrentUserId(), LocalDate.parse(todayDate()));
    return entity == null ? null : EntityMapper.toDto(entity);
  }

  public CheckinDto getByDate(LocalDate date) {
    CheckinEntity entity = checkinMapper.findByUserIdAndDate(identity.getCurrentUserId(), date);
    return entity == null ? null : EntityMapper.toDto(entity);
  }

  public CheckinDto getById(String id) {
    CheckinEntity entity = checkinMapper.findOwnedById(id, identity.getCurrentUserId());
    if (entity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "checkin not found");
    }
    return EntityMapper.toDto(entity);
  }

  @Transactional
  public CheckinDto upsertFromAgent(String userId, LocalDate date, String rawText,
      String expectedUpdatedAt, String expectedRawText) {
    if (!userId.equals(identity.getCurrentUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "checkin not found");
    }
    CheckinEntity current = checkinMapper.lockByUserIdAndDate(userId, date);
    if (current == null ? expectedUpdatedAt != null
        : !current.getUpdatedAt().toString().equals(expectedUpdatedAt)
            || !current.getRawText().equals(expectedRawText)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "today record changed; review the updated text");
    }
    if (rawText == null || rawText.isBlank() || rawText.length() > 2000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "record text must be 1-2000 characters");
    }
    try {
      return upsert(new CheckinCreateInput(rawText, date.toString()));
    } catch (DuplicateKeyException conflict) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "today record changed; review the updated text");
    }
  }

  @Transactional
  public CheckinDto upsert(CheckinCreateInput input) {
    String userId = identity.getCurrentUserId();
    LocalDate date =
        input.date() != null ? EntityMapper.parseDate(input.date()) : LocalDate.parse(todayDate());
    Instant now = EntityMapper.now();

    CheckinEntity existing = checkinMapper.lockByUserIdAndDate(userId, date);
    if (existing == null) {
      CheckinEntity created = new CheckinEntity();
      created.setId(UUID.randomUUID().toString());
      created.setUserId(userId);
      created.setCheckinDate(date);
      created.setRawText(input.rawText().trim());
      created.setCreatedAt(now);
      created.setUpdatedAt(now);
      checkinMapper.insert(created);
      return EntityMapper.toDto(created);
    }

    String newText = input.rawText().trim();
    if (!existing.getRawText().equals(newText)) {
      candidates.invalidatePending(existing.getId(), userId);
      memories.deactivateCheckinSource(userId, existing.getId());
      summaries.deleteByCheckinId(existing.getId(), userId);
    }
    existing.setRawText(newText);
    existing.setUpdatedAt(now);
    checkinMapper.update(existing);
    return EntityMapper.toDto(existing);
  }

  public List<CheckinDto> listRecent(int limit) {
    return checkinMapper.listRecentByUserId(identity.getCurrentUserId(), limit).stream()
        .map(EntityMapper::toDto)
        .toList();
  }

  /** 流水线内部按 id 读取（不校验当前用户，调用方已持有 job） */
  public CheckinDto findByIdForPipeline(String checkinId) {
    CheckinEntity entity = checkinMapper.findById(checkinId);
    return entity == null ? null : EntityMapper.toDto(entity);
  }

  public CheckinDto lockByIdForPipeline(String checkinId, String userId) {
    CheckinEntity entity = checkinMapper.lockById(checkinId, userId);
    return entity == null ? null : EntityMapper.toDto(entity);
  }
}
