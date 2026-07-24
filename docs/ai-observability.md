# AI 可观测性

`aigateway` 在每次 `complete` / `embed` 后写入审计，并维护进程内计数。

## 落库

表 `ai_call_logs`：

| 字段 | 说明 |
|------|------|
| kind | `complete` \| `embed` |
| task | `summary` / `memory_extract` / `proactive` / `embed` |
| provider | `llm` \| `heuristic` |
| outcome | `ok` \| `fallback` \| `failed` \| `skipped` |
| elapsed_ms | 耗时 |
| input_units | embedding 条数等 |
| error_message | 失败/降级原因（截断） |

默认保留 `TODAY_AI_LOG_RETAIN_DAYS`（7）天，每日清理。

## 查询

```bash
# 进程计数也在 /health.ai
curl -s localhost:3001/health | jq .ai

# 近 24h 汇总（未配置口令时需登录；配置了则带头）
curl -s -H "Authorization: Bearer $TOKEN" \
  -H "X-Today-Admin-Token: $TODAY_AI_ADMIN_TOKEN" \
  'localhost:3001/v1/admin/ai/stats?hours=24'

curl -s -H "Authorization: Bearer $TOKEN" \
  -H "X-Today-Admin-Token: $TODAY_AI_ADMIN_TOKEN" \
  'localhost:3001/v1/admin/ai/calls?limit=50'
```

| 变量 | 默认 | 说明 |
|------|------|------|
| `TODAY_AI_ADMIN_TOKEN` | 空 | 非空则管理接口校验 `X-Today-Admin-Token` |
| `TODAY_AI_LOG_RETAIN_DAYS` | `7` | 日志保留天数 |
