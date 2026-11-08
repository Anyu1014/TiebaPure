import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { storePackage } from './store-package.mjs';

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tiebapure-packages-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const releases = path.join(root, 'releases');
  const source = path.join(root, 'incoming');
  return { root, releases, source };
}
function packages(root) {
  return fs.readdirSync(root, { recursive: true, withFileTypes: true }).filter(e => e.isFile() && /\.(apk|ipa)$/.test(e.name)).map(e => path.join(e.parentPath, e.name));
}
test('latest APK and signed IPA share one folder and older bytes are archived', t => {
  const { root, releases, source } = fixture(t);
  fs.writeFileSync(source, 'old APK'); storePackage(source, releases, 'TiebaPure-1.0.apk');
  fs.writeFileSync(source, 'signed IPA'); storePackage(source, releases, 'TiebaPure-1.0.ipa');
  fs.writeFileSync(source, 'new APK'); const apk = storePackage(source, releases, 'TiebaPure-1.0.apk');
  assert.equal(path.dirname(apk), path.join(root, 'latest'));
  assert.equal(fs.readFileSync(apk, 'utf8'), 'new APK');
  assert.deepEqual(fs.readdirSync(path.join(root, 'latest')).sort(), ['TiebaPure-1.0.apk', 'TiebaPure-1.0.ipa']);
  assert.equal(packages(root).filter(p => fs.readFileSync(p, 'utf8') === 'old APK').length, 1);
  assert.equal(packages(releases).length, 1);
  fs.writeFileSync(source, 'new signed IPA'); storePackage(source, releases, 'TiebaPure-1.0.ipa');
  assert.equal(fs.readFileSync(apk, 'utf8'), 'new APK');
  assert.deepEqual(packages(releases).map(p => fs.readFileSync(p, 'utf8')).sort(), ['old APK', 'signed IPA']);
});
test('repeated builds and rollback keep one copy of each unique package', t => {
  const { root, releases, source } = fixture(t);
  for (const bytes of ['A', 'A', 'B', 'A']) {
    fs.writeFileSync(source, bytes); storePackage(source, releases, 'TiebaPure-1.0.apk');
  }
  assert.equal(packages(root).length, 2);
  assert.equal(fs.readFileSync(path.join(root, 'latest/TiebaPure-1.0.apk'), 'utf8'), 'A');
  assert.equal(fs.readdirSync(releases).length, 1);
});
test('debug APK and unsigned IPA cannot replace installable current packages', t => {
  const { root, releases, source } = fixture(t);
  for (const [name, bytes] of [['TiebaPure-1.0.apk', 'release'], ['TiebaPure-1.0.ipa', 'signed'], ['TiebaPure-1.0-debug.apk', 'debug'], ['TiebaPure-1.0-unsigned.ipa', 'unsigned']]) {
    fs.writeFileSync(source, bytes); storePackage(source, releases, name);
  }
  assert.deepEqual(fs.readdirSync(path.join(root, 'latest')).sort(), ['TiebaPure-1.0.apk', 'TiebaPure-1.0.ipa']);
  assert.equal(packages(root).length, 4);
});
test('new version archives the previous filename and rejects path traversal', t => {
  const { root, releases, source } = fixture(t);
  fs.writeFileSync(source, 'old'); storePackage(source, releases, 'TiebaPure-1.0.apk');
  fs.writeFileSync(source, 'new'); storePackage(source, releases, 'TiebaPure-1.1.apk');
  assert.deepEqual(fs.readdirSync(path.join(root, 'latest')), ['TiebaPure-1.1.apk']);
  assert.throws(() => storePackage(source, releases, '../TiebaPure-1.0.apk'));
  assert.throws(() => storePackage(path.join(source, 'missing'), releases, 'TiebaPure-1.0.apk'));
  assert.equal(packages(root).length, 2);
});
test('archive collision preserves the current package and removes the temporary copy', t => {
  const { root, releases, source } = fixture(t);
  fs.writeFileSync(source, 'old'); const current = storePackage(source, releases, 'TiebaPure-1.0.apk');
  const hash = createHash('sha256').update('old').digest('hex');
  const folder = path.join(releases, `TiebaPure-1.0-${hash.slice(0, 12)}`);
  fs.mkdirSync(folder, { recursive: true }); fs.writeFileSync(path.join(folder, 'TiebaPure-1.0.apk'), 'different');
  fs.writeFileSync(source, 'new');
  assert.throws(() => storePackage(source, releases, 'TiebaPure-1.0.apk'), /Archive collision/);
  assert.equal(fs.readFileSync(current, 'utf8'), 'old');
  assert.deepEqual(fs.readdirSync(path.dirname(current)), ['TiebaPure-1.0.apk']);
});
test('unchanged builds use latest without creating an empty history directory', t => {
  const { root, releases, source } = fixture(t);
  fs.writeFileSync(source, 'same');
  storePackage(source, releases, 'TiebaPure-1.0.apk');
  storePackage(source, releases, 'TiebaPure-1.0.apk');
  assert.deepEqual(fs.readdirSync(root).sort(), ['incoming', 'latest']);
  assert.equal(fs.existsSync(releases), false);
});

test('renamed TiebaPure packages archive the old brand per platform', t => {
  const { root, releases, source } = fixture(t);
  fs.writeFileSync(source, 'old APK'); storePackage(source, releases, 'TiebaPure-1.3.apk');
  fs.writeFileSync(source, 'old IPA'); storePackage(source, releases, 'TiebaPure-1.3.ipa');
  fs.writeFileSync(source, 'new APK'); storePackage(source, releases, 'TiebaPure-2.0.apk');
  assert.deepEqual(fs.readdirSync(path.join(root, 'latest')).sort(), ['TiebaPure-1.3.ipa', 'TiebaPure-2.0.apk']);
  fs.writeFileSync(source, 'new IPA'); storePackage(source, releases, 'TiebaPure-2.0.ipa');
  assert.deepEqual(fs.readdirSync(path.join(root, 'latest')).sort(), ['TiebaPure-2.0.apk', 'TiebaPure-2.0.ipa']);
  assert.deepEqual(packages(releases).map(p => fs.readFileSync(p, 'utf8')).sort(), ['old APK','old IPA']);
});
