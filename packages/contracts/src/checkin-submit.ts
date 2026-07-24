import { z } from "zod";
import { DaySummaryDtoSchema } from "./summary";
import { CheckinDtoSchema } from "./checkin";

/** checkin 提交结果：async 时 summary 可能暂为空，前端轮询 summaryByDate */
export const CheckinSubmitStatusSchema = z.enum(["processing", "ready"]);

export type CheckinSubmitStatus = z.infer<typeof CheckinSubmitStatusSchema>;

export const CheckinSubmitResultSchema = z.object({
  checkin: CheckinDtoSchema,
  summary: DaySummaryDtoSchema.nullable(),
  status: CheckinSubmitStatusSchema,
});

export type CheckinSubmitResult = z.infer<typeof CheckinSubmitResultSchema>;
