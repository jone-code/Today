# 内部试用开发与验证记录

日期：2026-11-10～2026-11-11。范围为内部账号 MVP；正式上架和真实语音供应商不在本次交付范围。

## 已完成的开发

- 导出：持久化队列、事务领取、同账号活动任务复用、幂等创建、原任务重试、24 小时包清理。
- 导出文件使用 AES-256-GCM 加密存储，仅通过鉴权后的下载响应解密；下载凭证原子消费，10 分钟过期。
- ZIP 包包含 JSON、CSV、SHA-256 manifest、记忆来源、跟进偏好、隐私审计及有效照片/头像文件与媒体清单；不导出密钥、embedding 和模型调用日志。
- 移动端可重新读取服务端任务、查看状态、重试、下载后 Android 使用系统目录选择器保存，iOS 使用系统分享面板保存；操作结束清理临时 ZIP。
- Profile 设置页显示对话、语音、AI 记忆授权、导出和删除的隐私说明；关闭 Chat 记忆只停止后续检索，不删除回看数据。
- 删除改为后台分步任务：严格清理向量、清理媒体、最终业务数据删除。失败保留步骤和任务 ID，重试继续；外部清理异常不会伪装成成功。
- 删除请求立即停止跟进、提醒，失效记录/对话来源和记忆上下文，并撤销导出；普通 API 访问被阻止，状态/重试入口保留至账号最终删除。
- Android/iOS 共用输入框确认删除，替代 iOS 专属 Alert.prompt；删除中的独立页面支持退出、恢复状态和失败重试。
- 内部邮箱白名单为空时拒绝注册、登录和已有 JWT 访问；指标令牌为空时拒绝读取。激活统计修正起始日期过滤，质量接口增加任务聚合计数。
- Compose 增加相关环境变量；新增导出/删除开关和发布配置检查；CI 增加 MySQL 集成测试、模拟向量服务和移动端类型/测试/双端导出。

## 验证结果

- 后端测试报告：227 项，0 失败、0 错误、0 跳过。完整回归后修正媒体清理次数断言，重跑相关测试通过。
- 共享契约、移动端 TypeScript 检查通过；移动端 40 项测试通过。
- Web ESLint、Next.js 生产构建通过。
- 新增 `e2e/metrics.spec.ts` 覆盖指标看板加载、响应脱敏、令牌不落盘和 JSON 导出；Playwright 用例已通过测试发现/编译检查，完整浏览器运行需安装 Chromium。
- Web CI 已配置为在生产构建后安装 Chromium 并实际运行该 Playwright 用例，并上传页面截图、Web 日志和 `test-results` artifact；实际通过结果仍需下一次 Actions 运行确认。
- Expo Android 和 iOS JavaScript/Hermes 导出通过；这不是签名原生安装包。
- MySQL 验证：导出队列/幂等、ZIP 内容和校验和、跨账号拒绝、令牌重复消费与过期、清理、删除分步失败与恢复、账号隔离。
- HTTP E2E：新建专用测试账号，检查隐私旧版本 409、保存非空记录、异步导出、可解压 ZIP、单次下载、错误删除短语 400、删除期间普通访问 403、最终旧 JWT 与登录 401。
- 发布配置脚本验证：空配置拒绝，合成完整配置通过；Compose 配置解析通过。
- 运行态证据位于本机 `test-results/validation/`；ZIP 为专用测试账号的合成内容。自动化脚本不打印凭据。

本轮补验：共享契约与移动端类型检查通过，移动端 40 项测试再次全部通过；后端现有报告复核仍为 227 项、零失败/错误/跳过。新增证据位于 `test-results/validation-2026-11-11/`。

## 本地复现

```bash
npm run typecheck:contracts
npm run typecheck:mobile
npm test -w @today/mobile
npm run lint
npm run build
(cd apps/mobile && npx expo export --platform android --platform ios --output-dir dist)

node scripts/mock-qdrant.mjs
# 在另一终端运行，使用隔离/测试 MySQL：
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4 \
TODAY_TEST_MYSQL_URL='jdbc:mysql://localhost:3306/today?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
TODAY_TEST_MOCK_QDRANT_URL=http://127.0.0.1:6339 npm run test:api
```

`npm run e2e:data` 要求 `TODAY_E2E_LIFECYCLE_EMAIL` 和 `TODAY_E2E_LIFECYCLE_PASSWORD` 指向一个**尚未注册、已列入白名单**的专用账号。脚本会删除自己新建的账号；如果注册返回冲突，则拒绝使用或删除原账号。

## 发布配置与回滚

配置项见 `.env.example`：`TODAY_INTERNAL_EMAIL_ALLOWLIST`、`TODAY_METRICS_ADMIN_TOKEN`、`TODAY_DATA_EXPORT_KEY`。导出密钥必须为独立的 32 字节 Base64 密钥，不能在未过期包存在期间直接替换；正式 JWT 和管理员令牌也必须独立。

部署前设置 `TODAY_PUBLIC_API_URL` 为 HTTPS 地址，再运行 `npm run release:check`。该检查不替代证书、设备、备份和监控验证。

数据库迁移 `migration-data-request-steps.sql` 为增量字段，先备份后部署；保留新增字段以兼容回滚。旧明文导出标记过期，由清理任务删除，用户可重新导出。

