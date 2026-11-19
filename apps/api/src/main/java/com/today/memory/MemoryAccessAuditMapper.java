package com.today.memory;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryAccessAuditMapper {
  int insert(@Param("id") String id, @Param("userId") String userId, @Param("memoryId") String memoryId,
      @Param("action") String action, @Param("source") String source, @Param("requestId") String requestId,
      @Param("occurredAt") Instant occurredAt, @Param("metadataJson") String metadataJson);
  List<MemoryAccessAuditDto> list(@Param("userId") String userId, @Param("memoryId") String memoryId, @Param("limit") int limit);
}
