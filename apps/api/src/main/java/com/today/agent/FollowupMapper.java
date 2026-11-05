package com.today.agent;

import com.today.persistence.FollowupPreferenceEntity;
import com.today.persistence.FollowupTopicEntity;
import com.today.persistence.InboxItemEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FollowupMapper {
  int insertTopic(FollowupTopicEntity entity);
  FollowupTopicEntity findTopic(@Param("id") String id, @Param("userId") String userId);
  FollowupTopicEntity lockTopic(@Param("id") String id, @Param("userId") String userId);
  String lockActiveConversationForTopic(@Param("id") String id, @Param("userId") String userId);
  List<FollowupTopicEntity> listUserTopics(@Param("userId") String userId, @Param("limit") int limit);
  List<FollowupTopicEntity> listDueTopics(@Param("now") Instant now);
  int updateTopic(FollowupTopicEntity entity);
  int pauseByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("at") Instant at);
  int stopTopic(@Param("id") String id, @Param("userId") String userId, @Param("at") Instant at);
  int resumeTopic(@Param("id") String id, @Param("userId") String userId,
      @Param("at") Instant at, @Param("eligibleAt") Instant eligibleAt);
  int resumeAutoPausedFromInbox(@Param("id") String id, @Param("userId") String userId,
      @Param("at") Instant at, @Param("eligibleAt") Instant eligibleAt);
  int acknowledgeSnoozedTopic(@Param("id") String id, @Param("userId") String userId,
      @Param("at") Instant at);
  int dismissTopicInbox(@Param("topicId") String topicId, @Param("userId") String userId);
  int redactInboxByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);
  FollowupPreferenceEntity findPreference(@Param("userId") String userId);
  int insertPreference(FollowupPreferenceEntity entity);
  int ensurePreference(@Param("userId") String userId, @Param("now") Instant now);
  FollowupPreferenceEntity lockPreference(@Param("userId") String userId);
  int updatePreference(FollowupPreferenceEntity entity);
  int countDeliveredSinceLocked(@Param("userId") String userId, @Param("since") Instant since);
  int insertInbox(InboxItemEntity entity);
  InboxItemEntity latestForTopic(@Param("userId") String userId, @Param("topicId") String topicId);
  InboxItemEntity findInbox(@Param("id") String id, @Param("userId") String userId);
  InboxItemEntity lockInbox(@Param("id") String id, @Param("userId") String userId);
  List<InboxItemEntity> listInbox(@Param("userId") String userId, @Param("limit") int limit);
  int countUnread(@Param("userId") String userId);
  int wakeSnoozed(@Param("userId") String userId, @Param("now") Instant now);
  int updateInboxState(@Param("id") String id, @Param("userId") String userId, @Param("state") String state, @Param("readAt") Instant readAt, @Param("snoozedUntil") Instant snoozedUntil);
}
