import type {
  DayEntry,
  DaySummary,
  MemoryItem,
  Mood,
  ProactivePrompt,
} from "./types";
import { todayKey } from "./storage";

const MOOD_RULES: Array<{
  mood: Mood;
  label: string;
  patterns: RegExp[];
}> = [
  {
    mood: "great",
    label: "很好",
    patterns: [/很开心/, /太棒/, /兴奋/, /收获很大/, /顺利/, /开心/],
  },
  {
    mood: "tired",
    label: "疲惫",
    patterns: [/很累/, /疲惫/, /加班/, /熬夜/, /压力大/, /累了/, /累/],
  },
  {
    mood: "low",
    label: "低落",
    patterns: [/难过/, /沮丧/, /焦虑/, /郁闷/, /不开心/, /崩溃/],
  },
  {
    mood: "good",
    label: "不错",
    patterns: [/不错/, /还行/, /挺好/, /完成了/, /搞定/],
  },
];

const KEYWORD_RULES: Array<{ word: string; pattern: RegExp }> = [
  { word: "工作", pattern: /工作|项目|上线|开会|需求|代码|客户/ },
  { word: "学习", pattern: /学习|读书|课程|教程|AI|英语/ },
  { word: "家庭", pattern: /家人|父母|孩子|伴侣|回家|家人/ },
  { word: "运动", pattern: /跑步|健身|运动|散步|游泳|骑车/ },
  { word: "睡眠", pattern: /睡眠|失眠|熬夜|睡得|早睡/ },
  { word: "创业", pattern: /创业|融资|产品|Demo|MVP|启动/ },
  { word: "面试", pattern: /面试|投递|简历|Offer/ },
  { word: "情绪", pattern: /情绪|心情|焦虑|压力|开心|难过/ },
];

function detectMood(text: string): { mood: Mood; label: string } {
  for (const rule of MOOD_RULES) {
    if (rule.patterns.some((p) => p.test(text))) {
      return { mood: rule.mood, label: rule.label };
    }
  }
  return { mood: "okay", label: "平常" };
}

function extractKeywords(text: string): string[] {
  const found = KEYWORD_RULES.filter((r) => r.pattern.test(text)).map(
    (r) => r.word,
  );
  return found.length > 0 ? found.slice(0, 4) : ["日常"];
}

function extractCompleted(text: string): string[] {
  const parts = text
    .split(/[。！？!?\n；;]+/)
    .map((s) => s.trim())
    .filter((s) => s.length >= 4);

  const completed = parts.filter((p) =>
    /完成|搞定|做了|学习|写了|见了|跑了|开了|准备|开始/.test(p),
  );

  if (completed.length > 0) return completed.slice(0, 3);
  if (parts.length > 0) return parts.slice(0, 2);
  return ["记录了今天的片刻"];
}

function buildOneLiner(text: string, moodLabel: string, keywords: string[]): string {
  const focus = keywords.filter((k) => k !== "日常").slice(0, 2).join("与");
  if (/累|压力|加班/.test(text)) {
    return focus
      ? `今天围绕${focus}忙碌推进，虽然有些疲惫，但依然留下了痕迹。`
      : "今天有些累，但你仍然认真地过完了这一天。";
  }
  if (/开心|顺利|收获/.test(text)) {
    return focus
      ? `今天在${focus}上有不错的推进，整体感觉${moodLabel}。`
      : `今天过得${moodLabel}，值得被记住。`;
  }
  return focus
    ? `今天主要与${focus}有关，一句话概括：你认真地留下了今天。`
    : "今天虽然平静，但也被认真地记了下来。";
}

function buildHighlight(completed: string[], keywords: string[]): string {
  if (completed[0]) {
    const short =
      completed[0].length > 28 ? `${completed[0].slice(0, 28)}…` : completed[0];
    return short;
  }
  return keywords[0] ? `关于「${keywords[0]}」的一天` : "留下了今天";
}

export function summarizeToday(rawText: string): DaySummary {
  const text = rawText.trim();
  const { mood, label } = detectMood(text);
  const keywords = extractKeywords(text);
  const completed = extractCompleted(text);
  return {
    completed,
    mood,
    moodLabel: label,
    keywords,
    oneLiner: buildOneLiner(text, label, keywords),
    highlight: buildHighlight(completed, keywords),
  };
}

