import { Controller, Get, Inject } from "@nestjs/common";
import { ApiRoutes, type ProactiveTodayDto } from "@today/contracts";
import { ProactiveService } from "./proactive.service";

@Controller()
export class ProactiveController {
  constructor(
    @Inject(ProactiveService) private readonly proactive: ProactiveService,
  ) {}

  @Get(ApiRoutes.proactiveToday.replace(/^\//, ""))
  today(): Promise<ProactiveTodayDto> {
    return this.proactive.today();
  }
}
