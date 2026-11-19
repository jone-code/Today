"use client";

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  apiArchiveMemory,
  apiDeleteMemory,
  apiGetMemories,
  apiGetMemoryAccessAudit,
  apiGetMemoryOrigins,
  apiUnarchiveMemory,
  apiUpdateMemory,
  apiUpdateMemoryContextScope,
  type MemoryDto,
} from "@/shared/lib/api-client";
import { useAuth } from "@/shared/lib/auth-context";
import { mapMemoryDtoToMemoryItem } from "@/shared/lib/mappers";
import { memoryKeys, todayKeys } from "@/shared/lib/query-keys";
import { useToday } from "@/shared/lib/today-context";
import type { MemoryItem } from "@/shared/lib/types";

const categoryLabel = {
  work: "工作",
  health: "健康",
  learning: "学习",
  life: "生活",
  emotion: "情绪",
  goal: "目标",
} as const;

const categories = Object.keys(categoryLabel) as Array<
  keyof typeof categoryLabel
>;

type EditDraft = {
  id: string;
  text: string;
  category: MemoryItem["category"];
};

export function MemoryPanel() {
  const { ready: authReady, user } = useAuth();
  const { ready, entries, mode, memories: contextMemories } = useToday();
  const queryClient = useQueryClient();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [edit, setEdit] = useState<EditDraft | null>(null);
  const [actionError, setActionError] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<{ origins: Awaited<ReturnType<typeof apiGetMemoryOrigins>>; audits: Awaited<ReturnType<typeof apiGetMemoryAccessAudit>> } | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const enabled = authReady && !!user && mode === "api";

  const listQuery = useQuery({
    queryKey: memoryKeys.list(includeArchived),
    queryFn: async () => {
      const res = await apiGetMemories(includeArchived);
      return res.items.map(mapMemoryDtoToMemoryItem);
    },
    enabled,
  });

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: memoryKeys.all }),
      queryClient.invalidateQueries({ queryKey: todayKeys.all }),
    ]);
  };

  const updateMutation = useMutation({
    mutationFn: (draft: EditDraft) =>
      apiUpdateMemory(draft.id, {
        text: draft.text.trim(),
        category: draft.category,
      }),
    onSuccess: async () => {
      setEdit(null);
      await invalidate();
    },
  });

  const archiveMutation = useMutation({
    mutationFn: (item: MemoryItem) =>
      item.archived ? apiUnarchiveMemory(item.id) : apiArchiveMemory(item.id),
    onSuccess: invalidate,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiDeleteMemory(id),
    onSuccess: invalidate,
  });

  const scopeMutation = useMutation({
    mutationFn: ({ id, scope, expectedUpdatedAt }: { id: string; scope: MemoryDto["chatContextScope"]; expectedUpdatedAt: string }) =>
      apiUpdateMemoryContextScope(id, scope, expectedUpdatedAt),
    onSuccess: invalidate,
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: memoryKeys.all });
    },
  });

  const toggleDetail = async (id: string) => {
    if (expandedId === id) { setExpandedId(null); setDetail(null); return; }
    setExpandedId(id); setDetail(null); setDetailLoading(true); setActionError("");
    try {
      const [origins, audits] = await Promise.all([apiGetMemoryOrigins(id), apiGetMemoryAccessAudit(id)]);
      setDetail({ origins, audits });
    } catch (err) { setActionError(err instanceof Error ? err.message : "来源读取失败"); }
    finally { setDetailLoading(false); }
  };

  if (!ready || (enabled && listQuery.isLoading)) {
    return <p className="muted loading-line">正在整理长期记忆…</p>;
  }

  if (mode === "guest") {
    return (
      <div className="empty-state reveal">
        <p>登录后才能查看长期记忆。</p>
      </div>
    );
  }

  const memories =
    mode === "api"
      ? (listQuery.data ?? [])
      : contextMemories.filter((m) => includeArchived || !m.archived);
  const canManage = mode === "api";
  const error =
    actionError ||
    (listQuery.error instanceof Error ? listQuery.error.message : "");

  if (memories.length === 0 && !includeArchived) {
    return (
      <div className="empty-state reveal">
        <p>记忆还在等待第一天。</p>
        <p className="muted">
          你只负责留下今天；关于你的理解，会在这里慢慢成形。
        </p>
      </div>
    );
  }

  const onSaveEdit = async (e: FormEvent) => {
    e.preventDefault();
    if (!edit || !edit.text.trim()) return;
    setActionError("");
    try {
      await updateMutation.mutateAsync(edit);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "保存失败");
    }
  };

  const runAction = async (fn: () => Promise<MemoryDto | void>) => {
    setActionError("");
    try {
      await fn();
    } catch (err) {
      const status = err && typeof err === "object" && "status" in err ? (err as { status?: number }).status : undefined;
      setActionError(status === 409 ? "这条记忆已被其他设备修改，已刷新最新内容。" : err instanceof Error ? err.message : "操作失败");
    }
  };

  return (
    <div className="memory-panel">
      <p className="memory-intro reveal">
        已根据你留下的 {entries.length} 天自动形成理解。可编辑、归档或删除。
      </p>

      <div className="filter-row reveal">
        <button
          type="button"
          className={!includeArchived ? "chip active" : "chip"}
          onClick={() => setIncludeArchived(false)}
        >
          进行中
        </button>
        <button
          type="button"
          className={includeArchived ? "chip active" : "chip"}
          onClick={() => setIncludeArchived(true)}
        >
          含已归档
        </button>
      </div>

      {error ? <p className="form-error reveal">{error}</p> : null}

      <ul className="memory-list">
        {memories.length === 0 ? (
          <li className="muted reveal">没有记忆。</li>
        ) : (
          memories.map((m, index) => (
            <li
              key={m.id}
              className={`memory-item reveal${m.archived ? " is-archived" : ""}`}
              style={{ animationDelay: `${index * 50}ms` }}
            >
              {edit?.id === m.id ? (
                <form className="memory-edit" onSubmit={onSaveEdit}>
                  <label>
                    分类
                    <select
                      value={edit.category}
                      onChange={(e) =>
                        setEdit({
                          ...edit,
                          category: e.target.value as MemoryItem["category"],
                        })
                      }
                    >
                      {categories.map((c) => (
                        <option key={c} value={c}>
                          {categoryLabel[c]}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    内容
                    <textarea
                      value={edit.text}
                      onChange={(e) =>
                        setEdit({ ...edit, text: e.target.value })
                      }
                      rows={3}
                      maxLength={512}
                      required
                    />
                  </label>
                  <div className="memory-edit-actions">
                    <button
                      type="submit"
                      className="btn-primary"
                      disabled={updateMutation.isPending}
                    >
                      {updateMutation.isPending ? "保存中…" : "保存"}
                    </button>
                    <button
                      type="button"
                      className="text-btn"
                      onClick={() => setEdit(null)}
                    >
                      取消
                    </button>
                  </div>
                </form>
              ) : (
                <>
                  <div className="memory-body">
                    <span className="memory-cat">
                      {categoryLabel[m.category]}
                      {m.archived ? " · 已归档" : ""}
                    </span>
                    <p>{m.text}</p>
                    <span className="memory-strength">提及 {m.strength} 次</span>
                    {canManage ? <div className="memory-scope">
                      <span>Chat 使用范围</span>
                      {(["chat_and_timeline", "timeline_only", "disabled"] as const).map(scope => (
                        <button key={scope} type="button" className={m.chatContextScope === scope ? "chip active" : "chip"}
                          disabled={scopeMutation.isPending}
                          onClick={() => void runAction(() => scopeMutation.mutateAsync({ id: m.id, scope, expectedUpdatedAt: m.updatedAt }))}>
                          {scope === "chat_and_timeline" ? "Chat 可用" : scope === "timeline_only" ? "仅回看" : "停用"}
                        </button>
                      ))}
                    </div> : null}
                    {canManage ? <button type="button" className="text-btn memory-detail-toggle" onClick={() => void toggleDetail(m.id)}>
                      {expandedId === m.id ? "收起来源与使用记录" : "查看来源与使用记录"}
                    </button> : null}
                    {expandedId === m.id ? <div className="memory-detail">
                      {detailLoading ? <span className="muted">正在加载来源和使用记录…</span> : detail ? <>
                        <strong>来源</strong>
                        {detail.origins.length ? detail.origins.map(origin => <span key={`${origin.kind}-${origin.sourceId}`}>
                          {origin.kind === "checkin" ? `每日记录 · ${origin.date ?? ""}` : "对话记录"}
                        </span>) : <span className="muted">没有可查看的来源</span>}
                        <strong>最近使用记录</strong>
                        {detail.audits.length ? detail.audits.slice(0, 10).map(audit => <span key={audit.id}>
                          {audit.source === "chat" ? "Chat" : audit.source === "timeline" ? "时间轴" : audit.source} · {audit.action === "retrieved" ? "被读取" : audit.action} · {new Date(audit.occurredAt).toLocaleString("zh-CN")}
                        </span>) : <span className="muted">暂时没有使用记录</span>}
                      </> : null}
                    </div> : null}
                  </div>
                  <div className="module-actions">
                    {canManage ? (
                      <>
                        <button
                          type="button"
                          className="text-btn"
                          onClick={() =>
                            setEdit({
                              id: m.id,
                              text: m.text,
                              category: m.category,
                            })
                          }
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          className="text-btn"
                          onClick={() =>
                            runAction(() => archiveMutation.mutateAsync(m))
                          }
                          disabled={archiveMutation.isPending}
                        >
                          {m.archived ? "取消归档" : "归档"}
                        </button>
                        <button
                          type="button"
                          className="text-btn"
                          onClick={() => {
                            if (!window.confirm("确定删除这条记忆？")) return;
                            runAction(() => deleteMutation.mutateAsync(m.id));
                          }}
                          disabled={deleteMutation.isPending}
                        >
                          删除
                        </button>
                      </>
                    ) : null}
                  </div>
                </>
              )}
            </li>
          ))
        )}
      </ul>
    </div>
  );
}