function memoryId(category: MemoryItem["category"], text: string) {
  return `${category}:${text}`;
}

export function updateMemoriesFromEntry(
  memories: MemoryItem[],
  entry: DayEntry,
): MemoryItem[] {
  const next = [...memories];
  const now = entry.createdAt;
  const text = entry.rawText;
  const candidates: Array<Omit<MemoryItem, "id" | "updatedAt" | "strength">> =
    [];

  if (/压力|加班|上线/.test(text)) {
    candidates.push({
      category: "work",
      text: "最近工作压力偏高",
    });
  }
  if (/运动|跑步|健身|散步/.test(text)) {
    candidates.push({
      category: "health",
      text: "正在坚持运动",
    });
  }
  if (/睡眠|失眠|熬夜/.test(text)) {
    candidates.push({
      category: "health",
      text: "最近睡眠需要关注",
    });
  }
  if (/学习|AI|课程|读书/.test(text)) {
    candidates.push({
      category: "learning",
      text: "正在持续学习",
    });
  }
  if (/创业|产品|Demo|MVP/.test(text)) {
    candidates.push({
      category: "goal",
      text: "正在推进创业 / 产品",
    });
  }
  if (/面试/.test(text)) {
    candidates.push({
      category: "goal",
      text: "近期有面试相关安排",
    });
  }
  if (/累|疲惫/.test(text)) {
    candidates.push({
      category: "emotion",
      text: "近期多次提到疲惫",
    });
  }

  for (const c of candidates) {
    const id = memoryId(c.category, c.text);
    const existing = next.find((m) => m.id === id);
    if (existing) {
      existing.strength += 1;
      existing.updatedAt = now;
    } else {
      next.push({
        id,
        text: c.text,
        category: c.category,
        strength: 1,
        updatedAt: now,
      });
    }
  }

  return next.sort((a, b) => b.strength - a.strength || b.updatedAt.localeCompare(a.updatedAt));
}

export function buildProactivePrompts(
  entries: DayEntry[],
  memories: MemoryItem[],
): ProactivePrompt[] {
  const prompts: ProactivePrompt[] = [];
  const today = todayKey();
  const yesterdayDate = new Date();
  yesterdayDate.setDate(yesterdayDate.getDate() - 1);
  const yesterday = todayKey(yesterdayDate);

  const yesterdayEntry = entries.find((e) => e.date === yesterday);
  if (yesterdayEntry && /明天|面试|准备/.test(yesterdayEntry.rawText)) {
    if (/面试/.test(yesterdayEntry.rawText)) {
      prompts.push({
        id: "followup-interview",
        text: "昨天提到今天有面试，结果怎么样？",
        relatedDate: yesterday,
      });
    } else {
      prompts.push({
        id: "followup-plan",
        text: "昨天你提到了今天的计划，现在进展如何？",
        relatedDate: yesterday,
      });
    }
  }

  const tiredCount = entries
    .filter((e) => {
      const days =
        (Date.parse(today) - Date.parse(e.date)) / (1000 * 60 * 60 * 24);
      return days <= 14 && /很累|疲惫|累/.test(e.rawText);
    }).length;

  if (tiredCount >= 3) {
    prompts.push({
      id: "pattern-tired",
      text: `最近两周你已经有 ${tiredCount} 天提到“累”，我在陪你留意这件事。`,
    });
  }

  const topMemory = memories[0];
  if (topMemory && topMemory.strength >= 2 && prompts.length < 2) {
    prompts.push({
      id: `memory-${topMemory.id}`,
      text: `我还记得：${topMemory.text}。今天有没有新的变化？`,
    });
  }

  if (prompts.length === 0 && entries.length > 0) {
    prompts.push({
      id: "gentle-checkin",
      text: "我在这里。用几句话留下今天就好。",
    });
  }

  return prompts.slice(0, 2);
}

export const moodEmoji: Record<Mood, string> = {
  great: "😊",
  good: "🙂",
  okay: "😐",
  tired: "😮‍💨",
  low: "😔",
};
