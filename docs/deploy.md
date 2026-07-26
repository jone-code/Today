# Today 发布与本地全栈

一键起全栈（MySQL + Qdrant + API + Web）与基础 CI 说明。

## 一键启动（Docker Compose）

前置：Docker / Docker Compose v2。

```bash
cp .env.example .env   # 可选；按需改密钥与 OPENAI_API_KEY
npm run stack:up       # 等价 docker compose up -d --build
```

### 国内拉镜像失败

1. **不要用失效的阿里云个人加速器**：`*.mirror.aliyuncs.com` 常返回 `403 Forbidden`。  
   建议 `daemon.json` 改用 DaoCloud：`"registry-mirrors": ["https://docker.m.daocloud.io"]`，然后重启 Docker。
2. **或直接用带前缀的镜像名**（`.env.example` 已写好）：`MYSQL_IMAGE` / `QDRANT_IMAGE` / `JAVA_*` / `NODE_IMAGE`。
3. Dockerfile **已去掉** `# syntax=docker/dockerfile:1`，避免 BuildKit 再去拉 `docker/dockerfile` frontend。

### `today-api` 起不来 / unhealthy

先看日志：

```bash
docker compose logs api --tail=200
docker compose ps
curl -s http://127.0.0.1:3001/health/live
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
| 健康检查超时 | 健康检查走 `/health/live`；重建镜像：`docker compose up -d --build api` |
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

- `NEXT_PUBLIC_API_BASE_URL`：写入 Web 镜像的浏览器侧 API 地址（默认 `http://localhost:3001`）。改端口后需重建 web：`docker compose up -d --build web`。
- `TODAY_JWT_SECRET`：共享/生产环境务必更换。
- `OPENAI_API_KEY`：有则走真实 LLM；无则 Heuristic 降级，本地可跑通主链路。
- `TODAY_VECTOR_PROVIDER=qdrant`：API 容器内已指向 `http://qdrant:6333`。

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

1. **API** — Java 17 + `mvn test`
2. **Web** — `npm ci` → contracts typecheck → lint → `next build`
3. **Docker** — Buildx 构建 api / web 镜像（不推送）

本地等价：

```bash
npm run test:api
npm run ci:web
```

## 发布检查清单

1. 更换 `TODAY_JWT_SECRET`；按需设置 admin token。
2. 配置 `OPENAI_*`（生产建议真实模型）。
3. `npm run stack:up` 后访问 `/health`（含 `ai` / `vector` 字段）。
4. 跑冒烟：`npm run e2e:smoke`（需本机或已起的 API/Web）。
5. 若切 Qdrant：对存量记忆执行 `vector:reindex`（见 `docs/vector-store.md`）。
