package com.today.today;

import com.today.checkin.CheckinAiJobDto;
import com.today.checkin.CheckinDto;
import com.today.summary.DaySummaryDto;

public record TodayHomeDto(
    CheckinDto checkin,
    CheckinAiJobDto aiJob,
    DaySummaryDto summary,
    int pendingMemoryCount,
    int pendingProposalCount,
    int unreadInboxCount,
    int openTodoCount,
    int habitsTotal,
    int habitsDone,
    int enabledReminderCount) {}
