#!/usr/bin/env bash
# No devices, network, APK builds or real ADB: execute the production collector
# and teardown against deterministic read/child-lifecycle endpoints.
set -euo pipefail
umask 077
here="$(cd "$(dirname "$0")" && pwd)"
source "$here/test-main-lib.sh"
fail() { echo "FAIL: $*" >&2; exit 1; }
fixture="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-p2p.test.XXXXXX")"
trap 'rm -rf "$fixture"' EXIT

for helper in collect_physical_artifacts_once collect_physical_artifacts record_p2p_failure finish_physical_session retain_physical_cleanup_ownership clear_physical_cleanup_ownership cleanup_physical_sessions; do
  # Only named production function definitions are loaded, never runner startup.
  # shellcheck disable=SC2294
  eval "$(sed -n "/^$helper()/,/^}/p" "$here/test-main.sh")"
done

(
  adb=fake_cleanup_adb
  run_dir=unused-test-run-dir
  identity_valid=0
  ledger_reads=0
  marker_deletes=0
  authorize_selected_device() { [ "$1" = emulator-5556 ] && [ "$identity_valid" = 1 ]; }
  pull_android_acceptance_active_clients() { ledger_reads=$((ledger_reads + 1)); }
  pull_android_acceptance_private_client_id() { ledger_reads=$((ledger_reads + 1)); }
  timeout() { shift; "$@"; }
  fake_cleanup_adb() {
    [ "$*" = '-s emulator-5556 shell run-as com.bringyour.network rm -f files/acceptance/physical-active-client-id files/acceptance/active-client-ids' ] || fail "cleanup targeted unapproved files"
    marker_deletes=$((marker_deletes + 1))
  }
  if retain_physical_cleanup_ownership emulator-5556 provider "$fixture" 2; then fail "lost device identity supplied cleanup client IDs"; fi
  if clear_physical_cleanup_ownership emulator-5556; then fail "lost device identity allowed private marker deletion"; fi
  [ "$ledger_reads:$marker_deletes" = 0:0 ] || fail "unowned device was read or mutated during ledger cleanup"
  identity_valid=1
  retain_physical_cleanup_ownership emulator-5556 provider "$fixture" 2 || fail "owned device ledgers not retained"
  clear_physical_cleanup_ownership emulator-5556 || fail "owned private markers not cleared"
  [ "$ledger_reads:$marker_deletes" = 2:1 ] || fail "owned cleanup was not exact"
) || fail "P2P client-ownership cleanup identity gate"

for mode in complete failed-client-pull failed-provider-pull failed-release failed-marker-clear; do
  (
    out="$fixture/cleanup-$mode"
    mkdir -p "$out"
    p2p_cleanup_failed=0
    retained=0
    released=0
    cleared=0
    retain_physical_cleanup_ownership() {
      retained=$((retained + 1))
      case "$mode:$2" in failed-client-pull:client|failed-provider-pull:provider) return 1 ;; esac
    }
    release_active_clients() { released=$((released + 1)); [ "$mode" != failed-release ]; }
    clear_physical_cleanup_ownership() { cleared=$((cleared + 1)); [ "$mode" != failed-marker-clear ]; }
    result=0
    cleanup_physical_sessions "$out" emulator-5554 emulator-5556 || result=$?
    [ "$retained:$released" = 2:1 ] || fail "independent cleanup ledgers were not attempted"
    case "$mode" in
      complete) [ "$result:$cleared:$p2p_cleanup_failed" = 0:2:0 ] || fail "complete cleanup failed" ;;
      failed-marker-clear) [ "$result:$cleared:$p2p_cleanup_failed" = 1:2:1 ] || fail "marker clear failure was hidden" ;;
      *) [ "$result:$cleared:$p2p_cleanup_failed" = 1:0:1 ] || fail "failed ownership retention/release erased recovery ledgers" ;;
    esac
  ) || fail "P2P cleanup retention control $mode"
done

