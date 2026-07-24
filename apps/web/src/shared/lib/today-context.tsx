"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  buildProactivePrompts,
  summarizeToday,
  updateMemoriesFromEntry,
} from "@/shared/lib/ai";
import { apiPostCheckin, apiWaitForSummary } from "@/shared/lib/api-client";
import { useAuth } from "@/shared/lib/auth-context";
import { allowLocalFallback, mapCheckinDtoToDayEntry } from "@/shared/lib/mappers";
import { todayKeys } from "@/shared/lib/query-keys";
import {
  loadEntries,
  loadMemories,
  saveEntries,
  saveMemories,
  todayKey,
  upsertTodayEntry,
} from "@/shared/lib/storage";
import {
  fetchTodayBundle,
  type TodayBundle,
} from "@/shared/lib/today-api";
import type { DayEntry, MemoryItem, ProactivePrompt } from "@/shared/lib/types";

type TodayContextValue = {
  ready: boolean;
  mode: "api" | "local" | "guest";
  entries: DayEntry[];
  memories: MemoryItem[];
  todayEntry: DayEntry | null;
  prompts: ProactivePrompt[];
  error: string | null;
  saveToday: (rawText: string) => Promise<DayEntry>;
};

const TodayContext = createContext<TodayContextValue | null>(null);

function loadFromLocal(): TodayBundle {
  const entries = loadEntries();
  const memories = loadMemories();
  const todayEntry = entries.find((e) => e.date === todayKey()) ?? null;
  const prompts = buildProactivePrompts(entries, memories);
  return { entries, memories, todayEntry, prompts };
}

export function TodayProvider({ children }: { children: ReactNode }) {
  const { ready: authReady, token } = useAuth();
  const queryClient = useQueryClient();
  const localEnabled = allowLocalFallback() && !token;

  const [localBundle, setLocalBundle] = useState<TodayBundle | null>(() =>
    localEnabled ? loadFromLocal() : null,
  );

  const bundleQuery = useQuery({
    queryKey: todayKeys.bundle(),
    queryFn: fetchTodayBundle,
    enabled: authReady && !!token,
  });

  const saveMutation = useMutation({
    mutationFn: async (rawText: string) => {
      const trimmed = rawText.trim();
      if (!trimmed) throw new Error("rawText is empty");

      if (token) {
        const res = await apiPostCheckin(trimmed);
        if (res.status === "processing" || !res.summary) {
          const pending = mapCheckinDtoToDayEntry(res.checkin, null);
          queryClient.setQueryData<TodayBundle>(todayKeys.bundle(), (old) => ({
            entries: old?.entries ?? [],
            memories: old?.memories ?? [],
            prompts: old?.prompts ?? [],
            todayEntry: pending,
          }));
          const summary = await apiWaitForSummary(res.checkin.date);
          return mapCheckinDtoToDayEntry(res.checkin, summary);
        }
        return mapCheckinDtoToDayEntry(res.checkin, res.summary);
      }

      if (!allowLocalFallback()) {
        throw new Error("请先登录后再留下今天");
      }

      const date = todayKey();
      const summary = summarizeToday(trimmed);
      const entry: DayEntry = {
        id: `${date}-${Date.now()}`,
        date,
        rawText: trimmed,
        createdAt: new Date().toISOString(),
        summary,
      };
      const current = localBundle ?? loadFromLocal();
      const nextEntries = upsertTodayEntry(current.entries, entry);
      const nextMemories = updateMemoriesFromEntry(current.memories, entry);
      saveEntries(nextEntries);
      saveMemories(nextMemories);
      const next: TodayBundle = {
        entries: nextEntries,
        memories: nextMemories,
        todayEntry: entry,
        prompts: buildProactivePrompts(nextEntries, nextMemories),
      };
      setLocalBundle(next);
      return entry;
    },
    onSuccess: async (entry) => {
      if (!token) return;
      await queryClient.invalidateQueries({ queryKey: todayKeys.all });
      queryClient.setQueryData<TodayBundle>(todayKeys.bundle(), (old) => ({
        entries: old?.entries ?? [],
        memories: old?.memories ?? [],
        prompts: old?.prompts ?? [],
        todayEntry: entry,
      }));
    },
  });

  const saveToday = useCallback(
    (rawText: string) => saveMutation.mutateAsync(rawText),
    [saveMutation],
  );

  const apiBundle = bundleQuery.data;
  const mode: TodayContextValue["mode"] = token
    ? "api"
    : localEnabled
      ? "local"
      : "guest";

  const value = useMemo<TodayContextValue>(() => {
    if (mode === "api") {
      return {
        ready: authReady && !bundleQuery.isLoading,
        mode,
        entries: apiBundle?.entries ?? [],
        memories: apiBundle?.memories ?? [],
        todayEntry: apiBundle?.todayEntry ?? null,
        prompts: apiBundle?.prompts ?? [],
        error: bundleQuery.error
          ? bundleQuery.error instanceof Error
            ? bundleQuery.error.message
            : "加载失败"
          : null,
        saveToday,
      };
    }

    if (mode === "local") {
      const local = localBundle ?? loadFromLocal();
      return {
        ready: authReady,
        mode,
        entries: local.entries,
        memories: local.memories,
        todayEntry: local.todayEntry,
        prompts: local.prompts,
        error: null,
        saveToday,
      };
    }

    return {
      ready: authReady,
      mode,
      entries: [],
      memories: [],
      todayEntry: null,
      prompts: [],
      error: null,
      saveToday,
    };
  }, [
    mode,
    authReady,
    bundleQuery.isLoading,
    bundleQuery.error,
    apiBundle,
    localBundle,
    saveToday,
  ]);

  return <TodayContext.Provider value={value}>{children}</TodayContext.Provider>;
}

export function useToday() {
  const ctx = useContext(TodayContext);
  if (!ctx) throw new Error("useToday must be used within TodayProvider");
  return ctx;
}

export type { ProactivePrompt };
