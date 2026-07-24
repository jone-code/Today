package com.today.checkin;

import com.today.persistence.CheckinAiJobEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CheckinAiJobMapper {

  int upsert(CheckinAiJobEntity entity);

  CheckinAiJobEntity findByCheckinId(@Param("checkinId") String checkinId);

  CheckinAiJobEntity findByUserAndDate(
      @Param("userId") String userId, @Param("checkinDate") LocalDate checkinDate);

  List<CheckinAiJobEntity> listDue(
      @Param("now") Instant now, @Param("limit") int limit);

  int claim(
      @Param("id") String id,
      @Param("expectedStatus") String expectedStatus,
      @Param("runningStatus") String runningStatus,
      @Param("lockedAt") Instant lockedAt,
      @Param("updatedAt") Instant updatedAt);

  int markSucceeded(
      @Param("id") String id,
      @Param("status") String status,
      @Param("updatedAt") Instant updatedAt);

  int markFailed(
      @Param("id") String id,
      @Param("status") String status,
      @Param("attempts") int attempts,
      @Param("lastError") String lastError,
      @Param("nextRunAt") Instant nextRunAt,
      @Param("updatedAt") Instant updatedAt);

  int resetForRetry(
      @Param("id") String id,
      @Param("status") String status,
      @Param("nextRunAt") Instant nextRunAt,
      @Param("updatedAt") Instant updatedAt);
}
