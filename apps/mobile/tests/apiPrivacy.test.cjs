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

test("privacy updates carry the independent followup authorization", async () => {
  const requests = [];
  const sandboxModule = { exports: {} };
  vm.runInNewContext(compiled, {
    module: sandboxModule,
    exports: sandboxModule.exports,
    Headers,
    FormData,
    AbortController,
    setTimeout,
    clearTimeout,
    require(name) {
      if (name === "@today/contracts") return {
        ApiRoutes: { profilePrivacy: "/v1/profile/privacy" },
        PrivacySettingsSchema: { parse: value => value },
        PrivacySettingsRequestSchema: { parse: value => value },
      };
      if (name === "zod") return require("zod");
      throw new Error(`Unexpected dependency: ${name}`);
    },
    async fetch(url, options) {
      requests.push({ url, method: options.method, body: JSON.parse(options.body) });
      return { ok: true, status: 200, text: async () => JSON.stringify({
        userId: "owner", chatMemoryEnabled: false, voiceRetention: "delete_on_success",
        followupEnabled: true, version: 4, updatedAt: "2026-11-18T00:00:00Z",
      }) };
    },
  });
  const api = new sandboxModule.exports.TodayApi("https://api.example.com", "token");

  await api.updatePrivacy({ version: 3, chatMemoryEnabled: false,
    voiceRetention: "delete_on_success", followupEnabled: true });

  assert.deepEqual(requests, [{
    url: "https://api.example.com/v1/profile/privacy",
    method: "PATCH",
    body: { version: 3, chatMemoryEnabled: false,
      voiceRetention: "delete_on_success", followupEnabled: true },
  }]);
});
