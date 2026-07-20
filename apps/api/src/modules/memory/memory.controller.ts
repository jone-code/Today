import { Controller, Get, Inject } from "@nestjs/common";
import { ApiRoutes, type MemoryListDto } from "@today/contracts";
import { MemoryService } from "./memory.service";

@Controller()
export class MemoryController {
  constructor(@Inject(MemoryService) private readonly memories: MemoryService) {}

  @Get(ApiRoutes.memories.replace(/^\//, ""))
  list(): MemoryListDto {
    return { items: this.memories.list() };
  }
}
