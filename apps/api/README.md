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

记忆的向量更新与删除通过 `vector_sync_jobs` 持久化排队，Qdrant 模式默认每 15 秒扫描。MySQL 为权限与归档状态的真相源；Qdrant 不可用时任务退避重试，恢复后按当前记忆状态同步。暂时切到 MySQL 向量模式不会确认未完成的 Qdrant 任务；归档记忆不会在重建时重新入索引。

`POST /v1/checkins` 默认异步：立刻返回 `{ status: "processing", summary: null }`，后台跑 summary → 待确认记忆候选；前端轮询 `GET /v1/summaries/:date`。设 `TODAY_AI_ASYNC_CHECKIN=false` 可改回同步。Agent 对话的每日记录提议通过 `PATCH /v1/agent/proposals/{id}/checkin-preview` 编辑/刷新预览，`POST /v1/agent/proposals/{id}/confirm` 经版本校验后写入并触发同一流水线；确认每日记录不会自动确认长期记忆。

移动端提交每日记录时带 `Idempotency-Key`，服务端在同一事务内记录首次提交与 AI 任务：同键同正文重试返回原结果，同键不同正文返回 409；未带键的旧客户端保持兼容。移动端在发送前需持久化正文与请求 ID，不能在响应超时后换键重试。

习惯照片替换、取消打卡和删除习惯时，在业务事务内将旧照片加入 `punch_photo_cleanup_jobs`；后台按租约重试物理删除。照片读取必须仍有该用户的有效打卡引用，删除后即使文件暂未清除也不可访问。已有库执行 `migration-punch-photo-cleanup.sql` 后再启动新版 API。

在独立的临时 MySQL 测试库中设置 `TODAY_TEST_MYSQL_URL`、`MYSQL_USER` 和 `MYSQL_PASSWORD`，运行 `mvn -Dtest=PunchPhotoCleanupDatabaseTest test` 可验证真实 MyBatis 映射、事务回滚、清理重试和删除后的媒体权限。不要指向生产库；测试库地址会覆盖常规 `MYSQL_URL`。

同一临时库可运行 `mvn -Dtest=FollowupConcurrencyDatabaseTest test`，用两个并发扫描验证同一账号在多个话题同时到期时仍遵守单日投递上限，并验证关闭跟进的 100 个话题不会长期占住扫描队列。

`mvn -Dtest=FollowupLifecycleDatabaseTest test` 验证两次未回应后的自动暂停、从站内消息继续恢复、选择稍后清除未回应计数，以及手动停止不能被旧消息绕过。

`PATCH /v1/agent/followups/topics/{id}` 允许本人编辑已授权话题缘由及可选 `nextDate`（用户时区的当地 09:00）；请求必须携带列表中看到的 `expectedReason` 与 `expectedNextEligibleAt`，并发修改返回 409。日期限未来 30 天，且不得突破同话题 7 天间隔。可在隔离库运行 `TODAY_TEST_MYSQL_URL=<测试库 JDBC URL> mvn -Dtest=FollowupTopicUpdateDatabaseTest test` 验证属主、时区、重试和状态边界。

“继续聊”同一站内消息允许成功后重试，返回原会话且不重复调整跟进状态；手动停止或来源删除后仍拒绝继续。`FollowupLifecycleDatabaseTest` 同时覆盖并发重复请求和单次约定的重试。

明确说“不要记住”或“别再跟进”时，本会话未确认的同类提议会在用户消息入库事务内拒绝，模型返回的新同类候选也会被过滤；不自动删已确认的记忆和已授权话题。`ProposalOptOutDatabaseTest` 验证撤销仅作用于当前用户和会话。

同一会话内，模型不能再次生成与已拒绝跟进卡缘由文本完全相同的确认卡；`ProposalOptOutDatabaseTest` 验证拒绝状态不影响其他会话或其他账号。这不是语义去重，改写缘由仍可能再次提议，需在真实模型评测中继续观察。

`GET /v1/agent/actions` 返回最近的本人操作记录。确认后的待办编辑、习惯编辑、每日提醒编辑可调用 `POST /v1/agent/actions/{proposalId}/undo` 撤销：服务端锁定操作及目标对象，只有目标仍与该次编辑后的快照一致才恢复编辑前内容；后续改动或目标已删除返回 409，不覆盖新内容。重复撤销返回原结果；旧操作（没有快照）、创建、打卡和单次约定不支持撤销。已投递的提醒不因设置撤销而收回。升级已有库需执行 `migration-agent-action-undo.sql`，也可使用 `npm run db:migrate`；新库结构已包含所需字段。

