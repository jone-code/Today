package com.today.agent;

import com.today.persistence.AgentMediaEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMediaMapper {
  AgentMediaEntity findById(@Param("id") String id, @Param("userId") String userId);
  int insert(AgentMediaEntity entity);
  int markReplaced(@Param("id") String id, @Param("userId") String userId, @Param("at") java.time.Instant at);
  List<AgentMediaEntity> listReplaced(@Param("limit") int limit);
  int markPurged(@Param("id") String id, @Param("userId") String userId);
  int deferCleanup(@Param("id") String id, @Param("userId") String userId);
}
