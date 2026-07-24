package com.today.aigateway;

import com.today.persistence.AiCallLogEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiCallLogMapper {

  int insert(AiCallLogEntity entity);

  List<AiCallLogEntity> listRecent(@Param("limit") int limit);

  List<Map<String, Object>> aggregateSince(@Param("since") Instant since);

  Double avgElapsedSince(
      @Param("since") Instant since, @Param("outcome") String outcome);

  int deleteOlderThan(@Param("before") Instant before);
}
