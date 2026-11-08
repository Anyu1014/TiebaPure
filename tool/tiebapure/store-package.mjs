import fs from 'node:fs';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const digest = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const packageName = /^(?:TiebaLite|TiebaPure)-\d+\.\d+(?:\.\d+)?(?:-debug|-unsigned)?\.(apk|ipa)$/;

export function storePackage(source, releaseRoot, name) {
  if (!packageName.test(name) || !fs.statSync(source).isFile()) throw Error('Invalid package source or filename');
  const current = path.join(path.dirname(releaseRoot), 'latest');
  const history = releaseRoot;
  fs.mkdirSync(current, { recursive: true });
  const sha = digest(source);
  const extension = path.extname(name);
  const development = /-(debug|unsigned)\./.test(name);
  function archive(file, filename, hash) {
    const folder = path.join(history, `${path.basename(filename, extension)}-${hash.slice(0, 12)}`);
    fs.mkdirSync(folder, { recursive: true });
    const target = path.join(folder, filename);
    if (fs.existsSync(target)) {
      if (digest(target) !== hash) throw Error(`Archive collision: ${target}`);
      fs.unlinkSync(file);
    } else fs.renameSync(file, target);
    return target;
  }
  const temporary = path.join(current, `.${randomUUID()}.tmp`);
  let destination;
  try {
    fs.copyFileSync(source, temporary, fs.constants.COPYFILE_EXCL);
    if (digest(temporary) !== sha) throw Error('Package copy verification failed');
    if (development) destination = archive(temporary, name, sha);
    else {
      for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
        if (entry.isFile() && packageName.test(entry.name) && path.extname(entry.name) === extension) {
          const old = path.join(current, entry.name);
          if (digest(old) !== sha) archive(old, entry.name, digest(old));
        }
      }
      destination = path.join(current, name);
      fs.renameSync(temporary, destination);
    }
    // Keep one copy of identical package bytes, including repeated builds.
    for (const directory of [current, history]) {
      if (!fs.existsSync(directory)) continue;
      for (const entry of fs.readdirSync(directory, { recursive: true, withFileTypes: true })) {
        const file = path.join(entry.parentPath, entry.name);
        if (entry.isFile() && packageName.test(entry.name) && path.extname(entry.name) === extension && file !== destination && digest(file) === sha) fs.unlinkSync(file);
      }
    }
    const folders = fs.existsSync(history) ? fs.readdirSync(history, { recursive: true, withFileTypes: true }).filter(e => e.isDirectory()).map(e => path.join(e.parentPath, e.name)) : [];
    for (const folder of folders.sort((a, b) => b.length - a.length)) if (fs.readdirSync(folder).length === 0) fs.rmdirSync(folder);
    return destination;
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
  // 成品统一进项目根 build/（build/history 存历史、build/latest 是最新一份）
  console.log(storePackage(process.argv[2], path.join(root, '..', 'build', 'history'), process.argv[3]));
}
