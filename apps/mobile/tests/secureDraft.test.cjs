const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

const source = fs.readFileSync(path.join(__dirname, "../src/secureDraft.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

function harness() {
  const data = new Map();
  let failAt = null;
  const secureStore = {
    async getItemAsync(key) { return data.get(key) ?? null; },
    async setItemAsync(key, value) {
      if (key === failAt) throw new Error("device storage failed");
      assert.ok(Buffer.byteLength(value, "utf8") <= 2048, `oversized SecureStore value: ${key}`);
      data.set(key, value);
    },
    async deleteItemAsync(key) { data.delete(key); },
  };
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports,
    require(name) {
      assert.equal(name, "expo-secure-store");
      return secureStore;
    },
  });
  return { ...module.exports, data, failOn(key) { failAt = key; } };
}

test("splits long Unicode text without breaking code points or exceeding the device limit", async () => {
  const store = harness();
  const value = "今😀".repeat(4000);
  const parts = store.splitDraft(value);
  assert.equal(parts.join(""), value);
  assert.ok(parts.length > 1);
  await store.writeSecureDraft("draft-key", value);
  assert.equal(await store.readSecureDraft("draft-key"), value);
});

test("reads legacy single-value drafts and clears unused slots on replacement", async () => {
  const store = harness();
  store.data.set("draft-key", "legacy draft");
  assert.equal(await store.readSecureDraft("draft-key"), "legacy draft");
  await store.writeSecureDraft("draft-key", "中".repeat(2000));
  await store.writeSecureDraft("draft-key", "short draft");
  assert.equal(await store.readSecureDraft("draft-key"), "short draft");
  assert.deepEqual([...store.data.keys()], ["draft-key"]);
});

test("keeps the previous draft if writing a new slot fails before index commit", async () => {
  const store = harness();
  const first = "旧".repeat(2000);
  await store.writeSecureDraft("draft-key", first);
  store.failOn("draft-key-b-1");
  await assert.rejects(store.writeSecureDraft("draft-key", "新".repeat(2000)));
  assert.equal(await store.readSecureDraft("draft-key"), first);
});

test("deletes the index and both slots", async () => {
  const store = harness();
  await store.writeSecureDraft("draft-key", "中".repeat(2000));
  await store.deleteSecureDraft("draft-key");
  assert.equal(await store.readSecureDraft("draft-key"), null);
  assert.equal(store.data.size, 0);
  assert.throws(() => store.splitDraft("中".repeat(10000)), /上限/);
});
