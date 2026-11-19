package com.today.telemetry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MetricsMapper {
  @Select("""
      SELECT event_name AS eventName, platform, app_version AS appVersion,
             event_schema_version AS eventSchemaVersion, COUNT(*) AS eventCount,
             COUNT(DISTINCT user_id) AS userCount
      FROM product_events
      WHERE occurred_at >= #{from} AND occurred_at < #{until}
        AND (#{platform} IS NULL OR platform = #{platform})
        AND (#{appVersion} IS NULL OR app_version = #{appVersion})
        AND (#{eventSchemaVersion} IS NULL OR event_schema_version = #{eventSchemaVersion})
      GROUP BY event_name, platform, app_version, event_schema_version
      ORDER BY eventCount DESC, eventName, platform, appVersion
      """)
  List<Map<String, Object>> eventCounts(@Param("from") LocalDate from, @Param("until") LocalDate until,
      @Param("platform") String platform, @Param("appVersion") String appVersion,
      @Param("eventSchemaVersion") Integer eventSchemaVersion);

  @Select("""
      SELECT COUNT(*) AS registeredUsers,
        COALESCE(SUM(CASE WHEN EXISTS (
          SELECT 1 FROM checkins c
          WHERE c.user_id = u.id AND c.created_at >= u.created_at AND c.created_at < #{until}
        ) THEN 1 ELSE 0 END), 0) AS activatedUsers,
        COALESCE(SUM(CASE WHEN EXISTS (
          SELECT 1 FROM conversations c
          WHERE c.user_id = u.id AND c.created_at >= u.created_at AND c.created_at < #{until}
        ) THEN 1 ELSE 0 END), 0) AS chatUsers,
        COALESCE(SUM(CASE WHEN EXISTS (
          SELECT 1 FROM day_summaries s
          WHERE s.user_id = u.id AND s.created_at >= u.created_at AND s.created_at < #{until}
        ) OR EXISTS (
          SELECT 1 FROM agent_proposals p
          WHERE p.user_id = u.id AND p.status = 'confirmed'
            AND p.confirmed_at >= u.created_at AND p.confirmed_at < #{until}
        ) THEN 1 ELSE 0 END), 0) AS valueUsers
        ,COALESCE(SUM(CASE WHEN (
          SELECT COUNT(*) FROM checkins c3
          WHERE c3.user_id = u.id AND c3.created_at >= u.created_at AND c3.created_at < #{until}
        ) >= 3 AND (EXISTS (
          SELECT 1 FROM day_summaries s3
          WHERE s3.user_id = u.id AND s3.created_at >= u.created_at AND s3.created_at < #{until}
        ) OR EXISTS (
          SELECT 1 FROM agent_proposals p3
          WHERE p3.user_id = u.id AND p3.status = 'confirmed'
            AND p3.confirmed_at >= u.created_at AND p3.confirmed_at < #{until}
        )) THEN 1 ELSE 0 END), 0) AS northStarUsers
        ,(SELECT COUNT(*) FROM checkins c4
          WHERE c4.created_at >= #{from} AND c4.created_at < #{until}) AS checkinsSaved
        ,(SELECT COUNT(*) FROM product_events e4
          WHERE e4.event_name = 'checkin_saved' AND e4.occurred_at >= #{from} AND e4.occurred_at < #{until}) AS checkinSavedEvents
        ,(SELECT COUNT(DISTINCT c5.user_id) FROM checkins c5
          WHERE c5.created_at >= #{from} AND c5.created_at < #{until}
            AND NOT EXISTS (
              SELECT 1 FROM product_events e5
              WHERE e5.user_id = c5.user_id AND e5.event_name = 'checkin_saved'
                AND e5.occurred_at >= #{from} AND e5.occurred_at < #{until}
            )) AS checkinUsersMissingSavedEvent
        ,(SELECT COUNT(DISTINCT e6.user_id) FROM product_events e6
          WHERE e6.event_name = 'checkin_saved' AND e6.occurred_at >= #{from} AND e6.occurred_at < #{until}
            AND NOT EXISTS (
              SELECT 1 FROM checkins c6
              WHERE c6.user_id = e6.user_id AND c6.created_at >= #{from} AND c6.created_at < #{until}
            )) AS savedEventUsersMissingCheckin
      FROM users u
      WHERE u.created_at >= #{from} AND u.created_at < #{until}
      """)
  Map<String, Object> activation(@Param("from") LocalDate from, @Param("until") LocalDate until);

  @Select("SELECT type,status,COUNT(*) AS taskCount FROM data_requests WHERE requested_at>=#{from} AND requested_at<#{until} GROUP BY type,status")
  List<Map<String,Object>> dataQuality(@Param("from") LocalDate from, @Param("until") LocalDate until);

  @Select("SELECT status,error_code AS errorCode,COUNT(*) AS taskCount FROM voice_transcription_jobs WHERE created_at>=#{from} AND created_at<#{until} GROUP BY status,error_code")
  List<Map<String,Object>> voiceQuality(@Param("from") LocalDate from, @Param("until") LocalDate until);

  @Select("SELECT kind, COUNT(*) AS feedbackCount, COUNT(DISTINCT user_id) AS userCount FROM feedback_reports WHERE created_at>=#{from} AND created_at<#{until} AND deleted_at IS NULL GROUP BY kind ORDER BY feedbackCount DESC, kind")
  List<Map<String,Object>> feedbackQuality(@Param("from") LocalDate from, @Param("until") LocalDate until);

  @Select("""
      SELECT COUNT(DISTINCT CASE WHEN e.event_name IN
          ('today_viewed','checkin_saved','summary_viewed','conversation_started','message_sent',
           'proposal_confirmed','action_completed','inbox_viewed','weekly_review_viewed')
          AND e.occurred_at >= u.created_at + INTERVAL 1 DAY
          AND e.occurred_at < u.created_at + INTERVAL 2 DAY THEN u.id END) AS d1,
        COUNT(DISTINCT CASE WHEN e.event_name IN
          ('today_viewed','checkin_saved','summary_viewed','conversation_started','message_sent',
           'proposal_confirmed','action_completed','inbox_viewed','weekly_review_viewed')
          AND e.occurred_at >= u.created_at + INTERVAL 3 DAY
          AND e.occurred_at < u.created_at + INTERVAL 4 DAY THEN u.id END) AS d3,
        COUNT(DISTINCT CASE WHEN e.event_name IN
          ('today_viewed','checkin_saved','summary_viewed','conversation_started','message_sent',
           'proposal_confirmed','action_completed','inbox_viewed','weekly_review_viewed')
          AND e.occurred_at >= u.created_at + INTERVAL 7 DAY
          AND e.occurred_at < u.created_at + INTERVAL 8 DAY THEN u.id END) AS d7
      FROM users u LEFT JOIN product_events e ON e.user_id = u.id
      WHERE u.created_at >= #{from} AND u.created_at < #{until}
      """)
  Map<String, Object> retention(@Param("from") LocalDate from, @Param("until") LocalDate until);
}