回滚首先关闭 `TODAY_DATA_EXPORT_ENABLED` 和 `TODAY_ACCOUNT_DELETE_ENABLED`，停止新建请求，保留查询、重试与清理 worker；不要回退到没有账号删除访问限制的旧版本。已删除数据不通过应用回滚恢复。

## 内部 7 天试用执行清单

### 试用前

1. 为每位参与者创建独立测试账号，并将邮箱加入 `TODAY_INTERNAL_EMAIL_ALLOWLIST`；不得复用历史账号或生产账号。
2. 设置独立的 JWT、指标令牌和导出密钥，运行 `npm run release:check`；确认 `TODAY_PUBLIC_API_URL` 使用 HTTPS。
3. 记录客户端版本、平台和试用开始日期；只保留聚合指标和脱敏错误，不收集原始记录正文作为运营表格。
   事件口径中，`checkin_started` 表示用户发起保存，`checkin_saved` 表示服务端保存成功；两者使用不同的事件去重 ID，同一请求重试不会重复计数。`proposal_viewed` 按提议 ID 去重，用于区分建议展示和确认。
4. 用 `npm run db:backup` 完成迁移前备份；确认备份文件权限为 0600，并在测试库验证可恢复。

### 每位参与者的首日路径

1. 注册并保存一条文字记录，确认 Today 首页显示记录和整理状态。
2. 打开 Chat 发送一条消息，检查回答中的记忆来源和待确认提议；未确认前不应出现新的长期记忆或行动。
3. 在 Timeline 修改一条记忆使用范围，确认来源仍可见；用另一设备或旧版本更新同一条记忆时应得到 409 并要求重新读取。
4. 尝试一次语音记录；由于本阶段未配置真实供应商，应看到可解释的失败状态，并能编辑文字后保存。
5. 记录是否完成首条记录、是否查看总结、是否确认建议和是否发生失败回退，不记录私密正文。

### 第 2～7 天

- 每天完成一次有效行为（保存记录、发送 Chat、确认建议或完成行动），记录缺失事件与真实业务事实是否一致。
- 检查导出任务的处理中、成功、失败和重试状态；下载包只使用一次，验证过期包会被清理。
- 如需删除账号，先执行错误短语校验，再用二次确认完成删除；观察普通 API 访问被拒绝，直到后台清理结束。
- 每日查看聚合 activation、retention、quality 指标；发现删除残留、跨账号数据或记忆权限越界时立即停止该账号试用。

### 试用结束

导出聚合指标和脱敏反馈，按账号检查删除结果和任务失败数；不要把原始记录复制到分析表。使用“继续投入、调整方案、暂停开放”三选一形成决策，并保留版本、平台、事件缺失和失败原因。报告同时输出按反馈类型的用户数、事件平台/应用版本/schema version 分布和记录事实/客户端事件对账结果，可先运行 `npm run test:trial-report` 验证报告结构。

## 尚未获得的发布证据

- Android / iOS 真机核心路径、签名原生发布包和商店分发。当前已有 Android/iOS 模拟器运行证据和 JavaScript 导出证据，但没有签名原生包或真机证据。
- Android 模拟器已恢复并通过登录、Today/回看/Chat/设置页加载及导出下载进入系统分享面板；Android 已实际完成专用测试账号的删除确认、后台清理、返回登录页，且服务端三步清理日志一致。本轮新增目录保存实测通过：新建 TodayVerification 目录、授权、保存、ADB 取回 7,495 字节 ZIP；44 个条目 CRC 检查通过，manifest 全部文件 SHA-256 校验通过。完整隐私权限矩阵的设备回归仍待验证。
- iOS 模拟器运行态：已通过 GitHub API 下载并安装 Expo Go，启动 Metro 后成功打开当前项目，完成 JavaScript bundle 下载并显示 Today 登录页；本轮仅验证启动和首屏加载，应用内登录、数据生命周期和分享保存验收仍待完成。双端导出不代替运行态验证。
- 生产 HTTPS、独立密钥、外部崩溃监控/告警、迁移备份恢复和生产回滚演练。
- 大数据量、进程强杀、多 worker 和所有在途请求竞态的完整压力验收；目前只有已列出的事务、失效来源、租约等待和失败恢复测试证据。
- 本机 `expo-doctor` 通过 17/18 项；唯一未通过项是 CocoaPods 未安装/版本不足，因此 iOS 原生签名构建仍需在具备 CocoaPods 的 macOS 或 CI 环境完成。
- 真实语音转写供应商仍按既定决策不接入；`provider_unconfigured` 保持文字回退，不伪造成功。

因此本次代码和自动化验证已有进展，正式试用/上线验收仍不能标记为 100%。

## 2026-11-17 本地候选版本复核

本轮继续验证指标与数据生命周期切片：

