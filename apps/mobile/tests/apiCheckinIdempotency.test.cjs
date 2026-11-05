const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../src/api.ts"), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

test("a lost checkin response retries with the same payload and request key", async () => {
  const calls = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData, AbortController, setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return { ApiRoutes: { checkins: "/v1/checkins" },
        CheckinSubmitResultSchema: { parse: value => value } };
      if (name === "zod") return require("zod");
      throw new Error(name);
    },
    async fetch(_url, options) {
      calls.push({ key: options.headers.get("Idempotency-Key"), body: options.body });
      if (calls.length === 1) throw new Error("response lost after commit");
      return { ok: true, status: 200, text: async () => "null" };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  const send = () => api.submitCheckin("今天完成报告", undefined, "stable-checkin-key");
  await assert.rejects(send(), /无法连接服务器/);
  await send();
  assert.deepEqual(calls.map(call => call.key), ["stable-checkin-key", "stable-checkin-key"]);
  assert.equal(calls[0].body, calls[1].body);
});
