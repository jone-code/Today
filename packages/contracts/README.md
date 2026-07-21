# `@today/contracts`

跨端契约：Zod schema + DTO + API 路径常量。

对应业务模块：

| 模块 | 文件 |
|------|------|
| checkin | `src/checkin.ts` |
| summary | `src/summary.ts` |
| memory | `src/memory.ts` |
| timeline | `src/timeline.ts` |
| proactive | `src/proactive.ts` |

前端与 `apps/api`（Spring Boot）依赖本包的路由与字段约定；Java 侧 DTO 在 `com.today.*` 包中手动对齐。
