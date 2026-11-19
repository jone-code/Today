import { expect, test } from "@playwright/test";

const activation = {
  from: "2026-11-01", to: "2026-11-07",
  filters: { platform: "android", appVersion: "0.1.0", eventSchemaVersion: 1 },
  funnel: {
    registeredUsers: 4, activatedUsers: 3, chatUsers: 2, valueUsers: 2,
    northStarUsers: 1, checkinsSaved: 7, checkinSavedEvents: 7,
    checkinUsersMissingSavedEvent: 0, savedEventUsersMissingCheckin: 0,
  },
  events: [{ eventName: "checkin_saved", platform: "android", appVersion: "0.1.0", eventSchemaVersion: 1, eventCount: 7, userCount: 3 }],
};
const retention = { from: "2026-11-01", to: "2026-11-07", cohort: { d1: 2, d3: 1, d7: 1 } };
const quality = {
  from: "2026-11-01", to: "2026-11-07",
  filters: { platform: "android", appVersion: "0.1.0", eventSchemaVersion: 1 },
  events: [{ eventName: "checkin_saved", platform: "android", appVersion: "0.1.0", eventSchemaVersion: 1, eventCount: 7, userCount: 3 }],
  dataRequests: [{ type: "export", status: "succeeded", taskCount: 2 }],
  voiceTranscriptions: [{ status: "failed", errorCode: "provider_unconfigured", taskCount: 1 }],
  feedback: [{ kind: "idea", feedbackCount: 2, userCount: 2 }],
};

test("metrics dashboard loads scoped aggregates and exports without persisting token", async ({ page }, testInfo) => {
  await page.route("**/api/v1/admin/metrics/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith("/activation") ? activation : path.endsWith("/retention") ? retention : quality;
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });

  await page.goto("/admin/metrics");
  const token = "trial-admin-token";
  await expect(page.getByRole("button", { name: "读取指标" })).toBeDisabled();
  await page.getByLabel("管理员令牌").fill(token);
  await page.getByLabel("开始日期").fill("2026-11-01");
  await page.getByLabel("结束日期").fill("2026-11-07");
  await page.getByLabel("事件平台").selectOption("android");
  await page.getByLabel("应用版本").fill("0.1.0");
  await page.getByLabel("事件契约").selectOption("1");
  const metricRequests: Array<{ url: string; headers: Record<string, string> }> = [];
  page.on("request", request => {
    if (request.url().includes("/api/v1/admin/metrics/")) {
      metricRequests.push({ url: request.url(), headers: request.headers() });
    }
  });
  await page.getByRole("button", { name: "读取指标" }).click();

  const requests = await page.evaluate(() => performance.getEntriesByType("resource")
    .map((entry) => (entry as PerformanceResourceTiming).name)
    .filter((name) => name.includes("/api/v1/admin/metrics/")));
  expect(requests.every((url) => url.includes("platform=android") && url.includes("appVersion=0.1.0"))).toBeTruthy();
  expect(metricRequests).toHaveLength(3);
  expect(metricRequests.every(({ url }) => !url.includes(token))).toBeTruthy();
  expect(metricRequests.every(({ headers }) => headers["x-today-admin-token"] === token)).toBeTruthy();

  await expect(page.getByText("北极星用户")).toBeVisible();
  await expect(page.getByText("记录事实与客户端事件差异：0；用户级缺口：业务事实漏报 0，事件无事实 0。当前对账一致。")).toBeVisible();
  await expect(page.getByText("checkin_saved · android · v0.1.0")).toBeVisible();
  await expect(page.getByText("checkin_saved · android · v0.1.0 · schema 1")).toBeVisible();
  await expect(page.getByText("平台和版本筛选只作用于事件质量明细")).toBeVisible();
  await expect(page.getByText("failed · provider_unconfigured")).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("metrics-dashboard.png"), fullPage: true });

  const storage = await page.evaluate(() => ({ local: { ...localStorage }, session: { ...sessionStorage } }));
  expect(storage).toEqual({ local: {}, session: {} });

  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出 JSON" }).click();
  expect((await download).suggestedFilename()).toBe("today-metrics-2026-11-01-2026-11-07.json");
});
