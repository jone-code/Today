import { z } from "zod";

/** punch — 习惯打卡 */
export const PunchHabitCreateInputSchema = z.object({
  title: z.string().trim().min(1).max(128),
  description: z.string().trim().max(512).optional().nullable(),
  enabled: z.boolean().optional(),
});

export type PunchHabitCreateInput = z.infer<typeof PunchHabitCreateInputSchema>;

export const PunchHabitUpdateInputSchema = z.object({
  title: z.string().trim().min(1).max(128).optional(),
  description: z.string().trim().max(512).optional().nullable(),
  enabled: z.boolean().optional(),
});

export type PunchHabitUpdateInput = z.infer<typeof PunchHabitUpdateInputSchema>;

export const PunchHabitDtoSchema = z.object({
  id: z.string(),
  userId: z.string(),
  title: z.string(),
  description: z.string().nullable(),
  enabled: z.boolean(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  punchedToday: z.boolean().optional(),
  streak: z.number().int().nonnegative().optional(),
  todayPhotoUrl: z.string().nullable().optional(),
});

export type PunchHabitDto = z.infer<typeof PunchHabitDtoSchema>;

export const PunchHabitListDtoSchema = z.object({
  items: z.array(PunchHabitDtoSchema),
  date: z.string(),
});

export type PunchHabitListDto = z.infer<typeof PunchHabitListDtoSchema>;

export const PunchToggleInputSchema = z.object({
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
  note: z.string().trim().max(512).optional().nullable(),
});

export type PunchToggleInput = z.infer<typeof PunchToggleInputSchema>;

export const PunchLogDtoSchema = z.object({
  id: z.string(),
  habitId: z.string(),
  userId: z.string(),
  punchDate: z.string(),
  note: z.string().nullable(),
  photoUrl: z.string().nullable().optional(),
  createdAt: z.string().datetime(),
});

export type PunchLogDto = z.infer<typeof PunchLogDtoSchema>;