success_transcript() {
  printf '%s\n' \
    'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
    'INSTRUMENTATION_STATUS: current=1' \
    'INSTRUMENTATION_STATUS: numtests=1' \
    'INSTRUMENTATION_STATUS: test=physicalLowbarSession' \
    'INSTRUMENTATION_STATUS_CODE: 1' \
    'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
    'INSTRUMENTATION_STATUS: current=1' \
    'INSTRUMENTATION_STATUS: numtests=1' \
    'INSTRUMENTATION_STATUS: test=physicalLowbarSession' \
    'INSTRUMENTATION_STATUS_CODE: 0' \
    'INSTRUMENTATION_RESULT: stream=' '' 'Time: 25.25' '' \
    'OK (1 test)' '' 'INSTRUMENTATION_CODE: -1'
}
success_transcript >"$fixture/success.log"
android_acceptance_verify_p2p_instrumentation "$fixture/success.log" || fail "complete terminal receipt rejected"
sed 's/$/\r/' "$fixture/success.log" >"$fixture/crlf.log"
android_acceptance_verify_p2p_instrumentation "$fixture/crlf.log" || fail "CRLF terminal receipt rejected"
for mutation in empty start-only missing-final wrong-final zero-tests wrong-test duplicate-final duplicate-start trailing-data crash-then-success failed-then-success; do
  case "$mutation" in
    empty) : >"$fixture/invalid.log" ;;
    start-only) sed -n '1,5p' "$fixture/success.log" >"$fixture/invalid.log" ;;
    missing-final) sed '/^INSTRUMENTATION_CODE:/d' "$fixture/success.log" >"$fixture/invalid.log" ;;
    wrong-final) sed 's/INSTRUMENTATION_CODE: -1/INSTRUMENTATION_CODE: 0/' "$fixture/success.log" >"$fixture/invalid.log" ;;
    zero-tests) sed 's/OK (1 test)/OK (0 tests)/' "$fixture/success.log" >"$fixture/invalid.log" ;;
    wrong-test) sed 's/test=physicalLowbarSession/test=someOtherTest/' "$fixture/success.log" >"$fixture/invalid.log" ;;
    duplicate-final) cp "$fixture/success.log" "$fixture/invalid.log"; printf 'INSTRUMENTATION_CODE: -1\n' >>"$fixture/invalid.log" ;;
    duplicate-start) printf 'INSTRUMENTATION_STATUS_CODE: 1\n' >"$fixture/invalid.log"; success_transcript >>"$fixture/invalid.log" ;;
    trailing-data) cp "$fixture/success.log" "$fixture/invalid.log"; printf 'adb: device offline\n' >>"$fixture/invalid.log" ;;
    crash-then-success) printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n' >"$fixture/invalid.log"; success_transcript >>"$fixture/invalid.log" ;;
    failed-then-success) printf 'FAILURES!!!\n' >"$fixture/invalid.log"; success_transcript >>"$fixture/invalid.log" ;;
  esac
  if android_acceptance_verify_p2p_instrumentation "$fixture/invalid.log"; then
    fail "$mutation instrumentation stream qualified as success"
  fi
done
ln -s "$fixture/success.log" "$fixture/symlink.log"
if android_acceptance_verify_p2p_instrumentation "$fixture/symlink.log"; then fail "symlink receipt accepted"; fi

for invalid_pid in '' 0 00 -1 1:2 not-a-pid; do
  if android_acceptance_wait_for_session_exit "$invalid_pid" 1; then fail "invalid child PID accepted"; fi
done
for invalid_polls in '' 0 00 -1 1:2 01; do
  if android_acceptance_wait_for_session_exit "$$" "$invalid_polls"; then fail "invalid grace bound accepted"; fi
done

# Also exercise real shell child/wait semantics, without a device. Both a
# clean exit and a nonzero exit publish identical positive terminal text.
for child_result in 0 7; do
  (
    out="$fixture/real-child-$child_result"
    mkdir -p "$out"
    p2p_cleanup_failed=0
    adb=never-contact-adb
    authorize_selected_device() { [ "$1" = emulator-5554 ]; }
    send_physical_command() { return 0; }
    wait_physical_status() { return 0; }
    timeout() { fail "naturally exiting host child caused a device command"; }
    (
      sleep 0.05
      success_transcript >"$out/client-instrumentation.log"
      exit "$child_result"
    ) &
    actual_child=$!
    result=0
    finish_physical_session emulator-5554 client "$actual_child" "$out" || result=$?
    if [ "$child_result" = 0 ]; then
      [ "$result" = 0 ] || fail "real child natural exit rejected"
    else
      [ "$result" != 0 ] || fail "real nonzero child exit hidden by successful receipt"
    fi
  ) || fail "real retained child exit control"
done

