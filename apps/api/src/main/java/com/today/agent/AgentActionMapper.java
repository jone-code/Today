package com.today.agent;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentActionMapper {
  int insert(@Param("proposalId") String proposalId, @Param("userId") String userId,
      @Param("kind") String kind, @Param("targetId") String targetId,
      @Param("summary") String summary, @Param("executedAt") Instant executedAt);
  int attachUndoSnapshot(@Param("proposalId") String proposalId, @Param("userId") String userId,
      @Param("snapshot") String snapshot);
  AgentActionEntity lockOwned(@Param("proposalId") String proposalId, @Param("userId") String userId);
  int markUndone(@Param("proposalId") String proposalId, @Param("userId") String userId,
      @Param("undoneAt") Instant undoneAt);
  List<AgentActionEntity> list(@Param("userId") String userId, @Param("limit") int limit);
  int redactByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);
}
