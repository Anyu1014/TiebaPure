import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const docs = fs.readFileSync(path.join(root, 'docs/DEPENDENCIES.md'), 'utf8');
const modules = [...docs.matchAll(/^\|\s*`([^`]+:[^`]+:[^`]+)`\s*\|\s*([^|]+)\|/gm)].map(m => ({ id: m[1], license: m[2].trim() }));
assert(modules.length > 0 && modules.some(m => m.id === 'com.google.protobuf:protobuf-java:3.25.5'), 'Resolved module audit must include the actual Protobuf runtime');
const cache = path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1');
const target = path.join(root, 'android/app/src/main/assets/licenses/dependencies');
const temp = path.join(root, '..', '.cache/notice-scan');
fs.mkdirSync(target, { recursive: true });
fs.mkdirSync(temp, { recursive: true });
const notices = new Map();
const records = [];
let archives = 0;
const noticeName = /(^|\/)(LICENSE(?:[._-][^/]*)?|NOTICE(?:[._-][^/]*)?|COPYRIGHT(?:[._-][^/]*)?|AL2\.0|LGPL2\.1)$/i;
function scan(archive, module, scope = '') {
  const entries = execFileSync('/usr/bin/unzip', ['-Z', '-1', archive], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 }).split('\n');
  for (const entry of entries.filter(e => noticeName.test(e))) {
    const data = execFileSync('/usr/bin/unzip', ['-p', archive, entry], { maxBuffer: 8 * 1024 * 1024 });
    assert(data.length > 0, `Empty notice in ${module}: ${entry}`);
    const hash = crypto.createHash('sha256').update(data).digest('hex');
    if (!notices.has(hash)) {
      const name = `${hash}.txt`;
      fs.writeFileSync(path.join(target, name), data);
      assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(target, name))).digest('hex'), hash);
      notices.set(hash, { name, text: data.toString('utf8') });
    }
    records.push({ module, entry: scope + entry, sha256: hash });
  }
  if (archive.endsWith('.aar') && entries.includes('classes.jar')) {
    const file = path.join(temp, crypto.createHash('sha256').update(archive).digest('hex') + '.jar');
    fs.writeFileSync(file, execFileSync('/usr/bin/unzip', ['-p', archive, 'classes.jar'], { maxBuffer: 64 * 1024 * 1024 }));
    try { scan(file, module, 'classes.jar/'); } finally { fs.unlinkSync(file); }
  }
}
for (const module of modules) {
  const [group, name, version] = module.id.split(':');
  const directory = path.join(cache, group, name, version);
  if (!fs.existsSync(directory)) continue;
  for (const hash of fs.readdirSync(directory)) {
    const folder = path.join(directory, hash);
    if (!fs.statSync(folder).isDirectory()) continue;
    for (const file of fs.readdirSync(folder).filter(f => /\.(jar|aar)$/.test(f))) { archives++; scan(path.join(folder, file), module.id); }
  }
}
assert(archives > 0 && notices.size > 0, 'No runtime archive notices found');
const summary = { moduleNodes: modules.length, inspectedArchives: archives, noticeEntries: records.length, uniqueTexts: notices.size, records };
fs.writeFileSync(path.join(root, 'docs/ANDROID_NOTICE_INVENTORY.json'), JSON.stringify(summary, null, 2) + '\n');
let text = '# 第三方依赖与版权材料\n\nTiebaPure Android：本衍生项目采用 GPL-3.0-only。以下依赖节点的许可标注来自已解析版本的 POM；BOM/多平台元数据节点不等于打包代码。\n\n';
text += modules.map(m => `- ${m.id} — ${m.license}`).join('\n');
text += '\n\n下列正文从缓存的对应 JAR/AAR（包括 classes.jar）原样提取，按内容 SHA-256 去重；单独保存在 licenses/dependencies。源代码逐行权属及动态素材授权不在此扫描范围。\n';
for (const [hash, notice] of notices) text += `\n\n## ${notice.name}\n\n来源：${records.filter(r => r.sha256 === hash).map(r => `${r.module} (${r.entry})`).join('；')}\n\n${notice.text}`;
fs.writeFileSync(path.join(root, 'android/app/src/main/assets/THIRD_PARTY_NOTICES.md'), text + '\n');
console.log(JSON.stringify({ moduleNodes: modules.length, inspectedArchives: archives, noticeEntries: records.length, uniqueTexts: notices.size }));
