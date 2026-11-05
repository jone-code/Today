import * as Crypto from "expo-crypto";
import * as FileSystem from "expo-file-system";

async function directory(scope: string) {
  if (!FileSystem.documentDirectory) throw new Error("设备不支持保存待上传照片");
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, scope);
  return `${FileSystem.documentDirectory}pending-punch/${hash}/`;
}

function fileName(requestId: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(requestId)) {
    throw new Error("照片草稿标识无效");
  }
  return `${requestId}.jpg`;
}

export async function photoDraftUri(scope: string, requestId: string) {
  return `${await directory(scope)}${fileName(requestId)}`;
}

export async function photoDraftExists(scope: string, requestId: string) {
  const info = await FileSystem.getInfoAsync(await photoDraftUri(scope, requestId));
  return info.exists && !info.isDirectory && (info.size ?? 0) > 0;
}

export async function savePhotoDraft(scope: string, requestId: string, source: string) {
  const dir = await directory(scope);
  const uri = `${dir}${fileName(requestId)}`;
  await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
  try {
    await FileSystem.copyAsync({ from: source, to: uri });
    if (!await photoDraftExists(scope, requestId)) throw new Error("照片保存不完整");
  } catch (error) {
    await FileSystem.deleteAsync(uri, { idempotent: true }).catch(() => {});
    throw error;
  }
  return uri;
}

export async function deletePhotoDraft(scope: string, requestId: string) {
  await FileSystem.deleteAsync(await photoDraftUri(scope, requestId), { idempotent: true });
}

export async function prunePhotoDrafts(scope: string, keepRequestId?: string) {
  const dir = await directory(scope);
  const info = await FileSystem.getInfoAsync(dir);
  if (!info.exists) return;
  const keep = keepRequestId ? fileName(keepRequestId) : null;
  for (const name of await FileSystem.readDirectoryAsync(dir)) {
    if (name !== keep && /^[0-9a-f-]{36}\.jpg$/i.test(name)) {
      await FileSystem.deleteAsync(`${dir}${name}`, { idempotent: true });
    }
  }
}
