package com.today.punch;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PunchPhotoCleanupMapper {
  int enqueue(@Param("path") String path, @Param("userId") String userId, @Param("now") Instant now);
  List<PunchPhotoCleanupJob> ready(@Param("now") Instant now, @Param("limit") int limit);
  int claim(@Param("path") String path, @Param("now") Instant now,
      @Param("leaseUntil") Instant leaseUntil);
  int complete(@Param("path") String path, @Param("leaseUntil") Instant leaseUntil);
  int retry(@Param("path") String path, @Param("leaseUntil") Instant leaseUntil,
      @Param("nextAttemptAt") Instant nextAttemptAt);
}
