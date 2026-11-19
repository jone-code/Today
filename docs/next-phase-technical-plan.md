# Today 下一阶段技术方案

状态：方案已确认，进入发布候选验证（CI、设备和生产环境证据仍待完成）

版本：2026-11-08

## 1. 技术原则

- MySQL 是业务数据和权限的唯一真相，Qdrant 只提供可重建的检索索引。
- 语音转写、Chat 整理和导出都必须是可恢复任务；客户端不把“请求发出”当成“业务成功”。
- 模型只返回结构化建议，不能直接写 check-in、memory、todo、followup 或权限设置。
- 所有跨端写入继续使用幂等键、对象版本和用户归属校验。
- 隐私设置在服务端过滤上下文，客户端只负责展示当前状态。

## 2. 现有能力盘点

- 已有 `POST /v1/checkins`、异步总结/记忆候选、Chat 提议确认和 `GET /v1/today/home`。
- 已有 `product_events` 和批量 `POST /v1/events`，但需要补齐事件字典、看板查询和版本/平台字段约束。
- 已有记忆来源、归档、删除和向量同步重试；需要增加“是否允许 Chat 使用”的独立范围字段。
- 移动端使用 Expo 53；当前未安装音频录制或转写 SDK，语音方案需要先决定供应商和隐私策略。

## 3. 数据模型

### 3.1 产品指标

保留 `product_events` 作为事实表，增加/约束：

- `platform`、`app_version`、`event_schema_version`。
- `occurred_at` 与服务端接收时间分开保存。
- `(user_id, client_request_id)` 去重策略和非法未来时间校正。
- 事件字典由代码和文档共同维护；服务端通过 `ProductEventCatalog` 拒绝未登记事件，避免未知事件进入看板。

新增只读聚合查询或物化日报：`activation_funnel_daily`、`retention_daily`、`feature_quality_daily`。只允许内部管理员访问，默认返回脱敏计数，不返回原始文本。

### 3.2 语音任务

新增 `voice_transcription_jobs`：

```text
id, user_id, client_request_id, status,
media_path, content_type, duration_ms, transcript,
error_code, attempts, lease_until, expires_at,
created_at, updated_at, completed_at
```

状态：`queued / uploading / transcribing / succeeded / failed / expired`。

- 唯一键：`(user_id, client_request_id)`。
- 音频文件短期存储，任务成功或过期后清理；保留期限由配置控制。
- 转写服务只返回文本和置信度摘要，不直接创建 check-in。

### 3.3 记忆上下文权限

在 `memories` 增加 `chat_context_scope`：`chat_and_timeline / timeline_only / disabled`，默认 `chat_and_timeline`；归档和删除状态优先级高于该字段。

新增 `memory_access_audit`：

```text
id, user_id, memory_id, action, source,
request_id, occurred_at, metadata_json
```

Chat 检索必须在 SQL 权限过滤后再进入向量检索，或对向量结果二次按 memory ID 过滤，不能只依赖 Qdrant payload。

### 3.4 数据导出与删除

新增 `data_requests`：

```text
id, user_id, type, status, requested_at,
started_at, completed_at, expires_at,
attempts, error_code, output_path
```

状态：`queued / running / succeeded / failed / expired / cancelled`；同一用户同一类型同时只能有一个运行任务。导出包加密存储、短期下载、一次性令牌，过期后删除。

## 4. API 契约

### 指标

- `POST /v1/events`：沿用批量写入，补充 schema/platform 校验。
- `GET /v1/admin/metrics/activation?from=&to=`：漏斗计数。
- `GET /v1/admin/metrics/retention?from=&to=`：D1/D3/D7 cohort。
- `GET /v1/admin/metrics/quality?from=&to=`：转写、提议、导出和删除质量。

管理员接口必须独立权限、默认脱敏、限制时间范围，并记录访问审计。

### 语音

- `POST /v1/voice/transcriptions`：multipart 音频 + Idempotency-Key，返回任务。
- `GET /v1/voice/transcriptions/{id}`：查询状态和转写文本。
- `POST /v1/voice/transcriptions/{id}/retry`：失败任务重试。

