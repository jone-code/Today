import { z } from "zod";
import { DaySummaryDtoSchema } from "./summary";
import { CheckinDtoSchema } from "./checkin";

/** checkin 提交结果：async 时 summary 可能暂为空，前端轮询 summaryByDate */
export const CheckinSubmitStatusSchema = z.enum(["processing", "ready", "failed"]);

export type CheckinSubmitStatus = z.infer<typeof CheckinSubmitStatusSchema>;

export const CheckinAiJobDtoSchema = z.object({
  id: z.string(),
  checkinId: z.string(),
  checkinDate: z.string(),
  status: z.enum(["pending", "running", "succeeded", "failed"]),
  attempts: z.number().int(),
  maxAttempts: z.number().int(),
  lastError: z.string().nullable().optional(),
});

export type CheckinAiJobDto = z.infer<typeof CheckinAiJobDtoSchema>;

export const CheckinSubmitResultSchema = z.object({
  checkin: CheckinDtoSchema,
  summary: DaySummaryDtoSchema.nullable(),
  status: CheckinSubmitStatusSchema,
  aiJob: CheckinAiJobDtoSchema.nullable().optional(),
});

export type CheckinSubmitResult = z.infer<typeof CheckinSubmitResultSchema>;
