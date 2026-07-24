"use client";

type ApiError = {
  statusCode?: number;
  message?: string;
};

const DEFAULT_API_BASE_URL = "http://localhost:3001";
const TOKEN_KEY = "today.auth.token.v1";

export function getApiBaseUrl() {
  const v = process.env.NEXT_PUBLIC_API_BASE_URL;
  return v && v.length > 0 ? v : DEFAULT_API_BASE_URL;
}

export function getAuthToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setAuthToken(token: string | null) {
  if (typeof window === "undefined") return;
  if (!token) localStorage.removeItem(TOKEN_KEY);
  else localStorage.setItem(TOKEN_KEY, token);
}

async function requestJson<T>(
  path: string,
  init?: RequestInit & { timeoutMs?: number },
): Promise<T> {
  const controller = new AbortController();
  const { timeoutMs: customTimeout, ...fetchInit } = init ?? {};
  const timeoutMs = customTimeout ?? 12000;
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const token = getAuthToken();
    const headers: Record<string, string> = {
      "content-type": "application/json",
      ...(fetchInit.headers as Record<string, string> | undefined),
    };
    if (token) headers.authorization = `Bearer ${token}`;

    const res = await fetch(`${getApiBaseUrl()}${path}`, {
      ...fetchInit,
      headers,
      signal: controller.signal,
    });
    const text = await res.text();
    if (!res.ok) {
      let parsed: ApiError | undefined;
      try {
        parsed = text ? (JSON.parse(text) as ApiError) : undefined;
      } catch {
        // ignore
      }
      const err = new Error(
        parsed?.message || `API request failed: ${res.status} ${res.statusText}`,
      ) as Error & { status?: number };
      err.status = res.status;
      throw err;
    }
    if (!text) return undefined as T;
    return JSON.parse(text) as T;
  } finally {
    clearTimeout(t);
  }
}

export type UserDto = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
};

export type AuthTokenResponse = {
  token: string;
  tokenType: "Bearer";
  user: UserDto;
};

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
  category: "work" | "health" | "learning" | "life" | "emotion" | "goal";
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

