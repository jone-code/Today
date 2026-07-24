package com.today.checkin;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.persistence.CheckinEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinService {

  private final CheckinMapper checkinMapper;
  private final IdentityService identity;

  public CheckinService(CheckinMapper checkinMapper, IdentityService identity) {
    this.checkinMapper = checkinMapper;
    this.identity = identity;
  }

  public String todayDate() {
    return EntityMapper.todayUtc().toString();
  }

  public CheckinDto getToday() {
    CheckinEntity entity =
        checkinMapper.findByUserIdAndDate(identity.getCurrentUserId(), EntityMapper.todayUtc());
    return entity == null ? null : EntityMapper.toDto(entity);
  }

  @Transactional
  public CheckinDto upsert(CheckinCreateInput input) {
    String userId = identity.getCurrentUserId();
    LocalDate date =
        input.date() != null ? EntityMapper.parseDate(input.date()) : EntityMapper.todayUtc();
    Instant now = EntityMapper.now();

    CheckinEntity existing = checkinMapper.findByUserIdAndDate(userId, date);
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

    existing.setRawText(input.rawText().trim());
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
}
