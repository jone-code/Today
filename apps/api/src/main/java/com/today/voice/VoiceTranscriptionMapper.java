package com.today.voice;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VoiceTranscriptionMapper {
  VoiceTranscriptionJobEntity findByClientRequestId(String userId, String clientRequestId);
  VoiceTranscriptionJobEntity findOwnedById(String userId, String id);
  int insert(VoiceTranscriptionJobEntity job);
  int markRetrying(String userId, String id, Instant now);
  int updateOutcome(String userId, String id, String status, String transcript, String errorCode,
      String mediaPath, Instant completedAt, Instant updatedAt);
  int markExpired(Instant now);
  List<VoiceTranscriptionJobEntity> findExpiredWithMedia(Instant now, int limit);
  int clearMediaPath(String id);
}
