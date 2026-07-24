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

换 embedding 模型维度后需重建 Qdrant collection。

## 本地启用 Qdrant

```bash
docker compose up -d qdrant
# API
export TODAY_VECTOR_PROVIDER=qdrant
npm run dev:api
```

`/health` 会返回 `vectorProvider`（如 `qdrant+fallback:mysql` 或 `mysql`）。

## 点（point）约定

- Point id：`UUID.nameUUIDFromBytes("today-memory:" + memoryId)`
- Payload：`memoryId`, `userId`, `category`, `text`, `strength`, `archived`
- 检索 filter：`userId` + `archived=false`
