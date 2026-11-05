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

test("undo action posts to the owned action and parses the result", async () => {
  const requests = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData, AbortController, setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return {
        ApiRoutes: { agentActions: "/v1/agent/actions" },
        AgentActionSchema: z.object({ proposalId: z.string(), result: z.string(), undoable: z.boolean() }),
      };
      if (name === "zod") return require("zod");
      throw new Error(name);
    },
    async fetch(url, options) {
      requests.push({ url, method: options.method, authorization: options.headers.get("Authorization") });
      return { ok: true, status: 200,
        text: async () => JSON.stringify({ proposalId: "a/b", result: "undone", undoable: false }) };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  const action = await api.undoAction("a/b");
  assert.equal(action.result, "undone");
  assert.deepEqual(requests, [{ url: "https://api.example.com/v1/agent/actions/a%2Fb/undo",
    method: "POST", authorization: "Bearer token" }]);
});
