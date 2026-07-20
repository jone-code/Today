import { Inject, Injectable } from "@nestjs/common";
import type { MemoryCategory, MemoryDto } from "@today/contracts";
import { AiGatewayService } from "../ai-gateway/ai-gateway.service";
import { IdentityService } from "../identity/identity.service";

@Injectable()
export class MemoryService {
  private readonly store = new Map<string, MemoryDto>();

  constructor(
    @Inject(AiGatewayService) private readonly ai: AiGatewayService,
    @Inject(IdentityService) private readonly identity: IdentityService,
  ) {}

  list(): MemoryDto[] {
    const userId = this.identity.getCurrentUserId();
    return [...this.store.values()]
      .filter((m) => m.userId === userId)
      .sort(
        (a, b) =>
          b.strength - a.strength || b.updatedAt.localeCompare(a.updatedAt),
      );
  }

  async upsertFromCheckin(rawText: string): Promise<MemoryDto[]> {
    const result = await this.ai.complete(
      "memory_extract",
      { rawText },
      () => heuristicExtract(rawText),
    );

    const userId = this.identity.getCurrentUserId();
    const now = new Date().toISOString();

    for (const item of result.data) {
      const id = `${userId}:${item.category}:${item.text}`;
      const existing = this.store.get(id);
      this.store.set(id, {
        id,
        userId,
        category: item.category,
        text: item.text,
        strength: (existing?.strength ?? 0) + 1,
        updatedAt: now,
      });
    }

    return this.list();
  }
}

function heuristicExtract(
  rawText: string,
): Array<{ category: MemoryCategory; text: string }> {
  const out: Array<{ category: MemoryCategory; text: string }> = [];
  if (/压力|加班|上线/.test(rawText)) {
    out.push({ category: "work", text: "最近工作压力偏高" });
  }
  if (/运动|跑步|健身/.test(rawText)) {
    out.push({ category: "health", text: "正在坚持运动" });
  }
  if (/学习|AI|课程/.test(rawText)) {
    out.push({ category: "learning", text: "正在持续学习" });
  }
  if (/创业|产品|Demo|MVP/.test(rawText)) {
    out.push({ category: "goal", text: "正在推进创业 / 产品" });
  }
  if (/面试/.test(rawText)) {
    out.push({ category: "goal", text: "近期有面试相关安排" });
  }
  if (/累|疲惫/.test(rawText)) {
    out.push({ category: "emotion", text: "近期多次提到疲惫" });
  }
  return out;
}