- `npm run db:migrate` 可在已有本地 MySQL 上重复执行；旧会话幂等字段和产品事件唯一索引均通过启动校验。
- `npm run e2e:metrics` 在真实 Spring Boot + MySQL 运行态通过 activation、retention、quality 三个管理员接口校验；错误令牌返回 403，响应不包含原始记录文本。
- `npm run e2e:data` 使用新建白名单测试账号通过隐私版本冲突、记录保存、导出幂等、ZIP 单次下载和账号删除闭环。
- 导出包实测包含 44 个文件和 `manifest.json`，记录 JSON/CSV 行数及 SHA-256 校验值。
- `npm run typecheck:contracts`、`npm run typecheck:mobile`、`npm run lint`、`npm run build` 和发布配置检查通过。
- 后端使用 mock Qdrant 的完整回归为 230 项通过；移动端已有 40 项测试通过。
- iOS 模拟器（iPhone 15，iOS 17.4）已启动 Expo Go 并成功加载当前 `apps/mobile` bundle，显示 Today 登录页；Metro `/status` 返回 `packager-status:running`。
- 语音记录切片补强：移动端达到 120 秒后自动结束录音并进入转写流程，手动停止会清理计时器；`npm run typecheck:mobile` 和移动端 40 项测试通过。
- 语音上传切片补强：服务端按用户隔离保存短期音频路径并写入转写任务，保留原有 `provider_unconfigured` 可重试回退；语音服务测试通过，数据库迁移增加 `media_path` 字段。
- 语音短期媒体清理已接入定时任务：过期失败任务逐批删除音频，删除成功后清空任务路径，删除失败保留路径等待下一轮重试。
- 本轮完整检查：后端 Maven 全量回归、contracts/mobile 类型检查、Web ESLint 和生产构建均通过。
- 新增语音清理调度器测试，覆盖清理成功和失败重试保留路径两种结果。
- 移动端 Today 页面已接入失败转写任务的“重试转写”按钮；成功获得文本后仍需用户检查并明确保存。移动端类型检查和 40 项测试通过。
- 语音任务幂等键现在校验音频 SHA-256、类型和时长；相同请求返回原任务，不同 payload 返回 409，并有服务端测试覆盖。迁移和启动校验增加 `payload_hash` 字段。
- 追加覆盖相同类型/时长但音频字节不同的冲突测试，确认不会复用错误任务。
- 语音指标事件补齐成功、失败和重试三类结果，失败事件只记录错误码，不记录转写文本。
- 语音录音权限拒绝、启动失败和上传失败现在单独记录 `voice_recording_failed`，可与转写服务失败区分。
- `scripts/db-init.sh` 现已同步检查语音任务表、幂等键、payload 哈希、媒体路径及状态索引。
- 语音服务抽出 `TranscriptionProvider` 接口和未配置供应商实现；供应商异常会落为可重试的 `provider_error` 任务，不把异常伪装成成功。
- 转写重试现在会再次调用 provider；成功时删除短期音频并更新任务为 `succeeded`，失败继续保留可重试状态。相关服务测试通过。
- 转写重试增加 worker 领取和结果更新的影响行数校验；竞态下不会重复调用 provider，状态变化会返回冲突。
- 重试结果统一归一化错误码；provider 未返回错误码时使用 `provider_failed`，避免客户端收到不可解释的失败任务。
- 清理调度器现在先将到期的失败任务标记为 `expired`，再清理音频，状态机与客户端契约保持一致。
- 新增语音接口边界测试，覆盖空文件、超过 120 秒和不支持 MIME 类型的服务端拒绝。
- 语音创建接口显式拒绝缺失或空白 `Idempotency-Key`，并增加控制器测试。
- 过期媒体清理现在也覆盖 provider 成功但即时删除失败的任务，避免成功任务的残留音频绕过清理。
- 新建语音任务接入账号级小时/日限流；相同幂等键复用已有任务时不重复消耗限额，默认每小时 20 次、每日 100 次。
- 增加限流幂等测试，确认重复请求直接复用任务且不调用限流器。
- `.env.example` 与 Compose 已暴露语音小时/日限流配置，发布时可按内部试用规模调整。
- `release:check` 现在校验语音小时/日限流为正整数，且小时限额不得超过日限额。
- CI 新增发布配置 job，覆盖完整内部试用配置通过和无效语音限额关系拒绝。
- README 已补充语音供应商、音频保留、大小/时长限制和限流配置说明。
- 转写重试增加服务端最大尝试次数（3 次）；达到上限后返回 409，客户端不再显示可重试状态。
- 记忆 Chat 使用范围更新增加 `updatedAt` 乐观并发校验；Timeline 会携带当前版本，旧版本更新返回 409，避免两个设备互相覆盖。
- Timeline 现在记录记忆范围变更事件（仅记录范围枚举，不记录记忆正文），便于分析授权变化。
- 语音迁移改为基于 `INFORMATION_SCHEMA` 的动态幂等 SQL，兼容已有数据库重复执行和旧 MySQL 版本。
- 移动端离开 Today 页面时会停止并卸载进行中的录音，恢复音频模式，避免后台继续占用麦克风；移动端类型检查和 40 项测试通过。
- 移动端上传前增加 10 MB 音频文件大小检查，与服务端媒体限制一致，超限时直接回退文字输入。

该阶段记录当时未重新获得 Android 真机/模拟器连接；随后 2026-11-18 已补充 Android 模拟器 bundle 和 Today 首页运行证据。iOS 仍仅完成启动和首屏加载，双端应用内登录、数据生命周期和分享保存验收仍未完成。上述结果不能替代真机、签名包、生产 HTTPS、备份恢复和真实用户 7 天试用证据。测试账号和本地导出包均为合成数据，未用于正式用户环境。

## 2026-11-18 隔离数据库复核

