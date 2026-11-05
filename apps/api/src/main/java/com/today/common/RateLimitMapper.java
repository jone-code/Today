package com.today.common;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RateLimitMapper {
  int increment(@Param("scope") String scope, @Param("subjectHash") String subjectHash,
      @Param("windowStart") Instant windowStart, @Param("maxHits") int maxHits);
  int insertFirst(@Param("scope") String scope, @Param("subjectHash") String subjectHash,
      @Param("windowStart") Instant windowStart);
  int deleteOlderThan(@Param("before") Instant before);
}
