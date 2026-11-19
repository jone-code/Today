package com.today.review;

import com.today.persistence.WeeklyReviewEntity;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WeeklyReviewMapper {
  WeeklyReviewEntity find(@Param("userId") String userId, @Param("weekStart") LocalDate weekStart);
  int insert(WeeklyReviewEntity entity);
  int update(WeeklyReviewEntity entity);
}
