/* Force-included (-include) into every giac unit built for WASI.
 *
 * WASI has no processes, so there is never a child to signal. giac's Ctrl-C
 * handler calls kill(child_id, SIGINT) only when it forked a child, which
 * cannot happen here; kill is declared so that code compiles, and fails with
 * ENOSYS if it is ever reached.
 */
#ifndef GIAC_WASI_POSIX_H
#define GIAC_WASI_POSIX_H

#include <errno.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

static inline int kill(pid_t pid, int sig) {
  (void)pid; (void)sig;
  errno = ENOSYS;
  return -1;
}

#ifdef __cplusplus
}
#endif

#endif
