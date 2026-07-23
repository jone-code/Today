import { z } from "zod";

/** reminder — 每日定时提醒 */
export const ReminderCreateInputSchema = z.object({
  title: z.string().trim().min(1).max(128),
  message: z.string().trim().min(1).max(512),
  remindTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/),
  timezone: z.string().min(1).max(64).default("Asia/Shanghai"),
  enabled: z.boolean().default(true),
});

export type ReminderCreateInput = z.infer<typeof ReminderCreateInputSchema>;

export const ReminderUpdateInputSchema = z.object({
  title: z.string().trim().min(1).max(128).optional(),
  message: z.string().trim().min(1).max(512).optional(),
  remindTime: z
    .string()
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/)
    .optional(),
  timezone: z.string().min(1).max(64).optional(),
  enabled: z.boolean().optional(),
});

export type ReminderUpdateInput = z.infer<typeof ReminderUpdateInputSchema>;

export const ReminderDtoSchema = z.object({
  id: z.string(),
  userId: z.string(),
  title: z.string(),
  message: z.string(),
  remindTime: z.string(),
  timezone: z.string(),
  enabled: z.boolean(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export type ReminderDto = z.infer<typeof ReminderDtoSchema>;

export const ReminderListDtoSchema = z.object({
  items: z.array(ReminderDtoSchema),
});

export type ReminderListDto = z.infer<typeof ReminderListDtoSchema>;

export const ReminderDeliveryDtoSchema = z.object({
  id: z.string(),
  reminderId: z.string(),
  userId: z.string(),
  fireDate: z.string(),
  title: z.string(),
  message: z.string(),
  status: z.enum(["pending", "read"]),
  createdAt: z.string().datetime(),
  readAt: z.string().datetime().nullable(),
});

export type ReminderDeliveryDto = z.infer<typeof ReminderDeliveryDtoSchema>;

export const ReminderDeliveryListDtoSchema = z.object({
  items: z.array(ReminderDeliveryDtoSchema),
});

export type ReminderDeliveryListDto = z.infer<
  typeof ReminderDeliveryListDtoSchema
>;
