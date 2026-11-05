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
  let failWrite = false;
  const secure = {
    getItemAsync: async key => data.get(key) ?? null,
    setItemAsync: async (key, value) => {
      if (failWrite) throw new Error("secure storage unavailable");
      data.set(key, value);
    },
    deleteItemAsync: async key => { data.delete(key); },
  };
  function reopen() {
    const storage = { exports: {} };
    vm.runInNewContext(compile("secureDraft.ts"), { module: storage, exports: storage.exports,
      require(name) { assert.equal(name, "expo-secure-store"); return secure; } });
    const drafts = { exports: {} };
    vm.runInNewContext(compile("chatDraft.ts"), { module: drafts, exports: drafts.exports,
      require(name) {
        if (name === "./secureDraft") return storage.exports;
        if (name === "zod") return require("zod");
        throw new Error(name);
      } });
    return drafts.exports;
  }
  return { data, reopen, setFailWrite(value) { failWrite = value; } };
}

const conversationId = "a5b674a9-cb65-443a-aa66-a1da02075229";

test("pending chat message survives restart with its original request id", async () => {
  const local = device();
  const first = local.reopen();
  await first.writeChatDraft("api-a:user-a", {
    text: "Hello", id: "stable-id", conversationId,
  });
  const restored = await local.reopen().readChatDraft("api-a:user-a");
  assert.deepEqual(JSON.parse(JSON.stringify(restored)), {
    text: "Hello", id: "stable-id", conversationId,
  });
  assert.equal(await first.readChatDraft("api-a:user-b"), null);
  assert.equal(await first.readChatDraft("api-b:user-a"), null);
});

test("malformed drafts cannot silently become a new request", async () => {
  const local = device();
  const drafts = local.reopen();
  for (const malformed of ["not json", "{}", '{"text":"hello","id":123,"conversationId":null}',
    '{"text":"hello","id":null,"conversationId":"a5b674a9-cb65-443a-aa66-a1da02075229"}']) {
    local.data.set("damaged", malformed);
    await assert.rejects(drafts.readChatDraft("damaged"), /本机对话草稿无法读取/);
  }
});

test("failed safe-storage write leaves the previous id intact for recovery", async () => {
  const local = device();
  const drafts = local.reopen();
  await drafts.writeChatDraft("chat", { text: "original", id: "first", conversationId: null });
  local.setFailWrite(true);
  await assert.rejects(drafts.writeChatDraft("chat", {
    text: "changed", id: "second", conversationId: null,
  }), /secure storage unavailable/);
  assert.equal((await local.reopen().readChatDraft("chat")).id, "first");
  local.setFailWrite(false);
  await drafts.writeChatDraft("chat", { text: "changed", id: "second", conversationId: null });
  assert.equal((await local.reopen().readChatDraft("chat")).id, "second");
});
