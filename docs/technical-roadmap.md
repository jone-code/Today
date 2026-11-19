# Today 技术实施方案

状态：Slice A、Slice B、Slice C、Slice D1、Slice D2 和 Slice E 已完成；指标、语音、隐私和数据生命周期切片已进入发布候选验证

更新时间：2026-11-18

下一阶段详细方案：

- [下一阶段产品方案](next-phase-product-plan.md)
- [下一阶段技术方案](next-phase-technical-plan.md)

关联文档：

- [产品发展路线图](product-roadmap.md)
- [Personal Agent 第一版产品需求](prd-personal-agent-v1.md)
- [Personal Agent 第一版技术方案](technical-personal-agent-v1.md)

## 1. 技术目标

在保留现有功能和信息架构的前提下，把 Today 建设为一套以“记录—理解—行动—回看”为核心闭环的移动端产品。

技术工作分为两类：

1. **稳定现有能力**：统一移动端体验、数据状态、错误处理、幂等重试和可观测性。
2. **补齐产品闭环**：让 AI 建议、长期记忆、行动和主动跟进都经过明确授权，并能被追溯、撤销或删除。

## 2. 当前架构基线

### 客户端

- `apps/mobile`：React Native + Expo 53 + TypeScript，Android/iOS 共用代码。
- `apps/web`：Next.js Web 客户端，继续复用 API 契约和服务端能力。
- `packages/contracts`：跨端 Zod DTO、API 路径和状态契约。
- 移动端登录凭据和草稿使用 Expo SecureStore；默认虚拟宠物 Logo 为本地资源。

### 服务端

- `apps/api`：Java 17 + Spring Boot 3 + MyBatis + MySQL。
- AI 能力统一通过 `aigateway`，支持 OpenAI 兼容模型和 Heuristic 降级。
- MySQL 保存业务真相；Qdrant 只作为可替换的记忆向量检索层。
- 已有模块：checkin、summary、memory、timeline、proactive、agent、todo、punch、reminder、feedback、identity。

### 已具备的关键机制

- 对话、每日记录、待办、习惯、提醒和头像上传的幂等请求。
- 记忆来源追踪、归档和向量同步任务。
- 主动跟进频率限制、暂停、恢复和站内收件箱。
- 操作记录和部分可撤销操作。
- AI 调用审计、Heuristic 降级、限流和账号隔离。

## 3. 目标架构原则

### 3.1 服务端决定权限，客户端只展示和确认

模型不得直接调用业务写接口。模型只能返回结构化建议；服务端验证用户、对象版本、授权状态和幂等键后执行操作。

### 3.2 业务数据和向量索引分离

MySQL 是用户数据、权限、来源和删除状态的唯一真相。Qdrant 的延迟或故障不能让已删除、已归档或无权限的内容重新出现在回答中。

### 3.3 所有异步流程可恢复

AI 总结、记忆抽取、向量同步、跟进投递和媒体清理都必须有明确状态、租约、重试次数和失败原因。晚到结果写入前重新校验来源版本和删除状态。

### 3.4 每个会影响用户数据的动作可解释

提议卡需要展示：操作类型、目标对象、拟修改内容、时间和时区、来源依据以及确认后的影响。

## 4. 数据与接口建设

### 4.1 第一优先级：产品指标与事件

新增统一事件模型，建议事件字段：

```text
event_id
user_id
session_id
event_name
screen
entity_type
entity_id
properties_json
occurred_at
client_request_id
app_version
```

首批事件：

- `auth_completed`
- `today_viewed`
- `checkin_started`
- `checkin_saved`
- `summary_viewed`
- `conversation_started`
- `message_sent`
- `proposal_viewed`
- `proposal_confirmed`
- `proposal_rejected`
- `action_completed`
- `inbox_item_opened`
- `inbox_item_read`
- `inbox_item_dismissed`
- `followup_continued`
- `followup_snoozed`
- `followup_stopped`
- `followup_resumed`
- `followup_updated`
- `appointment_cancelled`
- `weekly_review_viewed`

事件写入失败不能阻塞核心业务；客户端可本地短暂排队，服务端提供批量上报接口并限制单用户数据量。

### 4.2 第二优先级：Today 闭环状态

补充一个聚合接口，减少移动端首屏多次请求：

```text
GET /v1/today/home
```

返回：

