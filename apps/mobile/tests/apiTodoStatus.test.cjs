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

test("setting a todo status sends an explicit, repeatable target state", async () => {
  const requests = [];
  const sandboxModule = { exports: {} };
  vm.runInNewContext(compiled, {
    module: sandboxModule, exports: sandboxModule.exports, Headers, FormData, AbortController,
    setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return { ApiRoutes: { todos: "/v1/todos" } };
      if (name === "zod") return require("zod");
      throw new Error(`Unexpected dependency: ${name}`);
    },
    async fetch(url, options) {
      requests.push({ url, method: options.method, body: JSON.parse(options.body) });
      return { ok: true, status: 200, text: async () => "null" };
    },
  });
  const api = new sandboxModule.exports.TodayApi("https://api.example.com", "token");

  await api.setTodoStatus("todo-1", "done");
  await api.setTodoStatus("todo-1", "done");

  assert.deepEqual(requests, [
    { url: "https://api.example.com/v1/todos/todo-1", method: "PUT", body: { status: "done" } },
    { url: "https://api.example.com/v1/todos/todo-1", method: "PUT", body: { status: "done" } },
  ]);
});