- 在本机临时 MySQL 5.7 实例（独立数据目录和 3307 端口）上，schema 与全部增量迁移曾成功重复执行，所需表、字段、唯一键和状态索引均通过脚本校验。
- API 数据库回归随后确认该实例不满足运行要求：导出/删除队列使用 `FOR UPDATE SKIP LOCKED`，MySQL 5.7 不支持该语法，导致 3 个数据生命周期测试错误；另有 1 个旧媒体清理断言差异。该结果不代表 MySQL 8 回归失败，也不作为完整数据库通过证据。
- `scripts/db-init.sh` 已增加 MySQL 主版本检查，低于 8 的实例会在初始化前明确拒绝，避免生成 API 无法正确服务的数据库。
- `release:check` 现在也校验导出和账号删除开关只能取 `true`/`false`；合法配置通过，`TODAY_DATA_EXPORT_ENABLED=enabled` 会被拒绝。
- 正式数据库验证仍需使用 Compose/CI 采用的 MySQL 8.4；本机 Docker daemon 当前不可用。

## 2026-11-18 运行态就绪探针

- 新增 `/health/ready`，检查数据库 `SELECT 1` 和向量存储健康状态；依赖异常返回 503，`/health/live` 继续只表示进程存活并返回 200。
- Docker Compose API 健康检查已切换到 `/health/ready`，避免数据库或向量依赖不可用时仍被路由为健康实例。
- 新增 `HealthControllerTest` 覆盖数据库失败返回 503 和依赖正常返回 200；后端完整 Maven 测试通过。
- `e2e-smoke.sh` 和 Docker 部署脚本的等待逻辑也改用 `/health/ready`；`/health/live` 仅保留为进程存活诊断。

## 2026-11-18 迁移前备份能力

- 新增 `npm run db:backup` / `scripts/db-backup.sh`，优先使用 Compose 的 `today-mysql`，否则使用本机 `mysqldump`。
- 备份使用 `--single-transaction`、包含 routines/events/triggers 和二进制字段，输出 gzip 文件并设置权限 0600；目标已存在或备份失败时不会留下半成品。
- 当前本机没有运行数据库，因此只完成脚本语法和失败路径验证；真实备份/恢复演练仍需 MySQL 8.4 测试库，并且不能把备份文件提交到仓库。
- CI 已增加 MySQL 8.4 备份恢复步骤：生成 gzip 备份，恢复到隔离库并核验表数量；本地尚未得到该 CI 运行结果。

## 2026-11-18 指标北极星与事实对账

- activation 聚合新增 `northStarUsers`：注册 cohort 内至少完成 3 次有效记录，并至少产生一次总结或确认建议的用户数。
- 同一聚合新增 `checkinsSaved` 与 `checkinSavedEvents`，用于对账服务端业务事实和客户端事件上报差异；仍只返回计数，不返回原始文本。
- activation 对账继续细分 `checkinUsersMissingSavedEvent` 与 `savedEventUsersMissingCheckin`，可区分业务事实已发生但客户端事件漏报、以及客户端事件存在但没有对应业务事实；试用报告和 readiness 告警会把任一用户级缺口视为需要调查的对账异常。
- 共享 contracts 新增三类管理员指标响应 schema 和路由常量，后续看板可在客户端解析失败时安全提示，而不是把任意 JSON 当成有效指标。

## 2026-11-18 iOS 模拟器运行态复核

- 启动 iPhone 15 模拟器（iOS 17.4，设备 ID `A8C473D8-4B2C-4E22-9E75-D33F434A3AFF`），Expo Go 已安装并启动。
- Metro 在 `apps/mobile` 目录启动，`/status` 返回 `packager-status:running`；通过 `exp://127.0.0.1:8081` 打开当前 bundle，成功显示 Today 登录页。
- 截图证据：`test-results/validation-2026-11-18/ios-login-screen.png`。
- 本次只证明模拟器启动、bundle 下载和登录首屏渲染；尚未验证登录、记录生命周期、语音权限、导出保存或账号删除。

## 2026-11-18 Android 模拟器运行态复核

- 通过本机 Android SDK 的 `Nexus_5X_API_30` 启动 Android 模拟器，`adb` 连接成功，Expo Go 已安装。
- Metro 在 `apps/mobile` 目录启动后，通过 `exp://10.0.2.2:8081` 加载当前 bundle；Android 日志确认 React Native 主入口已运行，Today 页面完成渲染。
- 页面截图证据：`test-results/validation-2026-11-18/android-today-screen.png`。
- 模拟器保留了此前测试会话，但 API 服务未运行，因此页面显示“无法连接服务器”；这次只证明 Android bundle 和页面运行态，不证明登录、接口请求、录音、导出或删除流程。
- 当次运行日志提示 Expo AV 将在 SDK 54 移除及多个 App/页面的 require cycle；后续模块依赖整理已消除 require cycle，语音录音已迁移到 Expo SDK 53 对应的 `expo-audio ~0.4.9`。

## 2026-11-18 移动端模块依赖整理

- 将会话上下文和根导航类型从 `App.tsx` 拆到 `src/session.ts`、`src/navigation.ts`，各页面不再反向导入 `App.tsx`。
- 重跑 Android Expo export：3138 个模块打包成功，输出 5.68 MB Hermes bundle；本次导出日志不再出现 require cycle 警告。
- `npm run typecheck:mobile` 和移动端 40 项测试均通过；迁移后的 Android/iOS Expo 导出均通过，导出日志不再包含 `expo-av` 弃用提示。

## 2026-11-18 语音短期媒体切片复核

