# `@today/api` — Spring Boot 后端

Java 17 + Spring Boot 3 + **MyBatis + MySQL**（原生 MyBatis，不用 MyBatis-Plus）。

## 模块包

```text
src/main/java/com/today/
  checkin/      每日记录
  summary/      AI 总结
  memory/       长期记忆
  timeline/     时间轴
  proactive/    主动关联
  aigateway/    模型抽象（OpenAI 兼容 + Heuristic 降级）
  identity/     用户认证
  reminder/     定时提醒
  todo/         待办
  punch/        习惯打卡
  persistence/  数据库实体
```

## AI Gateway

`summary` / `memory` / `proactive` 均经 `AiGatewayService.complete`：

1. 配置了 `OPENAI_API_KEY` → 调 OpenAI 兼容 Chat Completions，要求结构化 JSON
2. 无 key、超时、非 JSON、解析失败 → 自动降级现有 Heuristic
3. `provider` 字段写入 summary / proactive 响应用于观测

记忆写入时会调用 Embedding API，把向量存入 `memories.embedding_json`；`proactive` 先按近期记录检索 Top-K 相关记忆，再交给 LLM 挑选/润色。无 embedding 时按 `strength` 降级。

`POST /v1/checkins` 默认异步：立刻返回 `{ status: "processing", summary: null }`，后台跑 summary → memory；前端轮询 `GET /v1/summaries/:date`。设 `TODAY_AI_ASYNC_CHECKIN=false` 可改回同步。

已有库请执行：`src/main/resources/db/migration-memory-embedding.sql`

本地无 key 即可完整跑通；接真模型只需设置环境变量后重启 API。

MyBatis XML：`src/main/resources/mapper/**/*.xml`

## 本地运行

**1. 启动 MySQL**

```bash
docker compose up -d mysql
```

**2. 启动 API**

```bash
cd apps/api
mvn spring-boot:run
```

或仓库根目录：

```bash
npm run db:up
npm run dev:api
```

- 默认端口：`3001`
- 健康检查：`GET /health`

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/today?...` | JDBC 连接 |
| `MYSQL_USER` | `today` | 数据库用户 |
| `MYSQL_PASSWORD` | `today` | 数据库密码 |
| `TODAY_JWT_SECRET` | 开发默认值（请更换） | JWT 签名密钥，至少 32 字节 |
| `TODAY_JWT_EXPIRE_HOURS` | `168` | Token 有效小时数 |
| `OPENAI_API_KEY` | — | 有值时走 LLM；为空则 Heuristic |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容网关 |
| `OPENAI_MODEL` | `gpt-4o-mini` | Chat 模型名 |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding 模型 |
| `OPENAI_TIMEOUT_MS` | `30000` | 调用超时 |
| `OPENAI_JSON_RESPONSE_FORMAT` | `true` | 不支持 `response_format` 的网关可设 `false` |
| `OPENAI_RETRIEVE_TOP_K` | `5` | proactive 记忆检索条数 |
| `TODAY_AI_ASYNC_CHECKIN` | `true` | checkin 后异步跑 AI；`false` 同步 |

## 认证

- `POST /v1/auth/register` — 注册
- `POST /v1/auth/login` — 登录
- `GET /v1/auth/me` — 当前用户（需 Bearer Token）

其余 `/v1/**` 接口均需 `Authorization: Bearer <token>`。

## 定时提醒

- `GET/POST /v1/reminders`
- `PUT/DELETE /v1/reminders/{id}`
- `GET /v1/reminders/deliveries`
- `POST /v1/reminders/deliveries/{id}/read`

调度器每分钟扫描一次到期提醒，写入 `reminder_deliveries`。

## Todo / 习惯打卡

- Todos：`GET/POST /v1/todos`，`PUT/DELETE /v1/todos/{id}`，`POST /v1/todos/{id}/toggle`
- Punch：`GET/POST /v1/punch/habits`，`PUT/DELETE /v1/punch/habits/{id}`，`POST/DELETE /v1/punch/habits/{id}/punch`

已有库迁移（推荐一键，幂等）：

```bash
npm run db:migrate
```

或按需手动执行：

- `src/main/resources/db/migration-auth-reminder.sql`
- `src/main/resources/db/migration-memory-embedding.sql`
- `src/main/resources/db/migration-todo-punch.sql`

表结构完整版见 `src/main/resources/db/schema.sql`。
