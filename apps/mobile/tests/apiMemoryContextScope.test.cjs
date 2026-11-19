const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

const compiled = ts.transpileModule(
  fs.readFileSync(path.join(__dirname, "../src/api.ts"), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } },
).outputText;

test("memory context scope updates carry the optimistic version", async () => {
  const calls = [];
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module,
    exports: module.exports,
    Headers,
    FormData,
    AbortController,
    setTimeout,
    clearTimeout,
    require(name) {
      if (name === "@today/contracts") {
        return {
          ApiRoutes: { memories: "/v1/memories" },
          MemoryContextScopeRequestSchema: { parse: value => value },
          MemoryDtoSchema: { parse: value => value },
        };
      }
      if (name === "zod") return require("zod");
      throw new Error(name);
    },
    async fetch(url, options) {
      calls.push({ url, method: options.method, body: options.body });
      return { ok: true, status: 200, text: async () => JSON.stringify({ id: "memory-1" }) };
    },
  });

  const api = new module.exports.TodayApi("https://api.example.com", "token");
  await api.updateMemoryContextScope("memory-1", "disabled", "2026-11-18T00:00:00Z");

  assert.deepEqual(calls, [{
    url: "https://api.example.com/v1/memories/memory-1/context-scope",
    method: "PATCH",
    body: JSON.stringify({ scope: "disabled", expectedUpdatedAt: "2026-11-18T00:00:00Z" }),
  }]);
});