mkdir -p "$fixture/logs"
printf 'bounded app log\n' >"$fixture/logs/app.log"
for mode in recovered persistent unrelated-error invalid-status invalid-png ownership-lost ownership-lost-after-read; do
  (
    out="$fixture/collection-$mode"
    adb=fake_adb
    reads=0
    ownership_checks=0
    timeout() { shift; "$@"; }
    sleep() { [ "$1" = 1 ] || fail "unexpected collector backoff"; }
    authorize_selected_device() {
      [ "$1" = emulator-5556 ] || fail "collector selected another serial"
      ownership_checks=$((ownership_checks + 1))
      if [ "$mode" = ownership-lost ] || \
         { [ "$mode" = ownership-lost-after-read ] && [ "$ownership_checks" -gt 1 ]; }; then return 1; fi
    }
    fake_adb() {
      [ "$1:$2" = '-s:emulator-5556' ] || fail "collector contacted another device"
      shift 2
      printf '%s\n' "$*" >>"$fixture/reads-$mode"
      case "$*" in
        'logcat -d -t 12000')
          if [ "$mode" = persistent ] || \
             { [ "$attempt" = 1 ] && { [ "$mode" = recovered ] || [ "$mode" = ownership-lost-after-read ]; }; }; then
            printf 'partial retained log\n'
            printf 'adb: device offline\n' >&2
            return 1
          elif [ "$mode" = unrelated-error ]; then
            printf 'local output error\n' >&2
            return 1
          fi
          printf 'complete retained log\n' ;;
        'exec-out screencap -p')
          if [ "$mode" = invalid-png ]; then printf 'not a png\n'; else printf '\211PNG\015\012\032\012fixture\n'; fi ;;
        'shell dumpsys activity activities') printf 'activity fixture\n' ;;
        'shell ps -A') printf 'process fixture\n' ;;
        'exec-out run-as com.bringyour.network cat files/acceptance/physical-status')
          if [ "$mode" = invalid-status ]; then printf '{'; else printf '{"state":"complete","commandId":"provider-proof","extra":{}}\n'; fi ;;
        'exec-out run-as com.bringyour.network cat files/acceptance/physical-startup-goroutines.txt') return 1 ;;
        'exec-out run-as com.bringyour.network tar -C files/logs -cf - .') command tar -C "$fixture/logs" -cf - . ;;
        *) fail "collector attempted a mutation or unexpected read: $*" ;;
      esac
    }
    result=0
    collect_physical_artifacts emulator-5556 "$out" || result=$?
    case "$mode" in
      recovered)
        [ "$result" = 0 ] && [ "$ownership_checks" = 2 ] || fail "same-owner recovery did not complete bounded retry"
        grep -Fq 'partial retained log' "$out/attempt-1/logcat.txt" || fail "first attempt was overwritten"
        grep -Fq 'adb: device offline' "$out/attempt-1/collection.stderr" || fail "first transport failure was erased"
        grep -Fq 'complete retained log' "$out/logcat.txt" || fail "recovered artifact was not published"
        [ -s "$out/glog/app.log" ] || fail "app logs were not retained" ;;
      persistent) [ "$result" != 0 ] && [ "$ownership_checks" = 3 ] || fail "persistent transport failure escaped retry bound" ;;
      ownership-lost)
        [ "$result" != 0 ] && [ "$ownership_checks" = 3 ] || fail "lost identity was accepted"
        [ ! -e "$fixture/reads-$mode" ] || fail "collector read a target without ownership" ;;
      ownership-lost-after-read)
        [ "$result" != 0 ] && [ "$ownership_checks" = 3 ] || fail "identity loss after disconnect was accepted"
        [ "$(wc -l <"$fixture/reads-$mode" | tr -d ' ')" = 1 ] || fail "collector read after losing ownership" ;;
      *) [ "$result" != 0 ] && [ "$ownership_checks" = 1 ] || fail "non-transport failure was retried or accepted: $mode" ;;
    esac
  ) || fail "artifact collector control $mode"
done

