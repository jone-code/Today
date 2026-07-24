"use client";

import type { ProactivePrompt } from "@/shared/lib/types";

export function ProactivePromptList({ prompts }: { prompts: ProactivePrompt[] }) {
  if (prompts.length === 0) return null;
  return (
    <div className="prompt-list reveal">
      {prompts.map((p) => (
        <p key={p.id} className="prompt-line">
          {p.text}
        </p>
      ))}
    </div>
  );
}
