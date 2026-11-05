import { z } from "zod";
import { deleteSecureDraft, readSecureDraft, writeSecureDraft } from "./secureDraft";

const Draft = z.object({
  requestId: z.string().uuid(),
  kind: z.enum(["issue", "idea"]),
  body: z.string().trim().min(1).max(1000),
}).strict();
export type FeedbackDraft = z.infer<typeof Draft>;

export function feedbackDraftKey(baseUrl: string, userId: string) {
  const scope = [baseUrl, userId]
    .map(value => encodeURIComponent(value).replace(/_/g, "_5F").replace(/%/g, "_"))
    .join("-");
  return `today-feedback-draft-v1-${scope}`;
}

export async function readFeedbackDraft(key: string): Promise<FeedbackDraft | null> {
  const value = await readSecureDraft(key);
  if (value === null) return null;
  try { return Draft.parse(JSON.parse(value)); }
  catch { throw new Error("本机反馈草稿无法读取，请重新读取或确认清除后再提交。"); }
}

export async function writeFeedbackDraft(key: string, value: FeedbackDraft): Promise<void> {
  await writeSecureDraft(key, JSON.stringify(Draft.parse(value)));
}

export async function deleteFeedbackDraft(key: string): Promise<void> {
  await deleteSecureDraft(key);
}
