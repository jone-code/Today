package com.today.privacy;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PrivacySettingsAuditMapper {
  int insert(@Param("id") String id, @Param("userId") String userId, @Param("field") String field,
      @Param("oldValue") String oldValue, @Param("newValue") String newValue,
      @Param("source") String source, @Param("occurredAt") Instant occurredAt);
}