# Model child liveness with a virtual tick counter. The actual production grace
# helper polls it; no sleeps, processes, ADB or connected devices are required.
for mode in natural artifact-error lost-stream genuine-crash timeout identity-loss identity-loss-at-force child-exit-failed provenance-write-failed finish-command-failed finish-ack-failed stuck-host; do
  (
    out="$fixture/finish-$mode"
    mkdir -p "$out"
    adb=fake_adb
    p2p_cleanup_failed=0
    ticks=0
    alive=1
    forced=0
    sent=0
    joined=0
    child_code=0
    authorizations=0
    signals=0
    target=emulator-5554
    role=client
    if [ "$mode" = lost-stream ] || [ "$mode" = provenance-write-failed ]; then role=provider; target=emulator-5556; alive=0; fi
    if [ "$mode" = provenance-write-failed ]; then record_p2p_failure() { return 1; }; fi
    if [ "$mode" = genuine-crash ]; then alive=0; child_code=1; fi
    if [ "$mode" = child-exit-failed ]; then child_code=1; fi
    sed -n '1,5p' "$fixture/success.log" >"$out/$role-instrumentation.log"
    if [ "$mode" = genuine-crash ]; then
      printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n' >>"$out/$role-instrumentation.log"
    elif [ "$mode" = artifact-error ]; then
      record_p2p_failure "$out" provider artifact-collection
    fi
    android_acceptance_session_running() { [ "$1" = 424242 ] || fail "wrong child PID"; [ "$alive" = 1 ]; }
    sleep() {
      [ "$1" = 0.2 ] || fail "unexpected session polling interval"
      ticks=$((ticks + 1))
      if [ "$ticks" -eq 3 ] && [ "$mode" != timeout ] && [ "$mode" != identity-loss ] && \
         [ "$mode" != identity-loss-at-force ] && [ "$mode" != stuck-host ]; then
        alive=0
        success_transcript >"$out/$role-instrumentation.log"
      fi
    }
    send_physical_command() {
      [ "$1" = "$target" ] && [ "$2" = "$role-finish|finish|" ] || fail "wrong finish command"
      sent=$((sent + 1))
      [ "$mode" != finish-command-failed ]
    }
    wait_physical_status() {
      [ "$1:$2:$3:$4:$5" = "$target:$role-finish:complete:none:120" ] || fail "wrong finish receipt request"
      if [ "$mode" = lost-stream ] || [ "$mode" = genuine-crash ] || [ "$mode" = provenance-write-failed ]; then
        [ -z "$6" ] || fail "dead host PID incorrectly bounded recovered app finish"
      else
        [ "$6" = 424242 ] || fail "live host child is not supervised"
      fi
      [ "$mode" != finish-ack-failed ]
    }
    collect_physical_artifacts() {
      [ "$ticks" -ge 150 ] || fail "force-stop collection occurred before natural-exit grace"
      printf 'collected\n' >>"$out/lifecycle"
    }
    authorize_selected_device() {
      [ "$1" = "$target" ] || fail "force-stop changed target"
      authorizations=$((authorizations + 1))
      if [ "$authorizations" -gt 1 ]; then
        [ -s "$out/lifecycle" ] || fail "force-stop ownership checked before slow collection"
        [ "$mode" != identity-loss-at-force ] || return 1
      fi
      [ "$mode" != identity-loss ]
    }
    timeout() { shift; "$@"; }
    fake_adb() {
      [ "$*" = "-s $target shell am force-stop com.bringyour.network" ] || fail "unexpected teardown device action"
      [ "$ticks" -ge 150 ] || fail "app was force-stopped before its natural-exit deadline"
      forced=$((forced + 1))
      [ "$mode" = stuck-host ] || alive=0
      printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n' >>"$out/$role-instrumentation.log"
    }
    kill() {
      [ "$2" = 424242 ] || fail "signal targeted another process"
      signals=$((signals + 1))
      if [ "$mode" = stuck-host ] && [ "$1" = -TERM ]; then return 0; fi
      if [ "$1" != -TERM ]; then
        [ "$mode" = stuck-host ] && [ "$1" = -KILL ] && [ "$ticks" = 175 ] || fail "unbounded or unexpected kill escalation"
      fi
      alive=0
    }
    wait() {
      [ "$1" = 424242 ] || fail "joined another process"
      [ "$alive" = 0 ] || fail "joined an unbounded live child"
      joined=$((joined + 1))
      return "$child_code"
    }
    result=0
    finish_physical_session "$target" "$role" 424242 "$out" || result=$?
    [ "$joined" = 1 ] || fail "retained session was not independently joined"
    if [ "$mode" = identity-loss ]; then
      [ "$sent" = 0 ] || fail "finish command mutated a target after identity loss"
    else
      [ "$sent" = 1 ] || fail "retained session was not independently finished"
    fi
    case "$mode" in
      natural)
        [ "$result" = 0 ] && [ "$ticks" = 3 ] && [ "$forced" = 0 ] || fail "natural finalizer was interrupted"
        [ ! -e "$out/p2p-first-failure.json" ] || fail "successful exit created a failure" ;;
      artifact-error)
        [ "$result" = 0 ] && [ "$forced" = 0 ] || fail "artifact failure caused premature app kill"
        grep -Fq '"reason":"artifact-collection"' "$out/p2p-first-failure.json" || fail "original artifact failure was erased" ;;
      lost-stream)
        [ "$result" != 0 ] && [ "$forced" = 0 ] || fail "lost provider stream was hidden by recovered finish"
        grep -Fq '"classification":"infrastructure"' "$out/p2p-first-failure.json" || fail "lost stream was not infrastructure failure" ;;
      genuine-crash)
        [ "$result" != 0 ] && [ "$forced" = 0 ] || fail "pre-existing crash was accepted or re-killed"
        grep -Fq '"classification":"crash"' "$out/p2p-first-failure.json" || fail "real crash was hidden" ;;
      timeout)
        [ "$result" != 0 ] && [ "$ticks" = 150 ] && [ "$forced" = 1 ] || fail "hung finalizer did not hit bounded force-stop"
        grep -Fq '"reason":"natural-exit-timeout"' "$out/p2p-first-failure.json" || fail "induced crash replaced timeout cause" ;;
      identity-loss)
        [ "$result" != 0 ] && [ "$forced" = 0 ] && [ "$p2p_cleanup_failed" = 1 ] || fail "identity loss permitted force-stop or unsafe continuation" ;;
      identity-loss-at-force)
        [ "$result" != 0 ] && [ "$forced" = 0 ] && [ "$p2p_cleanup_failed" = 1 ] && [ "$authorizations" = 2 ] || fail "ownership was not freshly checked at force-stop" ;;
      child-exit-failed)
        [ "$result" != 0 ] && [ "$forced" = 0 ] || fail "nonzero child exit accepted despite terminal text"
        grep -Fq '"reason":"child-exit-failed"' "$out/p2p-first-failure.json" || fail "child exit failure disappeared" ;;
      provenance-write-failed)
        [ "$result" != 0 ] && [ "$forced" = 0 ] && [ "$p2p_cleanup_failed" = 1 ] || fail "failed diagnostic write abandoned cleanup or hid failure" ;;
      finish-command-failed|finish-ack-failed)
        [ "$result" != 0 ] && [ "$forced" = 0 ] || fail "failed finish was hidden or caused premature kill"
        grep -Fq "\"reason\":\"$mode\"" "$out/p2p-first-failure.json" || fail "finish failure provenance changed" ;;
      stuck-host)
        [ "$result" != 0 ] && [ "$forced" = 1 ] && [ "$signals" = 2 ] && [ "$ticks" = 175 ] || fail "host child join was not bounded" ;;
    esac
  ) || fail "physical session lifecycle control $mode"
