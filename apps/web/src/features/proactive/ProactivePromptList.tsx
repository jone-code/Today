"use client";

import type { ProactivePrompt } from "@/shared/lib/types";

export function ProactivePromptList({
  prompts,
  onSelect,
}: {
  prompts: ProactivePrompt[];
  onSelect?: (prompt: ProactivePrompt) => void;
}) {
  if (prompts.length === 0) return null;
  return (
    <div className="prompt-list reveal">
      <p className="prompt-label">我记得这些，想从哪一句说起？</p>
      {prompts.map((p) =>
        onSelect ? (
          <button
            key={p.id}
            type="button"
            className="prompt-line prompt-btn"
            onClick={() => onSelect(p)}
          >
            {p.text}
          </button>
        ) : (
          <p key={p.id} className="prompt-line">
            {p.text}
          </p>
        ),
      )}
    </div>
  );
}
