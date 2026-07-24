# Today 技术架构：模块划分与选型

> 目标：即使当前是 demo，也按「完整产品能力」搭骨架。  
> 本文只定边界与选型；下一步再按模块迁移现有页面代码并接通真实后端。

---

## 1. 原则

1. **按产品能力拆模块**，不按页面或技术层硬切。
2. **前后端同一模块名**，接口与领域语言对齐。
3. **AI 能力必须可替换**：总结 / 记忆抽取 / 主动关联都走统一 AI Gateway，禁止业务里直接调模型 SDK。
4. **先本地可跑，再上云**：demo 可用内存/SQLite；生产用 PostgreSQL。

---

## 2. 仓库形态（目标）

```text
today/
├── apps/
│   ├── web/                 # 前端 Next.js
│   └── api/                 # 后端 Spring Boot（Java）
├── packages/
│   ├── contracts/           # 跨端 DTO / API 契约 / 错误码
│   └── config/              # 共享 eslint/tsconfig（可选）
└── docs/
    └── architecture.md
```

当前仓库：**前端已迁入 `apps/web`（`features/*` + `shared/`）**；后端在 `apps/api`；契约在 `packages/contracts`。

---

## 3. 技术选型

### 3.1 前端 `apps/web`

| 项 | 选型 | 原因 |
|----|------|------|
| 框架 | **Next.js（App Router）+ TypeScript** | 已有基础；SSR/落地页 + App 内交互都合适 |
| 样式 | **Tailwind CSS** | 迭代快，适合品牌向 UI |
| 数据请求 | **TanStack Query** | 今日态 / 提醒缓存与失效；默认走 API |
| 表单 | 原生 + 小组件即可 | MVP 只有一个主输入，不必上重型表单库 |
| 鉴权 | JWT（Bearer）+ 本地 token | 与 `apps/api` identity 对齐 |

**前端模块目录（按能力）：**

```text
apps/web/src/
├── app/                      # 路由壳：/ 、/app/*
├── features/
│   ├── checkin/              # 每日记录
│   ├── summary/              # 展示日总结（不直接调模型）
│   ├── timeline/             # 时间轴
│   ├── memory/               # 长期记忆面板
│   ├── proactive/            # 主动关联提示
│   ├── identity/             # 登录注册
│   ├── reminder/             # 定时提醒
│   ├── todo/                 # 待办
│   └── punch/                # 习惯打卡
└── shared/                   # UI 壳、http client、本地 fallback
```

前端 **不算** AI 实现层：只消费 API，渲染结果（无 key 时可用本地 heuristic fallback）。

### 3.2 后端 `apps/api`

| 项 | 选型 | 原因 |
|----|------|------|
| 运行时 | **Java 17** | LTS，稳定且与 Spring Boot 3 匹配 |
| 框架 | **Spring Boot 3** | 模块化清晰，与产品能力包一一对应 |
| ORM | **MyBatis**（原生，不用 MyBatis-Plus） | SQL 可控，与模块边界清晰 |
| 数据库 | **MySQL 8** | 按你的要求 |
| 迁移 | `db/schema.sql` + Docker init | 简单可重复 |
| 校验 | **Jakarta Validation** + `packages/contracts` 路由约定 | 前端 Zod 契约与 Java DTO 对齐 |
| API 风格 | **REST**（SpringDoc OpenAPI 可选） | MVP 简单；不先上 GraphQL |
| 异步任务（二期） | **Spring + Redis / 消息队列** | 总结/记忆抽取可异步，避免阻塞提交 |

**Java 包结构（与产品模块同名）：**

```text
apps/api/src/main/java/com/today/
├── checkin/
├── summary/
├── memory/
├── timeline/
├── proactive/
├── aigateway/
├── identity/
├── reminder/
├── todo/
├── punch/
└── health/
```

**为何不选 Node 当后端：**  
按你的要求，后端统一用 Spring Boot；前端仍用 TypeScript，`packages/contracts` 作为 API 契约源。

**为何不先上 Python：**  
AI 编排可在 Java `aigateway` 模块内接 OpenAI 兼容 HTTP SDK；若以后要重度批处理，再抽独立 worker。

### 3.3 数据与 AI

| 项 | 选型 | 原因 |
|----|------|------|
| 主库 | **MySQL 8** | 日记、记忆、时间轴结构化数据 |
| 向量 | **Qdrant**（可选外挂）+ MySQL `embedding_json` 回退 | `TODAY_VECTOR_PROVIDER=qdrant`；默认仍 mysql 扫描 |
| 缓存/队列 | **Redis**（二期） | 主动关联扫描、异步总结 |
| 对象存储 | S3 兼容（图片期再上） | MVP 不做图片 |
| LLM | **OpenAI 兼容 API**（环境变量切换） | 国内/海外模型可换 |
| 嵌入 | 同供应商 embedding 模型 | 与记忆检索配套 |
| AI 编排 | **AI Gateway 模块** | 统一 prompt、超时、降级、审计 |

