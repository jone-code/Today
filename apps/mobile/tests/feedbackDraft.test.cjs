const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

function compile(file) {
  return ts.transpileModule(fs.readFileSync(path.join(__dirname, "../src", file), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
}

function device() {
  const data = new Map();
  let fail = false;
  const secure = {
    getItemAsync: async key => data.get(key) ?? null,
    setItemAsync: async (key, value) => {
      if (fail) throw new Error("storage failed");
      data.set(key, value);
    },
    deleteItemAsync: async key => { data.delete(key); },
  };
  function reopen() {
    const base = { exports: {} };
    vm.runInNewContext(compile("secureDraft.ts"), { module: base, exports: base.exports,
      require(name) { assert.equal(name, "expo-secure-store"); return secure; } });
    const result = { exports: {} };
    vm.runInNewContext(compile("feedbackDraft.ts"), { module: result, exports: result.exports,
      require(name) {
        if (name === "./secureDraft") return base.exports;
        if (name === "zod") return require("zod");
        throw new Error(name);
      } });
    return result.exports;
  }
  return { data, reopen, failWrites(value) { fail = value; } };
}

const requestId = "a5b674a9-cb65-443a-aa66-a1da02075229";

test("a failed feedback submission can recover its original request after restart", async () => {
  const storage = device();
  const draft = storage.reopen();
  const key = draft.feedbackDraftKey("https://api.example.com", "user-1");
  await draft.writeFeedbackDraft(key, { requestId, kind: "issue", body: "页面打不开" });
  const restored = await storage.reopen().readFeedbackDraft(key);
  assert.deepEqual(JSON.parse(JSON.stringify(restored)), { requestId, kind: "issue", body: "页面打不开" });
  assert.equal(await draft.readFeedbackDraft(draft.feedbackDraftKey("https://api.example.com", "user-2")), null);
  assert.equal(await draft.readFeedbackDraft(draft.feedbackDraftKey("https://other.example.com", "user-1")), null);
  storage.failWrites(true);
  await assert.rejects(draft.writeFeedbackDraft(key,
    { requestId: "edb6614a-c43d-4958-bdad-3baadbf8b5f6", kind: "idea", body: "搜索" }));
  assert.equal((await draft.readFeedbackDraft(key)).requestId, requestId);
  storage.failWrites(false);
  await draft.deleteFeedbackDraft(key);
  assert.equal(await draft.readFeedbackDraft(key), null);
});

test("malformed feedback drafts cannot silently get a new request id", async () => {
  const storage = device();
  const draft = storage.reopen();
  const key = draft.feedbackDraftKey("https://api.example.com", "user-1");
  storage.data.set(key, JSON.stringify({ requestId: "invalid", kind: "issue", body: "页面打不开" }));
  await assert.rejects(draft.readFeedbackDraft(key), /无法读取/);
});