客户端拿到 transcript 后调用现有 `POST /v1/checkins`；不能由转写接口隐式保存。

### Chat 整理

沿用现有提议协议，新增 proposal 类型 `checkin_from_conversation`，字段包含来源消息 ID、正文预览、目标日期、来源摘要和 expiresAt。确认时调用现有 confirm 流程，服务端再次检查会话和消息仍属于当前用户且未删除。

### 记忆控制

- `PATCH /v1/memories/{id}/context-scope`：更新范围和版本。
- `GET /v1/memories/{id}/access-audit`：只返回当前用户可见的最近操作。
- `GET /v1/profile/privacy`、`PATCH /v1/profile/privacy`：全局 Chat 记忆开关、语音保留偏好和跟进授权分开保存。

### 导出与删除

- `POST /v1/data-requests/export`、`GET /v1/data-requests/{id}`、`GET /v1/data-requests/{id}/download`。
- `POST /v1/data-requests/delete-account`、`POST /v1/data-requests/{id}/retry`。

下载接口只允许短期令牌；删除接口必须二次确认短语或一次性确认 token，且幂等。

## 5. 语音实现决策

已确认本阶段采用“设备录音 + 服务端转写接口”的抽象，但暂不接入真实供应商：

1. 移动端使用 Expo 音频模块录制 m4a，限制时长、大小和采样率。
2. API 负责上传、鉴权、临时存储和任务状态。
3. `TranscriptionProvider` 只定义接口和本地/测试实现，返回明确的 `provider_unconfigured` 失败码；供应商不可出现在客户端。
4. 未配置供应商时保留任务为 failed，允许用户改用文字记录；不伪造转写成功。

成功转写后的音频立即删除；失败任务只按任务保留策略短暂保存，供重试或清理。真实供应商、数据出境和外部试用在后续决策中单独确认。

## 6. Chat 上下文管线

请求进入 → 校验全局 Chat 开关 → SQL 读取 `chat_context_scope=chat_and_timeline` 且有效的记忆 → 向量召回 → 二次权限过滤 → 注入来源 ID → 生成回答/结构化提议 → 记录 `memory_access_audit`。

关闭全局开关时直接跳过记忆检索；单条 disabled 或 timeline_only 记忆不能作为 Chat 上下文。审计记录不保存原始敏感文本，只保存 memory ID、结果数量、来源和策略版本。

## 7. 导出和删除流程

导出任务读取业务表的用户归属数据，按固定 manifest 生成 JSON/CSV/媒体索引；不从向量库反向生成用户数据。生成前后检查账号状态和任务版本，失败可重试。

删除任务按顺序执行：停止跟进与调度 → 逻辑删除会话/记录/记忆 → 撤销媒体访问 → 写入向量删除队列 → 清理导出包和短期音频 → 最终标记完成。每一步可重试，重试必须重新检查用户归属和当前状态。

## 8. 测试与发布门槛

- 契约测试：语音任务状态、提议确认、上下文范围、导出下载和删除幂等。
- 安全测试：跨账号 IDOR、关闭设置后的检索、删除竞态、过期下载令牌、管理员脱敏。
- 可靠性测试：上传超时、转写供应商超时、重复 webhook、worker 抢占、导出中断、Qdrant 不可用。
- 移动端测试：权限拒绝、录音中断、键盘编辑、断网重试、同一请求 ID 恢复。
- 发布检查：HTTPS、密钥分离、迁移前备份、回滚脚本、监控告警、Android 真机和 iOS 关键路径。

进入真实用户试用的硬门槛：上下文权限测试全绿、删除后检索残留为 0、导出可下载且过期可清理、语音失败可回退文字、所有 P0 事件可在看板重算。

## 9. 开发顺序

1. 指标字典、管理员查询和数据脱敏。
2. 语音任务抽象、移动端录音、转写适配器和文字回退。
3. Chat 整理记录提议卡复用现有确认协议。
4. 记忆范围字段、上下文过滤、隐私设置和审计。
5. 导出/删除任务、媒体和向量清理。
6. 真机验证、7 天试用、修复问题并评估下一阶段。

