package com.today.checkin;

import com.today.persistence.CheckinMemoryCandidateEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CheckinMemoryCandidateMapper {
  int insertIfAbsent(CheckinMemoryCandidateEntity entity);
  CheckinMemoryCandidateEntity findOwned(@Param("id") String id, @Param("userId") String userId);
  List<CheckinMemoryCandidateEntity> listByCheckin(@Param("checkinId") String checkinId, @Param("userId") String userId);
  int invalidatePending(@Param("checkinId") String checkinId, @Param("userId") String userId);
  int updatePendingStatus(@Param("id") String id, @Param("userId") String userId,
      @Param("status") String status, @Param("confirmedAt") Instant confirmedAt,
      @Param("text") String text);
}
