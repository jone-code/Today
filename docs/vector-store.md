# 向量库（VectorStore）

记忆语义检索支持可插拔向量索引。embedding 仍由 `aigateway` 生成；业务模块不直接调用 LLM SDK。

## 架构

```
CheckinAiPipeline
  → MemoryService.upsertFromCheckin / update / delete / archive
       ├─ MySQL memories（含 embedding_json，权威业务数据）
       └─ VectorStore.upsert / delete / setArchived / search
            ├─ mysql（默认）：扫描 embedding_json + 余弦 Top-K
            └─ qdrant：外挂检索；失败写入吞掉、检索回退 mysql
ProactiveService → MemoryService.retrieveRelevant → VectorStore.search
VectorReindexService → 存量 backfill / reindex / recreate
```

## 配置

`application.yml` / 环境变量：

| 变量 | 默认 | 说明 |
|------|------|------|
| `TODAY_VECTOR_PROVIDER` | `mysql` | `mysql` \| `qdrant` |
| `TODAY_QDRANT_URL` | `http://127.0.0.1:6333` | Qdrant HTTP |
| `TODAY_QDRANT_COLLECTION` | `today_memories` | collection 名 |
| `TODAY_VECTOR_DIMENSIONS` | `1536` | 需与 embedding 模型一致 |
| `TODAY_QDRANT_API_KEY` | 空 | 可选 |
| `TODAY_QDRANT_TIMEOUT_MS` | `5000` | |
| `TODAY_VECTOR_ADMIN_TOKEN` | 空 | 全量 reindex 口令；非空则需请求头 |

换 embedding 模型维度后需 `recreate=true` 重建 Qdrant collection。

## 本地启用 Qdrant

```bash
docker compose up -d qdrant   # 或 npm run vector:up
export TODAY_VECTOR_PROVIDER=qdrant
npm run dev:api
```

`GET /health` 返回：

- `vectorProvider`
- `vector`：`{ ok, detail, collectionExists, pointsCount, configuredDimensions, actualDimensions }`

维度不一致时 `vector.ok=false`，提示用 recreate reindex。

## 存量同步（reindex）

```bash
# 当前用户
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$API/v1/memories/reindex?fillMissingEmbeddings=true"

# 全量（可选重建 collection）
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "X-Today-Admin-Token: $TODAY_VECTOR_ADMIN_TOKEN" \
  "$API/v1/admin/vector/reindex?fillMissingEmbeddings=true&recreate=true"

# 脚本
TODAY_AUTH_TOKEN=$TOKEN SCOPE=user FILL_MISSING=true bash scripts/vector-reindex.sh
TODAY_AUTH_TOKEN=$TOKEN SCOPE=all RECREATE=true FILL_MISSING=true bash scripts/vector-reindex.sh
```

| 参数 | 含义 |
|------|------|
| `fillMissingEmbeddings` | 对无 `embedding_json` 的行调用 `aiGateway.embed` 写回 MySQL |
| `recreate` | 删除并重建 Qdrant collection（维度变更时用） |

## 点（point）约定

- Point id：`UUID.nameUUIDFromBytes("today-memory:" + memoryId)`
- Payload：`memoryId`, `userId`, `category`, `text`, `strength`, `archived`
- 检索 filter：`userId` + `archived=false`
