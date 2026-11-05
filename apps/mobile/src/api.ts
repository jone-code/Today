import {
  ApiRoutes,
  AgentActionSchema,
  AgentCheckinPreviewUpdateSchema,
  AppointmentSchema,
  AgentProfileSchema,
  AgentProposalSchema,
  AuthTokenResponseSchema,
  CheckinMemoryCandidateSchema,
  CheckinMemoryCandidateConfirmSchema,
  CheckinDtoSchema,
  CheckinSubmitResultSchema,
  ConversationMessageResultSchema,
  ConversationMessagePageSchema,
  ConversationCreateRequestSchema,
  ConversationSchema,
  DaySummaryDtoSchema,
  FollowupPreferenceSchema,
  FollowupTopicSchema,
  FollowupTopicUpdateSchema,
  FeedbackCreateRequestSchema,
  FeedbackSchema,
  InboxItemSchema,
  InboxUnreadCountSchema,
  MemoryListDtoSchema,
  MemoryDtoSchema,
  MemoryOriginSchema,
  MemoryUpdateRequestSchema,
  PunchHabitCreateInputSchema,
  PunchHabitDtoSchema,
  PunchHabitListDtoSchema,
  PunchHabitUpdateInputSchema,
  PunchLogDtoSchema,
  ReminderCreateInputSchema,
  ReminderDtoSchema,
  ReminderListDtoSchema,
  ReminderUpdateInputSchema,
  TimelinePageDtoSchema,
  TodoListDtoSchema,
  UserDtoSchema,
  type PunchHabitCreateInput,
  type PunchHabitUpdateInput,
  type ReminderCreateInput,
  type ReminderUpdateInput,
  type FollowupTopicUpdate,
  type FeedbackCreateRequest,
} from "@today/contracts";
import { z } from "zod";

export const profileSchema = AgentProfileSchema;
export const conversationSchema = ConversationSchema;
export const proposalSchema = AgentProposalSchema;
export const inboxSchema = InboxItemSchema;
export const checkinCandidateSchema = CheckinMemoryCandidateSchema;
export const checkinSubmitSchema = CheckinSubmitResultSchema;
export const timelineSchema = TimelinePageDtoSchema;
export const todoListSchema = TodoListDtoSchema;
export const memoryListSchema = MemoryListDtoSchema;

export const todaySchema = z.object({
  checkin: z.object({ id: z.string(), date: z.string(), rawText: z.string() }).passthrough().nullable(),
  aiJob: z.object({ status: z.string() }).passthrough().nullable(),
});

export class ApiRequestError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export function normalizeServerUrl(value: string): string {
  const input = value.trim().replace(/\/+$/, "");
  let url: URL;
  try { url = new URL(input); }
  catch { throw new Error("请输入完整的服务器地址，以 https:// 开头"); }
  if (url.protocol !== "https:" && url.protocol !== "http:") {
    throw new Error("请输入完整的服务器地址，以 https:// 开头");
  }
  if (url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error("服务器地址只能包含域名和端口");
  }
  if (!__DEV__ && url.protocol !== "https:") throw new Error("正式版本仅支持 HTTPS 服务器");
  return url.origin.toLowerCase();
}

export class TodayApi {
  constructor(public readonly baseUrl: string, public readonly token: string | null = null,
    private readonly onUnauthorized?: () => void) {}

