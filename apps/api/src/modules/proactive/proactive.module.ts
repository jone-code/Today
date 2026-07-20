import { Module } from "@nestjs/common";
import { CheckinModule } from "../checkin/checkin.module";
import { MemoryModule } from "../memory/memory.module";
import { ProactiveController } from "./proactive.controller";
import { ProactiveService } from "./proactive.service";

@Module({
  imports: [CheckinModule, MemoryModule],
  controllers: [ProactiveController],
  providers: [ProactiveService],
})
export class ProactiveModule {}
