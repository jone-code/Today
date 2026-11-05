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
  const files = new Map();
  const directories = new Set();
  const secureValues = new Map();
  let failCopy = false;
  let failSecure = false;
  const disk = {
    documentDirectory: "file:///private/",
    makeDirectoryAsync: async uri => { directories.add(uri); },
    copyAsync: async ({ from, to }) => {
      if (failCopy) throw new Error("disk full");
      files.set(to, files.get(from));
    },
    getInfoAsync: async uri => files.has(uri)
      ? { exists: true, isDirectory: false, size: files.get(uri)?.length || 0 }
      : { exists: directories.has(uri), isDirectory: directories.has(uri) },
    deleteAsync: async uri => { files.delete(uri); },
    readDirectoryAsync: async uri => [...files.keys()].filter(key => key.startsWith(uri)).map(key => key.slice(uri.length)),
  };
  const secure = {
    getItemAsync: async key => secureValues.get(key) ?? null,
    setItemAsync: async (key, value) => {
      if (failSecure) throw new Error("secure storage unavailable");
      secureValues.set(key, value);
    },
    deleteItemAsync: async key => { secureValues.delete(key); },
  };
  function reopen() {
    const storage = { exports: {} };
    vm.runInNewContext(compile("secureDraft.ts"), { module: storage, exports: storage.exports,
      require(name) { assert.equal(name, "expo-secure-store"); return secure; } });
    const drafts = { exports: {} };
    vm.runInNewContext(compile("avatarDraft.ts"), { module: drafts, exports: drafts.exports,
      require(name) {
        if (name === "expo-file-system") return disk;
        if (name === "expo-crypto") return { CryptoDigestAlgorithm: { SHA256: "SHA256" },
          digestStringAsync: async (_algorithm, value) => Buffer.from(value).toString("hex") };
        if (name === "./secureDraft") return storage.exports;
        throw new Error(name);
      } });
    return drafts.exports;
  }
  return { files, secureValues, reopen, failCopy(value) { failCopy = value; },
    failSecure(value) { failSecure = value; } };
}

const firstId = "a5b674a9-cb65-443a-aa66-a1da02075229";
const secondId = "269dfceb-c12a-4484-aa05-1cc21787eaba";

test("avatar upload draft survives restart and is scoped to account and server", async () => {
  const local = device();
  local.files.set("file:///temporary.jpg", "jpeg bytes");
  const drafts = local.reopen();
  const key = drafts.avatarDraftKey("https://api.example.com", "user-1");
  const uri = await drafts.saveAvatarDraft(key, firstId, "file:///temporary.jpg");
  await drafts.writeAvatarDraft(key, firstId);
  const restarted = local.reopen();
  assert.equal(await restarted.readAvatarDraft(key), firstId);
  assert.equal(await restarted.avatarDraftExists(key, firstId), true);
  assert.equal(await restarted.avatarDraftUri(key, firstId), uri);
  assert.equal(await restarted.readAvatarDraft(restarted.avatarDraftKey("https://api.example.com", "user-2")), null);
  assert.equal(await restarted.readAvatarDraft(restarted.avatarDraftKey("https://other.example.com", "user-1")), null);
  assert.equal(await restarted.avatarDraftExists(restarted.avatarDraftKey("https://api.example.com", "user-2"), firstId), false);
});

test("missing image and failed writes never become ready uploads", async () => {
  const local = device();
  const drafts = local.reopen();
  const key = drafts.avatarDraftKey("api", "user");
  local.failCopy(true);
  await assert.rejects(drafts.saveAvatarDraft(key, firstId, "file:///temporary.jpg"), /disk full/);
  assert.equal(await drafts.avatarDraftExists(key, firstId), false);
  local.failCopy(false);
  local.files.set("file:///temporary.jpg", "jpeg bytes");
  await drafts.saveAvatarDraft(key, firstId, "file:///temporary.jpg");
  local.failSecure(true);
  await assert.rejects(drafts.writeAvatarDraft(key, firstId), /secure storage unavailable/);
  assert.equal(await drafts.readAvatarDraft(key), null);
  local.failSecure(false);
  await drafts.writeAvatarDraft(key, firstId);
  await drafts.deleteAvatarDraft(key, firstId);
  assert.equal(await local.reopen().readAvatarDraft(key), firstId);
  assert.equal(await local.reopen().avatarDraftExists(key, firstId), false);
});

test("replacement prunes orphaned avatars but never touches punch photos", async () => {
  const local = device();
  local.files.set("file:///temporary.jpg", "jpeg bytes");
  const drafts = local.reopen();
  const key = drafts.avatarDraftKey("api", "user");
  await drafts.saveAvatarDraft(key, firstId, "file:///temporary.jpg");
  await drafts.saveAvatarDraft(key, secondId, "file:///temporary.jpg");
  local.files.set("file:///private/pending-punch/untouched.jpg", "punch bytes");
  await drafts.pruneAvatarDrafts(key, secondId);
  assert.equal(await drafts.avatarDraftExists(key, firstId), false);
  assert.equal(await drafts.avatarDraftExists(key, secondId), true);
  assert.equal(local.files.get("file:///private/pending-punch/untouched.jpg"), "punch bytes");
  await assert.rejects(drafts.avatarDraftUri(key, "../outside"), /标识无效/);
});
