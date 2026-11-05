const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");
const { URL: ExpoUrl } = require("whatwg-url-without-unicode");

const source = fs.readFileSync(path.join(__dirname, "../src/api.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

function normalize(dev, Url = URL) {
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module, exports: module.exports, URL: Url, __DEV__: dev,
    require(name) {
      if (name === "@today/contracts") return {};
      if (name === "zod") return require("zod");
      throw new Error(`Unexpected dependency: ${name}`);
    },
  });
  return module.exports.normalizeServerUrl;
}

test("server addresses are normalized to an origin in development", () => {
  for (const Url of [URL, ExpoUrl]) {
    const parse = normalize(true, Url);
    assert.equal(parse("  HTTPS://API.Example.com:443/// "), "https://api.example.com");
    assert.equal(parse("http://localhost:3001/"), "http://localhost:3001");
  }
});

test("credentials, paths, queries and fragments are rejected", () => {
  for (const Url of [URL, ExpoUrl]) {
    const parse = normalize(true, Url);
    for (const address of [
    "https://user:password@api.example.com",
    "https://api.example.com@other.example.com",
    "https://api.example.com/v1",
    "https://api.example.com/%2Fv1",
    "https://api.example.com?next=/v1",
    "https://api.example.com#settings",
    "ftp://api.example.com",
    "api.example.com",
    ]) {
      assert.throws(() => parse(address), { name: "Error" }, address);
    }
  }
});

test("release builds require HTTPS even for localhost", () => {
  for (const Url of [URL, ExpoUrl]) {
    const parse = normalize(false, Url);
    assert.equal(parse("https://api.example.com/"), "https://api.example.com");
    assert.throws(() => parse("http://localhost:3001"), /HTTPS/);
  }
});
