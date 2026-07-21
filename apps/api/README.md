# `@today/api` — Spring Boot 后端

Java 21 + Spring Boot 3。模块与产品能力一一对应，详见 [`docs/architecture.md`](../../docs/architecture.md)。

## 模块包

```text
src/main/java/com/today/
  checkin/      每日记录
  summary/      AI 总结
  memory/       长期记忆
  timeline/     时间轴
  proactive/    主动关联
  aigateway/    模型抽象（LLM / Heuristic）
  identity/     用户占位（MVP 固定 dev-user）
  health/       健康检查
```

## 本地运行

```bash
cd apps/api
mvn spring-boot:run
```

或仓库根目录：

```bash
npm run dev:api
```

- 默认端口：`3001`
- 健康检查：`GET /health`
- 提交今日：`POST /v1/checkins`

## 环境变量

| 变量 | 说明 |
|------|------|
| `PORT` | 服务端口（默认 3001） |
| `TODAY_DEV_USER_ID` | MVP 开发用户 ID（默认 `dev-user`） |
| `OPENAI_API_KEY` | 有值时走 LLM 路径（尚未接通，仍降级 Heuristic） |

当前持久化为内存 Map；下一步接 PostgreSQL + JPA。
