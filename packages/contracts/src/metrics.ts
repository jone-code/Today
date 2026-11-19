import { z } from "zod";

const CountSchema = z.number().int().nonnegative();
const MetricFiltersSchema = z.object({
  platform: z.string().nullable(),
  appVersion: z.string().nullable(),
  eventSchemaVersion: z.number().int().min(1).max(10).nullable(),
});

export const MetricEventCountSchema = z.object({
  eventName: z.string(), eventCount: CountSchema, userCount: CountSchema,
  platform: z.string().optional(), appVersion: z.string().nullable().optional(),
  eventSchemaVersion: CountSchema.optional(),
});
export type MetricEventCount = z.infer<typeof MetricEventCountSchema>;

export const ActivationFunnelSchema = z.object({
  registeredUsers: CountSchema,
  activatedUsers: CountSchema,
  chatUsers: CountSchema,
  valueUsers: CountSchema,
  northStarUsers: CountSchema,
  checkinsSaved: CountSchema,
  checkinSavedEvents: CountSchema,
  /** Users with a saved business fact but no matching save event in the range. */
  checkinUsersMissingSavedEvent: CountSchema.default(0),
  /** Users with a save event but no saved business fact in the range. */
  savedEventUsersMissingCheckin: CountSchema.default(0),
});
export type ActivationFunnel = z.infer<typeof ActivationFunnelSchema>;

export const ActivationMetricsSchema = z.object({
  from: z.string().date(), to: z.string().date(),
  filters: MetricFiltersSchema,
  funnel: ActivationFunnelSchema,
  events: z.array(MetricEventCountSchema),
});
export type ActivationMetrics = z.infer<typeof ActivationMetricsSchema>;

export const RetentionMetricsSchema = z.object({
  from: z.string().date(), to: z.string().date(),
  cohort: z.object({ d1: CountSchema, d3: CountSchema, d7: CountSchema }),
});
export type RetentionMetrics = z.infer<typeof RetentionMetricsSchema>;

const TaskQualitySchema = z.object({
  type: z.string(), status: z.string(), taskCount: CountSchema,
});
const VoiceQualitySchema = z.object({
  status: z.string(), errorCode: z.string().nullable(), taskCount: CountSchema,
});
const FeedbackQualitySchema = z.object({
  kind: z.string(), feedbackCount: CountSchema, userCount: CountSchema,
});
export const QualityMetricsSchema = z.object({
  from: z.string().date(), to: z.string().date(),
  filters: MetricFiltersSchema,
  events: z.array(MetricEventCountSchema),
  dataRequests: z.array(TaskQualitySchema),
  voiceTranscriptions: z.array(VoiceQualitySchema),
  feedback: z.array(FeedbackQualitySchema),
});
export type QualityMetrics = z.infer<typeof QualityMetricsSchema>;