- 今日记录和保存状态。
- 今日总结和 AI 任务状态。
- 待确认记忆候选数量。
- 待处理行动建议数量。
- 未读 Inbox 数量。
- 今日已完成待办/习惯概览。
- 可选的下一步建议。

该接口只做聚合，不改变现有资源接口；现有接口继续作为详情和写入入口。

### 4.3 第三优先级：周报

新增可恢复的周报任务和只读接口：

```text
GET  /v1/reviews/weekly?week=YYYY-MM-DD
POST /v1/reviews/weekly/:week/retry
```

周报只引用用户允许使用的有效记录、已确认记忆和已完成行动；不把周报生成结果自动写回长期记忆。

## 5. 移动端技术改造

### 5.1 设计系统

- 颜色、间距、圆角、阴影、字号统一通过 `theme.ts` Token 管理。
- 页面统一使用 `Page`、`Heading`、`Surface`、`Action`、`Notice`、`Avatar` 等基础组件。
- 所有图标使用同一套 Lucide 图标，避免 emoji 和混用风格。
- 每个页面只有一个主要 CTA；所有可点击目标至少 44×44dp。
- 默认宠物动画支持关闭，并使用 transform/opacity，避免布局抖动。

### 5.2 Today 首页

首屏顺序固定为：

1. 今日问候和宠物状态。
2. 今日记录输入。
3. AI 总结状态或结果。
4. 待确认记忆/行动入口。
5. 今日完成概览。

首屏只展示最重要的一个行动建议，其余进入 Inbox，避免首页变成信息流。

### 5.3 Chat

- 消息发送采用 `sending → processing → succeeded/failed` 状态。
- 网络失败重试复用原 `clientRequestId`。
- 提议卡和普通消息分离渲染。
- 删除会话、切换账号和切换会话时清理本地上下文。
- 继续聊必须携带来源会话或关注话题 ID。

### 5.4 Actions / Inbox / History / Profile

- Actions 以“今天要完成、正在坚持、稍后提醒”组织内容。
- Inbox 聚合所有待决定事项，处理后更新原消息状态，不重复创建。
- History 使用游标分页，避免一次加载全部消息。
- Profile 集中管理宠物、称呼、交流偏好、记忆、跟进权限和数据删除。

## 6. 开发阶段和验收门槛

### Slice A：指标和 Today 首屏

范围：事件模型、批量上报、`/v1/today/home`、Today 首屏接入、错误和加载状态统一。

验收：

- 新用户完成注册到保存第一条记录的链路可观测。
- Today 首屏不超过一个聚合请求加必要详情请求。
- 事件失败不影响保存记录和对话。
- Android 模拟器逐页截图，核心流程可回归。

### Slice B：Chat 与确认卡

范围：提议卡统一协议、确认/拒绝/过期状态、从对话整理今日记录、记忆和行动建议。

验收：

- 未确认前数据库不新增对应记忆、行动或跟进。
- 重试不重复消息或业务对象。
- 过期对象重新读取后要求再次确认。

### Slice C：Inbox 与主动跟进

范围：站内消息状态机、频率调度、稍后、停止、继续聊、时区处理。

验收：

- 每天最多 1 条、每周最多 3 条、同话题至少间隔 7 天。
- 连续 2 次未回应后暂停。
- 应用关闭期间不发系统通知，重新打开后仍可见未读消息。
- 并发扫描不重复投递。

### Slice D：周报和长期记忆管理

范围：周报任务、记忆来源、编辑/归档/删除、删除竞态和索引清理。

验收：

- 周报只使用有效授权数据。
- 删除会话后派生记忆、关注点和未投递跟进不再进入上下文。
- Qdrant 删除失败可重试，MySQL 权限检查仍然有效。

### Slice E：正式试用准备

范围：真实设备、崩溃与性能监控、隐私说明、数据导出、发布构建、灰度用户反馈。

验收：

- Android 真机核心流程通过。
- iOS 模拟器和真机关键路径通过。
- 生产环境使用 HTTPS、独立密钥、可回滚数据库迁移。

## 7. 测试策略

- 单元测试：状态机、频率规则、授权校验、幂等和请求冲突。
- API 集成测试：账号隔离、删除竞态、并发扫描、时区边界和向量重试。
- 移动端测试：SecureStore 草稿恢复、断网重试、键盘布局、导航和无动画模式。
- E2E 冒烟：注册 → 记录 → 总结 → 对话 → 确认建议 → Inbox → 回看。
- 视觉回归：Android 模拟器固定分辨率逐页截图，保存到 `test-results/`。

