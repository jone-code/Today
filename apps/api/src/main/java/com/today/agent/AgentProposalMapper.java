package com.today.agent;

import com.today.persistence.AgentProposalEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentProposalMapper {
  int insert(AgentProposalEntity entity);
  AgentProposalEntity findOwned(@Param("id") String id, @Param("userId") String userId);
  String lockSourceConversation(@Param("id") String id, @Param("userId") String userId);
  List<AgentProposalEntity> listPending(@Param("userId") String userId);
  List<AgentProposalEntity> listBySourceMessage(@Param("sourceMessageId") String sourceMessageId, @Param("userId") String userId);
  boolean hasRejectedFollowupReason(@Param("userId") String userId, @Param("conversationId") String conversationId,
      @Param("reason") String reason);
  int rejectPendingKindByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId,
      @Param("kind") String kind, @Param("at") Instant at);
  int updateStatus(@Param("id") String id, @Param("userId") String userId, @Param("status") String status, @Param("confirmedAt") Instant confirmedAt);
  int updatePayload(@Param("id") String id, @Param("userId") String userId, @Param("payloadJson") String payloadJson);
}