## 10. 已确认的决策

已确认：

1. 本阶段只做语音转写接口，不接真实供应商。
2. Chat 默认可使用有效长期记忆；成功转写后立即删除音频。
3. 首轮只开放内部账号验证。

仍沿用方案建议：导出包使用 JSON + CSV + 媒体清单 ZIP；真实供应商和外部试用在内部验收通过后再确认。

## 11. 数据流与迁移顺序

### 11.1 指标数据流

客户端事件队列 → `POST /v1/events` → schema 校验与去重 → `product_events` → 日聚合任务 → 管理员看板。业务事实（例如 check-in 保存、提议确认）同时作为对账源；看板不得只依赖客户端事件。

### 11.2 迁移顺序

1. 先创建新表和 nullable 字段，不改变旧读写路径。
2. 发布兼容契约和服务端只读查询。
3. 回填默认值和历史数据的 `unknown` 标记，记录数量与失败行。
4. 开启新写路径和灰度开关，观察错误率。
5. 验证权限、删除和重试后，再删除废弃字段或旧路径。

每个迁移必须有向前 SQL、回滚说明、备份点和启动校验；涉及用户删除的数据迁移不允许静默跳过失败行。

## 12. 接口行为约束

- 所有创建任务接受 `Idempotency-Key`，相同用户和相同 key 必须返回同一任务；不同 payload 返回 409。
- 所有查询和下载接口先做当前用户归属校验，再读取对象；不能通过错误信息泄露对象是否存在。
- 长任务返回任务 ID，不在 HTTP 请求中等待模型或压缩包完成。
- `retry` 只允许 failed/expired 状态，running 状态返回当前任务；重试次数和退避由服务端控制。
- 删除和权限更新使用对象版本，冲突返回 409 并要求客户端重新读取。

### 推荐的任务响应

```json
{
  "id": "uuid",
  "status": "queued",
  "createdAt": "2026-11-08T00:00:00Z",
  "expiresAt": "2026-11-09T00:00:00Z",
  "retryable": true
}
```

客户端不得根据 HTTP 200 推断任务已经完成，必须继续查询状态。

## 13. 可观测性与告警

每类任务至少记录：任务 ID、用户哈希、请求 ID、状态转移、耗时、重试次数、供应商类别、错误码和数据清理结果；禁止记录原始录音、转写全文或完整记忆正文。

首批告警：

- 转写成功率低于 95% 或 P95 超过 30 秒。
- 事件接收 5 分钟无数据、重复率异常或 schema 拒绝率超过 2%。
- Chat 关闭记忆用户出现非零记忆注入记录。
- 删除任务超过 24 小时未完成或向量删除失败持续增长。
- 导出失败率超过 5% 或过期文件清理延迟超过 1 小时。

## 14. 发布与回滚

发布分为四个开关：`voice_input_enabled`、`memory_context_control_enabled`、`data_export_enabled`、`account_delete_enabled`。默认关闭新供应商能力，内部账号验证通过后按用户列表开启。

回滚优先关闭开关，保留已创建任务的清理 worker；数据库迁移保持向后兼容，不能通过回滚应用重新启用已禁用的记忆或恢复已删除内容。每次发布前用脱敏种子数据演练一次“上传失败、权限关闭、导出失败、删除重试”。

## 15. 需求到技术验收映射

| 产品需求 | 技术验收 |
|---|---|
| P-01/P-02 | 业务事实与 product_events 对账误差可解释，事件 schema 测试通过 |
| P-03/P-05 | 录音中断、供应商超时、重复上传和文字回退测试通过 |
| P-04 | proposal 来源、版本、过期和确认事务测试通过 |
| P-06/P-07 | SQL/向量双重过滤、跨账号隔离、关闭后零注入测试通过 |
| P-08 | 导出 manifest 完整、下载令牌过期、包清理测试通过 |
| P-09 | 删除分步重试、媒体和 Qdrant 清理、最终一致性检查通过 |
| P-10 | 灰度开关、监控告警、数据库回滚演练通过 |
