package com.today.checkin;

import com.today.summary.DaySummaryDto;

public record CheckinSubmitResult(CheckinDto checkin, DaySummaryDto summary) {}
