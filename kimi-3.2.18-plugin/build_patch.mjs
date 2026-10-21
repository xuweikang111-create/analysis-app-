import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { Readable } from 'node:stream';
import * as asar from '@electron/asar';

const args = process.argv.slice(2);
const sourceAsar = args[0];
const destAsar = args[1];
const patchedMainOut = args[2];
if (!sourceAsar || !destAsar || !patchedMainOut) {
  console.error('usage: node build_patch.mjs <source.asar> <dest.asar> <patched-main.js>');
  process.exit(2);
}

const EXPECTED_MAIN_SHA = '4b20660fdc04d6c9de50bf252ccadb3fb9502f5be3065126529a83dcd5f98408';
const MAIN_PATH = 'out/main/index.js';
const CRED_ANCHOR = 'async function _0x4318a2(_0x34f147) {';
const RESOLVER_ANCHOR = 'return this.#opts[_0x5ab4de(7084)]?.()?.[_0x5ab4de(3178)]() || _0x3607cb;';
const TRANSLOCATION_CHECK = 'function _0x31c1a0() {';
const TRANSLOCATION_DIALOG = 'async function _0x1cd112() {';
const MARKER = 'MOZHE_KIMI_3218_RELAY_V1013';

function sha256(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}
function count(haystack, needle) {
  return haystack.split(needle).length - 1;
}
function requireOnce(source, needle, label) {
  const n = count(source, needle);
  if (n !== 1) {
    throw new Error(label + ': expected exactly one match, got ' + n);
  }
}
function replaceOnce(source, needle, replacement, label) {
  requireOnce(source, needle, label);
  return source.replace(needle, replacement);
}

const originalMain = asar.extractFile(sourceAsar, MAIN_PATH);
const originalMainSha = sha256(originalMain);
if (originalMainSha !== EXPECTED_MAIN_SHA) {
  throw new Error('Unexpected Kimi 3.2.18 main SHA256: ' + originalMainSha);
}
let source = originalMain.toString('utf8');

if (source.includes(MARKER)) {
  throw new Error('Source already contains MOZHE marker');
}
requireOnce(source, CRED_ANCHOR, 'credential writer anchor');
requireOnce(source, RESOLVER_ANCHOR, 'gateway resolver anchor');
requireOnce(source, TRANSLOCATION_CHECK, 'translocation check anchor');
requireOnce(source, TRANSLOCATION_DIALOG, 'translocation dialog anchor');

const credReplacement =
  CRED_ANCHOR +
  '\n  if (process.env.KWORK_MODEL_RELAY_BASE) {' +
  '\n    _0x34f147 = {' +
  '\n      ..._0x34f147,' +
  '\n      baseUrl: __mozheMacBase(_0x34f147 && _0x34f147.baseUrl)' +
  '\n    };' +
  '\n  }';

source = replaceOnce(source, CRED_ANCHOR, credReplacement, 'credential writer patch');

source = replaceOnce(
  source,
  RESOLVER_ANCHOR,
  'return __mozheMacBase(this.#opts[_0x5ab4de(7084)]?.()?.[_0x5ab4de(3178)]() || _0x3607cb);',
  'gateway resolver patch'
);

source += '\n\n/* MOZHE_KIMI_3218_RELAY_V1013 */\n' +
  'function __mozheMacBase(_0xFallback) {\n' +
  '  const _0xRaw = (process.env.KWORK_MODEL_RELAY_BASE || "").trim();\n' +
  '  if (!_0xRaw) return _0xFallback;\n' +
  '  if (!/^http:\\/\\/127\\.0\\.0\\.1:\\\\d{1,5}\\/v1\\/?$/.test(_0xRaw)) {\n' +
  '    throw new Error("[MOZHE] KWORK_MODEL_RELAY_BASE must be http://127.0.0.1:<port>/v1");\n' +
  '  }\n' +
  '  const _0xParsed = new URL(_0xRaw);\n' +
  '  const _0xPort = Number(_0xParsed.port);\n' +
  '  if (!Number.isInteger(_0xPort) || _0xPort < 1 || _0xPort > 65535) {\n' +
  '    throw new Error("[MOZHE] invalid relay port");\n' +
  '  }\n' +
  '  const _0xBase = _0xRaw.replace(/\\/+$/, "");\n' +
  '  process.env.KWORK_MODEL_RELAY_ACTIVE = "1";\n' +
  '  process.env.KWORK_MODEL_RELAY_EFFECTIVE = _0xBase;\n' +
  '  return _0xBase;\n' +
  '}\n';

const patchedMain = Buffer.from(source, 'utf8');
fs.mkdirSync(path.dirname(patchedMainOut), {recursive:true});
fs.writeFileSync(patchedMainOut, patchedMain);

const rawHeader = asar.getRawHeader(sourceAsar).header;
const streams = [];

function walk(node, prefix='') {
  if (!node || !node.files) return;
  for (const [name, entry] of Object.entries(node.files)) {
    const rel = prefix ? path.posix.join(prefix, name) : name;
    if (entry.files) {
      streams.push({ type: 'directory', path: rel, unpacked: !!entry.unpacked });
      walk(entry, rel);
    } else if (entry.link) {
      streams.push({
        type: 'link',
        path: rel,
        unpacked: !!entry.unpacked,
        symlink: entry.link,
        stat: { mode: 0o120777, size: 0 },
        streamGenerator: () => Readable.from([]),
      });
    } else {
      const isMain = rel === MAIN_PATH;
      const mode = entry.executable ? 0o100755 : 0o100644;
      const size = isMain ? patchedMain.length : entry.size;
      streams.push({
        type: 'file',
        path: rel,
        unpacked: !!entry.unpacked,
        stat: { mode, size },
        streamGenerator: () => Readable.from([
          isMain ? patchedMain : asar.extractFile(sourceAsar, rel)
        ]),
      });
    }
  }
}
walk(rawHeader);

await asar.createPackageFromStreams(destAsar, streams);

const rebuiltMain = asar.extractFile(destAsar, MAIN_PATH);
if (sha256(rebuiltMain) !== sha256(patchedMain)) {
  throw new Error('Patched main did not round-trip');
}
const packedBefore = asar.listPackage(sourceAsar, {isPack:true});
const packedAfter = asar.listPackage(destAsar, {isPack:true});
if (JSON.stringify(packedBefore) !== JSON.stringify(packedAfter)) {
  throw new Error('ASAR packed/unpacked layout changed');
}

let checked = 0;
for (const item of packedBefore) {
  const rel = item.slice(item.indexOf(':') + 1).trim().replace(/^\\/+/, '');
  if (!rel || rel === MAIN_PATH) continue;
  const entry = asar.statFile(sourceAsar, rel, false);
  if (entry.files || entry.link) continue;
  const a = asar.extractFile(sourceAsar, rel);
  const b = asar.extractFile(destAsar, rel);
  if (sha256(a) !== sha256(b)) {
    throw new Error('Unexpected content change: ' + rel);
  }
  checked++;
}

console.log('ORIGINAL_MAIN_SHA=' + originalMainSha);
console.log('PATCHED_MAIN_SHA=' + sha256(patchedMain));
console.log('PATCHED_ASAR_SHA=' + sha256(fs.readFileSync(destAsar)));
console.log('UNCHANGED_FILES_VERIFIED=' + checked);
console.log('PATCH_MARKER=' + MARKER);
