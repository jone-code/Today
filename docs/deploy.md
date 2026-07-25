# Today 发布与本地全栈

一键起全栈（MySQL + Qdrant + API + Web）与基础 CI 说明。

## 一键启动（Docker Compose）

前置：Docker / Docker Compose v2。

```bash
cp .env.example .env   # 可选；按需改密钥与 OPENAI_API_KEY
npm run stack:up       # 等价 docker compose up -d --build
```

| 服务 | 地址 |
|------|------|
| Web | http://localhost:3000 |
| API | http://localhost:3001/health |
| MySQL | localhost:3306（库 `today`） |
| Qdrant | http://localhost:6333（镜像可用 `QDRANT_IMAGE=docker.m.daocloud.io/qdrant/qdrant:v1.18.3`） |

查看日志：`npm run stack:logs`  
停止：`npm run stack:down`

首次 MySQL 会加载 `apps/api/src/main/resources/db/schema.sql`。旧库增量仍用 `npm run db:migrate`。

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