- 服务端接收的音频按账号写入 `voice/{user}/` 短期路径，并记录在转写任务 `media_path`；迁移和启动校验可识别该字段。
- 过期且失败的转写任务由定时清理器分批删除音频；文件删除失败时保留路径，下一轮继续重试。
- 语音服务测试通过，后端完整 Maven 回归通过；移动端类型检查和既有 40 项测试仍通过。

该切片仍使用 `provider_unconfigured` 失败回退，未接入真实供应商，也未替代真机和生产环境验收。

## 2026-11-18 Expo Audio 迁移复核

- Expo SDK 53 的 `node_modules/expo/bundledNativeModules.json` 推荐 `expo-audio ~0.4.9`；未直接升级到当前 latest，避免跨 SDK 引入不兼容原生模块。
- `apps/mobile` 已从 `expo-av ~15.1.7` 迁移到 `expo-audio ~0.4.9`，录音权限、音频模式、录音开始/停止、时长和文件 URI 均改用新 API。
- `npm run typecheck:mobile` 通过，移动端 40 项测试全部通过；Android 和 iOS Expo 导出均通过。
- 录音上限、文件大小限制、转写失败回退、离开页面清理和服务端任务流程保持不变。
- CI 移动端 job 已加入 `npx expo install --check` 和 `expo-av` 引用拒绝检查，避免 SDK 依赖漂移或弃用 API 回归。
- CI 还会执行 Expo prebuild 并检查 Android Manifest 的 `RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS` 以及 iOS `NSMicrophoneUsageDescription`，覆盖独立原生包的录音权限配置。
- 同一 CI 步骤还会确认 Today 图标源文件和 Android 生成的 launcher 资源存在，避免签名包生成无图标资源。
- CI 新增 macOS 原生配置 job：在 `macos-14` 上执行 Expo prebuild 和 `pod install --no-repo-update`，用于验证 iOS CocoaPods 依赖可生成；该 job 仍不等同于签名发布包或真机验收。
- CI 增加 `workflow_dispatch` 手动触发入口，便于在具备 GitHub Actions 环境时单独复核 MySQL 8.4、备份恢复和 macOS 原生配置。
- Web 新增 `/admin/metrics` 内部聚合看板：令牌只保留在当前页面内存，按日期读取 activation、retention、quality 三类计数，并用共享 zod contracts 校验响应；质量事件可按平台、应用版本和事件契约版本区分，同时聚合试用反馈的类型和用户数。页面不展示原始记录正文，同时显示保存记录事实与客户端事件的对账差异，可将当前聚合结果导出为 JSON。页面 metadata 设置为 `noindex,nofollow`，避免内部指标页被搜索引擎索引。
- Web Playwright 回归新增记忆权限场景：使用隔离 mock API 验证来源和最近使用审计展示、范围更新 409 冲突后的自动刷新与提示；CI Web job 与指标看板用例一起执行。生命周期 CI 的 MySQL 8.4 运行态还会执行真实管理员指标 E2E，检查三类聚合、维度筛选、脱敏和错误令牌拒绝。
- 产品事件补齐客户端发生时间与服务端接收时间；平台和事件契约版本有默认值及范围校验，未来超过 5 分钟的设备时间会校正为接收时间。旧数据库通过 `migration-product-events-schema.sql` 增量补列并回填接收时间。
- 移动端事件会自动携带当前 `app.json` 版本号；CI 会检查 `src/api.ts` 的事件版本常量与 Expo app config 一致。
- CI 的 MySQL 8.4 job 会在后端测试后检查这三个来源字段存在且 `received_at` 不为空；指标 E2E 会校验平台、应用版本和事件契约版本的返回类型。
- CI 移动端 job 会保留 7 天 Android/iOS Expo bundle；macOS 原生配置 job 会保留 `Podfile.lock`、iOS 麦克风用途和 Android 权限清单，便于审查候选版本的原生配置。
- 试用结束可运行 `TODAY_METRICS_ADMIN_TOKEN=... TODAY_TRIAL_FROM=YYYY-MM-DD TODAY_TRIAL_TO=YYYY-MM-DD npm run trial:report` 生成脱敏决策报告；报告只含聚合计数和建议，不读取原始记录，并带出服务端保存记录与客户端事件的差异。设置 `TODAY_TRIAL_REPORT_OUT` 时以 0600 权限创建新文件。
- 移动端新增 `eas.json`：`preview` 生成内部分发 APK，`production` 使用商店分发并自动递增版本；EAS 项目绑定、凭据和实际签名构建仍需在发布环境完成。
- Expo runtime version 已绑定 `appVersion`，原生依赖升级会进入新的运行时版本；preview 和 production 使用独立 EAS channel，CI 会同时校验 profile、channel 和 runtime 策略。
- CI 还会运行 `expo config --json`，确认 SDK 实际解析出的图标、包名、麦克风用途和 runtime 配置完整。

本次迁移后的运行态复核：iPhone 15 模拟器通过 Expo Go 加载当前 bundle，Metro `/status` 返回 `packager-status:running`，登录首屏正常渲染。截图证据：`test-results/validation-2026-11-18/ios-expo-audio-login-screen.png`。

Android 运行态复核：启动 `Nexus_5X_API_30` 模拟器，通过 Expo Go 加载 `exp://10.0.2.2:8081`，bundle 完成后 Today 首页正常渲染；API 未运行，因此页面显示网络错误，这次验证只覆盖 bundle、导航和页面渲染。截图证据：`test-results/validation-2026-11-18/android-expo-audio-today-screen.png`。

