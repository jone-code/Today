package com.today.checkin;

import com.today.summary.DaySummaryDto;

/**
 * status=processing 时 summary 可能为 null，前端应轮询 GET /v1/summaries/:date；
 * status=failed 时看 aiJob.lastError，可 POST reprocess。
 */
public record CheckinSubmitResult(
    CheckinDto checkin, DaySummaryDto summary, String status, CheckinAiJobDto aiJob) {

  public static final String STATUS_PROCESSING = "processing";
  public static final String STATUS_READY = "ready";
  public static final String STATUS_FAILED = "failed";

  public CheckinSubmitResult(CheckinDto checkin, DaySummaryDto summary, String status) {
    this(checkin, summary, status, null);
  }
}
