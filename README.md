# Today（今天）

**AI 记住你的每一天。**

> AI 不只是和你聊天，而是一直记得你。

---

## 产品定位

一款基于 AI 长期记忆的每日记录与陪伴产品。

它不是日记，也不是打卡工具，而是一个能够陪伴用户成长、理解用户变化，并持续积累人生记忆的 AI。

---

## 为什么存在

人们每天都会经历很多事：工作压力、情绪波动、学习成长、与家人朋友的相处、创业和生活中的各种想法。但绝大多数内容都会随着时间被遗忘。

传统日记坚持困难。传统 AI 每次聊天都会“失忆”。

用户真正需要的是：**一个能够长期记住自己，并每天陪伴自己的 AI。**

---

## 产品目标

让用户每天只花 30 秒，记录今天。剩下的事情交给 AI。

AI 自动整理、总结、建立长期记忆，并在未来主动关联。随着时间推移，形成用户专属的人生数据库。

---

## 目标用户（第一阶段）

20～40 岁年轻人：程序员、产品经理、创业者、自由职业者、学生。

他们每天都有很多事，但没有时间写日记。

---

## 产品理念

| 不是 | 而是 |
|------|------|
| 写日记 | 留下今天 |
| AI 聊天 | AI 记住你 |

---

## MVP 要验证什么

第一版只验证一件事：

**用户是否愿意每天用 30 秒留下今天，并因为 AI 真正“记得自己”而持续回来。**

### 核心体验

1. **每日记录** — 首页只有一个问题：「今天怎么样？」支持文字（语音与图片后续）。全程控制在 30 秒内。
2. **AI 自动总结** — 用户无需整理；AI 生成完成事项、情绪、关键词与一句话总结。
3. **长期记忆** — AI 自动建立并更新对用户的理解（工作压力、运动、睡眠、创业准备等），无需用户维护。
4. **时间轴** — 每天一张卡片，方便回看。
5. **AI 主动关联** — 产品核心。例如昨天提到面试，今天主动追问结果；或发现「最近两周已经连续四次说很累」。

### 第一版明确不做

社区、好友、评论、分享、复杂统计、AI 绘图、心理咨询、打卡排行榜。

核心仍围绕：**每天记录 + AI 长期记忆**；Todo / 习惯打卡作为日常工具轻量配套。

---

## 未来演进

当用户连续使用数月后，可逐步扩展：

- **AI 情绪陪伴** — 识别长期情绪变化，给予温和陪伴
- **AI 成长教练** — 帮助发现学习、运动、睡眠等趋势变化
- **AI 人生时间轴** — 自动生成年度总结与人生记录
- **AI 第二大脑** — 接入微信、日历、邮件、照片、健康数据等

---

## 产品价值

市面上的 AI 能回答问题。我们的 AI **记住你**。

市面上的日记记录过去。我们的 AI **连接过去、理解现在、陪伴未来**。

---

## 架构与模块

完整模块划分与前后端选型见：**[docs/architecture.md](./docs/architecture.md)**

| 层 | 选型 |
|----|------|
| 前端 | Next.js in `apps/web`（`features/*` + `shared/`） |
| 后端 | **Spring Boot 3 + Java 17**（按 `com.today.*` 包拆模块） |
| 契约 | `packages/contracts`（前端 Zod；Java DTO 对齐） |
| 数据 | **MySQL 8 + MyBatis**（原生，不用 MyBatis-Plus） |
| AI | `ai-gateway`（OpenAI 兼容；无 key 时 Heuristic 降级） |

业务模块：`checkin` · `summary` · `memory` · `timeline` · `proactive` · `identity` · `reminder` · `todo` · `punch` · `ai-gateway`

## 本地开发

### 一键全栈（推荐验证发布形态）

```bash
cp .env.example .env   # 可选
npm run stack:up       # mysql + qdrant + api + web
```

说明见 [`docs/deploy.md`](docs/deploy.md)。CI：推送 / PR 到 `main` 会跑 Maven 测试、Web lint/build、Docker 镜像构建。

### 本机热更新开发

```bash
npm install

# MySQL（Docker）
npm run db:up
# 可选：Qdrant 向量库
npm run vector:up
# 旧库增量迁移（幂等）
npm run db:migrate

# 后端 Spring Boot（启用 Qdrant：TODAY_VECTOR_PROVIDER=qdrant）
npm run dev:api

# 前端
npm run dev:web
```

- Web: [http://localhost:3000](http://localhost:3000)（`/login` `/register` `/app` `/app/todos` `/app/punch` `/app/reminders`）
- API health: [http://localhost:3001/health](http://localhost:3001/health)（含 `vectorProvider` / `ai`）
- Qdrant: [http://localhost:6333/dashboard](http://localhost:6333/dashboard)（可选）

```bash
# 冒烟：登录 → 打卡 → 记忆（自动拉起 API/Web 若未运行）
npx playwright install chromium   # 首次
npm run e2e:smoke
# 仅 API：npm run e2e:smoke:api
```

向量库说明见 [`docs/vector-store.md`](docs/vector-store.md)。切换 Qdrant 后可对存量记忆执行：

```bash
TODAY_AUTH_TOKEN=<jwt> FILL_MISSING=true npm run vector:reindex
# 全量重建：SCOPE=all RECREATE=true FILL_MISSING=true npm run vector:reindex
```

AI 调用可观测见 [`docs/ai-observability.md`](docs/ai-observability.md)（`/health.ai`、`/v1/admin/ai/stats`）。

当前状态：**前端已迁入 `apps/web` 并接入 TanStack Query**；Todo / 习惯打卡已落地；默认强制登录走 API；`aigateway` 已接通 OpenAI 兼容 LLM + Embedding 检索；checkin AI 流水线异步且可重试；记忆可管理；e2e 冒烟可用；向量检索可外挂 Qdrant 并可 reindex；AI 调用可审计；Compose 一键全栈 + GitHub Actions CI 可用。