独立原生包权限复核：`expo-image-picker` 的麦克风权限配置改为与 `expo-audio` 共用用途文案，避免其 `microphonePermission: false` 将 `RECORD_AUDIO` 加入全局 blocked permissions；`npx expo prebuild --clean --no-install` 生成的 Android Manifest 同时包含 `RECORD_AUDIO` 和 `MODIFY_AUDIO_SETTINGS`。

同次 prebuild 已补齐 Expo SDK 53 推荐的 `expo-system-ui ~5.0.11`，消除了 `userInterfaceStyle` 未安装系统 UI 模块的警告。

原生应用图标已配置为 `assets/todaylogo.png`；重新执行 prebuild 后 iOS 图标缺失警告消失，Android/iOS 原生资源均由 Expo 生成。

## 2026-11-18 隐私授权客户端一致性复核

- Profile 将语音成功后清理、Chat 记忆只影响 Chat 上下文、站内跟进独立授权展示在对应设置区域。
- 跟进设置保存后同步更新本地隐私状态，避免随后切换 Chat 记忆时使用过期的跟进授权值。
- Chat 记忆开关补充无障碍标签和选中状态；客户端隐私请求回归用例确认始终显式携带独立的 `followupEnabled`。
- `npm run typecheck:mobile` 通过，移动端 41 项测试全部通过，`npm run ci:web`、发布配置、运维和试用报告检查均通过。
- 服务端跟进偏好接口与隐私设置接口现在双向同步 `followupEnabled`，并在同一事务中递增隐私版本；新增单元测试覆盖状态一致性与并发冲突路径。
- 两条写路径统一先更新/校验隐私行，再锁定跟进偏好行，避免并发请求形成反向锁顺序。
- 新增 MySQL 集成测试覆盖两个接口依次切换授权后两张表的最终值一致；本机无 `TODAY_TEST_MYSQL_URL` 时该测试按环境条件跳过，CI MySQL 8.4 会执行。
- 新增冲突回归：隐私授权同步失败时，跟进偏好不会先写入，避免产生半更新状态。
- 新增 `privacy_settings_audit` 审计表，记录 Chat 记忆、语音保留和站内跟进授权的旧值/新值、来源与时间；不记录记录正文，并纳入导出包和数据库初始化检查。
- 管理员指标接口新增 `metrics_access_audit`，记录接口、查询日期和筛选维度，不保存管理员令牌或原始指标；审计写入失败时不返回指标。
- 生命周期 CI 在真实 MySQL 8.4 运行态核验 activation、retention、quality 三类指标访问审计行已写入。
- 指标审计失败回归确认会阻止聚合查询，避免返回无法追溯的管理员数据。
- API CI 新增隐私审计和指标审计表/索引检查，迁移缺失会在 MySQL 8.4 job 中直接失败。
- 数据生命周期集成测试新增断言：账号最终删除会清理带用户归属的隐私授权审计记录；无 `user_id` 的管理员指标访问审计不会被账号删除流程误删。
- 删除请求创建时立即撤销 Chat 和站内跟进授权、递增隐私版本并写入 `account_deletion` 审计；后台清理失败期间也不会继续使用已撤销授权。
- 导出集成测试明确验证用户隐私授权审计进入 ZIP，而管理员指标访问审计不进入用户导出包。
- 管理员指标访问审计默认保留 180 天，UTC 每日清理，可通过 `TODAY_METRICS_AUDIT_RETAIN_DAYS` 调整；清理器不触碰用户隐私审计。
- 指标审计清理遇到数据库异常时记录错误并保留数据，下一轮定时任务继续重试，不影响 API 进程存活。
- 发布配置检查要求指标审计保留期为正整数且不超过 3650 天；配置矩阵覆盖 0 天和超上限场景。
- Docker Compose 已将该保留期变量传入 API 容器，`.env` 中的调整会实际生效；Compose 配置解析检查通过。
- 发布配置 CI 增加 API 容器审计保留期覆盖检查：解析 `docker compose config --format json`，断言覆盖值为 240 天，避免 YAML 字符串引号导致文本匹配误报。本地执行同一断言通过；发布配置 9 个场景、运维检查、试用报告检查及 Compose 配置解析均通过。此处是本地验证结果，尚未取得当前改动对应的远程 CI 运行结果。

## 2026-11-18 发布备份失败保护

- 备份脚本设置 `umask 077` 并使用同目录 `mktemp`，临时备份从创建起仅当前用户可读写；退出及中断时清理临时文件。
- 使用同目录硬链接发布完整备份，原子拒绝已存在的目标，避免导出期间目标被其他进程创建时发生覆盖或误报成功；预先存在的悬空符号链接也会被拒绝。
- 新增 `npm run test:db-backup` 并纳入发布配置 CI。本地使用模拟导出命令验证临时/最终权限、压缩内容、导出失败清理、已有目标保护、并发目标保护和符号链接拒绝；脚本语法及 `git diff --check` 通过。
- 本次未访问真实数据库。Docker daemon 仍不可用，当前 PATH 中 MySQL 为 5.7；MySQL 8.4 的真实备份恢复与生产回滚演练仍未获得新证据。

## 2026-11-18 MySQL 8.4 本地完整回归与恢复演练

