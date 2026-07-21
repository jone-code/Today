import { z } from "zod";

/** summary — AI 日总结 */
export const MoodSchema = z.enum(["great", "good", "okay", "tired", "low"]);

export type Mood = z.infer<typeof MoodSchema>;

export const DaySummaryDtoSchema = z.object({
  checkinId: z.string(),
  date: z.string(),
  completed: z.array(z.string()),
  mood: MoodSchema,
  moodLabel: z.string(),
  keywords: z.array(z.string()),
  oneLiner: z.string(),
  highlight: z.string(),
  provider: z.enum(["llm", "heuristic"]),
  createdAt: z.string().datetime(),
});

export type DaySummaryDto = z.infer<typeof DaySummaryDtoSchema>;
