const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

function harness() {
  const files = new Map();
  const directories = new Set();
  let failCopy = false;
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
  const crypto = { CryptoDigestAlgorithm: { SHA256: "SHA256" },
    digestStringAsync: async (_algorithm, scope) => Buffer.from(scope).toString("hex") };
  function reopen() {
    const module = { exports: {} };
    vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname, "../src/punchPhotoDraft.ts"), "utf8"), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    }).outputText, { module, exports: module.exports, require(name) {
      if (name === "expo-file-system") return disk;
      if (name === "expo-crypto") return crypto;
      throw new Error(name);
    } });
    return module.exports;
  }
  return { files, reopen, setFailCopy(value) { failCopy = value; } };
}

const id = "a5b674a9-cb65-443a-aa66-a1da02075229";
const nextId = "269dfceb-c12a-4484-aa05-1cc21787eaba";

test("a restart finds the same private photo, isolated by account and server", async () => {
  const device = harness();
  device.files.set("file:///temporary.jpg", "photo bytes");
  const first = device.reopen();
  const uri = await first.savePhotoDraft("api-a:user-a", id, "file:///temporary.jpg");
  const reopened = device.reopen();
  assert.equal(await reopened.photoDraftUri("api-a:user-a", id), uri);
  assert.equal(await reopened.photoDraftExists("api-a:user-a", id), true);
  assert.equal(await reopened.photoDraftExists("api-a:user-b", id), false);
  assert.equal(await reopened.photoDraftExists("api-b:user-a", id), false);
  await reopened.deletePhotoDraft("api-a:user-a", id);
  assert.equal(await reopened.photoDraftExists("api-a:user-a", id), false);
});

test("failed copy or missing photo cannot be treated as a ready upload", async () => {
  const device = harness();
  const photos = device.reopen();
  device.setFailCopy(true);
  await assert.rejects(photos.savePhotoDraft("user", id, "file:///missing.jpg"));
  assert.equal(await photos.photoDraftExists("user", id), false);
  device.setFailCopy(false);
  await assert.rejects(photos.savePhotoDraft("user", id, "file:///missing.jpg"), /照片保存不完整/);
  assert.equal(await photos.photoDraftExists("user", id), false);
});

test("orphaned replaced files are removed while the active draft is retained", async () => {
  const device = harness();
  device.files.set("file:///temporary.jpg", "bytes");
  const photos = device.reopen();
  await photos.savePhotoDraft("user", id, "file:///temporary.jpg");
  await photos.savePhotoDraft("user", nextId, "file:///temporary.jpg");
  await photos.prunePhotoDrafts("user", nextId);
  assert.equal(await photos.photoDraftExists("user", id), false);
  assert.equal(await photos.photoDraftExists("user", nextId), true);
  await assert.rejects(photos.photoDraftUri("user", "../outside"), /标识无效/);
});
