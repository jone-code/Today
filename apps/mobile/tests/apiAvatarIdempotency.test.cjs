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

test("avatar upload after a lost response retries with the same picture and key", async () => {
  const calls = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData: FormDataStub, AbortController,
    setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return { AgentProfileSchema: { parse: value => value } };
      if (name === "zod") return require("zod");
      throw new Error(`unexpected dependency: ${name}`);
    },
    async fetch(url, options) {
      calls.push({ url, key: options.headers.get("Idempotency-Key"),
        type: options.headers.get("Content-Type"), parts: options.body.parts });
      if (calls.length === 1) throw new Error("response lost after server commit");
      return { ok: true, status: 200, text: async () => "null" };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  const send = () => api.uploadAvatar("file:///private/portrait.jpg", "stable-key");
  await assert.rejects(send(), /无法连接服务器/);
  await send();
  assert.deepEqual(calls.map(call => call.key), ["stable-key", "stable-key"]);
  assert.deepEqual(calls.map(call => call.parts[0][1].uri), [
    "file:///private/portrait.jpg", "file:///private/portrait.jpg",
  ]);
  assert.deepEqual(calls.map(call => call.type), [null, null]);
  assert.deepEqual(calls.map(call => call.url), [
    "https://api.example.com/v1/agent/avatar", "https://api.example.com/v1/agent/avatar",
  ]);
});
