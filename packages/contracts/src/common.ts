import { z } from "zod";

export const ApiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  details: z.unknown().optional(),
});

export type ApiError = z.infer<typeof ApiErrorSchema>;

/** 模块对外 HTTP 路径约定（api 实现必须对齐） */
export const ApiRoutes = {
  authRegister: "/v1/auth/register",
  authLogin: "/v1/auth/login",
  authMe: "/v1/auth/me",
  checkinToday: "/v1/checkins/today",
  checkins: "/v1/checkins",
  checkinReprocessToday: "/v1/checkins/today/reprocess",
  summaryByDate: "/v1/summaries/:date",
  memories: "/v1/memories",
  memoryById: "/v1/memories/:id",
  memoryArchive: "/v1/memories/:id/archive",
  memoryUnarchive: "/v1/memories/:id/unarchive",
  timeline: "/v1/timeline",
  proactiveToday: "/v1/proactive/today",
  proactiveSelect: "/v1/proactive/prompts/:id/select",
  proactiveDismiss: "/v1/proactive/prompts/:id/dismiss",
  memoriesReindex: "/v1/memories/reindex",
  adminVectorReindex: "/v1/admin/vector/reindex",
  adminAiStats: "/v1/admin/ai/stats",
  adminAiCalls: "/v1/admin/ai/calls",
  reminders: "/v1/reminders",
  reminderById: "/v1/reminders/:id",
  reminderDeliveries: "/v1/reminders/deliveries",
  reminderDeliveryRead: "/v1/reminders/deliveries/:id/read",
  todos: "/v1/todos",
  todoById: "/v1/todos/:id",
  punchHabits: "/v1/punch/habits",
  punchHabitById: "/v1/punch/habits/:id",
  punchToday: "/v1/punch/habits/:id/punch",
  health: "/health",
} as const;
