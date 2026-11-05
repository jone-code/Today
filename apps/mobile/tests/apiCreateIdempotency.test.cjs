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

test("create retries send the same request key for todos, habits and reminders", async () => {
  const calls = [];
  const module = { exports: {} };
  const schema = { parse: value => value };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Headers, FormData, AbortController, setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return {
        ApiRoutes: { todos: "/v1/todos" },
        PunchHabitCreateInputSchema: schema, PunchHabitDtoSchema: schema,
        ReminderCreateInputSchema: schema, ReminderDtoSchema: schema,
      };
      if (name === "zod") return require("zod");
      throw new Error(`Unexpected dependency: ${name}`);
    },
    async fetch(url, options) {
      calls.push({ url, key: options.headers.get("Idempotency-Key"), body: JSON.parse(options.body) });
      return { ok: true, status: 200, text: async () => "null" };
    },
  });
  const api = new module.exports.TodayApi("https://api.example.com", "token");
  await api.createTodo("read", "todo-key");
  await api.createTodo("read", "todo-key");
  await api.createHabit({ title: "walk" }, "habit-key");
  await api.createReminder({ title: "rest" }, "reminder-key");
  assert.deepEqual(calls.map(call => call.key), ["todo-key", "todo-key", "habit-key", "reminder-key"]);
  assert.deepEqual(calls.map(call => call.url), [
    "https://api.example.com/v1/todos", "https://api.example.com/v1/todos",
    "https://api.example.com/v1/punch/habits", "https://api.example.com/v1/reminders",
  ]);
});
