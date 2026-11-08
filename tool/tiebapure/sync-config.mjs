import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
export function validate(c) {
  if (typeof c.name !== 'string' || !c.name.trim() || /[\r\n$]/.test(c.name)) throw Error('Invalid app name');
  if (!/^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*){2,}$/.test(c.identifier)) throw Error('Invalid application identifier');
  if (!/^\d+\.\d+(?:\.\d+)?$/.test(c.version) || !Number.isSafeInteger(c.build) || c.build < 1) throw Error('Invalid version/build');
  if (!/^[a-z][a-z0-9-]*$/.test(c.urlScheme)) throw Error('Invalid URL scheme');
  return c;
}
export function sync() {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
  const c = validate(JSON.parse(fs.readFileSync(path.join(root, 'app-config.json'), 'utf8')));
  fs.writeFileSync(path.join(root, 'ios/App.xcconfig'), `// Generated from ../app-config.json by tool/tiebapure/sync-config.mjs\nAPP_DISPLAY_NAME = ${c.name}\nAPP_IDENTIFIER = ${c.identifier}\nAPP_VERSION = ${c.version}\nAPP_BUILD = ${c.build}\nAPP_URL_SCHEME = ${c.urlScheme}\n`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) sync();
