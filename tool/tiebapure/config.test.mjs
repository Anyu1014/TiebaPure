import test from 'node:test';
import assert from 'node:assert/strict';
import { validate } from './sync-config.mjs';
const config = { name: 'TiebaPure 二改版', identifier: 'dev.anyu.tiebapure', version: '1.4.15', build: 59, urlScheme: 'tiebapure-derived' };
test('valid shared configuration', () => assert.equal(validate(config), config));
test('two-part release version', () => assert.equal(validate({ ...config, version: '1.0' }).version, '1.0'));
test('reject malformed configuration before generating build settings', () => {
  for (const bad of [{ name: 'x\nBAD=1' }, { name: '$(OTHER)' }, { identifier: 'x' }, { version: 'latest' }, { build: 0 }, { urlScheme: 'https://' }]) assert.throws(() => validate({ ...config, ...bad }));
});
