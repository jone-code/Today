import { z } from "zod";

export const VoiceTranscriptionStatusSchema = z.enum(["queued", "uploading", "transcribing", "succeeded", "failed", "expired"]);
export const VoiceTranscriptionJobSchema = z.object({
  id: z.string(), status: VoiceTranscriptionStatusSchema, transcript: z.string().nullable(),
  errorCode: z.string().nullable(), attempts: z.number().int().nonnegative(),
  createdAt: z.string().datetime(), updatedAt: z.string().datetime(), expiresAt: z.string().datetime(),
  completedAt: z.string().datetime().nullable(), retryable: z.boolean(),
});
export type VoiceTranscriptionJob = z.infer<typeof VoiceTranscriptionJobSchema>;
