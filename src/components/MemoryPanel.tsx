"use client";

import { useToday } from "@/lib/today-context";

const categoryLabel = {
  work: "工作",
  health: "健康",
  learning: "学习",
  life: "生活",
  emotion: "情绪",
  goal: "目标",
} as const;

export function MemoryPanel() {
  const { memories, ready, entries } = useToday();

  if (!ready) {
    return <p className="muted loading-line">正在整理长期记忆…</p>;
  }

  if (memories.length === 0) {
    return (
      <div className="empty-state reveal">
        <p>记忆还在等待第一天。</p>
        <p className="muted">
          你只负责留下今天；关于你的理解，会在这里慢慢成形。
        </p>
      </div>
    );
  }

  return (
    <div className="memory-panel">
      <p className="memory-intro reveal">
        已根据你留下的 {entries.length} 天，自动更新这些理解。无需维护。
      </p>
      <ul className="memory-list">
        {memories.map((m, index) => (
          <li
            key={m.id}
            className="memory-item reveal"
            style={{ animationDelay: `${index * 50}ms` }}
          >
            <span className="memory-cat">{categoryLabel[m.category]}</span>
            <p>{m.text}</p>
            <span className="memory-strength">提及 {m.strength} 次</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
