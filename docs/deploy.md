# Today 发布与本地全栈

一键起全栈（MySQL + Qdrant + API + Web）与基础 CI 说明。

## 一键启动（Docker Compose）

前置：Docker / Docker Compose v2。

推荐用部署脚本（自动准备 `.env`、媒体目录、健康检查、DB 迁移）：

```bash
bash scripts/deploy-docker.sh up
# 或：npm run deploy:docker
```

常用子命令：

```bash
bash scripts/deploy-docker.sh status
bash scripts/deploy-docker.sh logs          # api + web
bash scripts/deploy-docker.sh health
# 部署后只读检查：存活、就绪、指标鉴权和脱敏响应
TODAY_OPS_API_URL=http://127.0.0.1:3001 \
TODAY_METRICS_ADMIN_TOKEN="$TODAY_METRICS_ADMIN_TOKEN" \
npm run ops:check
bash scripts/deploy-docker.sh down          # 停服务，保留数据卷
bash scripts/deploy-docker.sh down -v       # 停服务并清空数据卷
```

迁移或发布前先创建备份（备份包含用户数据，默认写入未纳入版本控制的 `backups/`）：

```bash
npm run db:backup
# 可指定输出路径：npm run db:backup -- ./backups/pre-release.sql.gz
```

脚本默认优先备份运行中的 `today-mysql` 容器，否则使用本机 `mysqldump`。通过 `TODAY_BACKUP_CONTAINER` 显式指定容器时，该容器不可用会直接失败，不会退回本机数据库。脚本使用事务一致性快照并拒绝覆盖已有文件。临时文件从创建起权限为 0600，失败时自动清理，完整备份以不覆盖目标的方式原子发布。

等价手工步骤：

```bash
cp .env.example .env   # 可选；按需改密钥与 OPENAI_API_KEY
npm run stack:up       # 等价 docker compose up -d --build
```

### 国内拉镜像失败

1. **不要用失效的阿里云个人加速器**：`*.mirror.aliyuncs.com` 常返回 `403 Forbidden`。  
   建议 `daemon.json` 改用 DaoCloud：`"registry-mirrors": ["https://docker.m.daocloud.io"]`，然后重启 Docker。
2. **或直接用带前缀的镜像名**（`.env.example` 已写好）：`MYSQL_IMAGE` / `QDRANT_IMAGE` / `JAVA_*` / `NODE_IMAGE`。
3. Dockerfile **已去掉** `# syntax=docker/dockerfile:1`，避免 BuildKit 再去拉 `docker/dockerfile` frontend。
4. **API 镜像 `mvn package` 失败**：Docker 构建使用 `apps/api/docker/maven-settings.xml`（阿里云 Maven 公共源）。重试：
   ```bash
   docker compose build --no-cache api
   ```
   若仍失败，把完整 `mvn` 日志末尾贴出（已去掉 `-q`）。

### `today-api` 起不来 / unhealthy

先看日志：

```bash
docker compose logs api --tail=200
docker compose ps
curl -s http://127.0.0.1:3001/health/ready
```

API 启动时会自动执行 classpath 里的 `schema.sql` + migrations（`today.schema.bootstrap=true`），补齐如 `checkin_ai_jobs` 等表。

仍失败时：

```bash
npm run db:init:docker
docker compose up -d --build api
```

| 现象 | 处理 |
|------|------|
| MySQL Access denied / 连不上 | `.env` 密码与旧数据卷不一致：`docker compose down -v` 后 `npm run stack:up`（清空本地 DB） |
| schema bootstrap 报 missing tables | `MYSQL_USER` 需有 DDL 权限；或用 root 跑 `db:init:docker` |
| 健康检查超时 | 容器健康检查走 `/health/ready`，会同时检查数据库和向量依赖；重建镜像：`docker compose up -d --build api` |
| 端口占用 | 改 `API_PORT` / 停掉占用 3001 的进程 |

| 服务 | 地址 |
|------|------|
| Web | http://localhost:3000 |
| API | http://localhost:3001/health |
| MySQL | localhost:3306（库 `today`） |
| Qdrant | http://localhost:6333（镜像可用 `QDRANT_IMAGE=docker.m.daocloud.io/qdrant/qdrant:v1.18.3`） |

查看日志：`npm run stack:logs`  
停止：`npm run stack:down`

首次 MySQL（空数据卷）会自动加载 `apps/api/src/main/resources/db/schema.sql`。  
若卷已存在但表为空/缺失，或报 `Table 'today.checkin_ai_jobs' doesn't exist`：

```bash
npm run db:init:docker          # 对 today-mysql 容器执行 schema+迁移并校验必填表
docker compose restart api
# 确认：
docker exec today-mysql mysql -uroot -proot -e "USE today; SHOW TABLES LIKE 'checkin%';"
```

注意：不要用本机另一个 MySQL（`:3306`）初始化，而 API 连的是 Compose 容器；`db:init` 在检测到 `today-mysql` 时会自动走 `--docker`。

