const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

const source = fs.readFileSync(path.join(__dirname, "../src/api.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

class FormDataStub {
  parts = [];
  append(name, value) { this.parts.push([name, value]); }
}

test("text and photo punch retries retain their operation key and photo date", async () => {
  const calls = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData: FormDataStub, AbortController,
    setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return { PunchLogDtoSchema: { parse: value => value } };
      if (name === "zod") return require("zod");
      throw new Error(`unexpected dependency: ${name}`);
    },
    async fetch(url, options) {
      calls.push({ url, key: options.headers.get("Idempotency-Key"),
        type: options.headers.get("Content-Type"), body: options.body });
      return { ok: true, status: 200, text: async () => "null" };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  await api.punch("habit-1", "2026-11-03", "text-key");
  await api.punch("habit-1", "2026-11-03", "text-key");
  await api.punchWithPhoto("habit-1", "2026-11-03", "file:///punch.jpg", "photo-key");
  await api.punchWithPhoto("habit-1", "2026-11-03", "file:///punch.jpg", "photo-key");
  assert.deepEqual(calls.map(call => call.key), ["text-key", "text-key", "photo-key", "photo-key"]);
  assert.deepEqual(JSON.parse(calls[0].body), { date: "2026-11-03" });
  assert.equal(calls[2].type, null);
  assert.deepEqual(calls[2].body.parts.map(([name]) => name), ["date", "photo"]);
  assert.equal(calls[2].body.parts[0][1], "2026-11-03");
  assert.equal(calls[3].body.parts[1][1].uri, "file:///punch.jpg");
});

test("a lost photo response can retry with the original file, date and request key", async () => {
  const calls = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData: FormDataStub, AbortController,
    setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return { PunchLogDtoSchema: { parse: value => value } };
      if (name === "zod") return require("zod");
      throw new Error(`unexpected dependency: ${name}`);
    },
    async fetch(_url, options) {
      calls.push({ key: options.headers.get("Idempotency-Key"), parts: options.body.parts });
      if (calls.length === 1) throw new Error("response lost after server commit");
      return { ok: true, status: 200, text: async () => "null" };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  const send = () => api.punchWithPhoto("habit-1", "2026-11-03", "file:///private/photo.jpg", "photo-key");
  await assert.rejects(send(), /无法连接服务器/);
  await send();
  assert.deepEqual(calls.map(call => call.key), ["photo-key", "photo-key"]);
  assert.deepEqual(calls.map(call => call.parts[0][1]), ["2026-11-03", "2026-11-03"]);
  assert.deepEqual(calls.map(call => call.parts[1][1].uri), [
    "file:///private/photo.jpg", "file:///private/photo.jpg",
  ]);
});
