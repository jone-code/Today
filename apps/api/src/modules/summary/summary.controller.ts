import { Controller, Get, Inject, Param } from "@nestjs/common";
import type { DaySummaryDto } from "@today/contracts";
import { SummaryService } from "./summary.service";

@Controller("v1/summaries")
export class SummaryController {
  constructor(
    @Inject(SummaryService) private readonly summaries: SummaryService,
  ) {}

  @Get(":date")
  getByDate(@Param("date") date: string): DaySummaryDto {
    return this.summaries.getByDate(date);
  }
}
