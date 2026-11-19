#!/usr/bin/env node
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, readdirSync, statSync, symlinkSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { gunzipSync } from "node:zlib";

const root = mkdtempSync(join(tmpdir(), "today-backup-test-"));
const bin = join(root, "bin");
mkdirSync(bin);
const script = resolve("scripts/db-backup.sh");
try {
  writeFileSync(join(bin, "docker"), "#!/bin/sh\nexit 1\n", { mode: 0o700 });
  writeFileSync(join(bin, "mysqldump"), `#!/bin/sh
case "$BACKUP_TEST_MODE" in
  failure) printf 'partial data'; exit 1 ;;
  race) printf 'existing destination' > "$BACKUP_TEST_OUTPUT" ;;
  permissions)
    for file in "$BACKUP_TEST_OUTPUT".tmp.*; do
      mode=$(stat -c %a "$file" 2>/dev/null || stat -f %Lp "$file")
      [ "$mode" = 600 ] || exit 2
    done ;;
esac
printf 'CREATE TABLE backup_test (id INT);\n'
`, { mode: 0o700 });

  function run(name, mode = "success", container = "") {
    const output = join(root, `${name}.sql.gz`);
    const result = spawnSync("bash", [script, output], {
      env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, TODAY_BACKUP_CONTAINER: container, BACKUP_TEST_MODE: mode, BACKUP_TEST_OUTPUT: output },
      encoding: "utf8",
      timeout: 10_000,
    });
    assert.ifError(result.error);
    assert.equal(readdirSync(root).some(file => file.startsWith(`${name}.sql.gz.tmp.`)), false, "temporary backups must be removed");
    return { ...result, output };
  }

  const success = run("success", "permissions");
  assert.equal(success.status, 0, success.stderr);
  assert.equal(statSync(success.output).mode & 0o777, 0o600);
  assert.equal(gunzipSync(readFileSync(success.output)).toString(), "CREATE TABLE backup_test (id INT);\n");

  const failure = run("failure", "failure");
  assert.notEqual(failure.status, 0);
  assert.equal(readdirSync(root).includes("failure.sql.gz"), false);

  const previous = readFileSync(success.output);
  assert.notEqual(run("success").status, 0);
  assert.deepEqual(readFileSync(success.output), previous);

  const race = run("race", "race");
  assert.notEqual(race.status, 0);
  assert.equal(readFileSync(race.output, "utf8"), "existing destination");

  symlinkSync(join(root, "missing-target"), join(root, "symlink.sql.gz"));
  assert.notEqual(run("symlink").status, 0);
  assert.equal(readdirSync(root).includes("missing-target"), false);
  const missingContainer = run("missing-container", "success", "unavailable-test-container");
  assert.notEqual(missingContainer.status, 0);
  assert.match(missingContainer.stderr, /requested backup container is not running/);
  assert.equal(readdirSync(root).includes("missing-container.sql.gz"), false);
  console.log("Backup regression checks passed (permissions, failure cleanup, existing destination, concurrent destination, symlink, unavailable container)");
} finally {
  rmSync(root, { recursive: true, force: true });
}
