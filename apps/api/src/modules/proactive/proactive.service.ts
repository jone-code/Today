import { Inject, Injectable } from "@nestjs/common";
import type { ProactivePromptDto, ProactiveTodayDto } from "@today/contracts";
import { AiGatewayService } from "../ai-gateway/ai-gateway.service";
import { CheckinService } from "../checkin/checkin.service";
import { MemoryService } from "../memory/memory.service";

@Injectable()
export class ProactiveService {
  constructor(
    @Inject(AiGatewayService) private readonly ai: AiGatewayService,
    @Inject(CheckinService) private readonly checkins: CheckinService,
    @Inject(MemoryService) private readonly memories: MemoryService,
  ) {}

  async today(): Promise<ProactiveTodayDto> {
    const date = this.checkins.todayDate();
    const recent = this.checkins.listRecent(14);
    const memories = this.memories.list();

    const result = await this.ai.complete(
      "proactive",
      { recent, memories },
      () => heuristicPrompts(date, recent, memories),
    );

    return {
      date,
      prompts: result.data.slice(0, 3),
      provider: result.provider,
    };
  }
}

function heuristicPrompts(
  today: string,
  recent: Array<{ date: string; rawText: string }>,
  memories: Array<{ id: string; text: string; strength: number }>,
): ProactivePromptDto[] {
  const prompts: ProactivePromptDto[] = [];
  const yesterdayDate = new Date(`${today}T12:00:00.000Z`);
  yesterdayDate.setUTCDate(yesterdayDate.getUTCDate() - 1);
  const yesterday = yesterdayDate.toISOString().slice(0, 10);
  const y = recent.find((e) => e.date === yesterday);

  if (y && /面试/.test(y.rawText)) {
    prompts.push({
      id: "followup-interview",
      text: "昨天提到今天有面试，结果怎么样？",
      relatedDate: yesterday,
      source: "followup",
    });
  } else if (y && /明天|准备/.test(y.rawText)) {
    prompts.push({
      id: "followup-plan",
      text: "昨天你提到了今天的计划，现在进展如何？",
      relatedDate: yesterday,
      source: "followup",
    });
  }

  const tiredCount = recent.filter((e) =>
    /很累|疲惫|累/.test(e.rawText),
  ).length;
  if (tiredCount >= 3) {
    prompts.push({
      id: "pattern-tired",
      text: `最近两周你已经有 ${tiredCount} 天提到“累”，我在陪你留意这件事。`,
      source: "pattern",
    });
  }

  const top = memories[0];
  if (top && top.strength >= 2 && prompts.length < 2) {
    prompts.push({
      id: `memory-${top.id}`,
      text: `我还记得：${top.text}。今天有没有新的变化？`,
      source: "memory",
    });
  }

  if (prompts.length === 0) {
    prompts.push({
      id: "gentle-checkin",
      text: "我在这里。用几句话留下今天就好。",
      source: "gentle",
    });
  }

  return prompts;
}
