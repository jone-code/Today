"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  buildProactivePrompts,
  summarizeToday,
  updateMemoriesFromEntry,
} from "@/shared/lib/ai";
import {
  apiPostCheckin,
  apiReprocessTodayCheckin,
  apiWaitForSummary,
} from "@/shared/lib/api-client";
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
import { fetchTodayBundle, type TodayBundle } from "@/shared/lib/today-api";
import type {
  CheckinLoopPhase,
  DayEntry,
  ProactivePrompt,
} from "@/shared/lib/types";

type TodayContextValue = {
  ready: boolean;
  mode: "api" | "local" | "guest";
  entries: DayEntry[];
  memories: TodayBundle["memories"];
  todayEntry: DayEntry | null;
  prompts: ProactivePrompt[];
  error: string | null;
  /** 主链路阶段 */
  loopPhase: CheckinLoopPhase;
  loopMessage: string | null;
  saveToday: (rawText: string) => Promise<DayEntry>;
  /** 总结轮询失败后重试（不重新提交 checkin） */
  retryProcessing: () => Promise<void>;
  /** 提交失败后用上次文案重试 */
  retrySubmit: () => Promise<DayEntry | null>;
  refreshToday: () => Promise<void>;
};

const TodayContext = createContext<TodayContextValue | null>(null);

function loadFromLocal(): TodayBundle {
  const entries = loadEntries().map((e) =>
    e.summaryStatus ? e : { ...e, summaryStatus: "ready" as const },
  );
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
  const [loopPhase, setLoopPhase] = useState<CheckinLoopPhase>("idle");
  const [loopMessage, setLoopMessage] = useState<string | null>(null);
  const lastRawTextRef = useRef<string | null>(null);
  const resumeStartedRef = useRef<string | null>(null);

  const bundleQuery = useQuery({
    queryKey: todayKeys.bundle(),
    queryFn: fetchTodayBundle,
    enabled: authReady && !!token,
  });

  const refreshToday = useCallback(async () => {
    if (!token) return;
    await queryClient.invalidateQueries({ queryKey: todayKeys.all });
    await queryClient.refetchQueries({ queryKey: todayKeys.bundle() });
  }, [queryClient, token]);

  const waitAndRefresh = useCallback(
    async (date: string) => {
      setLoopPhase("processing");
      setLoopMessage("正在整理今天的总结与记忆…");
      await apiWaitForSummary(date, {
        onAttempt: (attempt, attempts) => {
          if (attempt === 1 || attempt % 4 === 0) {
            setLoopMessage(`正在整理…（${attempt}/${attempts}）`);
          }
        },
      });
      await refreshToday();
      setLoopPhase("idle");
      setLoopMessage(null);
    },
    [refreshToday],
  );

  // 页面加载时若今日仍在 processing，自动续等；failed 则直接进 error
  useEffect(() => {
    const entry = bundleQuery.data?.todayEntry;
    if (!token || !entry) return;
    if (entry.summaryStatus === "failed") {
      queueMicrotask(() => {
        setLoopPhase("error");
        setLoopMessage(entry.pipelineError || "整理失败，请重试");
      });
      return;
    }
    if (entry.summaryStatus !== "processing") return;
    if (resumeStartedRef.current === entry.id) return;
    resumeStartedRef.current = entry.id;
    let cancelled = false;
    (async () => {
      try {
        await waitAndRefresh(entry.date);
      } catch (err) {
        if (cancelled) return;
        setLoopPhase("error");
        setLoopMessage(err instanceof Error ? err.message : "整理失败，请重试");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token, bundleQuery.data?.todayEntry, waitAndRefresh]);

  const saveMutation = useMutation({
    mutationFn: async (rawText: string) => {
      const trimmed = rawText.trim();
      if (!trimmed) throw new Error("请先写一点今天的内容");
      lastRawTextRef.current = trimmed;

      if (token) {
        setLoopPhase("submitting");
        setLoopMessage("正在留下今天…");
        const res = await apiPostCheckin(trimmed);

        if (res.status === "processing" || !res.summary) {
          const pending = mapCheckinDtoToDayEntry(res.checkin, null, res.aiJob);
          queryClient.setQueryData<TodayBundle>(todayKeys.bundle(), (old) => ({
            entries: old?.entries ?? [],
            memories: old?.memories ?? [],
            prompts: old?.prompts ?? [],
            todayEntry: pending,
          }));
          resumeStartedRef.current = pending.id;
          await waitAndRefresh(res.checkin.date);
          const fresh = queryClient.getQueryData<TodayBundle>(todayKeys.bundle());
          return fresh?.todayEntry ?? mapCheckinDtoToDayEntry(res.checkin, null, res.aiJob);
        }

        await refreshToday();
        setLoopPhase("idle");
        setLoopMessage(null);
        return mapCheckinDtoToDayEntry(res.checkin, res.summary);
      }

      if (!allowLocalFallback()) {
        throw new Error("请先登录后再留下今天");
      }

      setLoopPhase("submitting");
      const date = todayKey();
      const summary = summarizeToday(trimmed);
      const entry: DayEntry = {
        id: `${date}-${Date.now()}`,
        date,
        rawText: trimmed,
        createdAt: new Date().toISOString(),
        summary,
        summaryStatus: "ready",
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
      setLoopPhase("idle");
      setLoopMessage(null);
      return entry;
    },
    onError: (err) => {
      setLoopPhase("error");
      setLoopMessage(err instanceof Error ? err.message : "保存失败");
    },
  });

  const saveToday = useCallback(
    (rawText: string) => saveMutation.mutateAsync(rawText),
    [saveMutation],
  );

  const retryProcessing = useCallback(async () => {
    const entry = bundleQuery.data?.todayEntry;
    if (!entry) throw new Error("没有可重试的今日记录");
    try {
      setLoopPhase("processing");
      setLoopMessage("正在重新整理…");
      await apiReprocessTodayCheckin();
      resumeStartedRef.current = entry.id;
      await waitAndRefresh(entry.date);
    } catch (err) {
      setLoopPhase("error");
      setLoopMessage(err instanceof Error ? err.message : "整理失败，请重试");
      throw err;
    }
  }, [bundleQuery.data?.todayEntry, waitAndRefresh]);

  const retrySubmit = useCallback(async () => {
    const raw = lastRawTextRef.current;
    if (!raw) return null;
    return saveToday(raw);
  }, [saveToday]);

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
        loopPhase,
        loopMessage,
        saveToday,
        retryProcessing,
        retrySubmit,
        refreshToday,
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
        loopPhase,
        loopMessage,
        saveToday,
        retryProcessing,
        retrySubmit,
        refreshToday,
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
      loopPhase,
      loopMessage,
      saveToday,
      retryProcessing,
      retrySubmit,
      refreshToday,
    };
  }, [
    mode,
    authReady,
    bundleQuery.isLoading,
    bundleQuery.error,
    apiBundle,
    localBundle,
    loopPhase,
    loopMessage,
    saveToday,
    retryProcessing,
    retrySubmit,
    refreshToday,
  ]);

  return <TodayContext.Provider value={value}>{children}</TodayContext.Provider>;
}

export function useToday() {
  const ctx = useContext(TodayContext);
  if (!ctx) throw new Error("useToday must be used within TodayProvider");
  return ctx;
}

export type { ProactivePrompt };
