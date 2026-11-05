package com.today.agent;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppointmentMapper {
  int insert(AppointmentEntity entity);
  AppointmentEntity findOwned(@Param("id") String id, @Param("userId") String userId);
  AppointmentEntity lockOwned(@Param("id") String id, @Param("userId") String userId);
  List<AppointmentEntity> listUpcoming(@Param("userId") String userId);
  List<AppointmentEntity> listDue(@Param("now") Instant now);
  int markDelivered(@Param("id") String id, @Param("userId") String userId, @Param("now") Instant now);
  int cancel(@Param("id") String id, @Param("userId") String userId);
  int cancelByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);
  int redactInboxByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);
}
