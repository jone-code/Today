import type { DayEntry, MemoryItem, ProactivePrompt } from "@/shared/lib/types";
import type {
  CheckinDto,
  DaySummaryDto,
  MemoryDto,
  ProactivePromptDto,
  TimelineItemDto,
} from "@/shared/lib/api-client";

export function mapSummaryDtoToDaySummary(
  summary: DaySummaryDto,
): DayEntry["summary"] {
  return {
    completed: summary.completed,
    mood: summary.mood,
    moodLabel: summary.moodLabel,
    keywords: summary.keywords,
    oneLiner: summary.oneLiner,
    highlight: summary.highlight,
  };
}

export function pendingSummary(): DayEntry["summary"] {
  return {
    completed: [],
    mood: "okay",
    moodLabel: "整理中",
    keywords: [],
    oneLiner: "正在整理今天，马上就好…",
    highlight: "正在整理",
  };
}

export function mapCheckinDtoToDayEntry(
  checkin: CheckinDto,
  summary: DaySummaryDto | null,
): DayEntry {
  return {
    id: checkin.id,
    date: checkin.date,
    rawText: checkin.rawText,
    createdAt: checkin.createdAt,
    summary: summary ? mapSummaryDtoToDaySummary(summary) : pendingSummary(),
  };
}

export function mapTimelineItemToDayEntry(item: TimelineItemDto): DayEntry {
  return {
    id: item.checkinId,
    date: item.date,
    rawText: "",
    createdAt: "",
    summary: {
      completed: [],
      mood: item.mood,
      moodLabel: item.moodLabel,
      keywords: [],
      oneLiner: item.oneLiner,
      highlight: item.highlight,
    },
  };
}

export function mapMemoryDtoToMemoryItem(m: MemoryDto): MemoryItem {
  return {
    id: m.id,
    text: m.text,
    category: m.category,
    strength: m.strength,
    updatedAt: m.updatedAt,
  };
}

export function mapPromptDtoToPrompt(p: ProactivePromptDto): ProactivePrompt {
  const relatedDate =
    p.relatedDate && typeof p.relatedDate === "string" ? p.relatedDate : undefined;
  return { id: p.id, text: p.text, relatedDate };
}

/** 仅显式开启时允许未登录本地 demo；默认强制走 API */
export function allowLocalFallback() {
  return process.env.NEXT_PUBLIC_ALLOW_LOCAL_FALLBACK === "true";
}
