import { z } from "zod";

export const AgentProfileSchema = z.object({
  userId: z.string(),
  name: z.string(),
  tone: z.string(),
  avatarMediaId: z.string().nullable(),
  avatarUrl: z.string().nullable(),
  version: z.number().int(),
});
export type AgentProfile = z.infer<typeof AgentProfileSchema>;

export const ConversationMessageSchema = z.object({
  id: z.string(),
  conversationId: z.string(),
  role: z.enum(["user", "assistant"]),
  body: z.string(),
  status: z.string(),
  createdAt: z.string().datetime(),
  clientMessageId: z.string().nullable(),
});
export type ConversationMessage = z.infer<typeof ConversationMessageSchema>;

export const ConversationMessagePageSchema = z.object({
  messages: z.array(ConversationMessageSchema),
  hasMore: z.boolean(),
});
export type ConversationMessagePage = z.infer<typeof ConversationMessagePageSchema>;

export const ConversationSchema = z.object({
  id: z.string(),
  title: z.string().nullable(),
  status: z.string(),
  version: z.number().int(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  messages: z.array(ConversationMessageSchema),
});
export type Conversation = z.infer<typeof ConversationSchema>;

export const ConversationCreateRequestSchema = z.object({
  title: z.string().trim().max(128).optional(),
  clientRequestId: z.string().trim().min(1).max(128).optional(),
});
export type ConversationCreateRequest = z.infer<typeof ConversationCreateRequestSchema>;

export const ConversationMessageRequestSchema = z.object({
  body: z.string().trim().min(1).max(4000),
  clientMessageId: z.string().trim().min(1).max(128),
});
export type ConversationMessageRequest = z.infer<typeof ConversationMessageRequestSchema>;

export const AgentProposalSchema = z.object({
  id: z.string(),
  kind: z.string(),
  payloadJson: z.string(),
  status: z.string(),
  sourceMessageId: z.string(),
  createdAt: z.string().datetime(),
});
export type AgentProposal = z.infer<typeof AgentProposalSchema>;

export const AgentCheckinPreviewUpdateSchema = z.object({
  date: z.string().date(),
  text: z.string().trim().min(1).max(2000).optional(),
});
export type AgentCheckinPreviewUpdate = z.infer<typeof AgentCheckinPreviewUpdateSchema>;

export const AgentActionSchema = z.object({
  proposalId: z.string(),
  kind: z.string(),
  targetId: z.string(),
  summary: z.string(),
  result: z.string(),
  executedAt: z.string().datetime(),
  undoable: z.boolean(),
  undoneAt: z.string().datetime().nullable(),
});
export type AgentAction = z.infer<typeof AgentActionSchema>;

export const AppointmentSchema = z.object({
  id: z.string(),
  title: z.string(),
  dueAt: z.string().datetime(),
  timezone: z.string(),
  status: z.string(),
  conversationId: z.string(),
});
export type Appointment = z.infer<typeof AppointmentSchema>;

export const InboxItemSchema = z.object({
  id: z.string(),
  kind: z.string(),
  sourceKind: z.string(),
  sourceId: z.string(),
  body: z.string(),
  scheduledAt: z.string().datetime(),
  state: z.string(),
  createdAt: z.string().datetime(),
  conversationId: z.string().nullable(),
  snoozedUntil: z.string().datetime().nullable(),
});
export type InboxItem = z.infer<typeof InboxItemSchema>;

export const InboxUnreadCountSchema = z.object({ count: z.number().int().nonnegative() });

export const FollowupPreferenceSchema = z.object({
  enabled: z.boolean(),
  maxPerDay: z.number().int().min(0).max(1),
  maxPerWeek: z.number().int().min(0).max(3),
  timezone: z.string(),
});
export type FollowupPreference = z.infer<typeof FollowupPreferenceSchema>;

export const FollowupTopicSchema = z.object({
  id: z.string(),
  reason: z.string(),
  state: z.enum(["authorized", "paused"]),
  nextEligibleAt: z.string().datetime(),
  createdAt: z.string().datetime(),
});
export type FollowupTopic = z.infer<typeof FollowupTopicSchema>;

export const FollowupTopicUpdateSchema = z.object({
  reason: z.string().trim().min(1).max(200),
  nextDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  expectedReason: z.string().min(1),
  expectedNextEligibleAt: z.string().datetime(),
});
export type FollowupTopicUpdate = z.infer<typeof FollowupTopicUpdateSchema>;

export const ConversationMessageResultSchema = z.object({
  conversation: ConversationSchema,
  userMessage: ConversationMessageSchema,
  assistantMessage: ConversationMessageSchema,
  provider: z.string(),
  proposals: z.array(AgentProposalSchema),
});
