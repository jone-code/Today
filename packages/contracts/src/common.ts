import { z } from "zod";

export const ApiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  details: z.unknown().optional(),
});

export type ApiError = z.infer<typeof ApiErrorSchema>;

/** 模块对外 HTTP 路径约定（api 实现必须对齐） */
export const ApiRoutes = {
  checkinToday: "/v1/checkins/today",
  checkins: "/v1/checkins",
  summaryByDate: "/v1/summaries/:date",
  memories: "/v1/memories",
  timeline: "/v1/timeline",
  proactiveToday: "/v1/proactive/today",
  health: "/health",
} as const;
