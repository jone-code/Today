"use client";

import { moodEmoji } from "@/shared/lib/ai";
import type { DayEntry } from "@/shared/lib/types";

export function SummaryBlock({ entry }: { entry: DayEntry }) {
  const { summary } = entry;
  return (
    <div className="summary">
      <p className="one-liner">{summary.oneLiner}</p>
      <div className="summary-grid">
        <div>
          <h3>完成</h3>
          <ul>
            {summary.completed.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
        <div>
          <h3>情绪</h3>
          <p className="mood">
            <span aria-hidden>{moodEmoji[summary.mood]}</span> {summary.moodLabel}
          </p>
        </div>
      </div>
      <div className="keywords">
        <h3>今日关键词</h3>
        <p>{summary.keywords.join(" · ")}</p>
      </div>
    </div>
  );
}
