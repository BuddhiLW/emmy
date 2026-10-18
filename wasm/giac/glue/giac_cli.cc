// Command-line wrapper around caseval, for testing giac.wasm under a WASI
// runtime without a host binding:
//   zwasm run giac-cli.wasm 'factor(x^4-1)' 'integrate(1/(1+x^2),x)'
// Each argument is evaluated in order, in one shared giac context.
#include <cstdio>

extern "C" const char *giac_wasi_caseval(const char *s);

int main(int argc, char **argv) {
  for (int i = 1; i < argc; ++i)
    std::printf("%s\n", giac_wasi_caseval(argv[i]));
  return 0;
}
