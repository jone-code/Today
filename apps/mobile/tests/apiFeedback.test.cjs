const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");
const { z } = require("zod");

const source = fs.readFileSync(path.join(__dirname, "../src/api.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

test("feedback submission sends a stable request id and no conversation data", async () => {
  const requests = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData, AbortController, setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return {
        ApiRoutes: { feedback: "/v1/feedback" },
        FeedbackSchema: z.any(),
        FeedbackCreateRequestSchema: z.object({ kind: z.enum(["issue", "idea"]),
          body: z.string().trim().min(1).max(1000) }),
      };
      if (name === "zod") return require("zod");
      throw new Error(name);
    },
    async fetch(url, options) {
      requests.push({ url, method: options.method, key: options.headers.get("Idempotency-Key"),
        authorization: options.headers.get("Authorization"), body: JSON.parse(options.body) });
      return { ok: true, status: 200, text: async () => JSON.stringify({ id: "feedback-1" }) };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  await api.submitFeedback({ kind: "issue", body: "  页面打不开  " }, "request-1");
  assert.deepEqual(requests, [{ url: "https://api.example.com/v1/feedback", method: "POST",
    key: "request-1", authorization: "Bearer token", body: { kind: "issue", body: "页面打不开" } }]);
});
