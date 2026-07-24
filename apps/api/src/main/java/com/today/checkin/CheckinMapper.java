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

  CheckinEntity findById(@Param("id") String id);

  List<CheckinEntity> listRecentByUserId(
      @Param("userId") String userId, @Param("limit") int limit);

  int insert(CheckinEntity entity);

  int update(CheckinEntity entity);
}
