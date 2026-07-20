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

前端与 `apps/api` 只依赖本包类型/校验，不互相直接引用实现。
