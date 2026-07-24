import { expect, test } from "@playwright/test";

/**
 * UI smoke: register → logout → login → checkin → memory
 */
test("login → checkin → memory", async ({ page }) => {
  const stamp = Date.now();
  const email = `e2e.ui.${stamp}@example.com`;
  const password = "smoke-pass-123";
  const displayName = `UI${stamp % 100000}`;
  const rawText =
    "今天跑步锻炼了，还学了点 AI，工作压力有点大但整体还好。";

  await page.goto("/register");
  await page.getByLabel("昵称").fill(displayName);
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码（至少 6 位）").fill(password);
  await page.getByRole("button", { name: "注册并进入" }).click();
  await expect(page).toHaveURL(/\/app\/?$/);
  await expect(page.getByText(displayName)).toBeVisible();

  await page.getByRole("button", { name: "退出" }).click();
  await expect(page.getByRole("link", { name: "登录" }).first()).toBeVisible();

  await page.goto("/login");
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/app\/?$/);
  await expect(page.getByLabel("今天怎么样？")).toBeVisible();

  await page.getByLabel("今天怎么样？").fill(rawText);
  await page.getByRole("button", { name: "留下今天" }).click();

  await expect(page.getByText(/今日已留下|今日总结/)).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.locator(".summary .one-liner")).toBeVisible({
    timeout: 60_000,
  });
  await expect(page.getByText("AI 正在整理完成项、情绪与关键词…")).toHaveCount(
    0,
    { timeout: 60_000 },
  );

  await page.getByRole("link", { name: "记忆" }).click();
  await expect(page).toHaveURL(/\/app\/memory/);
  await expect(page.locator(".memory-item").first()).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.locator(".memory-item").first()).toContainText(
    /运动|学习|压力|工作/,
  );
});
