package com.today.vector;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VectorSyncJobMapper {
  int enqueue(@Param("memoryId") String memoryId, @Param("userId") String userId,
      @Param("now") Instant now);
  List<VectorSyncJob> ready(@Param("now") Instant now, @Param("limit") int limit);
  int claim(@Param("memoryId") String memoryId, @Param("userId") String userId,
      @Param("version") long version, @Param("now") Instant now,
      @Param("leaseUntil") Instant leaseUntil);
  int complete(@Param("memoryId") String memoryId, @Param("userId") String userId,
      @Param("version") long version);
  int retry(@Param("memoryId") String memoryId, @Param("userId") String userId,
      @Param("version") long version, @Param("nextAttemptAt") Instant nextAttemptAt,
      @Param("errorCode") String errorCode);
}
