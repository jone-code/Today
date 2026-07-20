import { Body, Controller, Get, Inject, Post } from "@nestjs/common";
import {
  ApiRoutes,
  CheckinCreateInputSchema,
  type CheckinDto,
  type DaySummaryDto,
} from "@today/contracts";
import { MemoryService } from "../memory/memory.service";
import { SummaryService } from "../summary/summary.service";
import { CheckinService } from "./checkin.service";

type CheckinSubmitResult = {
  checkin: CheckinDto;
  summary: DaySummaryDto;
};

@Controller()
export class CheckinController {
  constructor(
    @Inject(CheckinService) private readonly checkins: CheckinService,
    @Inject(SummaryService) private readonly summaries: SummaryService,
    @Inject(MemoryService) private readonly memories: MemoryService,
  ) {}

  @Get(ApiRoutes.checkinToday.replace(/^\//, ""))
  today(): { checkin: CheckinDto | null } {
    return { checkin: this.checkins.getToday() };
  }

  @Post(ApiRoutes.checkins.replace(/^\//, ""))
  async create(@Body() body: unknown): Promise<CheckinSubmitResult> {
    const input = CheckinCreateInputSchema.parse(body);
    const checkin = this.checkins.upsert(input);
    const summary = await this.summaries.generateForCheckin(
      checkin.id,
      checkin.date,
      checkin.rawText,
    );
    await this.memories.upsertFromCheckin(checkin.rawText);
    return { checkin, summary };
  }
}
