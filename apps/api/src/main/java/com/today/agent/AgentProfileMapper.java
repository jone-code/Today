package com.today.agent;

import com.today.persistence.AgentProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentProfileMapper {
  AgentProfileEntity findByUserId(@Param("userId") String userId);
  AgentProfileEntity lockByUserId(@Param("userId") String userId);
  int insert(AgentProfileEntity entity);
  int update(AgentProfileEntity entity);
}
