const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

const source = fs.readFileSync(path.join(__dirname, "../src/chatHistory.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const sandboxModule = { exports: {} };
vm.runInNewContext(compiled, { module: sandboxModule, exports: sandboxModule.exports });
const { appendOlderMessages, mergeConversationHistory } = sandboxModule.exports;

test("new server replies keep previously loaded older messages without duplicates", () => {
  const previous = { id: "conversation-1", messages: [{ id: "new" }, { id: "old" }] };
  const updated = { id: "conversation-1", messages: [{ id: "reply" }, { id: "new" }] };

  const merged = mergeConversationHistory(previous, updated);

  assert.deepEqual(Array.from(merged.messages, message => message.id), ["reply", "new", "old"]);
  assert.deepEqual(Array.from(appendOlderMessages(merged, [{ id: "old" }, { id: "older" }]).messages,
    message => message.id), ["reply", "new", "old", "older"]);
});

test("switching conversations never carries messages across accounts or threads", () => {
  const previous = { id: "conversation-1", messages: [{ id: "private" }] };
  const updated = { id: "conversation-2", messages: [{ id: "new" }] };

  assert.equal(mergeConversationHistory(previous, updated), updated);
});
