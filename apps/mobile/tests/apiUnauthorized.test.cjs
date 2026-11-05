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

function apiModule(fetch) {
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, fetch, Headers, FormData, AbortController,
    setTimeout, clearTimeout,
    require(name) {
      if (name === "@today/contracts") return {};
      if (name === "zod") return require("zod");
      throw new Error(`Unexpected dependency: ${name}`);
    },
  });
  return module.exports;
}

test("an authenticated 401 signals re-login and retains the HTTP error", async () => {
  let authorization;
  const { TodayApi } = apiModule(async (_, options) => {
    authorization = options.headers.get("Authorization");
    return { ok: false, status: 401, json: async () => ({}) };
  });
  let unauthorized = 0;
  const api = new TodayApi("https://api.example.com", "expired-token", () => unauthorized++);

  await assert.rejects(api.request("/v1/auth/me", { parse: value => value }), error => error.status === 401);
  assert.equal(authorization, "Bearer expired-token");
  assert.equal(unauthorized, 1);
});

test("a login failure without a session does not trigger session expiry", async () => {
  const { TodayApi } = apiModule(async () => ({ ok: false, status: 401, json: async () => ({}) }));
  let unauthorized = 0;
  const api = new TodayApi("https://api.example.com", null, () => unauthorized++);

  await assert.rejects(api.request("/v1/auth/login", { parse: value => value }), error => error.status === 401);
  assert.equal(unauthorized, 0);
});
