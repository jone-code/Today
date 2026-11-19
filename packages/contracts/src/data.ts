import { z } from "zod";
export const DataRequestSchema = z.object({ id:z.string(), type:z.string(), status:z.string(), requestedAt:z.string(), completedAt:z.string().nullable(), expiresAt:z.string().nullable(), retryable:z.boolean(), downloadToken:z.string().nullable().optional() });
export type DataRequest = z.infer<typeof DataRequestSchema>;
