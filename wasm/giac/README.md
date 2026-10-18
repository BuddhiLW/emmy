# giac for WebAssembly (WASI)

Builds [Giac](https://github.com/geogebra/giac), the C++ computer algebra
system behind GeoGebra and Xcas, as a WASI module, so that Emmy can call it
from any host that runs WebAssembly: cljw (zwasm), browsers, Node, wasmtime.

The module has one entry point, `caseval`: a giac command string in, the
result string out.

## Artifacts

Build output goes to `$GIAC_WASM_BUILD/out` (default `~/.cache/giac-wasm/out`):

| File            | Kind                              | Use                                  |
|-----------------|-----------------------------------|--------------------------------------|
| `giac.wasm`     | reactor (`-mexec-model=reactor`)  | host bindings; exports below         |
| `giac-cli.wasm` | command                           | testing: evaluates each argument     |

`giac.wasm` exports `caseval`, `giac_alloc`, `giac_free`, `memory` and
`_initialize`, and imports only `wasi_snapshot_preview1`.

Host protocol: call `_initialize` once, then for each command
`p = giac_alloc(len + 1)`, write the UTF-8 bytes and a NUL at `p`,
`r = caseval(p)`, read the NUL-terminated result at `r`, `giac_free(p)`.
The result buffer belongs to giac and is valid until the next call. All calls
share one giac context, so definitions persist (`f(x):=x^2+1`, then `f(3)`).

## Build

Needs [wasi-sdk](https://github.com/WebAssembly/wasi-sdk) 33 or later (C++
exceptions); paths are set in `env.sh` and can be overridden from the
environment.

```sh
wasm/giac/build-deps.sh   # GMP 6.3.0 + MPFR 4.2.2 for WASI (once)
wasm/giac/build.sh        # compile giac's 51 units, link both artifacts
```

`GIAC_SRC` must point at a checkout of github.com/geogebra/giac.

## Test

```sh
zwasm run ~/.cache/giac-wasm/out/giac-cli.wasm 'factor(x^4-1)' 'integrate(1/(1+x^2),x)'
wasmtime run -W exceptions=y ~/.cache/giac-wasm/out/giac-cli.wasm 'limit(sin(x)/x,x=0)'
node wasm/giac/test/smoke.mjs   # drives giac.wasm through the export protocol
```

## How the port works

- Compile flags follow giac's own Emscripten build (`build.gradle`,
  `emccCompileWasm`): `-DEMCC2 -DGIAC_GGB ...`, so the code takes the Wasm paths
  giac already tests.
- C++ exceptions use the standard Wasm exception-handling proposal
  (`-fwasm-exceptions -mllvm -wasm-use-legacy-eh=false`, `-lunwind`). Runtimes
  must support it: zwasm does by default, wasmtime needs `-W exceptions=y`.
- `shim/emscripten.h` replaces Emscripten's inline-JavaScript macros. The only
  one giac still needs with `GIAC_GGB` reads `Date.now()`; it is answered from
  the WASI clock. The rest are no-ops.
- WASI has no processes, users or shell: `HAVE_NO_PWD_H` and
  `HAVE_NO_SYS_RESOURCE_WAIT_H` are giac's own switches; `kill` and `system`
  fail with `ENOSYS` (`shim/wasi_posix.h`, `glue/giac_wasi.cc`). wasi-libc's
  signal, getpid and process-clock emulations cover the rest.
- GMP and MPFR are rebuilt from source: the prebuilt wasm archives in giac's
  tree target an old Emscripten libc and do not link against wasi-libc.

Giac, GMP and MPFR are GPL/LGPL; Emmy is GPL-3.
