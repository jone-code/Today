import { z } from "zod";

/** checkin — 每日记录 */
export const CheckinCreateInputSchema = z.object({
  rawText: z.string().trim().min(1).max(2000),
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
});

export type CheckinCreateInput = z.infer<typeof CheckinCreateInputSchema>;

export const CheckinDtoSchema = z.object({
  id: z.string(),
  userId: z.string(),
  date: z.string(),
  rawText: z.string(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export type CheckinDto = z.infer<typeof CheckinDtoSchema>;
