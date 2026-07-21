package com.today.timeline;

import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.common.Mood;
import com.today.summary.DaySummaryDto;
import com.today.summary.SummaryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TimelineService {

  private final CheckinService checkins;
  private final SummaryService summaries;

  public TimelineService(CheckinService checkins, SummaryService summaries) {
    this.checkins = checkins;
    this.summaries = summaries;
  }

  public TimelinePageDto list(int limit) {
    List<TimelineItemDto> items =
        checkins.listRecent(limit).stream().map(this::toItem).toList();
    return new TimelinePageDto(items, null);
  }

  private TimelineItemDto toItem(CheckinDto checkin) {
    try {
      DaySummaryDto s = summaries.getByDate(checkin.date());
      return new TimelineItemDto(
          checkin.date(),
          s.highlight(),
          s.oneLiner(),
          s.mood(),
          s.moodLabel(),
          checkin.id());
    } catch (Exception e) {
      String raw = checkin.rawText();
      String highlight = raw.length() > 28 ? raw.substring(0, 28) : raw;
      if (highlight.isBlank()) {
        highlight = "留下了今天";
      }
      return new TimelineItemDto(
          checkin.date(), highlight, raw, Mood.okay, "平常", checkin.id());
    }
  }
}