### 常用环境变量

见仓库根目录 [`.env.example`](../.env.example)。

- `NEXT_PUBLIC_API_BASE_URL`：浏览器侧 API 前缀（默认 `/api`）。
- `API_PROXY_TARGET`：Web → API（默认 `http://today-api:3001`）。若代理 502 / `ECONNREFUSED :3001`：曾因 entrypoint 误把 `PORT` 写成 MySQL `3306` 导致 API 听错端口，已修复；请 `docker compose up -d --build api`。
- `TODAY_JWT_SECRET`：共享/生产环境务必更换。
- `OPENAI_API_KEY`：有则走真实 LLM；无则 Heuristic 降级，本地可跑通主链路。
- `TODAY_VECTOR_PROVIDER=qdrant`：API 容器内已指向 `http://qdrant:6333`。
- `TODAY_MEDIA_PATH`：宿主机打卡照片目录（默认 `./data/media`），挂载到容器 `/data/media`。
- `TODAY_MEDIA_ROOT`：API 进程内媒体根路径（Compose 默认 `/data/media`，与 volume 对齐）。
- Linux 若上传报权限错误：`mkdir -p data/media && chmod 777 data/media`（容器以 `nobody` 写入）。

### 仅基础设施

开发时仍可只起依赖，本机跑进程：

```bash
npm run db:up
npm run vector:up
npm run dev:api
npm run dev:web
```

## 镜像

| 镜像 | Dockerfile |
|------|------------|
| `today-api:local` | `apps/api/Dockerfile`（Maven 打包 → JRE 17） |
| `today-web:local` | `apps/web/Dockerfile`（Next.js `output: "standalone"`） |

构建上下文均为仓库根目录（workspace + contracts）。

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 在 `main` 的 push / PR 上运行：

1. **API** — Java 17 + MySQL 8.4 + `mvn test`，并验证备份恢复与产品事件 schema。
2. **Lifecycle E2E** — 独立 MySQL 8.4 与 API 运行态，先验证三类管理员指标、维度筛选、脱敏和错误令牌拒绝，再使用临时白名单账号验证隐私版本冲突、导出幂等与单次下载、删除确认和凭据失效；失败时上传 API 日志。
3. **Web** — contracts typecheck → lint → `next build`，再用 Chromium 执行指标看板和记忆权限 Playwright 回归。
4. **Mobile** — Expo SDK 依赖、类型、41 项测试、Android/iOS bundle、EAS/native 权限和版本配置。
5. **Mobile native** — macOS 原生 prebuild 与 CocoaPods 依赖解析。
6. **Docker** — Buildx 构建 api / web 镜像（不推送）。
7. **Release configuration** — 运行 `test:release-check` 配置矩阵，验证合法配置及弱密钥、非布尔开关、非 HTTPS 地址拒绝。

本地等价：

```bash
npm run test:api
npm run ci:web
```

## 发布检查清单

1. 更换 `TODAY_JWT_SECRET`；按需设置 admin token。
2. 配置 `OPENAI_*`（生产建议真实模型）。
3. `npm run stack:up` 后访问 `/health`（含 `ai` / `vector` 字段）；流量接入和容器健康检查使用 `/health/ready`，只有数据库与向量依赖都正常时才返回 200。
4. 跑冒烟：`npm run e2e:smoke`（需本机或已起的 API/Web）。
5. 设置 `TODAY_METRICS_ADMIN_TOKEN` 后运行 `npm run ops:check`，确认 `/health/ready`、三类聚合指标和错误令牌拒绝均通过；脚本只输出耗时和状态，不输出指标正文或令牌。
   需要把失败率告警作为发布门槛时，再设置 `TODAY_OPS_FAIL_ON_WARNINGS=true`；默认只报告语音成功率低于 95% 或导出/删除失败率高于 5% 的告警，不会因当前未配置真实语音供应商而阻断检查。
   指标事件会区分 `android`、`ios`、`web` 和应用版本；Web 构建可通过 `NEXT_PUBLIC_APP_VERSION` 指定版本，未设置时使用 `0.1.0`。
6. 试用结束时设置 `TODAY_TRIAL_FROM`、`TODAY_TRIAL_TO` 和 `TODAY_TRIAL_REPORT_OUT`，运行 `npm run trial:report` 生成仅含聚合数据的决策报告；输出文件不会覆盖已有报告。
   报告包含反馈数量、按类型的反馈用户数、事件平台/应用版本/schema version 分布和记录事实/客户端事件对账结果；可用 `npm run test:trial-report` 在本地验证报告结构。
7. 若切 Qdrant：对存量记忆执行 `vector:reindex`（见 `docs/vector-store.md`）。
8. 指标访问审计默认保留 180 天；如需调整，设置 `TODAY_METRICS_AUDIT_RETAIN_DAYS` 并重新运行 `npm run release:check`（允许范围 1～3650 天）。
