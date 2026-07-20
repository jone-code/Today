import { Inject, Injectable, NotFoundException } from "@nestjs/common";
import type { DaySummaryDto, Mood } from "@today/contracts";
import { AiGatewayService } from "../ai-gateway/ai-gateway.service";

@Injectable()
export class SummaryService {
  private readonly store = new Map<string, DaySummaryDto>();

  constructor(@Inject(AiGatewayService) private readonly ai: AiGatewayService) {}

  async generateForCheckin(checkinId: string, date: string, rawText: string) {
    const result = await this.ai.complete(
      "summary",
      { rawText },
      () => heuristicSummary(rawText),
    );

    const dto: DaySummaryDto = {
      checkinId,
      date,
      ...result.data,
      provider: result.provider,
      createdAt: new Date().toISOString(),
    };
    this.store.set(date, dto);
    return dto;
  }

  getByDate(date: string): DaySummaryDto {
    const found = this.store.get(date);
    if (!found) throw new NotFoundException(`summary not found: ${date}`);
    return found;
  }
}

function heuristicSummary(rawText: string): Omit<
  DaySummaryDto,
  "checkinId" | "date" | "provider" | "createdAt"
> {
  const text = rawText.trim();
  const mood: Mood = /累|疲惫|加班/.test(text)
    ? "tired"
    : /开心|顺利|收获/.test(text)
      ? "great"
      : "okay";
  const moodLabel =
    mood === "tired" ? "疲惫" : mood === "great" ? "很好" : "平常";
  const keywords = ["工作", "学习", "家庭", "运动", "创业", "面试"].filter(
    (k) => text.includes(k),
  );
  const parts = text
    .split(/[。！？!?\n；;]+/)
    .map((s) => s.trim())
    .filter((s) => s.length >= 4);
  const completed = parts.slice(0, 2);
  const highlight = completed[0] ?? "留下了今天";
  return {
    completed: completed.length ? completed : ["记录了今天的片刻"],
    mood,
    moodLabel,
    keywords: keywords.length ? keywords.slice(0, 4) : ["日常"],
    oneLiner: `今天感觉${moodLabel}，已被认真记住。`,
    highlight,
  };
}
