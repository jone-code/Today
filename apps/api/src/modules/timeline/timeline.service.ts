import { Inject, Injectable } from "@nestjs/common";
import type { TimelinePageDto } from "@today/contracts";
import { CheckinService } from "../checkin/checkin.service";
import { SummaryService } from "../summary/summary.service";

/** timeline — 只读聚合，不写业务数据 */
@Injectable()
export class TimelineService {
  constructor(
    @Inject(CheckinService) private readonly checkins: CheckinService,
    @Inject(SummaryService) private readonly summaries: SummaryService,
  ) {}

  list(limit = 30): TimelinePageDto {
    const items = this.checkins.listRecent(limit).map((c) => {
      try {
        const s = this.summaries.getByDate(c.date);
        return {
          date: c.date,
          highlight: s.highlight,
          oneLiner: s.oneLiner,
          mood: s.mood,
          moodLabel: s.moodLabel,
          checkinId: c.id,
        };
      } catch {
        return {
          date: c.date,
          highlight: c.rawText.slice(0, 28) || "留下了今天",
          oneLiner: c.rawText,
          mood: "okay" as const,
          moodLabel: "平常",
          checkinId: c.id,
        };
      }
    });

    return { items, nextCursor: null };
  }
}