- 随后启动 Docker Desktop 成功，使用已有 MySQL 8.4 镜像创建独立容器 `today-validation-mysql-20261118`（本机端口 33316），未将测试指向现有 Today 数据库。
- 首次全量回归执行 267 项、无跳过，发现两项账号删除测试报错：MySQL JDBC 将授权字段返回为 Boolean，删除流程却强转为 Number。改为通过 ResultSet 的 `getBoolean` 读取授权、`getInt` 读取版本。
- 修复后完整 Maven 回归：267 项测试、0 failures、0 errors、0 skipped；包含 MySQL 隐私授权同步、导出/删除生命周期和模拟 Qdrant 集成。日志：`/tmp/today-validation-maven.log`。
- 备份脚本增加 `TODAY_BACKUP_CONTAINER`，可明确选择隔离测试容器。实际导出并恢复到同一临时实例中的 `today_validation_restore`，源库和恢复库均为 37 张表，包含两张审计表；逐项比较列元数据、索引及排序后的数据导出，一致。
- 原始 CREATE TABLE 文本因 MySQL 显式字符集表示不同而不逐字相同；上述列元数据比较包括字符集和排序规则，不能将文本差异误报为数据恢复失败。
- 合成测试数据备份位于 `/tmp/today-backup-validation.eCVPCS/today.sql.gz`，权限核验为 0600；备份失败保护回归和脚本语法检查通过。临时数据库容器测试后停止，保留以供复核。
- 此证据补齐本机 MySQL 8.4 自动化和隔离恢复验证，不代表远程 CI、生产数据恢复、生产回滚、签名包或真机验收完成。

## 2026-11-18 隔离 API 生命周期验收

- 使用当前源码打包的 API、上述隔离 MySQL 8.4 和临时媒体目录，在 127.0.0.1:33017 启动服务；使用新建的合成内部账号验证，没有使用现有账号。
- `e2e:metrics` 通过 activation/retention/quality 查询、维度筛选和错误令牌拒绝；数据库确认三类访问审计均已写入。
- `e2e:data` 通过隐私版本冲突、非本人记忆访问拒绝、幂等导出、一次性下载、删除确认、普通接口禁用与最终凭据失效；数据库确认测试用户及其隐私审计已清理。
- 导出 ZIP 的 CRC 和 44 个 manifest 条目的 SHA-256 校验通过；包含关闭 Chat 记忆的授权审计，不包含管理员指标访问审计。该边界断言已纳入生命周期 CI。
- 实际执行审计索引检查发现旧 CI 按 information_schema.statistics 行数计数，复合索引两列会误报为两个索引；改为统计不同索引名，两项索引检查在真实 MySQL 8.4 上通过。
- `ops:check` 在隔离 API 上通过，alerts 为空。API 日志位于 `/tmp/today-lifecycle-validation-api.log`，合成导出包位于 `/tmp/today-lifecycle-validation-export.zip`。AI 服务不可用时使用既有回退；本次不是实际模型服务质量验收。验证后停止本轮 API 和临时数据库容器。

## 2026-11-18 Android 原生编译门槛

- CI 新增独立 Android debug 原生编译 job：Java 17、Android SDK、Gradle 缓存，Expo 生成 Android 工程后执行 `:app:assembleDebug`，覆盖 arm64-v8a 和 x86_64，并上传 APK。设置 30 分钟超时，APK 缺失会导致失败。
- 该 APK 用于连接 Metro 的原生调试验证，不是可独立运行的生产签名包，也不替代真机验收；正式签名构建仍使用既有 EAS 发布流程。
- 本地实际尝试原生构建，但 Gradle 8.13 分发下载发生 SocketTimeoutException，尚未进入项目编译。日志：`/tmp/today-android-native-build.log`。CI YAML 解析通过，远程编译结果尚未取得。
- 设备清单复核：iPhone 15 iOS 17.4 模拟器已启动，ADB 未连接 Android 设备，本机 PATH 中没有 CocoaPods。

## 2026-11-18 Web 浏览器运行态补验

- 当前源码生产构建通过，测试服务绑定 127.0.0.1:33018。安装当前 Playwright 对应的 Chromium / Headless Shell 149.0.7827.55（revision 1228）后，使用仓库默认 Playwright 配置执行 `e2e/memory.spec.ts` 与 `e2e/metrics.spec.ts`，2/2 通过。
- 覆盖记忆来源和最近使用审计展示、范围更新 409 后刷新；指标查询筛选与令牌请求头、令牌不进入 URL/localStorage/sessionStorage、聚合展示及 JSON 下载。此前也在本机 Chrome 和完整 Chromium 上执行同组用例，均通过。
- 用例使用浏览器拦截的合成 API 响应，因此证明客户端交互而非真实 API 的浏览器端到端联调；真实 API 证据见上一节隔离生命周期验收。
- 构建日志：`/tmp/today-web-validation-build.log`；服务日志：`/tmp/today-web-validation-server.log`；默认配置测试截图：`/tmp/today-web-default-browser-validation/metrics-metrics-dashboard--c8de2-ts-without-persisting-token/metrics-dashboard.png`。测试结束后停止本轮 Web 服务。
- 本地完整浏览器运行证据已获得，当前改动对应的远程 CI 结果仍待取得。

## 2026-11-18 iOS 原生编译门槛补齐

