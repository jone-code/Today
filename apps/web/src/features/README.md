# 前端 features

与后端 Spring Boot 包（`com.today.*`）同名。页面只做组装，能力实现放在对应 feature。

| 模块 | 职责 |
|------|------|
| `checkin/` | 今日输入与提交 |
| `summary/` | 展示日总结 |
| `timeline/` | 时间轴列表 |
| `memory/` | 长期记忆面板 |
| `proactive/` | 主动关联提示 |
| `identity/` | 登录 / 注册 |
| `reminder/` | 定时提醒 |
| `todo/` | 待办 |
| `punch/` | 习惯打卡 |

共享壳与 HTTP：`src/shared/`。完整选型见 [`docs/architecture.md`](../../../docs/architecture.md)。
