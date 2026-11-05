#!/usr/bin/env node
// Run against a disposable Today database and scripts/mock-agent-model.mjs.
import { randomUUID } from "node:crypto";

const base = process.env.API_BASE_URL || "http://127.0.0.1:3101";
const mockBase = process.env.MOCK_MODEL_BASE_URL || "http://127.0.0.1:39011";
const email = `agent.smoke.${randomUUID()}@example.com`;
const password = `Smoke-${randomUUID()}`;

async function request(path, { method = "GET", token, body, status = 200, headers = {} } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (response.status !== status) {
    throw new Error(`${method} ${path}: expected ${status}, got ${response.status} ${JSON.stringify(data)}`);
  }
  return data;
}

function check(condition, message) {
  if (!condition) throw new Error(message);
}

async function main() {
  const health = await request("/health");
  check(health.service === "today-api", "not connected to Today API");
  check(health.aiProvider === "llm", "model stub is not configured");
  const { token } = await request("/v1/auth/register", {
    method: "POST", body: { email, password, displayName: "Agent smoke" },
  });
  const { token: otherToken } = await request("/v1/auth/register", {
    method: "POST", body: { email: `other.${email}`, password, displayName: "Other" },
  });
  await request("/v1/agent/profile", { method: "PUT", token,
    body: { name: "小圆", tone: "简短、有分寸" } });
  const conversation = await request("/v1/agent/conversations", {
    method: "POST", token, body: { title: "旅行计划" },
  });
  const clientMessageId = randomUUID();
  const messagePath = `/v1/agent/conversations/${conversation.id}/messages`;
  const style = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_AGENT_PROFILE", clientMessageId: randomUUID() } });
  check(style.assistantMessage.body === "小圆|简短、有分寸", "profile customization missing from model context");
  const input = { body: "TEST_AGENT_ACTION 帮我把整理旅行清单记成待办", clientMessageId };
  const result = await request(messagePath, { method: "POST", token, body: input });
  check(result.proposals.length === 2, "expected todo and memory proposals");
  const replay = await request(messagePath, { method: "POST", token, body: input });
  check(replay.assistantMessage.id === result.assistantMessage.id, "retry duplicated assistant reply");
  check((await request("/v1/todos", { token })).items.length === 0, "todo created before confirmation");
  check((await request("/v1/memories", { token })).items.length === 0, "memory created before confirmation");
  const beforeContext = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_AGENT_CONTEXT", clientMessageId: randomUUID() } });
  check(beforeContext.assistantMessage.body === "无确认记忆", "unconfirmed memory entered model context");
  const todo = result.proposals.find((item) => item.kind === "todo");
  const memory = result.proposals.find((item) => item.kind === "memory");
  await request(`/v1/agent/proposals/${todo.id}/confirm`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/agent/conversations/${conversation.id}`, { token: otherToken, status: 404 });
  await request(`/v1/agent/proposals/${todo.id}/confirm`, { method: "POST", token });
  await request(`/v1/agent/proposals/${todo.id}/confirm`, { method: "POST", token, status: 404 });
  await request(`/v1/agent/proposals/${memory.id}/confirm`, { method: "POST", token });
  check((await request("/v1/todos", { token })).items.length === 1, "todo confirmation not persisted once");
  check((await request("/v1/memories", { token })).items.length === 1, "memory confirmation not persisted");
  const confirmedMemoryId = (await request("/v1/memories", { token })).items[0].id;
  const originPath = `/v1/memories/${confirmedMemoryId}/origins`;
  const origins = await request(originPath, { token });
  check(origins.length === 1 && origins[0].kind === "conversation"
    && origins[0].sourceId === result.userMessage.id
    && origins[0].conversationId === conversation.id,
  "confirmed memory source does not point to the visible conversation");
  await request(originPath, { token: otherToken, status: 404 });
  const confirmedContext = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_AGENT_CONTEXT", clientMessageId: randomUUID() } });
  check(confirmedContext.assistantMessage.body === "已读取确认记忆", "confirmed memory missing from model context");
  const actions = await request("/v1/agent/actions", { token });
  check(actions.length === 2 && actions.every((action) => action.result === "succeeded"), "action log incomplete");
  check((await request("/v1/agent/actions", { token: otherToken })).length === 0, "action log leaked across accounts");
  console.log("conversation proposal, idempotency, confirmation and account isolation passed");

  const todoId = (await request("/v1/todos", { token })).items[0].id;
  const editRequest = { body: "TEST_AGENT_TODO_EDIT 请修改旅行清单的标题", clientMessageId: randomUUID() };
  const firstEdit = await request(messagePath, { method: "POST", token, body: editRequest });
  const editProposal = firstEdit.proposals.find((item) => item.kind === "todo_edit");
  check(editProposal && JSON.parse(editProposal.payloadJson).targetId === todoId, "edit target not bound to visible todo");
  check((await request("/v1/todos", { token })).items[0].title === "整理旅行清单", "todo edited before confirmation");
  const otherConversation = await request("/v1/agent/conversations", {
    method: "POST", token: otherToken, body: {},
  });
  const otherEdit = await request(`/v1/agent/conversations/${otherConversation.id}/messages`, {
    method: "POST", token: otherToken, body: { ...editRequest, clientMessageId: randomUUID() },
  });
  check(otherEdit.proposals.every((item) => item.kind !== "todo_edit"), "another user's todo entered model context");
  await request(`/v1/agent/proposals/${editProposal.id}/confirm`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/todos/${todoId}`, { method: "PUT", token, body: { title: "临时更改" } });
  await request(`/v1/agent/proposals/${editProposal.id}/confirm`, { method: "POST", token, status: 409 });
  check((await request("/v1/todos", { token })).items[0].title === "临时更改", "stale edit overwrote newer todo");
  await request(`/v1/agent/proposals/${editProposal.id}/reject`, { method: "POST", token });
  const secondEdit = await request(messagePath, { method: "POST", token,
    body: { ...editRequest, clientMessageId: randomUUID() } });
  const refreshedEdit = secondEdit.proposals.find((item) => item.kind === "todo_edit");
  check(refreshedEdit, "fresh edit proposal missing");
  await request(`/v1/agent/proposals/${refreshedEdit.id}/confirm`, { method: "POST", token });
  await request(`/v1/agent/proposals/${refreshedEdit.id}/confirm`, { method: "POST", token, status: 404 });
  check((await request("/v1/todos", { token })).items[0].title === "更新旅行清单", "confirmed todo edit missing");
  check((await request("/v1/agent/actions", { token })).length === 3, "todo edit action missing from log");
  console.log("visible todo edit, stale preview protection and confirmation passed");

  const checkinInput = { rawText: "上午开会" };
  const checkinKey = randomUUID();
  const initialCheckin = await request("/v1/checkins", { method: "POST", token,
    body: checkinInput, headers: { "Idempotency-Key": checkinKey } });
  const persistedBeforeReplay = await request("/v1/checkins/today", { token });
  await new Promise(resolve => setTimeout(resolve, 20));
  const checkinReplay = await request("/v1/checkins", { method: "POST", token,
    body: checkinInput, headers: { "Idempotency-Key": checkinKey } });
  check(checkinReplay.checkin.updatedAt === initialCheckin.checkin.updatedAt
    && checkinReplay.aiJob.id === initialCheckin.aiJob.id,
  "checkin retry changed saved record or AI job");
  check((await request("/v1/checkins/today", { token })).checkin.updatedAt === persistedBeforeReplay.checkin.updatedAt,
    "checkin replay updated the database despite returning a cached response");
  await request("/v1/checkins", { method: "POST", token, status: 409,
    body: { rawText: "冲突正文" }, headers: { "Idempotency-Key": checkinKey } });
  check((await request("/v1/checkins/today", { token })).checkin.rawText === "上午开会",
    "conflicting checkin request changed the saved record");
  const recordDraft = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_AGENT_CHECKIN 请整理到今天", clientMessageId: randomUUID() } });
  const recordProposal = recordDraft.proposals.find((item) => item.kind === "checkin");
  check(recordProposal, "checkin proposal missing");
  const originalPreview = JSON.parse(recordProposal.payloadJson);
  check(originalPreview.text === "上午开会\n下午完成了报告", "existing record was not merged in preview");
  check((await request("/v1/checkins/today", { token })).checkin.rawText === "上午开会",
    "record written before confirmation");
  const previewPath = `/v1/agent/proposals/${recordProposal.id}/checkin-preview`;
  await request(previewPath, { method: "PATCH", token: otherToken,
    body: { date: originalPreview.date, text: "非法修改" }, status: 404 });
  await request(previewPath, { method: "PATCH", token,
    body: { date: originalPreview.date, text: "上午开会\n下午完成了报告和校对" } });
  await request("/v1/checkins", { method: "POST", token,
    body: { date: originalPreview.date, rawText: "上午开会\n补充会议纪要" } });
  await request(`/v1/agent/proposals/${recordProposal.id}/confirm`, { method: "POST", token, status: 409 });
  check((await request("/v1/checkins/today", { token })).checkin.rawText === "上午开会\n补充会议纪要",
    "stale preview overwrote newer record");
  const refreshed = await request(previewPath, { method: "PATCH", token,
    body: { date: originalPreview.date } });
  check(JSON.parse(refreshed.payloadJson).text === "上午开会\n补充会议纪要\n下午完成了报告",
    "refresh did not rebase on latest record");
  await request(`/v1/agent/proposals/${recordProposal.id}/confirm`, { method: "POST", token });
  check((await request("/v1/checkins/today", { token })).checkin.rawText ===
    "上午开会\n补充会议纪要\n下午完成了报告", "confirmed record was not saved");
  const timeline = await request("/v1/timeline", { token });
  check(timeline.items.filter((item) => item.date === originalPreview.date).length === 1,
    "same-day conversation record created duplicate timeline entries");
  let summaryReady = false;
  for (let attempt = 0; attempt < 50; attempt++) {
    const response = await fetch(`${base}/v1/summaries/${originalPreview.date}`,
      { headers: { authorization: `Bearer ${token}` } });
    if (response.ok && (await response.json()).oneLiner === "确认后的报告记录已整理") {
      summaryReady = true;
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  check(summaryReady, "confirmed record did not produce a day summary");
  const updatedTimeline = await request("/v1/timeline", { token });
  check(updatedTimeline.items.find((item) => item.date === originalPreview.date)?.oneLiner ===
    "确认后的报告记录已整理", "timeline did not show the latest summary");
  const yesterday = new Date(Date.now() - 86400000)
    .toLocaleDateString("en-CA", { timeZone: "Asia/Shanghai" });
  await request("/v1/checkins", { method: "POST", token,
    body: { date: yesterday, rawText: "昨天完成对账" } });
  const dayContext = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_AGENT_DAY_CONTEXT", clientMessageId: randomUUID() } });
  check(dayContext.assistantMessage.body === "有当日|有历史",
    `saved today record or earlier timeline missing from conversation: ${dayContext.assistantMessage.body}`);
  const otherDayContext = await request(`/v1/agent/conversations/${otherConversation.id}/messages`, {
    method: "POST", token: otherToken,
    body: { body: "TEST_AGENT_DAY_CONTEXT", clientMessageId: randomUUID() },
  });
  check(otherDayContext.assistantMessage.body === "无当日|无历史",
    "another account's records entered conversation context");
  check((await request("/v1/agent/actions", { token })).length === 4, "record action missing from log");
  console.log("conversation record preview, conflict, refresh and confirmation passed");

  const future = new Date(Date.now() + 7 * 86400000);
  const local = `${future.toLocaleDateString("en-CA", { timeZone: "Asia/Shanghai" })}T09:00`;
  const appointment = await request(messagePath, {
    method: "POST", token,
    body: { body: `TEST_APPOINTMENT_${local} 请提醒我查看旅行清单`, clientMessageId: randomUUID() },
  });
  const proposed = appointment.proposals.find((item) => item.kind === "one_off_reminder");
  check(proposed, "appointment proposal missing");
  await request(`/v1/agent/proposals/${proposed.id}/confirm`, { method: "POST", token });
  const upcoming = await request("/v1/agent/appointments", { token });
  check(upcoming.length === 1, "appointment not persisted");
  await request(`/v1/agent/appointments/${upcoming[0].id}/cancel`, { method: "POST", token });
  check((await request("/v1/agent/appointments", { token })).length === 0, "cancelled appointment remains upcoming");
  const unspecifiedTime = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_APPOINTMENT_UNSPECIFIED 明天提醒我查看旅行清单", clientMessageId: randomUUID() } });
  check(unspecifiedTime.proposals.every(item => item.kind !== "one_off_reminder"),
    "model-invented appointment time became a confirmation card");
  console.log("one-off appointment scheduling and cancellation passed");

  const followupDraft = await request(messagePath, { method: "POST", token,
    body: { body: "TEST_AGENT_FOLLOWUP 过几天再聊聊旅行准备", clientMessageId: randomUUID() } });
  const followupProposal = followupDraft.proposals.find(item => item.kind === "followup");
  check(followupProposal, "followup suggestion missing");
  check((await request("/v1/agent/followups/topics", { token })).length === 0,
    "followup topic created before confirmation");
  await request(`/v1/agent/proposals/${followupProposal.id}/confirm`, { method: "POST", token });
  const topicList = await request("/v1/agent/followups/topics", { token });
  check(topicList.length === 1 && topicList[0].reason === "准备旅行清单" && topicList[0].state === "authorized",
    "authorized topic not visible before delivery");
  const topicId = topicList[0].id;
  check((await request("/v1/agent/followups/topics", { token: otherToken })).length === 0,
    "followup topics leaked across accounts");
  await request(`/v1/agent/followups/topics/${topicId}/stop`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/agent/followups/topics/${topicId}/stop`, { method: "POST", token });
  check((await request("/v1/agent/followups/topics", { token }))[0].state === "paused",
    "stopped topic not visible to its owner");
  await request(`/v1/agent/followups/topics/${topicId}/resume`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/agent/followups/topics/${topicId}/resume`, { method: "POST", token });
  const resumed = (await request("/v1/agent/followups/topics", { token }))[0];
  check(resumed.state === "authorized" && new Date(resumed.nextEligibleAt).getTime() >= Date.now() + 23 * 3600000,
    "resumed topic should not dispatch immediately");
  console.log("followup topic list, account isolation, stop and explicit resume passed");

  const dailyReminder = await request("/v1/reminders", { method: "POST", token,
    body: { title: "喝水", message: "喝水休息", remindTime: "09:00", timezone: "Asia/Shanghai", enabled: true } });
  const reminderEditText = "TEST_AGENT_REMINDER_EDIT 请把喝水每日提醒修改到10:30";
  const reminderDraft = await request(messagePath, { method: "POST", token,
    body: { body: reminderEditText, clientMessageId: randomUUID() } });
  const reminderProposal = reminderDraft.proposals.find(item => item.kind === "daily_reminder_edit");
  check(reminderProposal && JSON.parse(reminderProposal.payloadJson).targetId === dailyReminder.id,
    "reminder edit target not bound to owned reminder");
  check((await request("/v1/reminders", { token })).items[0].remindTime === "09:00",
    "reminder changed before confirmation");
  await request(`/v1/agent/proposals/${reminderProposal.id}/confirm`, { method: "POST", token: otherToken, status: 404 });
  const otherReminderDraft = await request(`/v1/agent/conversations/${otherConversation.id}/messages`, {
    method: "POST", token: otherToken,
    body: { body: reminderEditText, clientMessageId: randomUUID() },
  });
  check(otherReminderDraft.proposals.every(item => item.kind !== "daily_reminder_edit"),
    "another user's reminder entered model context");
  await request(`/v1/reminders/${dailyReminder.id}`, { method: "PUT", token,
    body: { message: "我手动改过的内容" } });
  await request(`/v1/agent/proposals/${reminderProposal.id}/confirm`, { method: "POST", token, status: 409 });
  check((await request("/v1/reminders", { token })).items[0].remindTime === "09:00",
    "stale reminder proposal overwrote newer state");
  const freshReminder = await request(messagePath, { method: "POST", token,
    body: { body: reminderEditText, clientMessageId: randomUUID() } });
  const renewedReminder = freshReminder.proposals.find(item => item.kind === "daily_reminder_edit");
  check(renewedReminder, "refreshed reminder edit missing");
  await request(`/v1/agent/proposals/${renewedReminder.id}/confirm`, { method: "POST", token });
  const savedReminder = (await request("/v1/reminders", { token })).items[0];
  check(savedReminder.remindTime === "10:30" && savedReminder.message === "我手动改过的内容",
    "confirmed reminder edit did not preserve untouched fields");
  const reminderAction = (await request("/v1/agent/actions", { token }))
    .find(item => item.proposalId === renewedReminder.id);
  check(reminderAction?.undoable && reminderAction.undoneAt === null,
    "confirmed reminder edit has no undo snapshot");
  await request(`/v1/agent/actions/${renewedReminder.id}/undo`, { method: "POST", token: otherToken, status: 404 });
  const reminderUndone = await request(`/v1/agent/actions/${renewedReminder.id}/undo`, { method: "POST", token });
  check(reminderUndone.result === "undone" && !reminderUndone.undoable && reminderUndone.undoneAt,
    "reminder undo result was not recorded");
  check((await request(`/v1/agent/actions/${renewedReminder.id}/undo`, { method: "POST", token })).undoneAt
    === reminderUndone.undoneAt, "retry changed a completed undo");
  check((await request("/v1/reminders", { token })).items[0].remindTime === "09:00"
    && (await request("/v1/reminders", { token })).items[0].message === "我手动改过的内容",
  "reminder undo did not restore only the agent's edit");
  const staleReminder = await request(messagePath, { method: "POST", token,
    body: { body: reminderEditText, clientMessageId: randomUUID() } });
  const staleUndoProposal = staleReminder.proposals.find(item => item.kind === "daily_reminder_edit");
  check(staleUndoProposal, "reminder edit after undo missing");
  await request(`/v1/agent/proposals/${staleUndoProposal.id}/confirm`, { method: "POST", token });
  await request(`/v1/reminders/${dailyReminder.id}`, { method: "PUT", token,
    body: { message: "撤销前又改了内容" } });
  await request(`/v1/agent/actions/${staleUndoProposal.id}/undo`, { method: "POST", token, status: 409 });
  check((await request("/v1/reminders", { token })).items[0].message === "撤销前又改了内容",
    "undo overwrote a later manual reminder edit");
  console.log("daily reminder edit account isolation, stale conflict and confirmation passed");

  const habit = await request("/v1/punch/habits", { method: "POST", token,
    body: { title: "跑步", description: "每周运动", enabled: true } });
  const habitEditText = "TEST_AGENT_HABIT_EDIT 请把跑步修改成晨跑";
  const habitEdit = await request(messagePath, { method: "POST", token,
    body: { body: habitEditText, clientMessageId: randomUUID() } });
  const habitEditProposal = habitEdit.proposals.find(item => item.kind === "habit_edit");
  check(habitEditProposal && JSON.parse(habitEditProposal.payloadJson).targetId === habit.id,
    "habit edit target not bound to owned habit");
  await request(`/v1/agent/proposals/${habitEditProposal.id}/confirm`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/punch/habits/${habit.id}`, { method: "PUT", token,
    body: { description: "手动补充说明" } });
  await request(`/v1/agent/proposals/${habitEditProposal.id}/confirm`, { method: "POST", token, status: 409 });
  check((await request("/v1/punch/habits", { token })).items[0].title === "跑步",
    "stale habit proposal overwrote newer values");
  const freshHabitEdit = await request(messagePath, { method: "POST", token,
    body: { body: habitEditText, clientMessageId: randomUUID() } });
  const renewedHabitEdit = freshHabitEdit.proposals.find(item => item.kind === "habit_edit");
  check(renewedHabitEdit, "refreshed habit edit missing");
  await request(`/v1/agent/proposals/${renewedHabitEdit.id}/confirm`, { method: "POST", token });
  check((await request("/v1/punch/habits", { token })).items[0].title === "晨跑",
    "confirmed habit edit did not persist");
  console.log("habit edit account isolation, conflict and confirmation passed");
  const punchDate = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Shanghai" });
  const punchText = `TEST_AGENT_PUNCH 请为${punchDate}晨跑打卡`;
  const punchDraft = await request(messagePath, { method: "POST", token,
    body: { body: punchText, clientMessageId: randomUUID() } });
  const punchProposal = punchDraft.proposals.find(item => item.kind === "habit_punch");
  check(punchProposal && JSON.parse(punchProposal.payloadJson).habitId === habit.id, "punch target not bound to owned habit");
  check(!(await request(`/v1/punch/habits?date=${punchDate}`, { token })).items[0].punchedToday,
    "punch was written before confirmation");
  await request(`/v1/agent/proposals/${punchProposal.id}/confirm`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/agent/proposals/${punchProposal.id}/confirm`, { method: "POST", token });
  check((await request(`/v1/punch/habits?date=${punchDate}`, { token })).items[0].punchedToday,
    "confirmed punch was not saved");
  const cancelDraft = await request(messagePath, { method: "POST", token,
    body: { body: `TEST_AGENT_UNPUNCH 请取消${punchDate}晨跑打卡`, clientMessageId: randomUUID() } });
  const cancelProposal = cancelDraft.proposals.find(item => item.kind === "habit_unpunch");
  check(cancelProposal, "cancel punch proposal missing");
  await request(`/v1/punch/habits/${habit.id}/punch`, { method: "POST", token,
    body: { date: punchDate, note: "后续添加的备注" } });
  await request(`/v1/agent/proposals/${cancelProposal.id}/confirm`, { method: "POST", token, status: 409 });
  check((await request(`/v1/punch/habits?date=${punchDate}`, { token })).items[0].punchedToday,
    "stale cancel removed a newer punch");
  const refreshedCancel = await request(messagePath, { method: "POST", token,
    body: { body: `TEST_AGENT_UNPUNCH 请取消${punchDate}晨跑打卡`, clientMessageId: randomUUID() } });
  const renewed = refreshedCancel.proposals.find(item => item.kind === "habit_unpunch");
  check(renewed, "refreshed cancel proposal missing");
  await request(`/v1/agent/proposals/${renewed.id}/confirm`, { method: "POST", token });
  check(!(await request(`/v1/punch/habits?date=${punchDate}`, { token })).items[0].punchedToday,
    "confirmed cancel did not remove punch");
  check((await request("/v1/agent/actions", { token })).find(item => item.proposalId === renewedHabitEdit.id)?.undoable,
    "confirmed habit edit has no undo snapshot");
  await request(`/v1/agent/actions/${renewedHabitEdit.id}/undo`, { method: "POST", token });
  check((await request("/v1/punch/habits", { token })).items[0].title === "跑步",
    "habit undo did not restore its previous name");
  console.log("habit punch, stale cancel protection and account isolation passed");

  await request(`/v1/agent/conversations/${conversation.id}`, { method: "DELETE", token });
  await request(`/v1/agent/conversations/${conversation.id}`, { token, status: 404 });
  check((await request("/v1/agent/followups/topics", { token })).length === 0,
    "deleted conversation topic remains visible");
  await request(`/v1/agent/followups/topics/${topicId}/resume`, { method: "POST", token, status: 404 });
  check((await request("/v1/memories", { token })).items.length === 0, "conversation-derived memory survived deletion");
  check((await request("/v1/memories?includeArchived=true", { token })).items.length === 0,
    "deleted conversation memory leaked through archived list");
  await request(originPath, { token, status: 404 });
  await request(`/v1/memories/${confirmedMemoryId}/unarchive`, { method: "POST", token, status: 404 });
  check((await request("/v1/todos", { token })).items[0]?.title === "更新旅行清单",
    "confirmed todo edit removed with conversation");
  check((await request("/v1/checkins/today", { token })).checkin.rawText ===
    "上午开会\n补充会议纪要\n下午完成了报告", "confirmed record removed with conversation");
  const actionsAfterDeletion = await request("/v1/agent/actions", { token });
  for (const proposalId of [memory.id, followupProposal.id, proposed.id]) {
    const action = actionsAfterDeletion.find(item => item.proposalId === proposalId);
    check(action?.result === "revoked" && action.summary === "来源已删除",
      "deleted conversation left sensitive details in a derived action");
  }
  check(actionsAfterDeletion.some(item => item.proposalId === todo.id && item.result === "succeeded"),
    "deleting conversation revoked an independently confirmed todo");
  const todoEditAction = actionsAfterDeletion.find(item => item.proposalId === refreshedEdit.id);
  check(todoEditAction?.undoable, "confirmed todo edit has no undo snapshot");
  await request(`/v1/agent/actions/${refreshedEdit.id}/undo`, { method: "POST", token: otherToken, status: 404 });
  await request(`/v1/agent/actions/${refreshedEdit.id}/undo`, { method: "POST", token });
  check((await request("/v1/todos", { token })).items[0].title === "临时更改",
    "todo edit undo did not restore its original title");
  check((await request(`/v1/agent/actions/${refreshedEdit.id}/undo`, { method: "POST", token })).result === "undone",
    "todo edit undo is not repeatable");
  check((await request("/v1/agent/proposals", { token })).length === 0,
    "deleted conversation left pending proposal cards visible");
  const fresh = await request("/v1/agent/conversations", { method: "POST", token, body: {} });
  const deletedContext = await request(`/v1/agent/conversations/${fresh.id}/messages`, {
    method: "POST", token, body: { body: "TEST_AGENT_CONTEXT", clientMessageId: randomUUID() },
  });
  check(deletedContext.assistantMessage.body === "无确认记忆", "deleted memory entered new conversation context");
  console.log("conversation deletion cleans derived memory and retains confirmed todo");

  const delayedPath = `/v1/agent/conversations/${fresh.id}/messages`;
  const delayedEndpoint = `${mockBase}/__delayed_conversation_started`;
  const beforeDelay = (await (await fetch(delayedEndpoint)).json()).count;
  const inFlight = request(delayedPath, { method: "POST", token, status: 404,
    body: { body: "TEST_DELAY_CONVERSATION", clientMessageId: randomUUID() } });
  let observed = false;
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    if ((await (await fetch(delayedEndpoint)).json()).count > beforeDelay) { observed = true; break; }
    await new Promise(resolve => setTimeout(resolve, 75));
  }
  check(observed, "delayed model request did not start");
  await request(`/v1/agent/conversations/${fresh.id}`, { method: "DELETE", token });
  await inFlight;
  check((await request("/v1/memories", { token })).items.length === 0,
    "late model response revived a memory after conversation deletion");
  check((await request("/v1/agent/proposals", { token })).length === 0,
    "late model response revived a proposal after conversation deletion");
  console.log("late conversation response cannot recreate deleted content");

  const photoHabit = await request("/v1/punch/habits", { method: "POST", token,
    body: { title: "阅读", description: "每天一点", enabled: true } });
  const picture = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLefwAAAABJRU5ErkJggg==", "base64");
  const form = new FormData();
  form.append("date", punchDate);
  form.append("photo", new Blob([picture], { type: "image/png" }), "reading.png");
  const photoUpload = await fetch(`${base}/v1/punch/habits/${photoHabit.id}/punch`, {
    method: "POST", headers: { authorization: `Bearer ${token}` }, body: form,
  });
  check(photoUpload.ok, `photo punch failed: ${photoUpload.status}`);
  const photoLog = await photoUpload.json();
  check(photoLog.photoUrl, "photo punch did not persist photo URL");
  const photoPath = photoLog.photoUrl;
  const photoBefore = await fetch(`${base}${photoPath}`, { headers: { authorization: `Bearer ${token}` } });
  check(photoBefore.ok, "owned punch photo is not readable");
  await photoBefore.arrayBuffer();
  await request(`/v1/punch/habits/${photoHabit.id}`, { method: "DELETE", token, status: 200 });
  check(!(await request(`/v1/punch/habits?date=${punchDate}`, { token })).items.some(item => item.id === photoHabit.id),
    "deleted habit is still listed");
  const photoAfter = await fetch(`${base}${photoPath}`, { headers: { authorization: `Bearer ${token}` } });
  check(photoAfter.status === 404, "habit deletion left a readable punch photo");
  await request(`/v1/punch/habits/${photoHabit.id}/punch`, { method: "POST", token,
    body: { date: punchDate }, status: 404 });
  console.log("manual photo punch and habit deletion clean up data and media passed");
}

main().catch((error) => {
  console.error("agent API smoke failed:", error);
  process.exitCode = 1;
});
