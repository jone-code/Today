#!/usr/bin/env node
// Run only against a disposable Today database and media directory.
import { randomUUID } from "node:crypto";
import { deflateSync } from "node:zlib";

const base = process.env.API_BASE_URL || "http://127.0.0.1:3101";
if (process.env.AVATAR_E2E_DISPOSABLE !== "1") {
  throw new Error("Set AVATAR_E2E_DISPOSABLE=1 only for a disposable database and media directory");
}
function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const name = Buffer.from(type);
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(Buffer.concat([name, data])));
  return Buffer.concat([length, name, data, checksum]);
}
const header = Buffer.alloc(13);
header.writeUInt32BE(1, 0); header.writeUInt32BE(1, 4);
header[8] = 8; header[9] = 2;
const image = Buffer.concat([
  Buffer.from("89504e470d0a1a0a", "hex"), pngChunk("IHDR", header),
  pngChunk("IDAT", deflateSync(Buffer.from([0, 10, 120, 190]))), pngChunk("IEND", Buffer.alloc(0)),
]);

async function register() {
  const response = await fetch(`${base}/v1/auth/register`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: `avatar.${randomUUID()}@example.com`,
      password: `Smoke-${randomUUID()}`, displayName: "Avatar test" }),
  });
  if (!response.ok) throw new Error(`registration failed: ${response.status}`);
  return response.json();
}

async function upload(token, key, bytes = image) {
  const form = new FormData();
  form.append("avatar", new Blob([bytes], { type: "image/png" }), "avatar.png");
  const response = await fetch(`${base}/v1/agent/avatar`, {
    method: "POST", headers: { authorization: `Bearer ${token}`,
      ...(key ? { "Idempotency-Key": key } : {}) }, body: form,
  });
  return response;
}

const first = await register();
const other = await register();
const key = randomUUID();
const [oneResponse, replayResponse] = await Promise.all([upload(first.token, key), upload(first.token, key)]);
if (oneResponse.status !== 200 || replayResponse.status !== 200) {
  throw new Error(`idempotent uploads failed: ${oneResponse.status}, ${replayResponse.status}`);
}
const one = await oneResponse.json();
const replay = await replayResponse.json();
if (!one.avatarMediaId || one.avatarMediaId !== replay.avatarMediaId || one.version !== replay.version) {
  throw new Error("same-key uploads did not return the same media and version");
}
const mismatch = await upload(first.token, key, Buffer.concat([image, Buffer.from([1])]));
if (mismatch.status !== 409) {
  throw new Error(`same key with different bytes should conflict, got ${mismatch.status}`);
}
const secondResponse = await upload(first.token, randomUUID());
if (secondResponse.status !== 200) {
  throw new Error(`new-key avatar upload failed: ${secondResponse.status} ${await secondResponse.text()}`);
}
const two = await secondResponse.json();
if (one.avatarMediaId === two.avatarMediaId || two.version !== one.version + 1) {
  throw new Error("new-key upload did not create one replacement");
}
const limited = await upload(first.token, randomUUID());
if (limited.status !== 429) {
  throw new Error(`third distinct upload should exhaust the two-upload quota, got ${limited.status}`);
}
const own = await fetch(`${base}/v1/agent/avatar`, { headers: { authorization: `Bearer ${first.token}` } });
const denied = await fetch(`${base}/v1/agent/avatar`, { headers: { authorization: `Bearer ${other.token}` } });
if (own.status !== 200 || !own.headers.get("cache-control")?.includes("no-store") || denied.status !== 404) {
  throw new Error(`avatar access or caching policy incorrect: own=${own.status} other=${denied.status}`);
}
console.log(`avatar replay, conflict, two-upload quota, replacement and access passed userId=${first.user.id}`);
