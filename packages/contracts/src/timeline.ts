import { z } from "zod";
import { MoodSchema } from "./summary";

/** timeline — 时间轴读模型 */
export const TimelineItemDtoSchema = z.object({
  date: z.string(),
  highlight: z.string(),
  oneLiner: z.string(),
  mood: MoodSchema,
  moodLabel: z.string(),
  checkinId: z.string(),
});

export type TimelineItemDto = z.infer<typeof TimelineItemDtoSchema>;

export const TimelinePageDtoSchema = z.object({
  items: z.array(TimelineItemDtoSchema),
  nextCursor: z.string().nullable(),
});

export type TimelinePageDto = z.infer<typeof TimelinePageDtoSchema>;