export type ReminderDto = {
  id: string;
  userId: string;
  title: string;
  message: string;
  remindTime: string;
  timezone: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ReminderDeliveryDto = {
  id: string;
  reminderId: string;
  userId: string;
  fireDate: string;
  title: string;
  message: string;
  status: "pending" | "read";
  createdAt: string;
  readAt: string | null;
};

export async function apiRegister(input: {
  email: string;
  password: string;
  displayName: string;
}): Promise<AuthTokenResponse> {
  return requestJson("/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function apiLogin(input: {
  email: string;
  password: string;
}): Promise<AuthTokenResponse> {
  return requestJson("/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function apiMe(): Promise<UserDto> {
  return requestJson("/v1/auth/me");
}

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
  summary: DaySummaryDto | null;
  status: "processing" | "ready";
}> {
  return requestJson("/v1/checkins", {
    method: "POST",
    body: JSON.stringify({ rawText }),
  });
}

/** 轮询直到 summary 就绪（async checkin 后） */
export async function apiWaitForSummary(
  date: string,
  opts?: {
    attempts?: number;
    intervalMs?: number;
    onAttempt?: (attempt: number, attempts: number) => void;
    signal?: AbortSignal;
  },
): Promise<DaySummaryDto> {
  const attempts = opts?.attempts ?? 40;
  const intervalMs = opts?.intervalMs ?? 750;
  let lastError: unknown;
  for (let i = 0; i < attempts; i++) {
    if (opts?.signal?.aborted) {
      throw new Error("已取消等待总结");
    }
    opts?.onAttempt?.(i + 1, attempts);
    try {
      return await apiGetSummaryByDate(date);
    } catch (e) {
      lastError = e;
      const status = (e as Error & { status?: number }).status;
      if (status !== 404) throw e;
      await new Promise((r) => setTimeout(r, intervalMs));
    }
  }
  throw lastError instanceof Error
    ? lastError
    : new Error("总结还没准备好，请再试一次");
}

export async function apiGetMemories(): Promise<{ items: MemoryDto[] }> {
  return requestJson("/v1/memories");
}

export async function apiGetProactiveToday(): Promise<ProactiveTodayDto> {
  return requestJson("/v1/proactive/today");
}

export async function apiGetTimeline(limit = 30): Promise<TimelinePageDto> {
  return requestJson(`/v1/timeline?limit=${encodeURIComponent(String(limit))}`);
}

export async function apiListReminders(): Promise<{ items: ReminderDto[] }> {
  return requestJson("/v1/reminders");
}

export async function apiCreateReminder(input: {
  title: string;
  message: string;
  remindTime: string;
  timezone?: string;
  enabled?: boolean;
}): Promise<ReminderDto> {
  return requestJson("/v1/reminders", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function apiUpdateReminder(
  id: string,
  input: Partial<{
    title: string;
    message: string;
    remindTime: string;
    timezone: string;
    enabled: boolean;
  }>,
): Promise<ReminderDto> {
  return requestJson(`/v1/reminders/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export async function apiDeleteReminder(id: string): Promise<void> {
  await requestJson(`/v1/reminders/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export async function apiListReminderDeliveries(
  limit = 30,
): Promise<{ items: ReminderDeliveryDto[] }> {
  return requestJson(
    `/v1/reminders/deliveries?limit=${encodeURIComponent(String(limit))}`,
  );
}

export async function apiMarkReminderDeliveryRead(
  id: string,
): Promise<ReminderDeliveryDto> {
  return requestJson(`/v1/reminders/deliveries/${encodeURIComponent(id)}/read`, {
    method: "POST",
  });
}

export type TodoDto = {
  id: string;
  userId: string;
  title: string;
  note: string | null;
  status: "open" | "done";
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type PunchHabitDto = {
  id: string;
  userId: string;
  title: string;
  description: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  punchedToday: boolean;
  streak: number;
};

export type PunchLogDto = {
  id: string;
  habitId: string;
  userId: string;
  punchDate: string;
  note: string | null;
  createdAt: string;
};

export async function apiListTodos(
  status?: "open" | "done" | "all",
): Promise<{ items: TodoDto[] }> {
  const q =
    status && status !== "all"
      ? `?status=${encodeURIComponent(status)}`
      : "";
  return requestJson(`/v1/todos${q}`);
}

export async function apiCreateTodo(input: {
  title: string;
  note?: string;
  dueDate?: string | null;
}): Promise<TodoDto> {
  return requestJson("/v1/todos", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function apiUpdateTodo(
  id: string,
  input: Partial<{
    title: string;
    note: string | null;
    dueDate: string | null;
    status: "open" | "done";
    clearDueDate: boolean;
  }>,
): Promise<TodoDto> {
  return requestJson(`/v1/todos/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export async function apiToggleTodo(id: string): Promise<TodoDto> {
  return requestJson(`/v1/todos/${encodeURIComponent(id)}/toggle`, {
    method: "POST",
  });
}

export async function apiDeleteTodo(id: string): Promise<void> {
  await requestJson(`/v1/todos/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export async function apiListPunchHabits(
  date?: string,
): Promise<{ items: PunchHabitDto[]; date: string }> {
  const q = date ? `?date=${encodeURIComponent(date)}` : "";
  return requestJson(`/v1/punch/habits${q}`);
}

export async function apiCreatePunchHabit(input: {
  title: string;
  description?: string | null;
  enabled?: boolean;
}): Promise<PunchHabitDto> {
  return requestJson("/v1/punch/habits", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function apiUpdatePunchHabit(
  id: string,
  input: Partial<{
    title: string;
    description: string | null;
    enabled: boolean;
  }>,
): Promise<PunchHabitDto> {
  return requestJson(`/v1/punch/habits/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export async function apiDeletePunchHabit(id: string): Promise<void> {
  await requestJson(`/v1/punch/habits/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export async function apiPunchHabit(
  id: string,
  input?: { date?: string; note?: string | null },
): Promise<PunchLogDto> {
  return requestJson(`/v1/punch/habits/${encodeURIComponent(id)}/punch`, {
    method: "POST",
    body: JSON.stringify(input ?? {}),
  });
}

export async function apiUnpunchHabit(
  id: string,
  date?: string,
): Promise<void> {
  const q = date ? `?date=${encodeURIComponent(date)}` : "";
  await requestJson(`/v1/punch/habits/${encodeURIComponent(id)}/punch${q}`, {
    method: "DELETE",
  });
}
