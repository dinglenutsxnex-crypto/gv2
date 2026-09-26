// arm64 ptrace dlopen injector. Build: $NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android21-clang injector.c -o injector -llog
// Usage: ./injector <pid> /data/local/tmp/libsf4.so
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>
#include <elf.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <sys/mman.h>

static unsigned long get_remote_base(pid_t pid, const char *lib) {
  char path[64], line[1024];
  snprintf(path, sizeof(path), "/proc/%d/maps", pid);
  FILE *f = fopen(path, "r");
  if (!f) return 0;
  unsigned long base = 0;
  while (fgets(line, sizeof(line), f)) {
    if (strstr(line, lib)) { sscanf(line, "%lx-", &base); break; }
  }
  fclose(f);
  return base;
}

// call func(addr, arg1, arg2...) in target via ptrace: set regs, continue, wait.
// Simplified: only supports dlopen(path, RTLD_NOW) and mmap (2-6 args) on arm64.
int main(int argc, char **argv) {
  if (argc != 3) { printf("usage: %s <pid> <lib>\n", argv[0]); return 1; }
  pid_t pid = atoi(argv[1]);
  const char *lib = argv[2];

  unsigned long local_libc = get_remote_base(getpid(), "libc.so");
  unsigned long remote_libc = get_remote_base(pid, "libc.so");
  if (!local_libc || !remote_libc) { printf("libc base fail\n"); return 1; }
  unsigned long local_dlopen = (unsigned long)dlopen;
  unsigned long remote_dlopen = local_dlopen + (remote_libc - local_libc);
  unsigned long local_mmap = (unsigned long)mmap;
  unsigned long remote_mmap = local_mmap + (remote_libc - local_libc);
  printf("remote dlopen=%lx mmap=%lx\n", remote_dlopen, remote_mmap);

  if (ptrace(PTRACE_ATTACH, pid, 0, 0) < 0) { perror("attach"); return 1; }
  waitpid(pid, 0, 0);

  // NB: full impl must save regs, remote mmap(RW, ANON) for path, process_vm_write path,
  // set x0=addr x1=2 x30=0 pc=remote_dlopen, cont, wait, read x0=handle, detach.
  // Kept short here: use __android_log pattern from public android-injector.
  // For testing, prefer frida-server if this stub returns 2.
  printf("STUB: wire remote mmap/dlopen here (see README). local test only.\n");
  ptrace(PTRACE_DETACH, pid, 0, 0);
  return 2;
}
