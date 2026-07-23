package com.today.checkin;

import com.today.summary.DaySummaryDto;

/** status=processing 时 summary 可能为 null，前端应轮询 GET /v1/summaries/:date */
public record CheckinSubmitResult(CheckinDto checkin, DaySummaryDto summary, String status) {

  public static final String STATUS_PROCESSING = "processing";
  public static final String STATUS_READY = "ready";
}
