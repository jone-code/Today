"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import {
  apiCreatePunchHabit,
  apiDeletePunchHabit,
  apiListPunchHabits,
  apiPunchHabit,
  apiUnpunchHabit,
  apiUpdatePunchHabit,
  type PunchHabitDto,
} from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export function PunchPanel() {
  const { user, ready } = useAuth();
  const [items, setItems] = useState<PunchHabitDto[] | null>(null);
  const [dateLabel, setDateLabel] = useState("");
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");

  const refresh = async () => {
    const res = await apiListPunchHabits();
    setItems(res.items);
    setDateLabel(res.date);
  };

  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await apiListPunchHabits();
        if (cancelled) return;
        setItems(res.items);
        setDateLabel(res.date);
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
  }, [ready, user]);

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
  if (items === null) return <p className="muted loading-line">正在读取打卡…</p>;

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setError("");
    try {
      await apiCreatePunchHabit({ title: title.trim(), enabled: true });
      setTitle("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建失败");
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
        <button type="submit" className="btn-primary">
          添加习惯
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
                  onClick={async () => {
                    if (item.punchedToday) await apiUnpunchHabit(item.id);
                    else await apiPunchHabit(item.id);
                    await refresh();
                  }}
                >
                  {item.punchedToday ? "取消打卡" : "打卡"}
                </button>
                <button
                  type="button"
                  className="text-btn"
                  onClick={async () => {
                    await apiUpdatePunchHabit(item.id, {
                      enabled: !item.enabled,
                    });
                    await refresh();
                  }}
                >
                  {item.enabled ? "暂停" : "开启"}
                </button>
                <button
                  type="button"
                  className="text-btn"
                  onClick={async () => {
                    await apiDeletePunchHabit(item.id);
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
