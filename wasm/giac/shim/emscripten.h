/* Stand-in for <emscripten.h> when giac is built for WASI.
 *
 * giac's Wasm code paths are guarded by EMCC2 and include this header. With
 * GIAC_GGB defined, the only Emscripten calls left are inline-JavaScript
 * macros. Under WASI there is no JavaScript host, so:
 *   - EM_ASM_DOUBLE_V is only used by emcctime() to read Date.now(); it is
 *     answered with the WASI wall clock in milliseconds.
 *   - every other EM_ASM_* form (browser prompts, window.open) is a no-op.
 */
#ifndef GIAC_WASI_EMSCRIPTEN_SHIM_H
#define GIAC_WASI_EMSCRIPTEN_SHIM_H

#include <time.h>

static inline double giac_wasi_now_ms(void) {
  struct timespec ts;
  clock_gettime(CLOCK_REALTIME, &ts);
  return ts.tv_sec * 1e3 + ts.tv_nsec / 1e6;
}

#define EM_ASM(...) ((void)0)
#define EM_ASM_ARGS(...) ((void)0)
#define EM_ASM_INT(...) 0
#define EM_ASM_INT_V(...) 0
#define EM_ASM_DOUBLE(...) giac_wasi_now_ms()
#define EM_ASM_DOUBLE_V(...) giac_wasi_now_ms()
#define EMSCRIPTEN_KEEPALIVE __attribute__((used))

#endif
