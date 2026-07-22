package com.today.reminder;

import com.today.persistence.ReminderDeliveryEntity;
import com.today.persistence.ReminderEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderMapper {

  ReminderEntity findById(@Param("id") String id);

  List<ReminderEntity> listByUserId(@Param("userId") String userId);

  List<ReminderEntity> listEnabled();

  int insert(ReminderEntity entity);

  int update(ReminderEntity entity);

  int deleteByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

  ReminderDeliveryEntity findDelivery(
      @Param("reminderId") String reminderId, @Param("fireDate") LocalDate fireDate);

  int insertDelivery(ReminderDeliveryEntity entity);

  List<ReminderDeliveryEntity> listDeliveriesByUserId(
      @Param("userId") String userId, @Param("limit") int limit);

  int markDeliveryRead(
      @Param("id") String id, @Param("userId") String userId, @Param("readAt") java.time.Instant readAt);
}
