#!/usr/bin/env bash
# Build static GMP and MPFR for WASI with wasi-sdk, into $PREFIX.
# giac's own prebuilt wasm archives target an old Emscripten libc (they import
# newlib's iprintf/fiprintf), so they do not link against wasi-libc.
set -euo pipefail
. "$(dirname "$0")/env.sh"

mkdir -p "$GIAC_WASM_BUILD/src" "$PREFIX"
cd "$GIAC_WASM_BUILD/src"

fetch() { # url file
  [ -f "$2" ] || curl -sSfL -o "$2" "$1"
}
# Upstream sites; ftp.gnu.org is often unreachable. mirrors.kernel.org/gnu carries both too.
fetch "https://gmplib.org/download/gmp/gmp-$GMP_VERSION.tar.xz" "gmp-$GMP_VERSION.tar.xz"
fetch "https://www.mpfr.org/mpfr-$MPFR_VERSION/mpfr-$MPFR_VERSION.tar.xz" "mpfr-$MPFR_VERSION.tar.xz"

# --host=none + --disable-assembly selects GMP's generic C code; limbs are 32-bit on wasm32.
if [ ! -f "$PREFIX/lib/libgmp.a" ]; then
  rm -rf "gmp-$GMP_VERSION" && tar xJf "gmp-$GMP_VERSION.tar.xz"
  (cd "gmp-$GMP_VERSION" &&
     ./configure --build="$(./config.guess)" --host=none --disable-assembly \
       --enable-static --disable-shared --prefix="$PREFIX" \
       CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 $EMU_CFLAGS" LIBS="$EMU_LIBS" \
       CC_FOR_BUILD=cc &&
     make -j"$JOBS" && make install)
fi

if [ ! -f "$PREFIX/lib/libmpfr.a" ]; then
  rm -rf "mpfr-$MPFR_VERSION" && tar xJf "mpfr-$MPFR_VERSION.tar.xz"
  (cd "mpfr-$MPFR_VERSION" &&
     ./configure --build="$(./config.guess)" --host=none \
       --enable-static --disable-shared --disable-thread-safe \
       --with-gmp="$PREFIX" --prefix="$PREFIX" \
       CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 $EMU_CFLAGS" LIBS="$EMU_LIBS" &&
     make -j"$JOBS" && make install)
fi

ls -la "$PREFIX/lib"