每个 Slice 必须同时通过类型检查、相关测试、Expo Android 导出和 `git diff --check`，再进入下一阶段。

## 8. 需要产品确认的事项

首个垂直切片已确认并完成：

1. **Slice A（已完成）**：指标、Today 聚合接口和首页闭环。
2. Slice B，先做 Chat 提议确认协议。
3. Slice C，先做 Inbox 和主动跟进。

Slice A 先建立了产品数据基础，同时改善了用户每天最常使用的入口；后续 Slice B/C 的成功率将依赖这些事件数据。

### Slice A 实现记录

- 新增 `product_events` 表、迁移脚本和 `/v1/events` 批量上报接口。
- 新增 `GET /v1/today/home` 聚合接口，返回今日记录、AI 状态、总结、待确认数量、未读数量和行动概览。
- 移动端 Today 首屏接入聚合接口，增加待办、习惯和未读概览卡片。
- 移动端记录 `today_viewed` 和 `checkin_saved` 事件；事件上报失败不会阻塞核心操作。

### Slice B 实现记录

- 提议 DTO 增加 `expiresAt`，移动端能识别并禁用已过期确认卡。
- Chat 确认卡统一使用服务端提议状态和有效期，过期时明确提示重新发起请求。
- 确认和拒绝分别记录 `proposal_confirmed` / `proposal_rejected` 产品事件。
- 服务端确认继续使用事务和对象版本校验；执行失败会回滚提议状态，允许用户重试。
- 未确认提议不会写入记忆、每日记录、待办、习惯或提醒。
- 下一阶段候选为 Slice D：周报和长期记忆管理，进入前需要确认周报的首版范围和记忆管理优先级。

### Slice C 实现记录

- Inbox 首屏接入 `inbox_viewed`，消息处理记录 `inbox_item_read`、`inbox_item_dismissed` 和 `followup_continued`。
- 稍后、停止、恢复、编辑跟进和取消一次性约定分别记录对应产品事件，事件上报失败不阻塞业务操作。
- 消息来源明确区分“主动跟进”和“一次性约定”，便于验证主动跟进的处理率和打扰控制。
- 继续复用服务端已有状态机：每日最多 1 条、每周最多 3 条、同话题至少间隔 7 天，连续 2 次未回应自动暂停，并按用户时区计算。
- 未改变现有 Inbox 状态、跟进调度或约定取消 API；本切片聚焦可观测性和处理反馈闭环。

## 9. Slice D 进入前的技术审计

Slice D 有两个可以独立交付的方向，当前代码基线不同：

### D1：周报首版

当前没有周报任务、周报 DTO 或 `/v1/reviews/weekly` 接口，需要新增：

- 周报任务表：按用户和周起始日唯一，记录 `queued/running/succeeded/failed`、租约、重试次数和错误原因。
- 周报只读接口和重试接口；生成输入固定为有效每日记录、已确认记忆和已完成行动。
- 移动端回看入口、加载/失败/重试状态和 `weekly_review_viewed` 埋点。
- 任务生成必须重新检查数据授权和逻辑删除状态，结果不自动写入长期记忆。

D1 是新的纵向能力，能直接验证“记录—理解—回看”闭环，但需要新增异步任务和展示页面。

### D2：长期记忆管理

当前已有可复用能力：

- `TimelineScreen` 已支持记忆查看、编辑、归档、恢复、删除和来源展开。
- `MemoryService` 已在 MySQL 维护权限和状态，并调用向量层的更新、归档和删除操作。
- 对话删除和记忆来源查询已有接口，可作为删除竞态测试基础。

D2 的主要缺口是完整性验证和可观测性：补充删除竞态、向量删除重试、跨账号隔离、归档记忆不可检索测试，以及记忆操作事件和来源展示的一致性检查。

### 进入条件

在用户确认 D1、D2 或并行后，再按所选范围拆分数据库迁移、契约、服务端、移动端和验收测试；未确认前不修改周报或记忆的业务行为。

## 10. Slice D1 实现记录：周报首版

