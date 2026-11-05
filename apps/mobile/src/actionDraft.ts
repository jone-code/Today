import { z } from "zod";
import { deleteSecureDraft, readSecureDraft, writeSecureDraft } from "./secureDraft";

const RequestId = z.string().uuid();
const Habit = z.object({ requestId: RequestId, title: z.string().max(128),
  description: z.string().max(512) }).strict();
const Reminder = z.object({ requestId: RequestId, title: z.string().max(128),
  message: z.string().max(512), time: z.string().max(5), timezone: z.string().max(64) }).strict();
const Photo = z.object({ requestId: RequestId, habitId: z.string().uuid(),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/) }).strict();
const Draft = z.object({
  todo: z.object({ requestId: RequestId, title: z.string().max(200) }).strict().nullable(),
  habit: Habit.nullable(),
  reminder: Reminder.nullable(),
  photo: Photo.nullable().default(null),
}).strict();

export type ActionDraft = z.infer<typeof Draft>;

export function actionDraftKey(baseUrl: string, userId: string) {
  const scope = [baseUrl, userId]
    .map(value => encodeURIComponent(value).replace(/_/g, "_5F").replace(/%/g, "_"))
    .join("-");
  return `today-actions-draft-v1-${scope}`;
}

export async function readActionDraft(key: string): Promise<ActionDraft | null> {
  const saved = await readSecureDraft(key);
  if (saved === null) return null;
  try { return Draft.parse(JSON.parse(saved)); }
  catch { throw new Error("本机行动草稿无法读取，请检查设备存储后重试。"); }
}

export async function writeActionDraft(key: string, draft: ActionDraft): Promise<void> {
  const validated = Draft.parse(draft);
  if (!validated.todo && !validated.habit && !validated.reminder && !validated.photo) {
    await deleteSecureDraft(key);
  } else {
    await writeSecureDraft(key, JSON.stringify(validated));
  }
}

export async function deleteActionDraft(key: string): Promise<void> {
  await deleteSecureDraft(key);
}

export async function deleteActionDraftAfterWrites(key: string, pending: Promise<void>): Promise<void> {
  await pending.catch(() => {});
  await deleteActionDraft(key);
}
