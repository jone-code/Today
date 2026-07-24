# `@today/web` — Next.js 前端

按产品能力拆在 `src/features/*`，与后端 `com.today.*` 同名。

```text
src/
├── app/                 # 路由壳：/ 、/app/* 、/login
├── features/
│   ├── checkin/         # 今日输入与提交
│   ├── summary/         # 日总结展示
│   ├── timeline/        # 时间轴
│   ├── memory/          # 长期记忆
│   ├── proactive/       # 主动关联提示
│   ├── identity/        # 登录注册
│   ├── reminder/        # 定时提醒
│   ├── todo/            # 待办
│   └── punch/           # 习惯打卡
└── shared/
    ├── lib/             # api-client、context、本地 fallback
    └── ui/              # AppShell / Nav / Providers
```

## 运行

仓库根目录：

```bash
npm install
npm run dev:web
```

或：

```bash
npm run dev -w @today/web
```

默认 `http://localhost:3000`，API 默认 `http://localhost:3001`（`NEXT_PUBLIC_API_BASE_URL` 可覆盖）。

## 规则

- `features/*` 只通过 HTTP 调 `apps/api`，不内嵌模型 SDK
- 契约优先从 `@today/contracts` 对齐；HTTP 客户端在 `shared/lib/api-client.ts`
- 数据请求经 **TanStack Query**（`today` / `reminders` / `todos` / `punch` query keys）
- 默认需登录走 API；仅当 `NEXT_PUBLIC_ALLOW_LOCAL_FALLBACK=true` 时允许未登录本地 demo