**Demo 降级策略：**  
无 `OPENAI_API_KEY` 时，Gateway 走 `HeuristicProvider`（现有正则逻辑迁入），保证本地可跑；有 key 时走 `LlmProvider`。

### 3.4 基础设施（本期最小）

| 项 | 选型 |
|----|------|
| 包管理 | npm workspaces（或 pnpm） |
| 本地 DB | Docker Compose：MySQL 8 |
| 配置 | `.env` + 各 app 的 schema 校验 |
| 测试 | Vitest（domain/api），前端关键 Testing Library 后补 |

---

## 4. 业务模块划分

每个模块在 **contracts / api / web** 三侧同名。

### 4.1 总览

```text
┌─────────────┐   ┌─────────────┐   ┌──────────────┐
│  checkin    │──▶│  summary    │──▶│   memory     │
│  每日记录    │   │  AI 总结     │   │  长期记忆     │
└─────────────┘   └─────────────┘   └──────┬───────┘
       │                  │                 │
       │                  ▼                 ▼
       │           ┌─────────────┐   ┌──────────────┐
       └──────────▶│  timeline   │   │  proactive   │
                   │  时间轴      │   │  主动关联     │
                   └─────────────┘   └──────────────┘
                                      ▲
                                      │
                              ┌───────┴────────┐
                              │  ai-gateway    │
                              │  模型/嵌入抽象  │
                              └────────────────┘
```

### 4.2 模块职责与边界

#### `checkin` — 每日记录（入口）

- **负责：** 接收「今天怎么样？」原文；保证一日一条（可覆盖/追加策略明确）；触发后续流水线。
- **不负责：** 总结文案、记忆抽取、主动提问。
- **核心 API：**
  - `POST /v1/checkins` — 提交/更新今日记录
  - `GET /v1/checkins/today` — 今日记录
- **数据：** `Checkin`（id, userId, date, rawText, createdAt, updatedAt）

#### `summary` — AI 总结

- **负责：** 基于单日 checkin 生成结构化总结（完成项、情绪、关键词、一句话、亮点）。
- **不负责：** 跨日模式、长期记忆写入。
- **核心 API：**
  - `GET /v1/summaries/:date`
  - （内部）checkin 提交后由编排调用 `SummaryService.generate`
- **依赖：** `ai-gateway`（summary prompt）
- **数据：** `DaySummary` 可内嵌于 Checkin 或独立表（建议独立，便于重算）

#### `memory` — 长期记忆

- **负责：** 从当日内容抽取/更新记忆条目；提供记忆列表与检索。
- **不负责：** 决定今天问用户什么（那是 proactive）。
- **核心 API：**
  - `GET /v1/memories`
  - （内部）`MemoryService.upsertFromCheckin`
- **依赖：** `ai-gateway`（抽取 + embedding）+ `vector`（检索索引）
- **数据：** `MemoryItem`（id, userId, category, text, strength, embedding, archived, updatedAt）

#### `timeline` — 时间轴

- **负责：** 按日聚合展示卡片（日期、亮点、情绪）。
- **不负责：** 生成总结（只读 summary/checkin）。
- **核心 API：**
  - `GET /v1/timeline?cursor=&limit=`
- **依赖：** checkin + summary 读模型

#### `proactive` — 主动关联（产品核心）

- **负责：** 结合近期 checkin + memories，生成今日开场/追问（最多 N 条）；记录展示/选择/忽略/已回答，避免重复追问。
- **不负责：** 改写用户日记、写记忆。
- **核心 API：**
  - `GET /v1/proactive/today`
  - `POST /v1/proactive/prompts/:id/select`
  - `POST /v1/proactive/prompts/:id/dismiss`
- **依赖：** `ai-gateway` + memory 检索 + 近 N 日 checkin
- **数据：** `proactive_prompt_events`（fingerprint + status）
- **规则：** 无模型时可用规则降级；有模型时「候选追问 + 检索记忆 → LLM 挑选/润色」；打卡正文可关闭开放追问。

#### `ai-gateway` — AI 基础设施（支撑模块）

- **负责：** Provider 抽象、prompt 模板、embedding、超时/重试/降级、调用日志与审计。
- **不负责：** 任何业务决策（不拥有 checkin/memory 表）。
- **接口示例：**
  - `complete(task: 'summary' | 'memory_extract' | 'proactive', input)`
  - `embed(texts: string[])`
  - `GET /v1/admin/ai/stats` · `GET /v1/admin/ai/calls`（可观测；可选 `TODAY_AI_ADMIN_TOKEN`）
- **数据：** `ai_call_logs`（outcome=ok|fallback|failed|skipped，含耗时）

#### `vector` — 向量索引（支撑模块）

- **负责：** 记忆向量 upsert / delete / archive 元数据 / Top-K 检索。
- **不负责：** 生成 embedding（仍走 `ai-gateway`）；不拥有业务表。
- **实现：** `VectorStore` — 默认 `mysql`（`embedding_json` + 余弦）；可选 `qdrant`（失败检索回退 mysql）。详见 `docs/vector-store.md`。

