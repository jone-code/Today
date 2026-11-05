import { z } from "zod";
import { readSecureDraft, writeSecureDraft } from "./secureDraft";

const Draft = z.object({
  text: z.string().max(4000),
  id: z.string().min(1).max(128).nullable(),
  conversationId: z.string().uuid().nullable(),
}).strict();

export type ChatDraft = z.infer<typeof Draft>;

export async function readChatDraft(key: string): Promise<ChatDraft | null> {
  const stored = await readSecureDraft(key);
  if (stored === null) return null;
  try {
    const draft = Draft.parse(JSON.parse(stored));
    if (!draft.id && draft.conversationId) throw new Error("invalid pending conversation");
    return draft;
  } catch {
    throw new Error("本机对话草稿无法读取，请重新读取或确认清除后再发送。");
  }
}

export async function writeChatDraft(key: string, draft: ChatDraft) {
  const valid = Draft.parse(draft);
  if (!valid.id && valid.conversationId) throw new Error("invalid pending conversation");
  await writeSecureDraft(key, JSON.stringify(valid));
}
