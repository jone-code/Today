import * as Crypto from "expo-crypto";
import * as FileSystem from "expo-file-system";
import { deleteSecureDraft, readSecureDraft, writeSecureDraft } from "./secureDraft";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function avatarDraftKey(baseUrl: string, userId: string) {
  const scope = [baseUrl, userId]
    .map(value => encodeURIComponent(value).replace(/_/g, "_5F").replace(/%/g, "_"))
    .join("-");
  return `today-avatar-draft-v1-${scope}`;
}

async function directory(scope: string) {
  if (!FileSystem.documentDirectory) throw new Error("设备不支持保存待上传的形象图片");
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, scope);
  return `${FileSystem.documentDirectory}pending-avatar/${hash}/`;
}

export async function avatarDraftUri(scope: string, requestId: string) {
  if (!UUID.test(requestId)) throw new Error("形象草稿标识无效");
  return `${await directory(scope)}${requestId}.jpg`;
}

export async function avatarDraftExists(scope: string, requestId: string) {
  const info = await FileSystem.getInfoAsync(await avatarDraftUri(scope, requestId));
  return info.exists && !info.isDirectory && (info.size ?? 0) > 0;
}

export async function saveAvatarDraft(scope: string, requestId: string, source: string) {
  const uri = await avatarDraftUri(scope, requestId);
  await FileSystem.makeDirectoryAsync(await directory(scope), { intermediates: true });
  try {
    await FileSystem.copyAsync({ from: source, to: uri });
    if (!await avatarDraftExists(scope, requestId)) throw new Error("形象图片保存不完整");
  } catch (error) {
    await FileSystem.deleteAsync(uri, { idempotent: true }).catch(() => {});
    throw error;
  }
  return uri;
}

export async function deleteAvatarDraft(scope: string, requestId: string) {
  await FileSystem.deleteAsync(await avatarDraftUri(scope, requestId), { idempotent: true });
}

export async function pruneAvatarDrafts(scope: string, keepRequestId?: string) {
  const dir = await directory(scope);
  if (!(await FileSystem.getInfoAsync(dir)).exists) return;
  const keep = keepRequestId ? `${keepRequestId}.jpg` : null;
  for (const name of await FileSystem.readDirectoryAsync(dir)) {
    if (name !== keep && UUID.test(name.slice(0, -4)) && name.endsWith(".jpg")) {
      await FileSystem.deleteAsync(`${dir}${name}`, { idempotent: true });
    }
  }
}

export async function readAvatarDraft(key: string) {
  const id = await readSecureDraft(key);
  if (id !== null && !UUID.test(id)) throw new Error("本机形象草稿无法读取，请清除后重新选择图片。");
  return id;
}

export async function writeAvatarDraft(key: string, requestId: string | null) {
  if (requestId === null) await deleteSecureDraft(key);
  else {
    if (!UUID.test(requestId)) throw new Error("形象草稿标识无效");
    await writeSecureDraft(key, requestId);
  }
}
