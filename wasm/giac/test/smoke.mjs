// Drive the giac.wasm reactor through its export protocol, as a host binding
// would: giac_alloc -> write UTF-8 + NUL -> caseval -> read NUL-terminated
// result -> giac_free.
//
//   node wasm/giac/test/smoke.mjs [path/to/giac.wasm]
import { readFile } from 'node:fs/promises';
import { WASI } from 'node:wasi';
import { homedir } from 'node:os';

const path = process.argv[2] ?? `${homedir()}/.cache/giac-wasm/out/giac.wasm`;
const wasi = new WASI({ version: 'preview1', args: [], env: {} });
const { instance } = await WebAssembly.instantiate(await readFile(path), wasi.getImportObject());
wasi.initialize(instance);
const { memory, giac_alloc, giac_free, caseval } = instance.exports;

const enc = new TextEncoder(), dec = new TextDecoder();
function giac(cmd) {
  const bytes = enc.encode(cmd);
  const p = giac_alloc(bytes.length + 1);
  new Uint8Array(memory.buffer, p, bytes.length + 1).set([...bytes, 0]);
  const r = caseval(p);
  giac_free(p);
  const mem = new Uint8Array(memory.buffer);
  let end = r;
  while (mem[end] !== 0) end++;
  return dec.decode(mem.subarray(r, end));
}

const cases = [
  ['factor(x^4-1)', '(x-1)*(x+1)*(x^2+1)'],
  ['integrate(1/(1+x^2),x)', 'atan(x)'],
  ['limit(sin(x)/x,x=0)', '1'],
  ["desolve(y''+y=0,y)", null],
  ['f(x):=x^2+1', null],
  ['f(3)', '10'],
  ['x+*2', null],  // syntax error must come back as text, not trap
];
let failed = 0;
for (const [cmd, want] of cases) {
  const got = giac(cmd).trim();
  const ok = want === null || got === want;
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${cmd} => ${got}${ok ? '' : `  (want ${want})`}`);
}
process.exit(failed ? 1 : 0);
