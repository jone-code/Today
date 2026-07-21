"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import {
  buildProactivePrompts,
  summarizeToday,
  updateMemoriesFromEntry,
} from "@/lib/ai";
import {
  loadEntries,
  loadMemories,
  saveEntries,
  saveMemories,
  todayKey,
  upsertTodayEntry,
} from "@/lib/storage";
import type { DayEntry, MemoryItem, ProactivePrompt } from "@/lib/types";
import {
  apiGetMemories,
  apiGetProactiveToday,
  apiGetSummaryByDate,
  apiGetTimeline,
  apiGetTodayCheckin,
  apiPostCheckin,
  type CheckinDto,
  type DaySummaryDto,
  type MemoryDto,
  type ProactivePromptDto,
  type TimelineItemDto,
} from "@/lib/api-client";

type TodayContextValue = {
  ready: boolean;
  entries: DayEntry[];
  memories: MemoryItem[];
  todayEntry: DayEntry | null;
  prompts: ProactivePrompt[];
  saveToday: (rawText: string) => Promise<DayEntry>;
};

const TodayContext = createContext<TodayContextValue | null>(null);

type Mode = "api" | "local";

function mapSummaryDtoToDaySummary(summary: DaySummaryDto): DayEntry["summary"] {
  return {
    completed: summary.completed,
    mood: summary.mood,
    moodLabel: summary.moodLabel,
    keywords: summary.keywords,
    oneLiner: summary.oneLiner,
    highlight: summary.highlight,
  };
}

function mapCheckinDtoToDayEntry(checkin: CheckinDto, summary: DaySummaryDto): DayEntry {
  return {
    id: checkin.id,
    date: checkin.date,
    rawText: checkin.rawText,
    createdAt: checkin.createdAt,
    summary: mapSummaryDtoToDaySummary(summary),
  };
}

function mapTimelineItemToDayEntry(item: TimelineItemDto): DayEntry {
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

function mapMemoryDtoToMemoryItem(m: MemoryDto): MemoryItem {
  return {
    id: m.id,
    text: m.text,
    category: m.category,
    strength: m.strength,
    updatedAt: m.updatedAt,
  };
}

function mapPromptDtoToPrompt(p: ProactivePromptDto): ProactivePrompt {
  const relatedDate =
    p.relatedDate && typeof p.relatedDate === "string" ? p.relatedDate : undefined;
  return { id: p.id, text: p.text, relatedDate };
}

async function tryLoadFromApi() {
  const checkinRes = await apiGetTodayCheckin();
  const [timelineRes, memoriesRes, proactiveRes] = await Promise.all([
    apiGetTimeline(30),
    apiGetMemories(),
    apiGetProactiveToday(),
  ]);

  const entries = timelineRes.items.map(mapTimelineItemToDayEntry).sort((a, b) => b.date.localeCompare(a.date));
  const memories = memoriesRes.items.map(mapMemoryDtoToMemoryItem);
  const prompts = proactiveRes.prompts.map(mapPromptDtoToPrompt);

  let todayEntry: DayEntry | null = null;
  if (checkinRes.checkin) {
    const summary = await apiGetSummaryByDate(checkinRes.checkin.date);
    todayEntry = mapCheckinDtoToDayEntry(checkinRes.checkin, summary);
  }

  return { entries, memories, todayEntry, prompts } as const;
}

function loadFromLocal() {
  const entries = loadEntries();
  const memories = loadMemories();
  const todayEntry = entries.find((e) => e.date === todayKey()) ?? null;
  const prompts = buildProactivePrompts(entries, memories);
  return { entries, memories, todayEntry, prompts } as const;
}

export function TodayProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [mode, setMode] = useState<Mode>("local");
  const [entries, setEntries] = useState<DayEntry[]>([]);
  const [memories, setMemories] = useState<MemoryItem[]>([]);
  const [todayEntry, setTodayEntry] = useState<DayEntry | null>(null);
  const [prompts, setPrompts] = useState<ProactivePrompt[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const loaded = await tryLoadFromApi();
        if (cancelled) return;
        setMode("api");
        setEntries(loaded.entries);
        setMemories(loaded.memories);
        setTodayEntry(loaded.todayEntry);
        setPrompts(loaded.prompts);
      } catch {
        if (cancelled) return;
        const loaded = loadFromLocal();
        setMode("local");
        setEntries(loaded.entries);
        setMemories(loaded.memories);
        setTodayEntry(loaded.todayEntry);
        setPrompts(loaded.prompts);
      } finally {
        if (!cancelled) setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const saveToday = async (rawText: string) => {
    const trimmed = rawText.trim();
    if (!trimmed) {
      throw new Error("rawText is empty");
    }

    if (mode === "api") {
      const res = await apiPostCheckin(trimmed);
      const entry = mapCheckinDtoToDayEntry(res.checkin, res.summary);

      // refresh the rest for consistency
      const [timelineRes, memoriesRes, proactiveRes] = await Promise.all([
        apiGetTimeline(30),
        apiGetMemories(),
        apiGetProactiveToday(),
      ]);

      setTodayEntry(entry);
      setEntries(timelineRes.items.map(mapTimelineItemToDayEntry).sort((a, b) => b.date.localeCompare(a.date)));
      setMemories(memoriesRes.items.map(mapMemoryDtoToMemoryItem));
      setPrompts(proactiveRes.prompts.map(mapPromptDtoToPrompt));
      return entry;
    }

    // local fallback
    const date = todayKey();
    const summary = summarizeToday(trimmed);
    const entry: DayEntry = {
      id: `${date}-${Date.now()}`,
      date,
      rawText: trimmed,
      createdAt: new Date().toISOString(),
      summary,
    };

    const nextEntries = upsertTodayEntry(entries, entry);
    const nextMemories = updateMemoriesFromEntry(memories, entry);
    setEntries(nextEntries);
    setMemories(nextMemories);
    saveEntries(nextEntries);
    saveMemories(nextMemories);

    // prompts depend on both
    setPrompts(buildProactivePrompts(nextEntries, nextMemories));
    setTodayEntry(entry);
    return entry;
  };

  const value: TodayContextValue = {
    ready,
    entries,
    memories,
    todayEntry,
    prompts,
    saveToday,
  };

  return <TodayContext.Provider value={value}>{children}</TodayContext.Provider>;
}

export function useToday() {
  const ctx = useContext(TodayContext);
  if (!ctx) throw new Error("useToday must be used within TodayProvider");
  return ctx;
}

export type { ProactivePrompt };
