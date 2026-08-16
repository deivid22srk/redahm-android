/**
 * @file        rex/core/fiber_android.cpp
 * @brief       Android (ARM64) backend for rex::thread::Fiber
 *
 * bionic removed getcontext/makecontext/swapcontext, so fibers use a
 * hand-rolled ARM64 context switch that preserves the AAPCS64 callee-saved
 * registers (x19-x28, x29, x30, sp, d8-d15).
 *
 * @copyright   Copyright (c) 2026 Tom Clay <tomc@tctechstuff.com>
 *              All rights reserved.
 *
 * @license     BSD 3-Clause License
 *              See LICENSE file in the project root for full license text.
 */

#include <rex/platform.h>
#if REX_PLATFORM_ANDROID && defined(__aarch64__)

#include <rex/thread/fiber.h>

#include <cassert>
#include <cstring>

namespace rex::thread {

thread_local Fiber* Fiber::tls_current_ = nullptr;

// Context layout (must match Fiber::AndroidContext): x19-x28, x29, x30, sp,
// d8-d15, 21 entries of 8 bytes each.

extern "C" void rex_android_fiber_save(uint64_t* ctx);
extern "C" void rex_android_fiber_switch(uint64_t* from, const uint64_t* to);

#define REX_ANDROID_FIBER_STORE_REGS " \
      stp x19, x20, [x0, #0]\n \
      stp x21, x22, [x0, #16]\n \
      stp x23, x24, [x0, #32]\n \
      stp x25, x26, [x0, #48]\n \
      stp x27, x28, [x0, #64]\n \
      stp x29, x30, [x0, #80]\n \
      mov x2, sp\n \
      str x2, [x0, #96]\n \
      stp d8, d9, [x0, #104]\n \
      stp d10, d11, [x0, #120]\n \
      stp d12, d13, [x0, #136]\n \
      stp d14, d15, [x0, #152]\n"

__attribute__((naked, visibility("hidden"))) void rex_android_fiber_save(uint64_t* ctx) {
  __asm__ __volatile__(REX_ANDROID_FIBER_STORE_REGS "ret\n" ::: "memory");
}

__attribute__((naked, visibility("hidden"))) void rex_android_fiber_switch(
    uint64_t* from, const uint64_t* to) {
  __asm__ __volatile__(REX_ANDROID_FIBER_STORE_REGS
      "ldp x19, x20, [x1, #0]\n"
      "ldp x21, x22, [x1, #16]\n"
      "ldp x23, x24, [x1, #32]\n"
      "ldp x25, x26, [x1, #48]\n"
      "ldp x27, x28, [x1, #64]\n"
      "ldp x29, x30, [x1, #80]\n"
      "ldr x2, [x1, #96]\n"
      "mov sp, x2\n"
      "ldp d8, d9, [x1, #104]\n"
      "ldp d10, d11, [x1, #120]\n"
      "ldp d12, d13, [x1, #136]\n"
      "ldp d14, d15, [x1, #152]\n"
      "ret\n" ::: "memory");
}

Fiber* Fiber::ConvertCurrentThread() {
  auto* f = new Fiber();
  f->is_thread_fiber_ = true;
  rex_android_fiber_save(reinterpret_cast<uint64_t*>(&f->context_));
  // The switch above captures the current registers and immediately resumes
  // here via the stored lr.
  tls_current_ = f;
  return f;
}

Fiber* Fiber::Create(size_t stack_size, void (*entry)(void*), void* arg) {
  auto* f = new Fiber();
  f->entry_ = entry;
  f->arg_ = arg;
  f->stack_.resize(stack_size);

  std::memset(&f->context_, 0, sizeof(f->context_));
  uintptr_t stack_top =
      reinterpret_cast<uintptr_t>(f->stack_.data() + f->stack_.size());
  // AAPCS64 requires sp to be 16-byte aligned on function entry.
  f->context_.sp = (stack_top - 16) & ~static_cast<uintptr_t>(15);
  f->context_.lr = reinterpret_cast<uint64_t>(&Fiber::Trampoline);
  return f;
}

/*static*/ void Fiber::Trampoline() {
  // tls_current_ was updated by SwitchTo before the switch into this fiber.
  Fiber* f = tls_current_;
  f->entry_(f->arg_);
  // A fiber entry should never return; trap instead of crashing later.
  __builtin_trap();
}

void Fiber::SwitchTo(Fiber* target) {
  Fiber* from = tls_current_;
  tls_current_ = target;
  rex_android_fiber_switch(reinterpret_cast<uint64_t*>(&from->context_),
                           reinterpret_cast<uint64_t*>(&target->context_));
}

void Fiber::Destroy() {
  if (is_thread_fiber_) {
    tls_current_ = nullptr;
  } else {
    assert(this != tls_current_ && "Destroy called on the currently running fiber");
  }
  delete this;
}

}  // namespace rex::thread

#endif  // REX_PLATFORM_ANDROID && __aarch64__