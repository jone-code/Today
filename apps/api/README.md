# `@today/api`

NestJS 后端。模块与产品能力一一对应，详见 [`docs/architecture.md`](../../docs/architecture.md)。

```text
src/modules/
  checkin/       每日记录
  summary/       AI 总结
  memory/        长期记忆
  timeline/      时间轴
  proactive/     主动关联
  ai-gateway/    模型抽象（LLM / Heuristic）
  identity/      用户占位（MVP 固定 dev-user）
```

```bash
# 在仓库根目录
npm install
npm run dev -w @today/api
```

当前为模块骨架 + 健康检查；业务实现按架构文档顺序接入。
