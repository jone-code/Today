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
| `TODAY_JWT_SECRET` | 开发默认值（请更换） | JWT 签名密钥，至少 32 字节 |
| `TODAY_JWT_EXPIRE_HOURS` | `168` | Token 有效小时数 |
| `OPENAI_API_KEY` | — | 有值时走 LLM（尚未接通） |

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

若数据库已初始化，请执行：
1. `src/main/resources/db/migration-auth-reminder.sql`
2. `src/main/resources/db/migration-todo-punch.sql`

表结构完整版见 `src/main/resources/db/schema.sql`。
