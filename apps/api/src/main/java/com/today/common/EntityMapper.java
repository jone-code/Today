package com.today.common;

import com.today.checkin.CheckinDto;
import com.today.memory.MemoryDto;
import com.today.persistence.CheckinEntity;
import com.today.persistence.DaySummaryEntity;
import com.today.persistence.MemoryEntity;
import com.today.summary.DaySummaryDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class EntityMapper {

  private EntityMapper() {}

  public static CheckinDto toDto(CheckinEntity entity) {
    return new CheckinDto(
        entity.getId(),
        entity.getUserId(),
        entity.getCheckinDate().toString(),
        entity.getRawText(),
        entity.getCreatedAt().toString(),
        entity.getUpdatedAt().toString());
  }

  public static DaySummaryDto toDto(DaySummaryEntity entity) {
    return new DaySummaryDto(
        entity.getCheckinId(),
        entity.getSummaryDate().toString(),
        JsonUtils.fromJsonList(entity.getCompletedJson()),
        Mood.valueOf(entity.getMood()),
        entity.getMoodLabel(),
        JsonUtils.fromJsonList(entity.getKeywordsJson()),
        entity.getOneLiner(),
        entity.getHighlight(),
        AiProvider.valueOf(entity.getProvider()),
        entity.getCreatedAt().toString());
  }

  public static MemoryDto toDto(MemoryEntity entity) {
    return new MemoryDto(
        entity.getId(),
        entity.getUserId(),
        MemoryCategory.valueOf(entity.getCategory()),
        entity.getMemoryText(),
        entity.getStrength(),
        entity.isArchived(),
        entity.getUpdatedAt().toString());
  }

  public static LocalDate parseDate(String date) {
    return LocalDate.parse(date);
  }

  public static Instant now() {
    return Instant.now();
  }

  public static LocalDate todayUtc() {
    // 产品日界按中国时区（与提醒默认 timezone 一致）
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }
}
