import { Global, Module } from "@nestjs/common";
import { IdentityService } from "./identity.service";

/** identity — 二期完整鉴权；MVP 提供固定开发用户 */
@Global()
@Module({
  providers: [IdentityService],
  exports: [IdentityService],
})
export class IdentityModule {}
