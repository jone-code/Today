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
  updatedAt: z.string().datetime(),
});

export type MemoryDto = z.infer<typeof MemoryDtoSchema>;

export const MemoryListDtoSchema = z.object({
  items: z.array(MemoryDtoSchema),
});

export type MemoryListDto = z.infer<typeof MemoryListDtoSchema>;
