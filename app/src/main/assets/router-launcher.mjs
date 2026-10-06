// Router launcher. Runs inside the embedded Node 24 runtime.
// It reports runtime facts, then imports the UNMODIFIED Tariffia Router CLI and
// starts `serve`. Ported from the proven PoC launcher.
import { writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const info = {
  version: process.version,
  arch: process.arch,
  platform: process.platform,
  mobile: process.versions && process.versions.mobile ? process.versions.mobile : null,
};

try {
  if (process.env.ROUTER_INFO_FILE) {
    writeFileSync(process.env.ROUTER_INFO_FILE, JSON.stringify(info));
  }
} catch (err) {
  console.error('[router] could not write info file:', err && err.message);
}

console.log('[router] node', process.version, process.arch, 'mobile=', info.mobile);

try {
  const order = 'a'.localeCompare('b');
  console.log('[router] localeCompare ok ->', order);
} catch (err) {
  console.error('[router] localeCompare FAILED ->', err && err.message);
}

const entry = process.env.ROUTER_ENTRY;
console.log('[router] importing Router CLI:', entry);
const mod = await import(pathToFileURL(entry).href);
await mod.main(['serve']);
