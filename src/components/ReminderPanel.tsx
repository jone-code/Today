"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  apiCreateReminder,
  apiDeleteReminder,
  apiListReminderDeliveries,
  apiListReminders,
  apiMarkReminderDeliveryRead,
  apiUpdateReminder,
  type ReminderDeliveryDto,
  type ReminderDto,
} from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";
import Link from "next/link";

export function ReminderPanel() {
  const { user, ready } = useAuth();
  const [items, setItems] = useState<ReminderDto[] | null>(null);
  const [deliveries, setDeliveries] = useState<ReminderDeliveryDto[]>([]);
  const [title, setTitle] = useState("留下今天");
  const [message, setMessage] = useState("今天过得怎么样？花 30 秒记下来。");
  const [remindTime, setRemindTime] = useState("21:00");
  const [error, setError] = useState("");

  const refresh = async () => {
    const [reminders, deliveryList] = await Promise.all([
      apiListReminders(),
      apiListReminderDeliveries(20),
    ]);
    setItems(reminders.items);
    setDeliveries(deliveryList.items);
  };

  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    (async () => {
      try {
        const [reminders, deliveryList] = await Promise.all([
          apiListReminders(),
          apiListReminderDeliveries(20),
        ]);
        if (cancelled) return;
        setItems(reminders.items);
        setDeliveries(deliveryList.items);
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

  if (!ready) {
    return <p className="muted loading-line">正在读取提醒…</p>;
  }

  if (!user) {
    return (
      <div className="empty-state reveal">
        <p>提醒需要登录后使用。</p>
        <p className="muted">
          <Link href="/login">登录</Link> 或 <Link href="/register">注册</Link>
        </p>
      </div>
    );
  }

  if (items === null) {
    return <p className="muted loading-line">正在读取提醒…</p>;
  }

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      await apiCreateReminder({
        title: title.trim(),
        message: message.trim(),
        remindTime,
        timezone: "Asia/Shanghai",
        enabled: true,
      });
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建失败");
    }
  };

  const toggle = async (item: ReminderDto) => {
    await apiUpdateReminder(item.id, { enabled: !item.enabled });
    await refresh();
  };

  const remove = async (id: string) => {
    await apiDeleteReminder(id);
    await refresh();
  };

  const markRead = async (id: string) => {
    await apiMarkReminderDeliveryRead(id);
    await refresh();
  };

  return (
    <div className="reminder-panel">
      <form className="reminder-form reveal" onSubmit={onCreate}>
        <h2>新建每日提醒</h2>
        <label>
          标题
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={128} />
        </label>
        <label>
          内容
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            required
            maxLength={512}
            rows={3}
          />
        </label>
        <label>
          时间（每天）
          <input
            type="time"
            value={remindTime}
            onChange={(e) => setRemindTime(e.target.value)}
            required
          />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <button type="submit" className="btn-primary">
          保存提醒
        </button>
      </form>

      <section className="reveal">
        <h2>我的提醒</h2>
        {items.length === 0 ? (
          <p className="muted">还没有提醒。设一个傍晚的「留下今天」吧。</p>
        ) : (
          <ul className="reminder-list">
            {items.map((item) => (
              <li key={item.id} className="reminder-item">
                <div>
                  <strong>{item.title}</strong>
                  <p className="muted">
                    每天 {item.remindTime} · {item.timezone}
                    {item.enabled ? "" : " · 已关闭"}
                  </p>
                  <p>{item.message}</p>
                </div>
                <div className="reminder-actions">
                  <button type="button" className="text-btn" onClick={() => toggle(item)}>
                    {item.enabled ? "暂停" : "开启"}
                  </button>
                  <button type="button" className="text-btn" onClick={() => remove(item.id)}>
                    删除
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="reveal">
        <h2>最近送达</h2>
        {deliveries.length === 0 ? (
          <p className="muted">到点后会出现在这里。调度器每分钟检查一次。</p>
        ) : (
          <ul className="reminder-list">
            {deliveries.map((d) => (
              <li key={d.id} className="reminder-item">
                <div>
                  <strong>{d.title}</strong>
                  <p className="muted">
                    {d.fireDate} · {d.status === "pending" ? "未读" : "已读"}
                  </p>
                  <p>{d.message}</p>
                </div>
                {d.status === "pending" ? (
                  <button type="button" className="text-btn" onClick={() => markRead(d.id)}>
                    标为已读
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
