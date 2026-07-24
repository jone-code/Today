"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  apiCreatePunchHabit,
  apiDeletePunchHabit,
  apiListPunchHabits,
  apiPunchHabit,
  apiUnpunchHabit,
  apiUpdatePunchHabit,
  type PunchHabitDto,
} from "@/shared/lib/api-client";
import { useAuth } from "@/shared/lib/auth-context";
import { punchKeys } from "@/shared/lib/query-keys";

export function PunchPanel() {
  const { user, ready } = useAuth();
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [formError, setFormError] = useState("");

  const enabled = ready && !!user;

  const listQuery = useQuery({
    queryKey: punchKeys.habits(),
    queryFn: () => apiListPunchHabits(),
    enabled,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: punchKeys.all });

  const createMutation = useMutation({
    mutationFn: () =>
      apiCreatePunchHabit({ title: title.trim(), enabled: true }),
    onSuccess: async () => {
      setTitle("");
      await invalidate();
    },
  });

  const punchMutation = useMutation({
    mutationFn: async (item: PunchHabitDto) => {
      if (item.punchedToday) await apiUnpunchHabit(item.id);
      else await apiPunchHabit(item.id);
    },
    onSuccess: invalidate,
  });

  const toggleMutation = useMutation({
    mutationFn: (item: PunchHabitDto) =>
      apiUpdatePunchHabit(item.id, { enabled: !item.enabled }),
    onSuccess: invalidate,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiDeletePunchHabit(id),
    onSuccess: invalidate,
  });

  if (!ready) return <p className="muted loading-line">正在读取打卡…</p>;
  if (!user) {
    return (
      <div className="empty-state reveal">
        <p>打卡需要登录后使用。</p>
        <p className="muted">
          <Link href="/login">登录</Link> 或 <Link href="/register">注册</Link>
        </p>
      </div>
    );
  }
  if (listQuery.isLoading) {
    return <p className="muted loading-line">正在读取打卡…</p>;
  }

  const items = listQuery.data?.items ?? [];
  const dateLabel = listQuery.data?.date ?? "";
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
      <p className="muted reveal">今天：{dateLabel || "—"}</p>

      <form className="module-form reveal" onSubmit={onCreate}>
        <h2>新建习惯</h2>
        <label>
          习惯名
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            maxLength={128}
            placeholder="例如：早起 / 阅读 30 分钟"
          />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <button
          type="submit"
          className="btn-primary"
          disabled={createMutation.isPending}
        >
          {createMutation.isPending ? "添加中…" : "添加习惯"}
        </button>
      </form>

      <ul className="module-list reveal">
        {items.length === 0 ? (
          <li className="muted">还没有习惯。先加一个今天想坚持的。</li>
        ) : (
          items.map((item) => (
            <li key={item.id} className="module-item">
              <div>
                <strong>{item.title}</strong>
                <p className="muted">
                  {item.enabled ? "进行中" : "已暂停"}
                  {item.punchedToday ? " · 今日已打卡" : " · 今日未打卡"}
                  {item.streak > 0 ? ` · 连续 ${item.streak} 天` : ""}
                </p>
              </div>
              <div className="module-actions">
                <button
                  type="button"
                  className="text-btn"
                  onClick={() => punchMutation.mutate(item)}
                >
                  {item.punchedToday ? "取消打卡" : "打卡"}
                </button>
                <button
                  type="button"
                  className="text-btn"
                  onClick={() => toggleMutation.mutate(item)}
                >
                  {item.enabled ? "暂停" : "开启"}
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
