package com.today.privacy;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PrivacySettingsMapper {
  String lockUser(@Param("userId") String userId);
  String lockDeletionRequest(@Param("userId") String userId);
  PrivacySettingsEntity find(@Param("userId") String userId);
  int insert(PrivacySettingsEntity entity);
  int update(@Param("userId") String userId, @Param("chatMemoryEnabled") boolean chatMemoryEnabled,
      @Param("voiceRetention") String voiceRetention, @Param("followupEnabled") boolean followupEnabled,
      @Param("version") int version, @Param("updatedAt") Instant updatedAt);
}