#### `identity` — 用户注册登录

- **负责：** 注册、登录、JWT 签发、当前用户解析。
- **核心 API：**
  - `POST /v1/auth/register`
  - `POST /v1/auth/login`
  - `GET /v1/auth/me`
- **数据：** `users`
- **实现：** Spring Security + JWT；`IdentityService.getCurrentUserId()` 从 SecurityContext 读取。

#### `reminder` — 定时提醒

- **负责：** 每日提醒配置、到点投递记录。
- **核心 API：**
  - `GET/POST /v1/reminders`
  - `PUT/DELETE /v1/reminders/:id`
  - `GET /v1/reminders/deliveries`
  - `POST /v1/reminders/deliveries/:id/read`
- **数据：** `reminders`、`reminder_deliveries`
- **调度：** `@Scheduled` 每分钟扫描到期提醒并写入投递。

---

## 5. 关键链路（提交一天）

```text
用户提交原文
  → checkin.save
  → summary.generate        (ai-gateway)
  → memory.upsert           (ai-gateway + embed)
  → timeline 自然可读
  → 次日 / 当日刷新时 proactive.build
```

默认异步：checkin 先入队 `checkin_ai_jobs` 并返回 `status=processing` → 线程池立即执行 + 调度器兜底重试 → 成功写 summary/memory；失败写入 `last_error` 并指数退避。前端可 `POST /v1/checkins/today/reprocess`。「继续整理」会真正重跑流水线。`TODAY_AI_ASYNC_CHECKIN=false` 可同步。

---

## 6. contracts 包（模块契约先行）

`packages/contracts` 先定义各模块输入输出，前后端都依赖它，避免 demo 时期类型漂移。

建议最小集合：

- `CheckinCreateInput` / `CheckinDto`
- `DaySummaryDto`
- `MemoryDto`
- `TimelineItemDto`
- `ProactivePromptDto`
- `Auth*` / `UserDto`
- `ReminderDto` / `ReminderDeliveryDto`
- 统一 `ApiError`

---

## 7. 明确不做（与产品一致，防模块膨胀）

社区、好友、评论、分享、复杂统计、AI 绘图、心理咨询、打卡排行榜 —— **不建模块、不建表**。  
（Todo / 习惯打卡已按产品优先级落地；不做社交化排行。）

---

## 8. 落地顺序（下一步开发）

1. ✅ `packages/contracts` + Spring Boot 模块骨架（checkin/summary/memory/timeline/proactive/aigateway）
2. ✅ **MyBatis + MySQL**（`db/schema.sql`）
3. ✅ 用户注册登录（JWT）+ 定时提醒模块
4. ✅ 在 `aigateway` 接通 LLM HTTP Client；保留 Heuristic 降级
5. ✅ 补 proactive 检索式关联（embedding + 余弦相似度 Top-K；无向量时按 strength 降级）
6. ✅ 慢路径异步化：checkin 先返回 → 后台更新 summary/memory（可轮询 summary）
7. ✅ 迁 `apps/web`，features 目录化
8. ✅ 接入 TanStack Query；默认强制走 API（本地 fallback 需 `NEXT_PUBLIC_ALLOW_LOCAL_FALLBACK=true`）
9. ✅ Todo / 习惯打卡合入 `apps/api` + `apps/web` features
10. ✅ 主链路体验：checkin 整理中状态 / 失败重试 / 保存后刷新时间轴·记忆·主动关联；追问一点即填
11. ✅ 记忆可管理（编辑 / 归档 / 删除；主动回忆排除已归档）
12. ✅ 向量库外挂（`VectorStore`：mysql 默认 / qdrant 可选 + 回退；见 `docs/vector-store.md`）
13. ✅ e2e 冒烟（登录→打卡→记忆；`npm run e2e:smoke`）
14. ✅ 主动关联变真（跨日追问候选、指纹去重、已回答/忽略抑制、选择填脚手架；见 `proactive_prompt_events`）
15. ✅ 向量库可运维（reindex/backfill、recreate、`/health.vector`；`scripts/vector-reindex.sh`）
16. ✅ 异步可靠化（`checkin_ai_jobs` 持久化 + 重试退避 + 失败可见 + reprocess）
17. ✅ 可观测性（`ai_call_logs` + `/health.ai` + `/v1/admin/ai/stats|calls`）

---

## 9. 选型一句话结论

- **前端：** Next.js + TS + Tailwind + TanStack Query，按 `features/*` 对齐产品能力。  
- **后端：** Spring Boot 3 + Java 17 + **MyBatis + MySQL**，按同名业务包拆分。  
- **AI：** 独立 `aigateway`，OpenAI 兼容协议；无 key 时 Heuristic 降级。  
- **共享：** `packages/contracts` 约束模块边界与 HTTP 路由（Java DTO 手动对齐）。
