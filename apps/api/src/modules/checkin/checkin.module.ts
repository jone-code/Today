import { Module } from "@nestjs/common";
import { MemoryModule } from "../memory/memory.module";
import { SummaryModule } from "../summary/summary.module";
import { CheckinController } from "./checkin.controller";
import { CheckinService } from "./checkin.service";

@Module({
  imports: [SummaryModule, MemoryModule],
  controllers: [CheckinController],
  providers: [CheckinService],
  exports: [CheckinService],
})
export class CheckinModule {}
