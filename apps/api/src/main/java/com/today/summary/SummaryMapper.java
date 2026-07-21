package com.today.summary;

import com.today.persistence.DaySummaryEntity;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SummaryMapper {

  DaySummaryEntity findByUserIdAndDate(
      @Param("userId") String userId, @Param("summaryDate") LocalDate summaryDate);

  int upsert(DaySummaryEntity entity);
}
