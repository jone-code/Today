import * as SecureStore from "expo-secure-store";

const CHUNK_BYTES = 1800;
const MAX_CHUNKS = 16;
const MARKER = "today-draft-chunks-v1:";
type Slot = "a" | "b";

function byteLength(codePoint: number) {
  return codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
}

export function splitDraft(value: string): string[] {
  const chunks: string[] = [];
  let current = "";
  let bytes = 0;
  for (const char of value) {
    const size = byteLength(char.codePointAt(0)!);
    if (bytes + size > CHUNK_BYTES) {
      chunks.push(current);
      current = "";
      bytes = 0;
    }
    current += char;
    bytes += size;
  }
  if (current || !chunks.length) chunks.push(current);
  if (chunks.length > MAX_CHUNKS) throw new Error("草稿超过本机安全存储上限");
  return chunks;
}

function marker(value: string | null): { slot: Slot; count: number } | null {
  if (!value?.startsWith(MARKER)) return null;
  const match = /^([ab]):(\d+)$/.exec(value.slice(MARKER.length));
  if (!match) throw new Error("本机草稿索引已损坏");
  const count = Number(match[2]);
  if (count < 1 || count > MAX_CHUNKS) throw new Error("本机草稿索引已损坏");
  return { slot: match[1] as Slot, count };
}

function partKey(key: string, slot: Slot, index: number) {
  return `${key}-${slot}-${index}`;
}

async function removeSlot(key: string, slot: Slot, start = 0) {
  for (let i = start; i < MAX_CHUNKS; i++) {
    await SecureStore.deleteItemAsync(partKey(key, slot, i));
  }
}

export async function readSecureDraft(key: string): Promise<string | null> {
  const value = await SecureStore.getItemAsync(key);
  const index = marker(value);
  if (!index) return value;
  const chunks: string[] = [];
  for (let i = 0; i < index.count; i++) {
    const part = await SecureStore.getItemAsync(partKey(key, index.slot, i));
    if (part === null) throw new Error("本机草稿内容不完整");
    chunks.push(part);
  }
  return chunks.join("");
}

export async function writeSecureDraft(key: string, value: string): Promise<void> {
  const chunks = splitDraft(value);
  const previous = marker(await SecureStore.getItemAsync(key));
  if (chunks.length === 1) {
    await SecureStore.setItemAsync(key, value);
    if (previous) {
      await removeSlot(key, "a");
      await removeSlot(key, "b");
    }
    return;
  }
  const slot: Slot = previous?.slot === "a" ? "b" : "a";
  for (let i = 0; i < chunks.length; i++) {
    await SecureStore.setItemAsync(partKey(key, slot, i), chunks[i]);
  }
  await SecureStore.setItemAsync(key, `${MARKER}${slot}:${chunks.length}`);
  await removeSlot(key, slot, chunks.length);
  if (previous) await removeSlot(key, previous.slot);
}

export async function deleteSecureDraft(key: string): Promise<void> {
  await SecureStore.deleteItemAsync(key);
  await removeSlot(key, "a");
  await removeSlot(key, "b");
}
