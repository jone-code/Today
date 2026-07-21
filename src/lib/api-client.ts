"use client";

type ApiError = {
  statusCode?: number;
  message?: string;
};

const DEFAULT_API_BASE_URL = "http://localhost:3001";

function getApiBaseUrl() {
  // Client-side env var
  const v = process.env.NEXT_PUBLIC_API_BASE_URL;
  return v && v.length > 0 ? v : DEFAULT_API_BASE_URL;
}

async function requestJson<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const controller = new AbortController();
  const timeoutMs = 8000;
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${getApiBaseUrl()}${path}`, {
      ...init,
      headers: {
        "content-type": "application/json",
        ...(init?.headers ?? {}),
      },
      signal: controller.signal,
    });
    const text = await res.text();
    if (!res.ok) {
      let parsed: ApiError | undefined;
      try {
        parsed = text ? (JSON.parse(text) as ApiError) : undefined;
      } catch {
        // ignore parse errors
      }
      throw new Error(
        parsed?.message ||
          `API request failed: ${res.status} ${res.statusText}`,
      );
    }
    if (!text) return undefined as T;
    return JSON.parse(text) as T;
  } finally {
    clearTimeout(t);
  }
}

export type CheckinDto = {
  id: string;
  userId: string;
  date: string;
  rawText: string;
  createdAt: string;
  updatedAt: string;
};

export type DaySummaryDto = {
  checkinId: string;
  date: string;
  completed: string[];
  mood: "great" | "good" | "okay" | "tired" | "low";
  moodLabel: string;
  keywords: string[];
  oneLiner: string;
  highlight: string;
  provider: "llm" | "heuristic";
  createdAt: string;
};

export type MemoryDto = {
  id: string;
  userId: string;
  category:
    | "work"
    | "health"
    | "learning"
    | "life"
    | "emotion"
    | "goal";
  text: string;
  strength: number;
  updatedAt: string;
};

export type ProactivePromptDto = {
  id: string;
  text: string;
  relatedDate?: string | null;
  source: "followup" | "pattern" | "memory" | "gentle";
};

export type ProactiveTodayDto = {
  date: string;
  prompts: ProactivePromptDto[];
  provider: "llm" | "heuristic";
};

export type TimelineItemDto = {
  date: string;
  highlight: string;
  oneLiner: string;
  mood: "great" | "good" | "okay" | "tired" | "low";
  moodLabel: string;
  checkinId: string;
};

export type TimelinePageDto = {
  items: TimelineItemDto[];
  nextCursor: string | null;
};

export async function apiGetTodayCheckin(): Promise<{
  checkin: CheckinDto | null;
}> {
  return requestJson("/v1/checkins/today");
}

export async function apiGetSummaryByDate(date: string): Promise<DaySummaryDto> {
  return requestJson(`/v1/summaries/${encodeURIComponent(date)}`);
}

export async function apiPostCheckin(rawText: string): Promise<{
  checkin: CheckinDto;
  summary: DaySummaryDto;
}> {
  return requestJson("/v1/checkins", {
    method: "POST",
    body: JSON.stringify({ rawText }),
  });
}

export async function apiGetMemories(): Promise<{ items: MemoryDto[] }> {
  return requestJson("/v1/memories");
}

export async function apiGetProactiveToday(): Promise<ProactiveTodayDto> {
  return requestJson("/v1/proactive/today");
}

export async function apiGetTimeline(
  limit = 30,
): Promise<TimelinePageDto> {
  return requestJson(`/v1/timeline?limit=${encodeURIComponent(String(limit))}`);
}

