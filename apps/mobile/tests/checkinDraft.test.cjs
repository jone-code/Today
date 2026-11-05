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

function harness() {
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
    vm.runInNewContext(compile("checkinDraft.ts"), { module: result, exports: result.exports,
      require(name) {
        if (name === "./secureDraft") return base.exports;
        if (name === "zod") return require("zod");
        throw new Error(name);
      } });
    return result.exports;
  }
  return { data, reopen, setFailure(value) { fail = value; } };
}

const requestId = "a5b674a9-cb65-443a-aa66-a1da02075229";
const otherId = "269dfceb-c12a-4484-aa05-1cc21787eaba";

test("a restart restores a stable checkin request only for the same account and server", async () => {
  const device = harness();
  const store = device.reopen();
  const key = store.checkinDraftKey("https://api.example.com", "user-1");
  const draft = { text: "今天完成报告", original: "", requestId };
  await store.writeCheckinDraft(key, draft);
  const reopened = device.reopen();
  assert.deepEqual(JSON.parse(JSON.stringify(await reopened.readCheckinDraft(key))), draft);
  assert.equal(await reopened.readCheckinDraft(reopened.checkinDraftKey("https://api.example.com", "user-2")), null);
  assert.equal(await reopened.readCheckinDraft(reopened.checkinDraftKey("https://other.example.com", "user-1")), null);
});

test("a failed draft update retains the old request id and legacy drafts remain readable", async () => {
  const device = harness();
  const store = device.reopen();
  const key = store.checkinDraftKey("https://api.example.com", "user-1");
  device.data.set(key, JSON.stringify({ text: "旧记录", original: "" }));
  assert.equal((await store.readCheckinDraft(key)).requestId, undefined);
  await store.writeCheckinDraft(key, { text: "旧记录", original: "", requestId });
  device.setFailure(true);
  await assert.rejects(store.writeCheckinDraft(key, { text: "新记录", original: "", requestId: otherId }));
  assert.equal((await device.reopen().readCheckinDraft(key)).requestId, requestId);
  device.setFailure(false);
  await store.writeCheckinDraft(key, null);
  assert.equal(await store.readCheckinDraft(key), null);
});

test("corrupted drafts cannot silently become a new request", async () => {
  const device = harness();
  const store = device.reopen();
  const key = store.checkinDraftKey("https://api.example.com", "user-1");
  device.data.set(key, JSON.stringify({ text: "今天", original: "", requestId: "invalid" }));
  await assert.rejects(store.readCheckinDraft(key), /无法读取/);
});
