// Command-line wrapper around caseval, for testing giac.wasm under a WASI
// runtime and for hosts that drive giac as a child process.
//
//   giac-cli.wasm 'factor(x^4-1)' 'integrate(1/(1+x^2),x)'
//     evaluates each argument in order and prints one result per line.
//
//   giac-cli.wasm            (no arguments)
//     server mode: reads one command per line from stdin and writes each
//     result framed as STX result ETX newline. giac prints its own log lines
//     ("// Parsing f") to stdout too; they fall outside the frame.
//
// All commands share one giac context, so definitions persist.
#include <cstdio>
#include <string>

extern "C" const char *giac_wasi_caseval(const char *s);

int main(int argc, char **argv) {
  if (argc > 1) {
    for (int i = 1; i < argc; ++i)
      std::printf("%s\n", giac_wasi_caseval(argv[i]));
    return 0;
  }
  std::string line;
  int c;
  while ((c = std::getchar()) != EOF) {
    if (c != '\n') {
      line.push_back(static_cast<char>(c));
      continue;
    }
    const char *r = giac_wasi_caseval(line.c_str());
    std::printf("\x02%s\x03\n", r);
    std::fflush(stdout);
    line.clear();
  }
  return 0;
}
