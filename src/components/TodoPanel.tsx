"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import {
  apiCreateTodo,
  apiDeleteTodo,
  apiListTodos,
  apiToggleTodo,
  type TodoDto,
} from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export function TodoPanel() {
  const { user, ready } = useAuth();
  const [items, setItems] = useState<TodoDto[] | null>(null);
  const [filter, setFilter] = useState<"all" | "open" | "done">("open");
  const [title, setTitle] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [error, setError] = useState("");

  const refresh = async (status = filter) => {
    const res = await apiListTodos(status);
    setItems(res.items);
  };

  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await apiListTodos(filter);
        if (!cancelled) setItems(res.items);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "加载失败");
          setItems([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [ready, user, filter]);

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
  if (items === null) return <p className="muted loading-line">正在读取待办…</p>;

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setError("");
    try {
      await apiCreateTodo({
        title: title.trim(),
        dueDate: dueDate || null,
      });
      setTitle("");
      setDueDate("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建失败");
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
        <button type="submit" className="btn-primary">
          添加
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
                <strong className={item.status === "done" ? "done-title" : undefined}>
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
                  onClick={async () => {
                    await apiToggleTodo(item.id);
                    await refresh();
                  }}
                >
                  {item.status === "done" ? "重开" : "完成"}
                </button>
                <button
                  type="button"
                  className="text-btn"
                  onClick={async () => {
                    await apiDeleteTodo(item.id);
                    await refresh();
                  }}
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
