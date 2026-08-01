package com.today.punch;

import com.today.persistence.PunchHabitEntity;
import com.today.persistence.PunchLogEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PunchMapper {

  PunchHabitEntity findHabitById(@Param("id") String id);

  List<PunchHabitEntity> listHabitsByUserId(@Param("userId") String userId);

  int insertHabit(PunchHabitEntity entity);

  int updateHabit(PunchHabitEntity entity);

  int deleteHabit(@Param("id") String id, @Param("userId") String userId);

  PunchLogEntity findLog(
      @Param("habitId") String habitId, @Param("punchDate") LocalDate punchDate);

  List<PunchLogEntity> listLogsByHabit(
      @Param("habitId") String habitId, @Param("limit") int limit);

  List<PunchLogEntity> listLogsByUserAndDate(
      @Param("userId") String userId, @Param("punchDate") LocalDate punchDate);

  int insertLog(PunchLogEntity entity);

  int updateLog(PunchLogEntity entity);

  int deleteLog(
      @Param("habitId") String habitId,
      @Param("userId") String userId,
      @Param("punchDate") LocalDate punchDate);
}
