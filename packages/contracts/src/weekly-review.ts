import { z } from "zod";

export const WeeklyReviewStatusSchema = z.enum(["queued", "running", "succeeded", "failed", "expired"]);
export type WeeklyReviewStatus = z.infer<typeof WeeklyReviewStatusSchema>;

export const WeeklyReviewSchema = z.object({
  week: z.string(),
  status: WeeklyReviewStatusSchema,
  attempts: z.number().int().nonnegative(),
  error: z.string().nullable(),
  daysRecorded: z.number().int().nonnegative(),
  completedTodos: z.number().int().nonnegative(),
  highlights: z.array(z.string()),
  reflection: z.string(),
  generatedAt: z.string().datetime().nullable(),
  retryable: z.boolean(),
});
export type WeeklyReview = z.infer<typeof WeeklyReviewSchema>;
