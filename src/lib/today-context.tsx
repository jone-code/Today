"use client";

import {
  createContext,
  useContext,
  useSyncExternalStore,
  type ReactNode,
} from "react";
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

type TodayContextValue = {
  ready: boolean;
  entries: DayEntry[];
  memories: MemoryItem[];
  todayEntry: DayEntry | null;
  prompts: ProactivePrompt[];
  saveToday: (rawText: string) => DayEntry;
};

const TodayContext = createContext<TodayContextValue | null>(null);

const LISTENERS = new Set<() => void>();

function emitChange() {
  LISTENERS.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  LISTENERS.add(listener);
  return () => {
    LISTENERS.delete(listener);
  };
}

function getEntriesSnapshot() {
  return JSON.stringify(loadEntries());
}

function getMemoriesSnapshot() {
  return JSON.stringify(loadMemories());
}

function getServerSnapshot() {
  return "[]";
}

function saveToday(rawText: string) {
  const date = todayKey();
  const summary = summarizeToday(rawText);
  const entry: DayEntry = {
    id: `${date}-${Date.now()}`,
    date,
    rawText: rawText.trim(),
    createdAt: new Date().toISOString(),
    summary,
  };
  const nextEntries = upsertTodayEntry(loadEntries(), entry);
  const nextMemories = updateMemoriesFromEntry(loadMemories(), entry);
  saveEntries(nextEntries);
  saveMemories(nextMemories);
  emitChange();
  return entry;
}

export function TodayProvider({ children }: { children: ReactNode }) {
  const entriesJson = useSyncExternalStore(
    subscribe,
    getEntriesSnapshot,
    getServerSnapshot,
  );
  const memoriesJson = useSyncExternalStore(
    subscribe,
    getMemoriesSnapshot,
    getServerSnapshot,
  );

  const entries = JSON.parse(entriesJson) as DayEntry[];
  const memories = JSON.parse(memoriesJson) as MemoryItem[];
  const todayEntry = entries.find((e) => e.date === todayKey()) ?? null;
  const prompts = buildProactivePrompts(entries, memories);

  return (
    <TodayContext.Provider
      value={{
        ready: true,
        entries,
        memories,
        todayEntry,
        prompts,
        saveToday,
      }}
    >
      {children}
    </TodayContext.Provider>
  );
}

export function useToday() {
  const ctx = useContext(TodayContext);
  if (!ctx) throw new Error("useToday must be used within TodayProvider");
  return ctx;
}

export type { ProactivePrompt };
