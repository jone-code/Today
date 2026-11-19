import { z } from "zod";

export const ProductEventSchema = z.object({
  eventName: z.string().trim().min(1).max(80),
  platform: z.enum(["android", "ios", "web", "unknown"]).optional(),
  eventSchemaVersion: z.number().int().min(1).max(10).optional(),
  occurredAt: z.string().datetime({ offset: true }).optional(),
  screen: z.string().trim().max(40).optional(),
  entityType: z.string().trim().max(40).optional(),
  entityId: z.string().trim().max(128).optional(),
  sessionId: z.string().trim().max(128).optional(),
  clientRequestId: z.string().trim().max(128).optional(),
  appVersion: z.string().trim().max(32).optional(),
  propertiesJson: z.string().max(4000).optional(),
});
export type ProductEvent = z.infer<typeof ProductEventSchema>;
