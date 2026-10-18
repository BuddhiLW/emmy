// WASI exports for giac: one string-in, string-out entry point plus the
// allocator a host needs to pass that string in.
//
// Host protocol:
//   p = giac_alloc(len + 1); write the UTF-8 command and a NUL at p;
//   r = caseval(p); read the NUL-terminated result at r; giac_free(p).
// The result buffer belongs to giac and stays valid until the next call.
#include <cerrno>
#include <cstdlib>
#include <exception>

extern "C" {

// Defined in giac's gen.cc with C linkage.
const char *caseval(const char *s);

// WASI has no shell; giac's shell escapes (global.cc) fail cleanly.
int system(const char *command) {
  if (!command) return 0;  // "no command processor available"
  errno = ENOSYS;
  return -1;
}

__attribute__((export_name("giac_alloc")))
void *giac_alloc(size_t n) { return std::malloc(n); }

__attribute__((export_name("giac_free")))
void giac_free(void *p) { std::free(p); }

__attribute__((export_name("caseval")))
const char *giac_wasi_caseval(const char *s) {
  try {
    return caseval(s);
  } catch (const std::exception &e) {
    static thread_local char buf[512];
    __builtin_snprintf(buf, sizeof buf, "error: %s", e.what());
    return buf;
  } catch (...) {
    return "error: unknown exception";
  }
}

}
