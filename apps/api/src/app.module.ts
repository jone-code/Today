import { Module } from "@nestjs/common";
import { HealthController } from "./health.controller";
import { AiGatewayModule } from "./modules/ai-gateway/ai-gateway.module";
import { CheckinModule } from "./modules/checkin/checkin.module";
import { IdentityModule } from "./modules/identity/identity.module";
import { MemoryModule } from "./modules/memory/memory.module";
import { ProactiveModule } from "./modules/proactive/proactive.module";
import { SummaryModule } from "./modules/summary/summary.module";
import { TimelineModule } from "./modules/timeline/timeline.module";

@Module({
  imports: [
    IdentityModule,
    AiGatewayModule,
    CheckinModule,
    SummaryModule,
    MemoryModule,
    TimelineModule,
    ProactiveModule,
  ],
  controllers: [HealthController],
})
export class AppModule {}
