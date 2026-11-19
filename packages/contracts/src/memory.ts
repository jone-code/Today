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

export const MemoryContextScopeSchema = z.enum(["chat_and_timeline", "timeline_only", "disabled"]);
export type MemoryContextScope = z.infer<typeof MemoryContextScopeSchema>;

export const MemoryDtoSchema = z.object({
  id: z.string(),
  userId: z.string(),
  category: MemoryCategorySchema,
  text: z.string(),
  strength: z.number().int().min(1),
  archived: z.boolean(),
  chatContextScope: MemoryContextScopeSchema,
  updatedAt: z.string().datetime(),
});

export type MemoryDto = z.infer<typeof MemoryDtoSchema>;

export const MemoryOriginSchema = z.object({
  kind: z.enum(["checkin", "conversation"]),
  sourceId: z.string(),
  date: z.string().nullable(),
  conversationId: z.string().nullable(),
});

export type MemoryOrigin = z.infer<typeof MemoryOriginSchema>;
export const MemoryAccessAuditSchema = z.object({ id: z.string(), memoryId: z.string(), action: z.string(), source: z.string(), occurredAt: z.string() });
export type MemoryAccessAudit = z.infer<typeof MemoryAccessAuditSchema>;

export const MemoryUpdateRequestSchema = z.object({
  text: z.string().min(1).max(512).optional(),
  category: MemoryCategorySchema.optional(),
});

export const MemoryContextScopeRequestSchema = z.object({ scope: MemoryContextScopeSchema, expectedUpdatedAt: z.string().datetime().optional() });
export type MemoryContextScopeRequest = z.infer<typeof MemoryContextScopeRequestSchema>;

export type MemoryUpdateRequest = z.infer<typeof MemoryUpdateRequestSchema>;

export const MemoryListDtoSchema = z.object({
  items: z.array(MemoryDtoSchema),
});

export type MemoryListDto = z.infer<typeof MemoryListDtoSchema>;
