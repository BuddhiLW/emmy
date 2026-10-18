# Shared settings for the giac -> WebAssembly (WASI) build. Source, don't run.
#
# Override any of these from the environment.
: "${WASI_SDK:=$HOME/.local/opt/wasi-sdk-34.0-x86_64-linux}"
: "${GIAC_SRC:=$HOME/PP/giac}"                # checkout of github.com/geogebra/giac
: "${GIAC_WASM_BUILD:=$HOME/.cache/giac-wasm}" # all build output lives here
: "${WASI_TARGET:=wasm32-wasip1}"
: "${JOBS:=6}"                                 # giac TUs are large; keep RAM in check

GMP_VERSION=6.3.0
MPFR_VERSION=4.2.2

CC="$WASI_SDK/bin/$WASI_TARGET-clang"
CXX="$WASI_SDK/bin/$WASI_TARGET-clang++"
AR="$WASI_SDK/bin/llvm-ar"
RANLIB="$WASI_SDK/bin/llvm-ranlib"

# Standard wasm exception handling, as required by wasi-sdk >= 33 (CppExceptions.md).
EH_CFLAGS="-fwasm-exceptions -mllvm -wasm-use-legacy-eh=false"
EH_LDFLAGS="-fwasm-exceptions -lunwind"

# wasi-libc emulations for POSIX bits GMP and giac use (raise/signal, getpid).
EMU_CFLAGS="-D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_GETPID -D_WASI_EMULATED_PROCESS_CLOCKS"
EMU_LIBS="-lwasi-emulated-signal -lwasi-emulated-getpid -lwasi-emulated-process-clocks"

PREFIX="$GIAC_WASM_BUILD/prefix"   # GMP + MPFR install root
OBJ="$GIAC_WASM_BUILD/obj"
OUT="$GIAC_WASM_BUILD/out"

export WASI_SDK GIAC_SRC GIAC_WASM_BUILD WASI_TARGET JOBS CC CXX AR RANLIB PREFIX OBJ OUT
