import { z } from "zod";

/** proactive — 主动关联 */
export const ProactivePromptDtoSchema = z.object({
  id: z.string(),
  text: z.string(),
  relatedDate: z.string().optional(),
  source: z.enum(["followup", "pattern", "memory", "gentle"]),
});

export type ProactivePromptDto = z.infer<typeof ProactivePromptDtoSchema>;

export const ProactiveTodayDtoSchema = z.object({
  date: z.string(),
  prompts: z.array(ProactivePromptDtoSchema).max(3),
  provider: z.enum(["llm", "heuristic"]),
});

export type ProactiveTodayDto = z.infer<typeof ProactiveTodayDtoSchema>;
