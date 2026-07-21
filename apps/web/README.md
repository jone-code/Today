# `@today/web`（迁移目标）

前端目标结构见 [`docs/architecture.md`](../../docs/architecture.md)。

当前可运行的 Next.js demo 仍在仓库根目录 `src/`。下一步迁移为：

```text
apps/web/src/
  app/                 # 路由
  features/
    checkin/
    summary/
    timeline/
    memory/
    proactive/
  shared/
```

规则：

- `features/*` 只通过 HTTP 调 `apps/api`，不内嵌模型逻辑
- 类型从 `@today/contracts` 引入
