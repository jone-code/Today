import { Controller, Get } from "@nestjs/common";
import { ApiRoutes } from "@today/contracts";

@Controller()
export class HealthController {
  @Get(ApiRoutes.health.replace(/^\//, ""))
  health() {
    return {
      ok: true,
      service: "today-api",
      modules: [
        "checkin",
        "summary",
        "memory",
        "timeline",
        "proactive",
        "ai-gateway",
        "identity",
      ],
    };
  }
}
