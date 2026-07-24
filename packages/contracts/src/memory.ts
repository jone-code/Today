import { z } from "zod";

/** memory — 长期记忆 */
export const MemoryCategorySchema = z.enum([
  "work",
  "health",
  "learning",
  "life",
  "emotion",
  "goal",
]);

export type MemoryCategory = z.infer<typeof MemoryCategorySchema>;

export const MemoryDtoSchema = z.object({
  id: z.string(),
  userId: z.string(),
  category: MemoryCategorySchema,
  text: z.string(),
  strength: z.number().int().min(1),
  archived: z.boolean(),
  updatedAt: z.string().datetime(),
});

export type MemoryDto = z.infer<typeof MemoryDtoSchema>;

export const MemoryUpdateRequestSchema = z.object({
  text: z.string().min(1).max(512).optional(),
  category: MemoryCategorySchema.optional(),
});

export type MemoryUpdateRequest = z.infer<typeof MemoryUpdateRequestSchema>;

export const MemoryListDtoSchema = z.object({
  items: z.array(MemoryDtoSchema),
});

export type MemoryListDto = z.infer<typeof MemoryListDtoSchema>;
