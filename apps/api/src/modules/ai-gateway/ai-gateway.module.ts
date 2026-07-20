import { Global, Module } from "@nestjs/common";
import { AiGatewayService } from "./ai-gateway.service";

/** ai-gateway — 唯一允许接触模型 SDK 的模块 */
@Global()
@Module({
  providers: [AiGatewayService],
  exports: [AiGatewayService],
})
export class AiGatewayModule {}
