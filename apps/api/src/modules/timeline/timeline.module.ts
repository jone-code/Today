import { Module } from "@nestjs/common";
import { CheckinModule } from "../checkin/checkin.module";
import { SummaryModule } from "../summary/summary.module";
import { TimelineController } from "./timeline.controller";
import { TimelineService } from "./timeline.service";

@Module({
  imports: [CheckinModule, SummaryModule],
  controllers: [TimelineController],
  providers: [TimelineService],
})
export class TimelineModule {}
