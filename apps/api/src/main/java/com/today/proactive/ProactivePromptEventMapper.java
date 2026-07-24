package com.today.proactive;

import com.today.persistence.ProactivePromptEventEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProactivePromptEventMapper {

  int upsert(ProactivePromptEventEntity entity);

  int updateStatus(
      @Param("userId") String userId,
      @Param("fingerprint") String fingerprint,
      @Param("status") String status,
      @Param("updatedAt") java.time.Instant updatedAt);

  int updateStatusByPromptIdAndDate(
      @Param("userId") String userId,
      @Param("promptId") String promptId,
      @Param("promptDate") LocalDate promptDate,
      @Param("status") String status,
      @Param("updatedAt") java.time.Instant updatedAt);

  List<ProactivePromptEventEntity> listByUserSince(
      @Param("userId") String userId, @Param("since") LocalDate since);

  List<ProactivePromptEventEntity> listOpenFollowups(@Param("userId") String userId);

  ProactivePromptEventEntity findByUserFingerprintDate(
      @Param("userId") String userId,
      @Param("fingerprint") String fingerprint,
      @Param("promptDate") LocalDate promptDate);
}
