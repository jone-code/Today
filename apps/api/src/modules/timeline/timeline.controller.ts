import { Controller, Get, Inject, Query } from "@nestjs/common";
import { ApiRoutes, type TimelinePageDto } from "@today/contracts";
import { TimelineService } from "./timeline.service";

@Controller()
export class TimelineController {
  constructor(
    @Inject(TimelineService) private readonly timeline: TimelineService,
  ) {}

  @Get(ApiRoutes.timeline.replace(/^\//, ""))
  list(@Query("limit") limit?: string): TimelinePageDto {
    const n = limit ? Number(limit) : 30;
    return this.timeline.list(Number.isFinite(n) ? n : 30);
  }
}
