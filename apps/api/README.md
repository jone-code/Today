# `@today/api` — Spring Boot 后端

Java 21 + Spring Boot 3 + **MyBatis + MySQL**（原生 MyBatis，不用 MyBatis-Plus）。

## 模块包

```text
src/main/java/com/today/
  checkin/      每日记录
  summary/      AI 总结
  memory/       长期记忆
  timeline/     时间轴
  proactive/    主动关联
  aigateway/    模型抽象
  identity/     用户占位
  persistence/  数据库实体
```

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
| `TODAY_DEV_USER_ID` | `dev-user` | MVP 开发用户 |
| `OPENAI_API_KEY` | — | 有值时走 LLM（尚未接通） |

表结构见 `src/main/resources/db/schema.sql`。
