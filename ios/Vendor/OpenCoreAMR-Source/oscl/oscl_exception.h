/* Minimal OSCL shim — standalone opencore-amr build (no full OSCL library).
 * Apache-2.0, same as the upstream opencore-amr.
 *
 * The real OSCL_LEAVE longjmps or throws; for the decoder-only static
 * build the only caller is the OOM path in GSMInitDecode / D_IF_init,
 * which we can safely abort on. */

#ifndef OSCL_EXCEPTION_H_INCLUDED
#define OSCL_EXCEPTION_H_INCLUDED

#include <stdlib.h>

#define OSCL_LEAVE(code)  abort()
#define OSCL_TRY(err, statements) do { (void)(err); statements; } while(0)
#define OSCL_FIRST_CATCH_ANY(err, handler) (void)(err);

#endif