done

# First-cause provenance is immutable even when a later cleanup prints a crash.
mkdir -p "$fixture/first-cause"
record_p2p_failure "$fixture/first-cause" provider artifact-collection
printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n' >"$fixture/first-cause/client-instrumentation.log"
record_p2p_failure "$fixture/first-cause" client child-exit-failed
grep -Fq '"reason":"artifact-collection"' "$fixture/first-cause/p2p-first-failure.json" || fail "later induced crash overwrote first failure"

# Keep the real orchestration connected to both checked role finalizers, not
# just an unused helper. No unconditional force-stop is allowed in that body.
peer_source="$(sed -n '/^run_android_peer_to_peer()/,/^record_device_cases()/p' "$here/test-main.sh")"
# shellcheck disable=SC2016
grep -Fq 'finish_physical_session "$serial" client "$client_session_pid" "$out"' <<<"$peer_source" || fail "client finalizer disconnected from real P2P path"
# shellcheck disable=SC2016
grep -Fq 'finish_physical_session "$peer_serial" provider "$provider_session_pid" "$out"' <<<"$peer_source" || fail "provider finalizer disconnected from real P2P path"
if grep -Fq 'shell am force-stop' <<<"$peer_source"; then fail "P2P orchestration still force-stops before bounded finalization"; fi

node --test "$here/scripts/acceptance-result.test.mjs" "$here/scripts/p2p-status.test.mjs"
echo "android P2P interruption/receipt tests passed"