- macOS CI 在 CocoaPods 安装后增加 `xcodebuild`：使用 Today workspace/scheme 构建 Debug iOS Simulator app，禁用分发签名，设置 45 分钟 job 超时。成功后打包模拟器 app，失败时仍上传构建日志。
- 生成工程中的 scheme 和 app 名称已核对，CI YAML 解析和差异检查通过；尚无该新增编译步骤的远程执行结果。模拟器调试 app 需要 Metro，不是 iOS 真机或商店签名包。
- 工作流全部 40 个 `run` 步骤通过 `bash -n` 语法检查；该检查不执行命令，也不替代 Actions 语义校验或远程构建结果。独立 actionlint 工具尚未安装成功（官方 Release API 请求发生 SSL 连接超时）。
- 本机 Xcode 15.3 满足 React Native 当前脚本声明的最低 15.1 要求。CocoaPods 直接安装因最新 nap 依赖要求 Ruby 3.3 失败；已在 `/tmp` 安装 Bundler 2.4.22 并尝试解析 Ruby 2.6.10 兼容依赖，尚未获得安装完成证据。
- Simulator 进程存在，但电脑交互工具按应用名及 bundle ID 均返回 `cgWindowNotFound`，本轮未获得应用内登录或数据操作验收证据。
- 随后 Bundler 完成 42 个 gem 安装，CocoaPods 1.16.2 版本检查通过。旧 Ruby 下需在 Bundler 设置依赖后显式 `require "logger"` 再加载 pod，且设置 UTF-8 locale；临时工具链位于 `/tmp/today-cocoapods-toolchain`。项目 `pod install` 已完成 React Native Codegen 并进入 Hermes 下载，尚未完成全部原生依赖安装或编译。
- 备份容器选项补充失败保护：显式指定 `TODAY_BACKUP_CONTAINER` 时不可用即失败，不再退回本机数据库；新增对应回归并通过全部备份检查、脚本语法和差异检查。
- iOS 首次 `pod install` 完成 Hermes debug/release 下载后，因官方 CDN 的 12 个 SocketRocket podspec SSL 连接失败而退出。已通过 CocoaPods/Specs 官方 GitHub API 获取同版本 podspec，核验名称和版本后填充本机 CocoaPods 缓存，再启动安装；未修改项目依赖版本、远端源或 TLS 校验。全部 Pods 安装和编译结果仍待取得。
- Gradle 官方 GitHub Release API 可下载分发包；首次下载达到命令时限退出后，已保留部分文件并通过 HTTP Range 续传，官方 SHA-256 已取得，完整下载后才可校验并使用。
- 随后通过官方 Release API 分段下载并合并完成 Gradle 8.13 分发包，SHA-256 与官方值 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` 一致；`gradle --version` 通过。工具解压到 `/tmp/today-android-toolchain/gradle-8.13`，已启动双架构 `:app:assembleDebug`，构建结果仍待取得。完整校验通过后停止了冗余的单连接下载。
- SDWebImage 和 libwebp 所缺 podspec 缓存已补齐，iOS 已进入原生源码安装，当前在获取 DoubleConversion；这仍不是 Pods 安装完成或 iOS 编译通过。

## 2026-11-18 删除与授权写入并发保护

- 隐私设置更新、跟进设置更新和首次隐私初始化现在先锁定账号行，并以锁定读取检查删除请求。这样已通过 HTTP 鉴权但尚未写入的请求，也不能在删除撤销授权后重新开启 Chat/跟进权限，或在账号已删除后重建默认授权。
- 新增 MySQL 并发测试：删除标记与授权撤销事务持有账号锁时，两个设置写入线程等待；提交后两条路径均返回 403，最终 Chat/跟进授权保持关闭。既有测试夹具补齐真实账号行，以匹配服务的账号存在性约束。
- 完整后端回归：270 项，0 failures、0 errors、0 skipped；包含新增并发测试和删除后默认授权不可重建的单元回归。日志：`/tmp/today-validation-maven.log`。`git diff --check` 通过。
- 随后将可能初始化默认偏好的“读取跟进设置”入口也纳入账号锁和删除检查；并发用例扩展为读取、隐私更新和跟进更新三条入口。相关 27 项测试在真实 MySQL 上通过，0 failures、0 errors、0 skipped；日志：`/tmp/today-privacy-entrypoints-tests.log`。

## 2026-11-18 原生构建依赖网络诊断

- Android 已进入 Expo/React Native Gradle 插件编译；`expo-autolinking-settings-plugin:compileKotlin` 显示 FAILED，但构建进程仍存活，尚未输出完整失败报告，不能据此认定应用源码编译错误。
- 最新 Gradle 线程栈显示依赖元数据 HEAD 请求等待 TCP 连接，目标 Google 地址处于 SYN_SENT。对 `maven.google.com` 和 `dl.google.com` 的独立 HTTPS 探测均在 5 秒连接期限内超时；系统代理关闭且常用代理环境变量未设置。此证据说明本机当前依赖网络不可达，不证明 Kotlin 任务失败的全部原因。
- iOS 已安装到 SDWebImage 5.21.7，Git 下载进程及 HTTPS 连接仍存活；临时 pack 曾增长至 10,485,759 字节，此后多次检查大小未变。尚无 Pods 全部安装完成或 xcodebuild 成功证据。
- 保留现有构建进程和下载内容，未因轮询超时重启，也未更改项目依赖版本或关闭 TLS 校验。
