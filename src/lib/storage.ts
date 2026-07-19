import type { DayEntry, MemoryItem, ProactivePrompt } from "./types";

const ENTRIES_KEY = "today.entries.v1";
const MEMORIES_KEY = "today.memories.v1";

function canUseStorage() {
  return typeof window !== "undefined" && !!window.localStorage;
}

export function loadEntries(): DayEntry[] {
  if (!canUseStorage()) return [];
  try {
    const raw = localStorage.getItem(ENTRIES_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as DayEntry[];
    return parsed.sort((a, b) => b.date.localeCompare(a.date));
  } catch {
    return [];
  }
}

export function saveEntries(entries: DayEntry[]) {
  if (!canUseStorage()) return;
  localStorage.setItem(ENTRIES_KEY, JSON.stringify(entries));
}

export function loadMemories(): MemoryItem[] {
  if (!canUseStorage()) return [];
  try {
    const raw = localStorage.getItem(MEMORIES_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as MemoryItem[];
  } catch {
    return [];
  }
}

export function saveMemories(memories: MemoryItem[]) {
  if (!canUseStorage()) return;
  localStorage.setItem(MEMORIES_KEY, JSON.stringify(memories));
}

export function todayKey(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function formatDisplayDate(dateKey: string) {
  const [y, m, d] = dateKey.split("-").map(Number);
  return `${y} 年 ${m} 月 ${d} 日`;
}

export function upsertTodayEntry(
  entries: DayEntry[],
  entry: DayEntry,
): DayEntry[] {
  const without = entries.filter((e) => e.date !== entry.date);
  return [entry, ...without].sort((a, b) => b.date.localeCompare(a.date));
}

export type { ProactivePrompt };
