import {
  apiGetMemories,
  apiGetProactiveToday,
  apiGetSummaryByDate,
  apiGetTimeline,
  apiGetTodayCheckin,
  apiPostCheckin,
  apiWaitForSummary,
  getAuthToken,
} from "@/shared/lib/api-client";
import {
  mapCheckinDtoToDayEntry,
  mapMemoryDtoToMemoryItem,
  mapPromptDtoToPrompt,
  mapTimelineItemToDayEntry,
} from "@/shared/lib/mappers";
import type { DayEntry, MemoryItem, ProactivePrompt } from "@/shared/lib/types";

export type TodayBundle = {
  entries: DayEntry[];
  memories: MemoryItem[];
  todayEntry: DayEntry | null;
  prompts: ProactivePrompt[];
};

export async function fetchTodayBundle(): Promise<TodayBundle> {
  if (!getAuthToken()) {
    throw new Error("no auth token");
  }

  const checkinRes = await apiGetTodayCheckin();
  const [timelineRes, memoriesRes, proactiveRes] = await Promise.all([
    apiGetTimeline(30),
    apiGetMemories(),
    apiGetProactiveToday(),
  ]);

  const entries = timelineRes.items
    .map(mapTimelineItemToDayEntry)
    .sort((a, b) => b.date.localeCompare(a.date));
  const memories = memoriesRes.items.map(mapMemoryDtoToMemoryItem);
  const prompts = proactiveRes.prompts.map(mapPromptDtoToPrompt);

  let todayEntry: DayEntry | null = null;
  if (checkinRes.checkin) {
    try {
      const summary = await apiGetSummaryByDate(checkinRes.checkin.date);
      todayEntry = mapCheckinDtoToDayEntry(checkinRes.checkin, summary);
    } catch (e) {
      const status = (e as Error & { status?: number }).status;
      if (status === 404) {
        todayEntry = mapCheckinDtoToDayEntry(checkinRes.checkin, null);
      } else {
        throw e;
      }
    }
  }

  return { entries, memories, todayEntry, prompts };
}

export async function submitTodayCheckin(rawText: string): Promise<DayEntry> {
  const res = await apiPostCheckin(rawText);
  let summary = res.summary;
  if (res.status === "processing" || !summary) {
    summary = await apiWaitForSummary(res.checkin.date);
  }
  return mapCheckinDtoToDayEntry(res.checkin, summary);
}
