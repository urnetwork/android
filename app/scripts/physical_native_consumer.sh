#!/usr/bin/env bash
# Post-writer consumer barrier. Does not build the native SDK or use a device.
# The only consumer command is the explicit argv after --. Keep it foreground;
# the owning shell retains the existing SDK output lock through command join.
set -Eeuo pipefail
umask 077

fail() { printf 'native consumer failed: %s-no-consumer-spawn\n' "$1" >&2; exit 2; }
root='' before='' after='' proof='' writer_receipt=''
while [ "$#" -gt 0 ] && [ "$1" != -- ]; do
  [ "$#" -ge 2 ] && [ -n "$2" ] || fail explicit-arguments-required
  case "$1" in
    --root) [ -z "$root" ] || fail duplicate-argument; root="$2" ;;
    --before) [ -z "$before" ] || fail duplicate-argument; before="$2" ;;
    --after) [ -z "$after" ] || fail duplicate-argument; after="$2" ;;
    --proof) [ -z "$proof" ] || fail duplicate-argument; proof="$2" ;;
    --writer-receipt) [ -z "$writer_receipt" ] || fail duplicate-argument; writer_receipt="$2" ;;
    *) fail explicit-arguments-required ;;
  esac
  shift 2
done
[ "$#" -ge 2 ] && [ "$1" = -- ] && [ -n "$2" ] || fail explicit-consumer-command-required
shift
[ -n "$root" ] && [ -n "$before" ] && [ -n "$after" ] && [ -n "$proof" ] && [ -n "$writer_receipt" ] || fail explicit-arguments-required
case "$root:$before:$after:$proof:$writer_receipt" in *$'\n'*|*$'\r'*) fail invalid-path ;; esac
for path in "$root" "$before" "$after" "$proof" "$writer_receipt"; do
  case "$path" in /*) ;; *) fail absolute-paths-required ;; esac
done
# This entry point owns a new consumer lock AFTER the writer has exited. Do not
# accidentally inherit the writer role or a stale lock marker from another call.
for marker in URNETWORK_ANDROID_SDK_OUTPUT_LOCK_HELD URNETWORK_ANDROID_SDK_OUTPUT_LOCK_DIR \
  URNETWORK_ANDROID_SDK_OUTPUT_LOCK_PATH URNETWORK_ANDROID_SDK_OUTPUT_LOCK_FD \
  URNETWORK_ANDROID_SDK_OUTPUT_LOCK_TOKEN URNETWORK_ANDROID_SDK_OUTPUT_LOCK_ROLE; do
  [ -z "${!marker-}" ] || fail inherited-output-lock-not-accepted
done
helper_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || fail helper-unavailable
consumer_directory="$(pwd -P)" || fail working-directory-unavailable
cd -- "$root/sdk/build" 2>/dev/null || fail sdk-output-directory-unavailable
# shellcheck source=/dev/null
source ./sdk-android-output-lock.sh >/dev/null 2>&1 || fail output-lock-helper-unavailable
# The existing lock helper can print private owner metadata on contention.
# Suppress it; the wrapper reports only this fixed reason, never pid/token/path.
sdk_android_output_lock_acquire physical-native-consumer >/dev/null 2>&1 || fail output-lock-unavailable
sdk_android_output_lock_verify_held >/dev/null 2>&1 || fail output-lock-not-held
node "$helper_dir/physical_native_provenance.mjs" prepare-consumer \
  --root "$root" --before "$before" --after "$after" --output "$proof" --writer-receipt "$writer_receipt" || exit 2
[ -s "$after" ] && [ -s "$proof" ] || fail native-proof-not-published
sdk_android_output_lock_verify_held >/dev/null 2>&1 || fail output-lock-lost
cd -- "$consumer_directory" || fail working-directory-unavailable
# No eval, shell-string composition, backgrounding or lock release before join.
"$@"
