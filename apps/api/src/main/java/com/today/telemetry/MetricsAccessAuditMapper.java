package com.today.telemetry;

import java.time.LocalDate;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MetricsAccessAuditMapper {
  @Insert("INSERT INTO metrics_access_audit (id,endpoint,from_date,to_date,platform,app_version,event_schema_version,accessed_at) "
      + "VALUES (#{id},#{endpoint},#{from},#{to},#{platform},#{appVersion},#{eventSchemaVersion},#{accessedAt})")
  int insert(@Param("id") String id, @Param("endpoint") String endpoint, @Param("from") LocalDate from,
      @Param("to") LocalDate to, @Param("platform") String platform, @Param("appVersion") String appVersion,
      @Param("eventSchemaVersion") Integer eventSchemaVersion, @Param("accessedAt") Instant accessedAt);
}
