"use client";

import { moodEmoji } from "@/shared/lib/ai";
import type { DayEntry } from "@/shared/lib/types";

export function SummaryBlock({ entry }: { entry: DayEntry }) {
  const { summary, summaryStatus, pipelineError } = entry;
  const processing = summaryStatus === "processing";
  const failed = summaryStatus === "failed";

  return (
    <div
      className={
        processing || failed ? "summary summary-processing" : "summary"
      }
    >
      <p className="one-liner">{summary.oneLiner}</p>
      {failed ? (
        <p className="form-error summary-wait">
          {pipelineError || "整理失败，可以点「继续整理」再试一次。"}
        </p>
      ) : processing ? (
        <p className="muted summary-wait">AI 正在整理完成项、情绪与关键词…</p>
      ) : (
        <>
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
                <span aria-hidden>{moodEmoji[summary.mood]}</span>{" "}
                {summary.moodLabel}
              </p>
            </div>
          </div>
          <div className="keywords">
            <h3>今日关键词</h3>
            <p>{summary.keywords.join(" · ")}</p>
          </div>
        </>
      )}
    </div>
  );
}
