"use client";

import { useState } from "react";
import {
  apiDismissProactivePrompt,
  apiSelectProactivePrompt,
} from "@/shared/lib/api-client";
import { answerScaffoldForPrompt } from "@/shared/lib/mappers";
import type { ProactivePrompt } from "@/shared/lib/types";

export function ProactivePromptList({
  prompts,
  onSelect,
}: {
  prompts: ProactivePrompt[];
  onSelect?: (prompt: ProactivePrompt, scaffold: string) => void;
}) {
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const [busyId, setBusyId] = useState<string | null>(null);

  const visible = prompts.filter((p) => !hidden.has(p.id));
  if (visible.length === 0) return null;

  const select = async (p: ProactivePrompt) => {
    setBusyId(p.id);
    try {
      await apiSelectProactivePrompt(p.id).catch(() => undefined);
      onSelect?.(p, answerScaffoldForPrompt(p));
    } finally {
      setBusyId(null);
    }
  };

  const dismiss = async (p: ProactivePrompt) => {
    setBusyId(p.id);
    try {
      await apiDismissProactivePrompt(p.id).catch(() => undefined);
      setHidden((prev) => new Set(prev).add(p.id));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="prompt-list reveal">
      <p className="prompt-label">我记得这些，想从哪一句说起？</p>
      {visible.map((p) => (
        <div key={p.id} className="prompt-row">
          <button
            type="button"
            className="prompt-line prompt-btn"
            disabled={busyId === p.id}
            onClick={() => void select(p)}
          >
            {p.source === "followup" ? (
              <span className="prompt-source">追问</span>
            ) : null}
            {p.text}
          </button>
          <button
            type="button"
            className="prompt-dismiss"
            disabled={busyId === p.id}
            onClick={() => void dismiss(p)}
            aria-label="先不聊这个"
          >
            忽略
          </button>
        </div>
      ))}
    </div>
  );
}
