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

test("editing a followup sends the original snapshot for conflict detection", async () => {
  const requests = [];
  const sandboxModule = { exports: {} };
  vm.runInNewContext(compiled, {
    module: sandboxModule, exports: sandboxModule.exports, Headers, FormData, AbortController,
    setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return {
        ApiRoutes: { agentFollowupTopic: "/v1/agent/followups/topics/:id" },
        FollowupTopicSchema: z.any(),
        FollowupTopicUpdateSchema: z.object({ reason: z.string(), nextDate: z.string().optional(),
          expectedReason: z.string(), expectedNextEligibleAt: z.string() }),
      };
      if (name === "zod") return require("zod");
      throw new Error(`Unexpected dependency: ${name}`);
    },
    async fetch(url, options) {
      requests.push({ url, method: options.method, body: JSON.parse(options.body) });
      return { ok: true, status: 200, text: async () => JSON.stringify({ id: "topic-1", reason: "面试结果" }) };
    },
  });
  const api = new sandboxModule.exports.TodayApi("https://api.example.com", "token");

  await api.updateFollowupTopic("topic-1", {
    reason: "面试结果", nextDate: "2026-11-10",
    expectedReason: "准备面试", expectedNextEligibleAt: "2026-11-08T09:00:00Z",
  });

  assert.deepEqual(requests, [{
    url: "https://api.example.com/v1/agent/followups/topics/topic-1",
    method: "PATCH",
    body: { reason: "面试结果", nextDate: "2026-11-10",
      expectedReason: "准备面试", expectedNextEligibleAt: "2026-11-08T09:00:00Z" },
  }]);
});
