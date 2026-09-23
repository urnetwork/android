#!/usr/bin/env bash
# Execute the production cleanup boundary with finite fake package/identity
# endpoints. Never load runner startup, build APKs, contact ADB or the network.
set -euo pipefail
umask 077
here="$(cd "$(dirname "$0")" && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }
fixture="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-reporting.test.XXXXXX")"
trap 'rm -rf "$fixture"' EXIT

for helper in uninstall_acceptance_packages record_acceptance_cleanup_failure; do
  # shellcheck disable=SC2294
  eval "$(sed -n "/^$helper()/,/^}/p" "$here/test-main.sh")"
done
cleanup_boundary="$(sed -n \
  '/^    if ! uninstall_acceptance_packages "\$serial" "\$out\/post-acceptance-cleanup"; then$/,/^    fi$/p' \
  "$here/test-main.sh")"
[ -n "$cleanup_boundary" ] || fail "missing production post-acceptance cleanup boundary"

for mode in ownership-lost removal-unverified prior-crash complete; do
  (
    out="$fixture/$mode"
    mkdir -p "$out"
    printf 'OK (2 tests)\nINSTRUMENTATION_CODE: -1\n' >"$out/instrumentation.log"
    serial=emulator-5554
    adb=never-contact-adb
    android_acceptance_timeout_executable=never-run-timeout
    test_status=0
    p2p_status=0
    overall=0
    run_peer_to_peer=1
    package_calls=0
    ownership_checks=0
    if [ "$mode" = prior-crash ]; then
      test_status=1
      printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n' >"$out/instrumentation.log"
    fi
    authorize_selected_device() {
      [ "$1" = "$serial" ] || fail "cleanup changed the selected serial"
      ownership_checks=$((ownership_checks + 1))
      [ "$mode" != ownership-lost ]
    }
    android_acceptance_uninstall_package() {
      [ "$1:$2:$3" = "never-run-timeout:never-contact-adb:$serial" ] || fail "cleanup selected a live device tool"
      case "$4" in com.bringyour.network|com.bringyour.network.test) ;; *) fail "cleanup changed package scope" ;; esac
      package_calls=$((package_calls + 1))
      printf 'bounded uninstall fixture\n' >"$5"
      [ "$mode" = complete ]
    }
    # Execute the real callsite, including when the prior test status is
    # captured, rather than simulating a cleaned-up result in the summarizer.
    # shellcheck disable=SC2294
    eval "$cleanup_boundary"
    [ "$ownership_checks" = 1 ] || fail "cleanup skipped its exact-device ownership gate"
    if [ "$mode" = complete ]; then
      [ "$package_calls:$test_status:$p2p_status" = 2:0:0 ] || fail "complete cleanup changed acceptance status"
      [ ! -e "$out/cleanup-failure.json" ] || fail "successful cleanup recorded a false failure"
    else
      [ "$test_status:$p2p_status" = 1:1 ] || fail "cleanup failure did not fail both affected cells"
      [ -f "$out/cleanup-failure.json" ] || fail "failed cleanup has no cell-local provenance"
      [ -f "$out/post-acceptance-cleanup/cleanup-status.txt" ] || fail "cleanup failure has no bounded diagnostic"
      if [ "$mode" = ownership-lost ]; then
        [ "$package_calls" = 0 ] || fail "unowned device received package mutation"
        grep -Fxq 'status=ownership-unavailable' "$out/post-acceptance-cleanup/cleanup-status.txt" || \
          fail "lost identity cleanup omitted its exact infrastructure reason"
      else
        [ "$package_calls" = 2 ] || fail "independent package cleanup attempts were lost"
        grep -Fxq 'status=package-removal-unverified' "$out/post-acceptance-cleanup/cleanup-status.txt" || \
          fail "failed package removal lacked finite provenance"
      fi
    fi

    REPORTING_MODE="$mode" REPORTING_OUT="$out" REPORTING_SCRIPT="$here/scripts/acceptance-result.mjs" \
      node --input-type=module <<'JS'
import assert from "node:assert/strict";
import path from "node:path";
import { pathToFileURL } from "node:url";
const { buildResult } = await import(pathToFileURL(process.env.REPORTING_SCRIPT));
const mode = process.env.REPORTING_MODE;
const directory = process.env.REPORTING_OUT;
const result = buildResult({
  outputPath: path.join(directory, "result.json"),
  environment: {
    UR_ACCEPT_RESULT_TARGET: "github", UR_ACCEPT_RESULT_PHASE: "instrumentation",
    UR_ACCEPT_RESULT_STATUS: mode === "complete" ? "passed" : "failed",
    UR_ACCEPT_RESULT_EXIT_CODE: mode === "complete" ? "0" : "1",
    UR_ACCEPT_RESULT_ARTIFACT_ROOT: directory,
    UR_ACCEPT_RESULT_LOG: path.join(directory, "instrumentation.log"),
  },
});
if (mode === "complete") {
  assert.equal(result.failure, null);
} else if (mode === "prior-crash") {
  assert.equal(result.failure.classification, "crash");
  assert.match(result.failure.signature, /Process crashed/);
  assert.equal(result.failure.cleanupFailure.precedingTestExitCode, 1);
} else {
  assert.equal(result.failure.classification, "infrastructure");
  assert.equal(result.failure.originalCause.precedingTestExitCode, 0);
  assert.notEqual(result.failure.signature, "no recognizable failure line");
  assert.ok(result.artifacts.some((artifact) => artifact.path === "cleanup-failure.json"));
  assert.ok(result.artifacts.some((artifact) => artifact.path === "post-acceptance-cleanup/cleanup-status.txt"));
}
JS
  ) || fail "post-acceptance cleanup reporting control: $mode"
done

echo "android cleanup failure reporting tests passed"