- 新增 `weekly_reviews` 表，以 `(user_id, week_start)` 保证幂等，并保存状态、尝试次数、错误、锁定时间和生成时间。
- 新增 `GET /v1/reviews/weekly?week=YYYY-MM-DD` 和 `POST /v1/reviews/weekly/retry?week=YYYY-MM-DD`。
- 周起始日统一归一到周一；生成内容只读取当前用户该周的每日记录和已完成待办，不读取未确认记忆、已删除会话或向量索引，也不自动写回长期记忆。
- 移动端“回看 → 时间轴”加入本周周报卡片，覆盖生成、成功、失败和重试状态，并记录 `weekly_review_viewed`。
- 当前 D1 使用服务端同步生成和数据库状态持久化，后续若周报生成耗时增加，再把 `running` 阶段接入独立调度器；接口和状态契约保持不变。

## 11. Slice D2 实现记录：长期记忆管理

- 复用现有 `MemoryService` 的用户归属校验、来源激活状态和逻辑删除；编辑会清空旧 embedding 后重新生成，归档/删除/来源失效都会进入向量同步队列。
- 向量同步使用版本号和租约抢占；删除或来源失效后，worker 重新读取 MySQL 的“已确认且有来源”状态，状态不存在时只执行向量删除，避免旧 worker 把记忆重新写回索引。
- Qdrant 删除失败保留同步任务并按退避重试；MySQL 向量模式不触碰 Qdrant 任务。
- 移动端记忆编辑、归档、恢复和删除分别记录 `memory_updated`、`memory_archived`、`memory_restored`、`memory_deleted`，失败时不记录事件。
- 现有 API 测试已覆盖跨用户来源隔离、未确认向量命中过滤、来源失效后的归档和向量删除竞态；本切片继续沿用逻辑删除，不改变物理清理策略。

## 12. Slice E Android 模拟器验证记录

- 已使用 `emulator-5554` 启动 Expo Go，Metro 必须从 `apps/mobile` 目录运行，端口为 8081。
- 登录/注册页已完成真实截图验证：Logo、奶白色面板、圆角、中文字体和长表单滚动正常，证据见 `test-results/emulator/`。
- 模拟器可访问本机 API 地址 `http://10.0.2.2:3001`；主机使用同一测试账号调用登录和 `/v1/auth/me` 返回 200。
- 已定位并解决登录后 401：旧的长期运行 Docker API 容器使用了过期镜像，导致 Today 聚合、Agent 和埋点接口与当前源码不一致；改用当前源码构建的 API 进程后，`/v1/auth/me`、`/v1/today/home`、`/v1/agent/inbox/unread-count` 和 `/v1/events` 均通过鉴权。
- 已完成认证态逐页截图：Today、Chat、Actions、Inbox、History、Profile，并保留 Today 滚动区域和 Profile 下半屏证据；文件见 `test-results/emulator/`。
- 当前 Expo Go 仅有 React Native require-cycle 警告，没有观察到 API 请求失败。原生 `expo run:android` 仍受 Gradle 8.13 下载超时影响，因此本轮使用 Expo Go 在 Android 模拟器完成验证。

## 13. 下一阶段开发记录：指标与语音接口

- 新增内部指标查询：`/v1/admin/metrics/activation`、`/v1/admin/metrics/retention`、`/v1/admin/metrics/quality`；默认只返回聚合计数，支持 `TODAY_METRICS_ADMIN_TOKEN` 保护和最长 31 天查询范围。
- 新增 `voice_transcription_jobs` 表、状态契约和三个接口：创建、查询、失败重试；按用户和 `Idempotency-Key` 保证幂等。
- 当前不接真实转写供应商，任务明确返回 `provider_unconfigured`，客户端可回退文字输入，不伪造转写成功。
- Today 页面接入 Expo 录音、麦克风权限、最长 120 秒限制、上传和失败提示；保存记录仍需用户显式点击，语音不会自动写入 check-in。
- API `mvn test`、contracts/mobile typecheck、mobile 40 项测试和 Android Expo export 已通过；运行态已验证指标接口返回 200、语音接口返回可重试的 `provider_unconfigured` 任务。
- 记忆上下文范围控制已实现：新增 `chat_and_timeline`、`timeline_only`、`disabled` 三档，历史记忆默认可用于 Chat；服务端查询、向量检索和移动端回看页均已接入范围过滤与设置入口。
- 隐私与数据生命周期切片开发中：Profile 全局 Chat 记忆开关和审计接口已接入；导出/删除目前只有同步原型，尚缺幂等任务、加密、一次性令牌消费、完整 manifest、清理重试和移动端下载/Android 删除确认。不得视为已验收或开放试用。真实语音供应商按已确认范围暂不接入。


### 隐私权限并发验证（2026-11-08）

