import { z } from "zod";
import { deleteSecureDraft, readSecureDraft, writeSecureDraft } from "./secureDraft";

const Saved = z.object({
  text: z.string().max(2000),
  original: z.string().max(2000),
  requestId: z.string().uuid().optional(),
}).strict();

export type CheckinDraft = { text: string; original: string; requestId: string };
export type SavedCheckinDraft = z.infer<typeof Saved>;

export function checkinDraftKey(baseUrl: string, userId: string) {
  const scope = [baseUrl, userId]
    .map(value => encodeURIComponent(value).replace(/_/g, "_5F").replace(/%/g, "_"))
    .join("-");
  return `today-checkin-draft-v1-${scope}`;
}

export async function readCheckinDraft(key: string): Promise<SavedCheckinDraft | null> {
  const stored = await readSecureDraft(key);
  if (stored === null) return null;
  try { return Saved.parse(JSON.parse(stored)); }
  catch { throw new Error("本机每日记录草稿无法读取，请重新读取或明确清除草稿。"); }
}

export async function writeCheckinDraft(key: string, draft: CheckinDraft | null): Promise<void> {
  if (draft === null) await deleteSecureDraft(key);
  else await writeSecureDraft(key, JSON.stringify(Saved.parse(draft)));
}
