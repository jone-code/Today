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
  let failWrite = false;
  let waitForWrite = null;
  const secure = {
    getItemAsync: async key => data.get(key) ?? null,
    setItemAsync: async (key, value) => {
      if (waitForWrite) {
        const wait = waitForWrite;
        waitForWrite = null;
        await wait;
      }
      if (failWrite) throw new Error("storage unavailable");
      assert.ok(Buffer.byteLength(value, "utf8") <= 2048);
      data.set(key, value);
    },
    deleteItemAsync: async key => { data.delete(key); },
  };
  function reopen() {
    const drafts = { exports: {} };
    vm.runInNewContext(compile("secureDraft.ts"), {
      module: drafts, exports: drafts.exports,
      require(name) { assert.equal(name, "expo-secure-store"); return secure; },
    });
    const actions = { exports: {} };
    vm.runInNewContext(compile("actionDraft.ts"), {
      module: actions, exports: actions.exports,
      require(name) {
        if (name === "./secureDraft") return drafts.exports;
        if (name === "zod") return require("zod");
        throw new Error(`unexpected dependency: ${name}`);
      },
    });
    return actions.exports;
  }
  return { data, reopen, setFailWrite(value) { failWrite = value; },
    pauseNextWrite(promise) { waitForWrite = promise; } };
}

const todoId = "a5b674a9-cb65-443a-aa66-a1da02075229";
const habitId = "269dfceb-c12a-4484-aa05-1cc21787eaba";
const reminderId = "c2429189-c3c2-481e-ae83-17d27b734e0b";

test("a restart restores all pending create requests under the same account", async () => {
  const device = harness();
  const first = device.reopen();
  const key = first.actionDraftKey("https://api.example.com", "user-1");
  const draft = {
    todo: { title: "read", requestId: todoId },
    habit: { title: "walk", description: "after work", requestId: habitId },
    reminder: { title: "rest", message: "pause", time: "09:00", timezone: "Asia/Shanghai", requestId: reminderId },
    photo: { requestId: todoId, habitId, date: "2026-11-03" },
  };
  await first.writeActionDraft(key, draft);
  const reopened = device.reopen();
  assert.deepEqual(JSON.parse(JSON.stringify(await reopened.readActionDraft(key))), draft);
  assert.equal(await reopened.readActionDraft(reopened.actionDraftKey("https://api.example.com", "user-2")), null);
  assert.equal(await reopened.readActionDraft(reopened.actionDraftKey("https://other.example.com", "user-1")), null);
  assert.notEqual(key, reopened.actionDraftKey("https://api.example.com", "user-2"));
});

test("cancelling every form removes the saved draft, but failed writes leave the prior request intact", async () => {
  const device = harness();
  const actions = device.reopen();
  const key = actions.actionDraftKey("https://api.example.com", "user-1");
  await actions.writeActionDraft(key, { todo: { title: "read", requestId: todoId }, habit: null, reminder: null });
  device.setFailWrite(true);
  await assert.rejects(actions.writeActionDraft(key, {
    todo: { title: "write", requestId: habitId }, habit: null, reminder: null,
  }));
  assert.equal((await device.reopen().readActionDraft(key)).todo.requestId, todoId);
  device.setFailWrite(false);
  await actions.writeActionDraft(key, { todo: null, habit: null, reminder: null, photo: null });
  assert.equal(await device.reopen().readActionDraft(key), null);
  assert.equal(device.data.size, 0);
});

test("a photo-only draft survives restart and is not confused with another account", async () => {
  const device = harness();
  const actions = device.reopen();
  const key = actions.actionDraftKey("https://api.example.com", "user-1");
  const photo = { requestId: todoId, habitId, date: "2026-11-03" };
  await actions.writeActionDraft(key, { todo: null, habit: null, reminder: null, photo });
  assert.equal((await device.reopen().readActionDraft(key)).photo.requestId, todoId);
  assert.equal(await device.reopen().readActionDraft(actions.actionDraftKey("https://api.example.com", "user-2")), null);
  device.setFailWrite(true);
  await assert.rejects(actions.writeActionDraft(key, { todo: null, habit: null, reminder: null,
    photo: { ...photo, requestId: reminderId } }));
  assert.equal((await device.reopen().readActionDraft(key)).photo.requestId, todoId);
});

test("clearing waits for an in-flight draft write so it cannot resurrect after deletion", async () => {
  const device = harness();
  const actions = device.reopen();
  const key = actions.actionDraftKey("https://api.example.com", "user-1");
  let release;
  device.pauseNextWrite(new Promise(resolve => { release = resolve; }));
  const pending = actions.writeActionDraft(key, {
    todo: null, habit: null, reminder: null,
    photo: { requestId: todoId, habitId, date: "2026-11-03" },
  });
  const clearing = actions.deleteActionDraftAfterWrites(key, pending);
  release();
  await clearing;
  assert.equal(await device.reopen().readActionDraft(key), null);
});

test("a malformed saved request cannot silently receive a new id", async () => {
  const device = harness();
  const actions = device.reopen();
  const key = actions.actionDraftKey("https://api.example.com", "user-1");
  device.data.set(key, JSON.stringify({ todo: { title: "read" }, habit: null, reminder: null }));
  await assert.rejects(actions.readActionDraft(key), /草稿无法读取/);
  await actions.deleteActionDraft(key);
  assert.equal(await actions.readActionDraft(key), null);
});
