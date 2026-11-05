import { z } from "zod";

export const FeedbackCreateRequestSchema = z.object({
  kind: z.enum(["issue", "idea"]),
  body: z.string().trim().min(1).max(1000),
});
export type FeedbackCreateRequest = z.infer<typeof FeedbackCreateRequestSchema>;

export const FeedbackSchema = FeedbackCreateRequestSchema.extend({
  id: z.string().uuid(),
  createdAt: z.string().datetime(),
});
export type Feedback = z.infer<typeof FeedbackSchema>;