  async request<T>(path: string, schema: z.ZodType<T>, options: RequestInit = {}, timeoutMs = 60000): Promise<T> {
    const headers = new Headers(options.headers);
    if (this.token) headers.set("Authorization", `Bearer ${this.token}`);
    if (options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, { ...options, headers, signal: controller.signal });
      if (!response.ok) {
        if (response.status === 401 && this.token) this.onUnauthorized?.();
        const data = await response.json().catch(() => ({})) as { message?: string; detail?: string };
        throw new ApiRequestError(response.status,
          response.status === 401 ? "登录已过期，请重新登录" : data.message || data.detail || `请求失败（${response.status}）`);
      }
      if (response.status === 204) return schema.parse(null);
      const raw = await response.text();
      return schema.parse(raw ? JSON.parse(raw) : null);
    } catch (error) {
      if (error instanceof ApiRequestError || error instanceof z.ZodError) throw error;
      throw new Error(error instanceof Error && error.name === "AbortError"
        ? "请求超时，原消息仍可重试" : "无法连接服务器，请检查网络和服务器地址");
    } finally {
      clearTimeout(timeout);
    }
  }

  login(email: string, password: string) {
    return this.request(ApiRoutes.authLogin, AuthTokenResponseSchema,
      { method: "POST", body: JSON.stringify({ email, password }) });
  }
  feedback() { return this.request(ApiRoutes.feedback, z.array(FeedbackSchema)); }
  submitFeedback(input: FeedbackCreateRequest, requestId: string) {
    return this.request(ApiRoutes.feedback, FeedbackSchema,
      { method: "POST", headers: { "Idempotency-Key": requestId },
        body: JSON.stringify(FeedbackCreateRequestSchema.parse(input)) });
  }
  deleteFeedback(id: string) {
    return this.request(`${ApiRoutes.feedback}/${encodeURIComponent(id)}`, z.null(), { method: "DELETE" });
  }
  register(email: string, password: string, displayName: string) {
    return this.request(ApiRoutes.authRegister, AuthTokenResponseSchema,
      { method: "POST", body: JSON.stringify({ email, password, displayName }) });
  }
  me(timeoutMs = 60000) { return this.request(ApiRoutes.authMe, UserDtoSchema, {}, timeoutMs); }
  profile() { return this.request(ApiRoutes.agentProfile, AgentProfileSchema); }
  updateProfile(name: string, tone: string) {
    return this.request(ApiRoutes.agentProfile, AgentProfileSchema,
      { method: "PUT", body: JSON.stringify({ name, tone }) });
  }
  avatarUrl() { return `${this.baseUrl}/v1/agent/avatar`; }
  async uploadAvatar(uri: string, requestId: string) {
    const form = new FormData();
    form.append("avatar", { uri, name: "avatar.jpg", type: "image/jpeg" } as unknown as Blob);
    return this.request("/v1/agent/avatar", AgentProfileSchema,
      { method: "POST", headers: { "Idempotency-Key": requestId }, body: form });
  }
  resetAvatar() { return this.request("/v1/agent/avatar", AgentProfileSchema, { method: "DELETE" }); }
  conversations() { return this.request(ApiRoutes.agentConversations, z.array(ConversationSchema)); }
  conversation(id: string) { return this.request(`${ApiRoutes.agentConversations}/${encodeURIComponent(id)}`, ConversationSchema); }
  conversationMessages(id: string, before: string, beforeId: string) {
    const path = ApiRoutes.agentConversationMessages.replace(":id", encodeURIComponent(id));
    return this.request(`${path}?before=${encodeURIComponent(before)}&beforeId=${encodeURIComponent(beforeId)}`,
      ConversationMessagePageSchema);
  }
  createConversation(clientRequestId: string) {
    return this.request(ApiRoutes.agentConversations, ConversationSchema,
      { method: "POST", body: JSON.stringify(ConversationCreateRequestSchema.parse({ clientRequestId })) });
  }
  send(conversationId: string, body: string, clientMessageId: string) {
    return this.request(`${ApiRoutes.agentConversations}/${encodeURIComponent(conversationId)}/messages`,
      ConversationMessageResultSchema, { method: "POST", body: JSON.stringify({ body, clientMessageId }) });
  }
  retryMessage(conversationId: string, messageId: string) {
    const path = ApiRoutes.agentConversationMessageRetry
      .replace(":id", encodeURIComponent(conversationId))
      .replace(":messageId", encodeURIComponent(messageId));
    return this.request(path, ConversationMessageResultSchema, { method: "POST" });
  }
  deleteConversation(id: string) {
    return this.request(`${ApiRoutes.agentConversations}/${encodeURIComponent(id)}`, z.any(), { method: "DELETE" });
  }
  proposals() { return this.request(ApiRoutes.agentProposals, z.array(AgentProposalSchema)); }
  actions() { return this.request(ApiRoutes.agentActions, z.array(AgentActionSchema)); }
  undoAction(proposalId: string) {
    return this.request(`${ApiRoutes.agentActions}/${encodeURIComponent(proposalId)}/undo`, AgentActionSchema,
      { method: "POST" });
  }
  appointments() { return this.request(ApiRoutes.agentAppointments, z.array(AppointmentSchema)); }
  cancelAppointment(id: string) {
    return this.request(`${ApiRoutes.agentAppointments}/${encodeURIComponent(id)}/cancel`, z.any(), { method: "POST" });
  }
  proposal(id: string, action: "confirm" | "reject") {
    return this.request(`${ApiRoutes.agentProposals}/${encodeURIComponent(id)}/${action}`, AgentProposalSchema, { method: "POST" });
  }
  updateCheckinPreview(id: string, date: string, text?: string) {
    const input = AgentCheckinPreviewUpdateSchema.parse({ date, text });
    return this.request(ApiRoutes.agentCheckinPreview.replace(":id", encodeURIComponent(id)), AgentProposalSchema,
      { method: "PATCH", body: JSON.stringify(input) });
  }
  inbox() { return this.request(ApiRoutes.agentInbox, z.array(InboxItemSchema)); }
  unreadCount() { return this.request(ApiRoutes.agentInboxUnreadCount, InboxUnreadCountSchema); }
  updateInbox(id: string, action: "read" | "dismiss" | "continue") {
    return this.request(`${ApiRoutes.agentInbox}/${encodeURIComponent(id)}/${action}`, InboxItemSchema, { method: "POST" });
  }
  snoozeInbox(id: string, date: string) {
    return this.request(ApiRoutes.agentInboxSnooze.replace(":id", encodeURIComponent(id)), InboxItemSchema,
      { method: "POST", body: JSON.stringify({ date }) });
  }
  stopTopic(id: string) {
    return this.request(ApiRoutes.agentFollowupTopicStop.replace(":id", encodeURIComponent(id)), z.any(), { method: "POST" });
  }
  followupTopics() {
    return this.request(ApiRoutes.agentFollowupTopics, z.array(FollowupTopicSchema));
  }
  updateFollowupTopic(id: string, input: FollowupTopicUpdate) {
    return this.request(ApiRoutes.agentFollowupTopic.replace(":id", encodeURIComponent(id)), FollowupTopicSchema,
      { method: "PATCH", body: JSON.stringify(FollowupTopicUpdateSchema.parse(input)) });
  }
  resumeTopic(id: string) {
    return this.request(ApiRoutes.agentFollowupTopicResume.replace(":id", encodeURIComponent(id)),
      z.any(), { method: "POST" });
  }
  followupPreferences() {
    return this.request(ApiRoutes.agentFollowupPreferences, FollowupPreferenceSchema);
  }
  updateFollowupPreferences(enabled: boolean, maxPerWeek: number, timezone: string) {
    return this.request(ApiRoutes.agentFollowupPreferences, FollowupPreferenceSchema,
      { method: "PATCH", body: JSON.stringify({ enabled, maxPerWeek, maxPerDay: 1, timezone }) });
  }
  today() { return this.request(ApiRoutes.checkinToday, todaySchema); }
  async summary(date: string) {
    try {
      return await this.request(`/v1/summaries/${encodeURIComponent(date)}`, DaySummaryDtoSchema);
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) return null;
      throw error;
    }
  }
  retryToday() {
    return this.request(ApiRoutes.checkinReprocessToday, CheckinSubmitResultSchema, { method: "POST" });
  }
  submitCheckin(rawText: string, date?: string, requestId?: string) {
    return this.request(ApiRoutes.checkins, CheckinSubmitResultSchema,
      { method: "POST", ...(requestId ? { headers: { "Idempotency-Key": requestId } } : {}),
        body: JSON.stringify({ rawText, date }) });
  }
  checkinCandidates(id: string) {
    return this.request(`${ApiRoutes.checkins}/${encodeURIComponent(id)}/memory-candidates`, z.array(CheckinMemoryCandidateSchema));
  }
  updateCheckinCandidate(id: string, action: "confirm" | "reject", text?: string) {
    return this.request(`${ApiRoutes.checkins}/memory-candidates/${encodeURIComponent(id)}/${action}`,
      CheckinMemoryCandidateSchema, { method: "POST", ...(action === "confirm" && text !== undefined
        ? { body: JSON.stringify(CheckinMemoryCandidateConfirmSchema.parse({ text })) } : {}) });
  }
  timeline() { return this.request(ApiRoutes.timeline, TimelinePageDtoSchema); }
  memories(includeArchived = false) {
    return this.request(`${ApiRoutes.memories}${includeArchived ? "?includeArchived=true" : ""}`, MemoryListDtoSchema);
  }
  memoryOrigins(id: string) {
    return this.request(`${ApiRoutes.memories}/${encodeURIComponent(id)}/origins`, z.array(MemoryOriginSchema));
  }
  checkin(id: string) {
    return this.request(`${ApiRoutes.checkins}/${encodeURIComponent(id)}`, CheckinDtoSchema);
  }
  updateMemory(id: string, text: string) {
    return this.request(`${ApiRoutes.memories}/${encodeURIComponent(id)}`, MemoryDtoSchema,
      { method: "PUT", body: JSON.stringify(MemoryUpdateRequestSchema.parse({ text: text.trim() })) });
  }
  archiveMemory(id: string, archived: boolean) {
    return this.request(`${ApiRoutes.memories}/${encodeURIComponent(id)}/${archived ? "archive" : "unarchive"}`,
      MemoryDtoSchema, { method: "POST" });
  }
  deleteMemory(id: string) {
    return this.request(`${ApiRoutes.memories}/${encodeURIComponent(id)}`, z.unknown(), { method: "DELETE" });
  }
  todos() { return this.request(ApiRoutes.todos, TodoListDtoSchema); }
  setTodoStatus(id: string, status: "open" | "done") {
    return this.request(`${ApiRoutes.todos}/${encodeURIComponent(id)}`, z.unknown(),
      { method: "PUT", body: JSON.stringify({ status }) });
  }
  createTodo(title: string, requestId: string) {
    return this.request(ApiRoutes.todos, z.unknown(), { method: "POST",
      headers: { "Idempotency-Key": requestId }, body: JSON.stringify({ title }) });
  }
  habits(date: string) {
    return this.request(`/v1/punch/habits?date=${encodeURIComponent(date)}`, PunchHabitListDtoSchema);
  }
  createHabit(input: PunchHabitCreateInput, requestId: string) {
    return this.request("/v1/punch/habits", PunchHabitDtoSchema,
      { method: "POST", headers: { "Idempotency-Key": requestId },
        body: JSON.stringify(PunchHabitCreateInputSchema.parse(input)) });
  }
  updateHabit(id: string, input: PunchHabitUpdateInput) {
    return this.request(`/v1/punch/habits/${encodeURIComponent(id)}`, PunchHabitDtoSchema,
      { method: "PUT", body: JSON.stringify(PunchHabitUpdateInputSchema.parse(input)) });
  }
  deleteHabit(id: string) {
    return this.request(`/v1/punch/habits/${encodeURIComponent(id)}`, z.unknown(), { method: "DELETE" });
  }
  punch(id: string, date: string, requestId: string) {
    return this.request(`/v1/punch/habits/${encodeURIComponent(id)}/punch`, PunchLogDtoSchema,
      { method: "POST", headers: { "Idempotency-Key": requestId }, body: JSON.stringify({ date }) });
  }
  async punchWithPhoto(id: string, date: string, uri: string, requestId: string) {
    const form = new FormData();
    form.append("date", date);
    form.append("photo", { uri, name: "punch.jpg", type: "image/jpeg" } as unknown as Blob);
    return this.request(`/v1/punch/habits/${encodeURIComponent(id)}/punch`, PunchLogDtoSchema,
      { method: "POST", headers: { "Idempotency-Key": requestId }, body: form });
  }
  unpunch(id: string, date: string) {
    return this.request(`/v1/punch/habits/${encodeURIComponent(id)}/punch?date=${encodeURIComponent(date)}`,
      z.unknown(), { method: "DELETE" });
  }
  reminders() { return this.request("/v1/reminders", ReminderListDtoSchema); }
  createReminder(input: ReminderCreateInput, requestId: string) {
    return this.request("/v1/reminders", ReminderDtoSchema,
      { method: "POST", headers: { "Idempotency-Key": requestId },
        body: JSON.stringify(ReminderCreateInputSchema.parse(input)) });
  }
  updateReminder(id: string, input: ReminderUpdateInput) {
    return this.request(`/v1/reminders/${encodeURIComponent(id)}`, ReminderDtoSchema,
      { method: "PUT", body: JSON.stringify(ReminderUpdateInputSchema.parse(input)) });
  }
  deleteReminder(id: string) {
    return this.request(`/v1/reminders/${encodeURIComponent(id)}`, z.unknown(), { method: "DELETE" });
  }
}
