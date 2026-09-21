#!/usr/bin/env bash
# Canonical retained-owner entry point. Resolve helpers from this file, not a
# caller's ROOT/SCRIPTS/RECEIPT environment or the executor's working directory.
# exec preserves the foreground PTY and makes its retained PID the real owner.
set -Eeuo pipefail
umask 077

fail() { printf 'physical launch failed: %s-no-spawn\n' "$1" >&2; exit 2; }
case "${1-}" in
  collector) helper=physical_collector_session.mjs; mode=run ;;
  collector-check) helper=physical_collector_session.mjs; mode=check ;;
  workload) helper=physical_workload_receipt.mjs; mode=owner-script ;;
  workload-preflight) helper=physical_workload_receipt.mjs; mode=script-preflight ;;
  *) fail explicit-launch-mode-required ;;
esac
shift
[ "$#" -gt 0 ] || fail explicit-launch-arguments-required
helper_dir="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || fail helper-directory-unavailable
[ -f "$helper_dir/$helper" ] && [ ! -L "$helper_dir/$helper" ] || fail sibling-helper-unavailable
# Forward the original argument vector; never evaluate a shell command string.
# The existing helpers retain all role, private-path, readiness and PTY gates.
exec node "$helper_dir/$helper" "$mode" "$@"
