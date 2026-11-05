package com.today.agent;

import com.today.persistence.ConversationEntity;
import com.today.persistence.ConversationMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMapper {
  ConversationEntity findById(@Param("id") String id, @Param("userId") String userId);
  ConversationEntity findByClientRequestId(@Param("userId") String userId, @Param("clientRequestId") String clientRequestId);
  ConversationEntity lockById(@Param("id") String id, @Param("userId") String userId);
  List<ConversationEntity> listByUserId(@Param("userId") String userId, @Param("limit") int limit);
  int insert(ConversationEntity entity);
  int insertIdempotent(ConversationEntity entity);
  int update(ConversationEntity entity);
  int softDelete(@Param("id") String id, @Param("userId") String userId, @Param("deletedAt") java.time.Instant deletedAt);
  int softDeleteMessages(@Param("conversationId") String conversationId, @Param("userId") String userId, @Param("deletedAt") java.time.Instant deletedAt);
  ConversationMessageEntity findMessageByClientId(@Param("userId") String userId, @Param("clientMessageId") String clientMessageId);
  ConversationMessageEntity findMessageById(@Param("id") String id, @Param("userId") String userId, @Param("conversationId") String conversationId);
  ConversationMessageEntity findReply(@Param("userId") String userId, @Param("messageId") String messageId);
  ConversationMessageEntity findProcessing(@Param("conversationId") String conversationId, @Param("userId") String userId);
  List<String> listMessageIds(@Param("conversationId") String conversationId, @Param("userId") String userId);
  List<ConversationMessageEntity> listMessages(@Param("conversationId") String conversationId, @Param("userId") String userId, @Param("limit") int limit);
  List<ConversationMessageEntity> listMessagesBefore(@Param("conversationId") String conversationId,
      @Param("userId") String userId, @Param("before") java.time.Instant before,
      @Param("beforeId") String beforeId, @Param("limit") int limit);
  List<ConversationMessageEntity> listHistoryBefore(@Param("conversationId") String conversationId,
      @Param("userId") String userId, @Param("before") java.time.Instant before, @Param("limit") int limit);
  int insertMessage(ConversationMessageEntity entity);
  int updateMessageStatus(@Param("id") String id, @Param("userId") String userId, @Param("status") String status, @Param("attempt") int attempt);
  int retryFailedMessage(@Param("id") String id, @Param("userId") String userId, @Param("now") java.time.Instant now);
  int failStaleMessages(@Param("before") java.time.Instant before);
}
