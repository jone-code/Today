package com.today.checkin;

public record CheckinTodayResponse(CheckinDto checkin, CheckinAiJobDto aiJob) {
  public CheckinTodayResponse(CheckinDto checkin) {
    this(checkin, null);
  }
}
