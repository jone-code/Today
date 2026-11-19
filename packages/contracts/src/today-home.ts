import { z } from "zod";
import { CheckinAiJobDtoSchema } from "./checkin-submit";
import { CheckinDtoSchema } from "./checkin";
import { DaySummaryDtoSchema } from "./summary";

export const TodayHomeSchema = z.object({
  checkin: CheckinDtoSchema.nullable(),
  aiJob: CheckinAiJobDtoSchema.nullable(),
  summary: DaySummaryDtoSchema.nullable(),
  pendingMemoryCount: z.number().int().nonnegative(),
  pendingProposalCount: z.number().int().nonnegative(),
  unreadInboxCount: z.number().int().nonnegative(),
  openTodoCount: z.number().int().nonnegative(),
  habitsTotal: z.number().int().nonnegative(),
  habitsDone: z.number().int().nonnegative(),
  enabledReminderCount: z.number().int().nonnegative(),
});

export type TodayHome = z.infer<typeof TodayHomeSchema>;
