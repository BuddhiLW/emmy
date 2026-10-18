#!/usr/bin/env bash
# Compile giac for WASI and link two artifacts into $OUT:
#   giac.wasm      reactor module exporting caseval / giac_alloc / giac_free
#   giac-cli.wasm  command module: evaluates each argv entry, for testing
# Run build-deps.sh first (GMP + MPFR).
#
#   build.sh            compile what is missing, then link
#   build.sh compile    compile only; failures are listed, logs in $OBJ/*.log
#   build.sh link       link only
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
. "$here/env.sh"

# Same translation units as giac's CMake minigiac target.
SOURCES=(
  alg_ext.cc cocoa.cc csturm.cc derive.cc desolve.cc ezgcd.cc
  freeglut_stroke_roman.c gauss.cc gausspol.cc gen.cc global.cc help.cc
  identificateur.cc ifactor.cc index.cc input_lexer.cc input_parser.cc
  intg.cc intgab.cc isom.cc lin.cc maple.cc mathml.cc misc.cc modfactor.cc
  modpoly.cc moyal.cc opengl.cc pari.cc permu.cc plot.cc plot3d.cc prog.cc
  quater.cc risch.cc rpn.cc series.cc solve.cc sparse.cc subst.cc
  sym2poly.cc symbolic.cc tex.cc threaded.cc ti89.cc tinymt32.cc
  TmpFGLM.cpp TmpLESystemSolver.cpp unary.cc usual.cc vecteur.cc
)

# Defines follow giac's Emscripten build (build.gradle, emccCompileWasm), so the
# code takes its tested Wasm paths. EMCC2 pulls in shim/emscripten.h.
DEFS=(-DIN_GIAC -DGIAC_GENERIC_CONSTANTS -DHAVE_CONFIG_H -DGIAC_GGB -DTIMEOUT
      -DEMCC2 -DHAVE_UNISTD_H -Dgammaf=tgammaf '-DVERSION="1.9.0"'
      -DHAVE_NO_PWD_H -DHAVE_NO_SYS_RESOURCE_WAIT_H)  # giac's own switches; WASI has neither
INCS=(-include "$here/shim/wasi_posix.h"
      -I"$here/shim" -I"$PREFIX/include" -I"$GIAC_SRC/src/giac/headers")
OPT=${OPT:--O2}
CXXFLAGS=($OPT -w $EH_CFLAGS $EMU_CFLAGS "${DEFS[@]}" "${INCS[@]}")

compile() {
  mkdir -p "$OBJ"
  local failed=()
  printf '%s\n' "${SOURCES[@]}" |
    xargs -P "$JOBS" -I{} bash -c '
      src="$GIAC_SRC/src/giac/cpp/$1"; o="$OBJ/${1%.*}.o"; log="$OBJ/${1%.*}.log"
      [ -f "$o" ] && [ "$o" -nt "$src" ] && exit 0
      case "$1" in *.c) cc="$CC" ;; *) cc="$CXX" ;; esac
      if "$cc" "${@:2}" -c "$src" -o "$o" >"$log" 2>&1; then rm -f "$log"
      else rm -f "$o"; echo "FAIL $1"; fi' _ {} "${CXXFLAGS[@]}" || true
  for s in "${SOURCES[@]}"; do
    [ -f "$OBJ/${s%.*}.o" ] || failed+=("$s")
  done
  if [ ${#failed[@]} -gt 0 ]; then
    echo "compile: ${#failed[@]} of ${#SOURCES[@]} failed: ${failed[*]}"
    return 1
  fi
  echo "compile: all ${#SOURCES[@]} units ok"
}

link() {
  mkdir -p "$OUT"
  "$CXX" "${CXXFLAGS[@]}" -c "$here/glue/giac_wasi.cc" -o "$OBJ/giac_wasi.o"
  "$CXX" "${CXXFLAGS[@]}" -c "$here/glue/giac_cli.cc" -o "$OBJ/giac_cli.o"
  local objs=()
  for s in "${SOURCES[@]}"; do objs+=("$OBJ/${s%.*}.o"); done
  local libs=("$PREFIX/lib/libmpfr.a" "$PREFIX/lib/libgmp.a" $EMU_LIBS)  # MPFR before GMP
  "$CXX" $OPT $EH_LDFLAGS -mexec-model=reactor \
    "${objs[@]}" "$OBJ/giac_wasi.o" "${libs[@]}" -o "$OUT/giac.wasm"
  "$CXX" $OPT $EH_LDFLAGS \
    "${objs[@]}" "$OBJ/giac_wasi.o" "$OBJ/giac_cli.o" "${libs[@]}" -o "$OUT/giac-cli.wasm"
  ls -la "$OUT"
}

case "${1:-all}" in
  compile) compile ;;
  link) link ;;
  all) compile && link ;;
  *) echo "usage: $0 [compile|link|all]" >&2; exit 2 ;;
esac
