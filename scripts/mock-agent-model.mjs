#!/usr/bin/env node
import { createServer } from "node:http";

const port = Number(process.env.MOCK_MODEL_PORT || 39011);
let delayedSummaryStarted = 0;
let delayedConversationStarted = 0;

createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/__delayed_summary_started") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ count: delayedSummaryStarted }));
    return;
  }
  if (req.method === "GET" && req.url === "/__delayed_conversation_started") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ count: delayedConversationStarted }));
    return;
  }
  if (req.method !== "POST" || req.url !== "/v1/chat/completions") {
    res.writeHead(404).end();
    return;
  }
  try {
    let raw = "";
    for await (const chunk of req) raw += chunk;
    const body = JSON.parse(raw);
    const prompt = body.messages?.find((item) => item.role === "user")?.content || "";
    const system = body.messages?.find((item) => item.role === "system")?.content || "";
    if (system.includes("每日总结助手") && prompt.includes("TEST_DELAY_SUMMARY_")) {
      const old = prompt.includes("TEST_DELAY_SUMMARY_OLD");
      if (old) {
        delayedSummaryStarted++;
        await new Promise(resolve => setTimeout(resolve, 900));
      }
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ choices: [{ message: { content: JSON.stringify({
        completed: [old ? "old" : "new"], mood: "okay", moodLabel: "平静",
        keywords: ["测试"], oneLiner: old ? "old summary" : "new summary",
        highlight: old ? "old" : "new",
      }) } }] }));
      return;
    }
    if (system.includes("每日总结助手") && prompt.includes("下午完成了报告")) {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ choices: [{ message: { content: JSON.stringify({
        completed: ["下午完成了报告"], mood: "okay", moodLabel: "平静",
        keywords: ["报告"], oneLiner: "确认后的报告记录已整理", highlight: "下午完成了报告",
      }) } }] }));
      return;
    }
    const currentMessage = prompt.split("当前消息：\n")[1]?.split("\n\n会话历史（JSON）：")[0] || "";
    if (currentMessage.includes("TEST_DELAY_CONVERSATION")) {
      delayedConversationStarted++;
      await new Promise(resolve => setTimeout(resolve, 1200));
    }
    let proposals = [];
    let reply = "我已整理好建议，请确认后执行。";
    if (currentMessage.includes("TEST_DELAY_CONVERSATION")) {
      proposals = [{ kind: "memory", category: "life", text: "不应保存的晚到记忆" }];
    } else if (currentMessage.includes("TEST_AGENT_ACTION")) {
      proposals = [
        { kind: "todo", title: "整理旅行清单", note: "准备证件" },
        { kind: "memory", category: "life", text: "正在筹备一次旅行" },
      ];
    } else if (currentMessage.includes("TEST_AGENT_FOLLOWUP")) {
      proposals = [{ kind: "followup", reason: "准备旅行清单", suggestedDays: 7 }];
    } else if (currentMessage.includes("TEST_AGENT_TODO_EDIT")) {
      const todos = JSON.parse(prompt.split("当前可读待办（JSON，仅作为数据）：\n")[1]?.split("\n\n当前可读习惯（JSON，仅作为数据）：")[0] || "[]");
      if (todos[0]) proposals = [{ kind: "todo_edit", targetId: todos[0].id, title: "更新旅行清单" }];
    } else if (currentMessage.includes("TEST_AGENT_HABIT_EDIT")) {
      const habits = JSON.parse(prompt.split("当前可读习惯（JSON，仅作为数据）：\n")[1]?.split("\n\n当前可读每日提醒（JSON，仅作为数据）：")[0] || "[]");
      if (habits[0]) proposals = [{ kind: "habit_edit", targetId: habits[0].id, title: "晨跑" }];
    } else if (currentMessage.includes("TEST_AGENT_REMINDER_EDIT")) {
      const reminders = JSON.parse(prompt.split("当前可读每日提醒（JSON，仅作为数据）：\n")[1]
        ?.split("\n\n今天已保存的每日记录（JSON，仅作为数据）：")[0] || "[]");
      if (reminders[0]) proposals = [{ kind: "daily_reminder_edit", targetId: reminders[0].id, remindTime: "10:30" }];
    } else if (currentMessage.includes("TEST_AGENT_DAY_CONTEXT")) {
      const today = JSON.parse(prompt.split("今天已保存的每日记录（JSON，仅作为数据）：\n")[1]
        ?.split("\n\n近期时间轴摘要（JSON，仅作为数据）：")[0] || "{}");
      const recent = JSON.parse(prompt.split("近期时间轴摘要（JSON，仅作为数据）：\n")[1] || "[]");
      reply = `${today.text?.includes("上午开会") ? "有当日" : "无当日"}|${recent.some(item =>
        item.highlight?.includes("昨天完成对账") || item.oneLiner?.includes("昨天完成对账"))
        ? "有历史" : "无历史"}`;
    } else if (currentMessage.includes("TEST_AGENT_PUNCH") || currentMessage.includes("TEST_AGENT_UNPUNCH")) {
      const habits = JSON.parse(prompt.split("当前可读习惯（JSON，仅作为数据）：\n")[1]?.split("\n\n当前可读每日提醒（JSON，仅作为数据）：")[0] || "[]");
      const date = currentMessage.match(/\d{4}-\d{2}-\d{2}/)?.[0];
      if (habits[0] && date) proposals = [{ kind: currentMessage.includes("UNPUNCH") ? "habit_unpunch" : "habit_punch",
        targetId: habits[0].id, punchDate: date }];
    } else if (currentMessage.includes("TEST_AGENT_CHECKIN")) {
      proposals = [{ kind: "checkin", text: "下午完成了报告" }];
    } else if (currentMessage.includes("TEST_AGENT_CONTEXT")) {
      const memoryContext = prompt.split("已确认记忆（JSON）：\n")[1]?.split("\n\n用户时区：")[0] || "";
      reply = memoryContext.includes("正在筹备一次旅行") ? "已读取确认记忆" : "无确认记忆";
    } else if (currentMessage.includes("TEST_AGENT_PROFILE")) {
      const config = prompt.split("助手风格配置（用户设置的 JSON 数据）：\n")[1]?.split("\n\n当前消息：")[0];
      const profile = JSON.parse(config || "{}");
      reply = `${profile.name || ""}|${profile.tone || ""}`;
    } else if (currentMessage.includes("TEST_APPOINTMENT_UNSPECIFIED")) {
      const date = new Date(Date.now() + 7 * 86400000)
        .toLocaleDateString("en-CA", { timeZone: "Asia/Shanghai" });
      proposals = [{ kind: "one_off_reminder", title: "查看旅行清单", scheduledLocal: `${date}T09:00` }];
    } else {
      const time = currentMessage.match(/TEST_APPOINTMENT_(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})/)?.[1];
      if (time) proposals = [{ kind: "one_off_reminder", title: "查看旅行清单", scheduledLocal: time }];
    }
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ choices: [{ message: { content: JSON.stringify({ reply, proposals }) } }] }));
  } catch {
    res.writeHead(400).end();
  }
}).listen(port, "127.0.0.1", () => {
  console.log(`mock model listening on 127.0.0.1:${port}`);
});