模型建议创建待办、习惯或每日提醒时，服务端仅在本轮用户消息有对应明确创建请求且未表达常见否定句时生成确认卡；普通闲聊不会自动成为操作。该规则是保守的关键词过滤，不等于可靠的自然语言意图识别，复杂句式和模型误判仍须通过真实模型评测与用户确认兜底。执行操作只发生在确认卡被显式确认之后。

对话还会读取该账号已保存的今日记录，以及最多 5 条更早日期的时间轴摘要（排除今天、每条正文限 160 字）。这是供回答时参考的历史数据，不是已确认长期记忆；不能仅凭历史记录自动新建记忆、关注话题或内部操作。只有未确认的新提议经用户明确确认后才可写入。

单次站内预约同样要求明确的提醒意图；“明天不要提醒我”这类否定请求不会生成单次预约确认卡。

单次预约须在用户本轮原话中找到具体钟点、`HH:mm` 或“半小时后”等明确相对时间；只有“明天”“上午”不创建带模型猜测时刻的确认卡。此检查不能替代时间语义解析与确认卡上的日期/时区核对。

站内消息“标记已读”和“关闭”也支持同状态重试：已读时间不会因重放改变，其他账号仍不可读取该消息。

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
- 启动时验证授权与幂等相关的关键表、列和唯一索引；结构不完整时 API 会拒绝启动。关闭 `TODAY_SCHEMA_BOOTSTRAP` 只关闭自动执行迁移，不关闭校验。
- 注册/登录、模型对话及形象上传使用 MySQL 原子计数限流，多实例共享限额；登录按请求的直连 IP 和邮箱分别限制，不信任可伪造的转发头。聊天的成功幂等重放和紧急安全回复不消耗模型额度，超额返回 `429` 与 `Retry-After`。可通过 `TODAY_RATE_*` 环境变量调节默认阈值（参见 `application.yml`）。

## Agent API 联调

在测试数据库上启动 MySQL 和本地模拟模型，再启动 API（下面使用 `3101` 避开常用的 `3001` 端口）：

```bash
docker compose up -d mysql
node scripts/mock-agent-model.mjs
```

另开终端启动 API：

```bash
cd apps/api
PORT=3101 OPENAI_API_KEY=mock-test OPENAI_BASE_URL=http://127.0.0.1:39011/v1 mvn spring-boot:run
```

第三个终端执行：

```bash
API_BASE_URL=http://127.0.0.1:3101 node scripts/e2e-agent-api.mjs
```

该用例覆盖形象名称/语气进入对话、已确认记忆的上下文过滤、会话幂等、提议确认和操作日志、待办/习惯/每日提醒编辑的当前用户可见范围、版本冲突、确认后执行及可撤销性、撤销后的重复请求与后续修改保护、对话整理为每日记录的可编辑预览/并发冲突/重预览、账号隔离、单次预约取消、模型擅自填入预约时刻的候选过滤、话题首次投递前可见/停止/恢复及来源删除后的不可恢复、会话删除的派生记忆清理。每日记录确认后沿用异步日总结；删除来源对话不删除已确认的记录和待办。模拟模型仅验证应用链路，不证明真实模型服务可用；不要对生产数据库运行此脚本。

同一临时数据库和模拟模型下可运行 `API_BASE_URL=http://127.0.0.1:3101 node scripts/e2e-checkin-race.mjs`。该用例在旧总结生成期间修改每日记录，验证晚到结果不会覆盖新总结或新任务状态，并要求新任务不依赖下一轮调度器才启动。

形象上传的重试与归属验证仅可对隔离的一次性 MySQL 库和独立临时 `TODAY_MEDIA_ROOT` 运行：将 API 的 `TODAY_RATE_AVATAR_PER_DAY=2`，再运行 `AVATAR_E2E_DISPOSABLE=1 API_BASE_URL=http://127.0.0.1:3101 node scripts/e2e-agent-avatar.mjs`。脚本验证同键并发重试只创建一次、不重复扣额，同键换图返回 409，第二个新键替换成功、第三个新键受限流，以及属主可读和 `no-store` 响应；在测试库 `agent_media` 中核对脚本输出的用户 ID 仅一条 `active`，另一条为可清理的 `replaced`。`TODAY_TEST_MYSQL_URL=<一次性测试库 JDBC URL> mvn -Dtest=AgentProfileConcurrencyDatabaseTest test` 可验证上传持锁时恢复默认等待及最终状态（需设置测试库账号密码）。移动端将待上传图片保存在按服务器和账号隔离的私有目录，安全草稿仅记录请求 ID，重启后以原 ID 重试。

