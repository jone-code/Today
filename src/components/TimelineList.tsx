"use client";

import { moodEmoji } from "@/lib/ai";
import { formatDisplayDate } from "@/lib/storage";
import { useToday } from "@/lib/today-context";

export function TimelineList() {
  const { entries, ready } = useToday();

  if (!ready) {
    return <p className="muted loading-line">正在展开时间轴…</p>;
  }

  if (entries.length === 0) {
    return (
      <div className="empty-state reveal">
        <p>还没有留下任何一天。</p>
        <p className="muted">从「今天怎么样？」开始，时间轴会慢慢长出来。</p>
      </div>
    );
  }

  return (
    <ol className="timeline">
      {entries.map((entry, index) => (
        <li
          key={entry.id}
          className="timeline-item reveal"
          style={{ animationDelay: `${index * 60}ms` }}
        >
          <time dateTime={entry.date}>{formatDisplayDate(entry.date)}</time>
          <p className="timeline-highlight">
            今天最大的收获：
            <br />
            {entry.summary.highlight}
          </p>
          <p className="timeline-mood" aria-label={entry.summary.moodLabel}>
            {moodEmoji[entry.summary.mood]}
          </p>
          <p className="muted timeline-one">{entry.summary.oneLiner}</p>
        </li>
      ))}
    </ol>
  );
}
