"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  apiCreateTodo,
  apiDeleteTodo,
  apiListTodos,
  apiToggleTodo,
} from "@/shared/lib/api-client";
import { useAuth } from "@/shared/lib/auth-context";
import { todoKeys } from "@/shared/lib/query-keys";

type Filter = "open" | "done" | "all";

export function TodoPanel() {
  const { user, ready } = useAuth();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>("open");
  const [title, setTitle] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [formError, setFormError] = useState("");

  const enabled = ready && !!user;

  const listQuery = useQuery({
    queryKey: todoKeys.list(filter),
    queryFn: async () => (await apiListTodos(filter)).items,
    enabled,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: todoKeys.all });

  const createMutation = useMutation({
    mutationFn: () =>
      apiCreateTodo({
        title: title.trim(),
        dueDate: dueDate || null,
      }),
    onSuccess: async () => {
      setTitle("");
      setDueDate("");
      await invalidate();
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (id: string) => apiToggleTodo(id),
    onSuccess: invalidate,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiDeleteTodo(id),
    onSuccess: invalidate,
  });

  if (!ready) return <p className="muted loading-line">正在读取待办…</p>;
  if (!user) {
    return (
      <div className="empty-state reveal">
        <p>待办需要登录后使用。</p>
        <p className="muted">
          <Link href="/login">登录</Link> 或 <Link href="/register">注册</Link>
        </p>
      </div>
    );
  }
  if (listQuery.isLoading) {
    return <p className="muted loading-line">正在读取待办…</p>;
  }

  const items = listQuery.data ?? [];
  const error =
    formError ||
    (listQuery.error instanceof Error ? listQuery.error.message : "");

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setFormError("");
    try {
      await createMutation.mutateAsync();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "创建失败");
    }
  };

  return (
    <div className="module-panel">
      <form className="module-form reveal" onSubmit={onCreate}>
        <h2>新建待办</h2>
        <label>
          标题
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            maxLength={200}
            placeholder="今天要完成什么？"
          />
        </label>
        <label>
          截止日期（可选）
          <input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
          />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <button
          type="submit"
          className="btn-primary"
          disabled={createMutation.isPending}
        >
          {createMutation.isPending ? "添加中…" : "添加"}
        </button>
      </form>

      <div className="filter-row reveal">
        {(["open", "done", "all"] as const).map((f) => (
          <button
            key={f}
            type="button"
            className={filter === f ? "chip active" : "chip"}
            onClick={() => setFilter(f)}
          >
            {f === "open" ? "未完成" : f === "done" ? "已完成" : "全部"}
          </button>
        ))}
      </div>

      <ul className="module-list reveal">
        {items.length === 0 ? (
          <li className="muted">还没有待办。</li>
        ) : (
          items.map((item) => (
            <li key={item.id} className="module-item">
              <div>
                <strong
                  className={item.status === "done" ? "done-title" : undefined}
                >
                  {item.title}
                </strong>
                <p className="muted">
                  {item.status === "done" ? "已完成" : "进行中"}
                  {item.dueDate ? ` · 截止 ${item.dueDate}` : ""}
                </p>
              </div>
              <div className="module-actions">
                <button
                  type="button"
                  className="text-btn"
                  onClick={() => toggleMutation.mutate(item.id)}
                >
                  {item.status === "done" ? "重开" : "完成"}
                </button>
                <button
                  type="button"
                  className="text-btn"
                  onClick={() => deleteMutation.mutate(item.id)}
                >
                  删除
                </button>
              </div>
            </li>
          ))
        )}
      </ul>
    </div>
  );
}