旧形象文件按批清理；删除失败的记录将下次尝试时间推迟 1 小时，不阻塞同批其他文件或后续批次。升级已有数据库须执行 `migration-agent-media-cleanup.sql`；启动校验要求 `agent_media.next_cleanup_at` 及到期扫描索引。可在隔离测试库运行 `TODAY_TEST_MYSQL_URL=<测试库 JDBC URL> mvn -Dtest=AgentMediaCleanupDatabaseTest test` 验证积压队列不会被前 100 条失败项阻塞。

限流并发测试需在临时库与本地模拟模型下启动 API，并指定 `TODAY_RATE_LOGIN_EMAIL_PER_FIVE_MINUTES=3`，然后运行 `API_BASE_URL=http://127.0.0.1:3101 node scripts/e2e-rate-limits.mjs`。同时验证默认每小时 30 次聊天额度、幂等重放和紧急安全响应；不要在生产或共享测试库上运行。

向量故障演练可将 API 设置为 `TODAY_VECTOR_PROVIDER=qdrant TODAY_QDRANT_URL=http://127.0.0.1:6339`：先保持该端口关闭，确认 `vector_sync_jobs` 留有重试任务，再运行 `node scripts/mock-qdrant.mjs`，查看 `http://127.0.0.1:6339/__stats` 的删除次数和队列清空情况。这个测试桩不存真实向量，也不代替 Qdrant 集成验收。

删除与重建竞态可在独立临时 MySQL 库运行 `TODAY_TEST_MYSQL_URL=<测试库 JDBC URL> TODAY_TEST_MOCK_QDRANT_URL=http://127.0.0.1:6339 mvn -Dtest=VectorReindexRaceDatabaseTest test`（同时运行上述 Qdrant 测试桩，并提供测试库账号）。测试桩只保存点 ID，不保存记忆正文；用例验证上传后来源失效时校正作业最终删除该点。

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
| `TODAY_MEDIA_ROOT` | `data/media` | 打卡照片本地目录（Docker 建议 `/data/media`） |
| `TODAY_MEDIA_MAX_BYTES` | `10485760` | 单张照片上限（字节） |

## 试用反馈

`POST /v1/feedback` 需 Bearer Token、`Idempotency-Key` 和 `{ "kind": "issue" | "idea", "body": "..." }`；同键同内容重试返回原结果。`GET /v1/feedback` 列出本人最近 20 条未删除反馈，`DELETE /v1/feedback/{id}` 仅能逻辑删除本人反馈，重复删除返回 204。删除会在同一事务内移除幂等响应缓存中的正文，原请求键仍保留防止重新创建；删除后用同键重放返回 409。

默认每账号每 24 小时最多提交 5 条，可用 `TODAY_RATE_FEEDBACK_PER_DAY` 调整；超限返回 429 和 `Retry-After`。已有数据库需先执行 `src/main/resources/db/migration-feedback-reports.sql`（以及此前的 `migration-create-idempotency.sql`、`migration-rate-limits.sql`），再启动 API。仅保存用户填写的类型和正文，不自动收集会话、照片或日志。隔离测试库可运行 `TODAY_TEST_MYSQL_URL=<JDBC URL> mvn -Dtest=FeedbackDatabaseTest test`，HTTP 用例为 `scripts/e2e-feedback-api.mjs`（仅对临时库，且限额设为 2）。逻辑删除的反馈正文仍保留在原表中；物理清除和备份保留期限需在上线前制定。

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
  - JSON 打卡：`Content-Type: application/json`
  - 照片打卡：`multipart/form-data`，字段 `photo`（可选 `date` / `note`）
- Media：`GET /v1/media/{path}`（需登录；仅本人 `punch/{userId}/...`）

已有库迁移（推荐一键，幂等）：

```bash
npm run db:migrate
```

或按需手动执行：

- `src/main/resources/db/migration-auth-reminder.sql`
- `src/main/resources/db/migration-memory-embedding.sql`
- `src/main/resources/db/migration-todo-punch.sql`
- `src/main/resources/db/migration-punch-photo.sql`

表结构完整版见 `src/main/resources/db/schema.sql`。
