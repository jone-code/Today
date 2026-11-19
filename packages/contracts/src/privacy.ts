import { z } from "zod";

export const PrivacySettingsSchema = z.object({
  userId: z.string(), chatMemoryEnabled: z.boolean(),
  voiceRetention: z.literal("delete_on_success"), followupEnabled: z.boolean(), version: z.number().int(), updatedAt: z.string().datetime(),
});
export type PrivacySettings = z.infer<typeof PrivacySettingsSchema>;
export const PrivacySettingsRequestSchema = z.object({
  version: z.number().int().min(1),
  chatMemoryEnabled: z.boolean(), voiceRetention: z.literal("delete_on_success").optional(), followupEnabled: z.boolean(),
});
export type PrivacySettingsRequest = z.infer<typeof PrivacySettingsRequestSchema>;
