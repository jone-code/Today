package com.today.checkin;

import com.today.persistence.CheckinEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CheckinMapper {

  CheckinEntity findByUserIdAndDate(
      @Param("userId") String userId, @Param("checkinDate") LocalDate checkinDate);
  CheckinEntity lockByUserIdAndDate(
      @Param("userId") String userId, @Param("checkinDate") LocalDate checkinDate);

  CheckinEntity findById(@Param("id") String id);
  CheckinEntity findOwnedById(@Param("id") String id, @Param("userId") String userId);
  CheckinEntity lockById(@Param("id") String id, @Param("userId") String userId);

  List<CheckinEntity> listRecentByUserId(
      @Param("userId") String userId, @Param("limit") int limit);
  List<CheckinEntity> listByUserIdAndRange(@Param("userId") String userId,
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  int insert(CheckinEntity entity);

  int update(CheckinEntity entity);
}