- 隐私 PATCH 请求必须携带读取到的 `version`；服务端同时检查请求版本与 SQL 条件更新结果，旧版本或并发冲突返回 409。移动端冲突后重新读取设置，不自动重放开启权限操作。
- 默认设置初始化改为不覆盖已有行的插入，并重新读取持久化结果，避免并发初始化返回错误的许可状态。
- 音频保留策略限定为本阶段已确认的 `delete_on_success`，不再接受未实现的“短期保留”选项。
- 记忆审计写入异常不再被吞掉，异常会阻止该次上下文返回；这不代表所有审计元数据、权限竞态或删除路径已经验收。
- 新增 MySQL 集成测试实际验证版本条件更新、初始化不重置权限、用户隔离，以及两种禁用范围不进入 Chat 查询；4 项数据库测试通过，事务回滚测试数据。
- 导出/删除仍为未验收原型；完整异步任务、文件加密、一次性下载令牌、删除分步重试与移动端下载闭环仍须继续实现。

### 当前进度核对（2026-11-10）

- 本节更新此前“导出只有同步原型”的描述：导出已改为持久化队列、后台调度和事务锁领取；创建接口要求幂等键，同账号活动任务复用，失败/过期重试沿用任务 ID。
- 下载令牌已加入原子消费和过期检查；清理任务删除过期包，失败时保留文件引用供后续重试。
- 已有 `DataExportDatabaseTest` 的 MySQL 实测报告（1 项通过），覆盖重复创建、队列处理、非空记录 ZIP、跨账号下载拒绝、令牌重复消费拒绝、过期清理和原任务重试。未证明进程崩溃、多 worker 并发或大数据量行为。
- 导出尚缺加密、完整 manifest/CSV/媒体清单和移动端任务恢复/下载入口；当前 Profile 只展示任务创建提示。
- 账户删除仍是同步原型，缺分步持久化、可靠向量/媒体清理和失败恢复；移动端使用 iOS 专属 Alert.prompt，Android 确认流程尚未完成。
- 内部账号名单与指标管理员令牌为空时仍放行，尚不满足内部试用的默认权限边界。
- 已有模拟器截图属于此前页面验证，不能代表新增隐私/导出/删除流程已通过 Android 回归。整体目标保持开发中，不应标记完成。

### 剩余开发与验证推进（2026-11-10）

本节更新前述导出和删除的实现状态：已新增加密 ZIP、JSON/CSV/媒体与校验清单、移动端下载/恢复、分步删除/重试、跨平台删除确认和默认拒绝的内部权限。详细测试证据、配置、回滚方式与尚未完成的设备/生产验收见 [内部试用开发与验证记录](internal-trial-validation.md)。不能将 JavaScript 导出或 API 测试通过解释为真机/正式发布验收通过。

### 2026-11-18～2026-11-19 语音与记忆权限增量

- 语音任务增加短期媒体路径、SHA-256 payload 校验、账号级小时/日限流、最大 3 次重试、provider 抽象、失败/成功清理和过期状态转换；不同幂等 payload 返回 409。
- 移动端录音增加 120 秒和 10 MB 限制、离开页面时释放麦克风、失败重试入口及录音/转写结果指标事件；成功后仍须用户明确保存文字。
- 记忆 Chat 范围更新增加 `updatedAt` 乐观并发校验，Timeline 上报范围变更事件；旧版本更新返回 409。
- 移动端记忆展开区同时展示可打开的来源和最近使用审计（来源、动作、时间），审计读取失败可重试，不阻塞范围设置。
- Web 端记忆面板同步支持 Chat 使用范围切换、来源展开和最近使用审计，并在旧的本地演示记忆缺少范围字段时默认按 `chat_and_timeline` 展示。
- 两端收到记忆范围更新的 409 并发冲突后自动重新读取最新记忆，并提示用户，避免继续使用过期版本覆盖其他设备的修改。
- 发布配置、Compose、`db-init.sh` 和语音迁移已同步增加限流、字段和索引检查；`release:check` 校验语音限额关系。
- 后端完整 Maven 回归、移动端 40 项测试、契约/移动端类型检查和脚本语法检查通过。
- 隐私授权变更新增 `privacy_settings_audit`，仅记录授权字段旧值/新值、来源和时间；跟进与隐私接口双向同步，并由 MySQL 集成测试校验两张设置表和审计记录一致。

以上代码和自动化证据仍不替代真实转写供应商决策、真机路径、生产 HTTPS/监控/备份恢复和 7 天内部试用。
