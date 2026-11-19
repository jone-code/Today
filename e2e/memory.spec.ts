import { expect, test } from "@playwright/test";

const memory = {
  id: "memory-1",
  userId: "user-1",
  category: "life",
  text: "正在持续学习",
  strength: 3,
  archived: false,
  chatContextScope: "chat_and_timeline",
  updatedAt: "2026-11-18T00:00:00Z",
};

test("memory controls show origins and audit, then recover a scope conflict", async ({ page }) => {
  let scopeAttempt = 0;
  await page.addInitScript(() => localStorage.setItem("today.auth.token.v1", "test-token"));
  await page.route("**/api/v1/**", async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = (body: unknown, status = 200) => route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
    if (path === "/api/v1/auth/me") return json({ id: "user-1", email: "test@example.com", displayName: "测试用户", createdAt: "2026-11-01T00:00:00Z" });
    if (path === "/api/v1/checkins/today") return json({ checkin: null, aiJob: null });
    if (path === "/api/v1/timeline") return json({ items: [], nextCursor: null });
    if (path === "/api/v1/proactive/today") return json({ date: "2026-11-18", prompts: [], provider: "heuristic" });
    if (path === "/api/v1/memories" && request.method() === "GET") {
      return json({ items: scopeAttempt > 0 ? [{ ...memory, chatContextScope: "timeline_only", updatedAt: "2026-11-18T00:01:00Z" }] : [memory] });
    }
    if (path === "/api/v1/memories/memory-1/origins") return json([{ kind: "checkin", sourceId: "checkin-1", date: "2026-11-17", conversationId: null }]);
    if (path === "/api/v1/memories/memory-1/access-audit") return json([{ id: "audit-1", memoryId: "memory-1", action: "retrieved", source: "chat", occurredAt: "2026-11-18T01:02:00Z" }]);
    if (path === "/api/v1/memories/memory-1/context-scope" && request.method() === "PATCH") {
      scopeAttempt += 1;
      return scopeAttempt === 1 ? json({ message: "memory changed; reload and retry" }, 409) : json({ ...memory, chatContextScope: "disabled", updatedAt: "2026-11-18T00:02:00Z" });
    }
    if (path === "/api/v1/events") return json(null);
    return json({});
  });

  await page.goto("/app/memory");
  await expect(page.getByText("正在持续学习")).toBeVisible();
  await page.getByRole("button", { name: "查看来源与使用记录" }).click();
  await expect(page.getByText("每日记录 · 2026-11-17")).toBeVisible();
  await expect(page.getByText("Chat · 被读取")).toBeVisible();

  await page.getByRole("button", { name: "停用" }).click();
  await expect(page.getByText("这条记忆已被其他设备修改，已刷新最新内容。")).toBeVisible();
  await expect(page.getByText("仅回看")).toBeVisible();
  expect(scopeAttempt).toBe(1);
});
