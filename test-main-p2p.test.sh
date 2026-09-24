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

for helper in boot_peer_emulator retain_peer_readiness_failure run_android_peer_to_peer observe_p2p_owned_guest collect_physical_adb_read collect_physical_artifacts_once collect_physical_artifacts record_p2p_failure finish_physical_session retain_physical_cleanup_ownership clear_physical_cleanup_ownership cleanup_physical_sessions; do
  # Only named production function definitions are loaded, never runner startup.
  # shellcheck disable=SC2294
  eval "$(sed -n "/^$helper()/,/^}/p" "$here/test-main.sh")"
done

# Ownership is necessary but is not evidence of completed readiness. Model the
# retained MAIN failure: initial API discovery fails, then a later flavor tries
# to reuse the same live peer. Neither stale failure nor stale success may
# replace a fresh boot/API/ABI/interactive/network preparation pass.
(
  artifacts="$fixture/peer-readiness"
  run_dir="$fixture/peer-readiness-state"
  adb=never-contact-adb
  serial=emulator-5554
  avd_name=urnetwork-acceptance
  peer_serial=emulator-5556
  peer_emulator_pid=424242
  peer_emulator_owner_token=peer-fixture-owner
  ownership_valid=1
  ownership_checks=0
  readiness_checks=0
  app_mutations=0
  readiness_state=api-unavailable
  mkdir -p "$artifacts/peer-emulator" "$run_dir"
  printf 'status=api-unavailable\n' >"$artifacts/peer-emulator/readiness.txt"
  runner_owns_peer_emulator() {
    ownership_checks=$((ownership_checks + 1))
    [ "$ownership_valid" = 1 ] && [ -n "$peer_serial" ] && [ -n "$peer_emulator_pid" ]
  }
  available_emulator_console_port() { fail "reused peer attempted to reserve a new port"; }
  run_android_acceptance_shared_avd_emulator() { fail "reused peer attempted a new emulator launch"; }
  android_acceptance_prepare_owned_emulator() {
    [ "$#" = 7 ] || fail "peer preparation changed its bounded default contract"
    [ "$1:$2:$3:$4" = "never-contact-adb:emulator-5556:urnetwork-acceptance:424242" ] || \
      fail "peer preparation lost its exact device/child identity"
    [ "$ANDROID_ACCEPTANCE_EMULATOR_OWNER_TOKEN" = peer-fixture-owner ] || \
      fail "peer preparation lost the live instance ownership token"
    [ "$5" = "$run_dir/peer-device" ] || fail "peer network cleanup state changed owner"
    readiness_checks=$((readiness_checks + 1))
    [ "$readiness_state" != missing-receipt ] || return 0
    printf 'status=%s\n' "$readiness_state" >"$6"
    printf 'result=%s\n' "$readiness_state" >"$7"
    [ "$readiness_state" = ready ]
  }
  # Load the production P2P caller too: preparation failure must stop before
  # package removal, install, credential staging or instrumentation.
  uninstall_acceptance_packages() { app_mutations=$((app_mutations + 1)); return 1; }
  cleanup_physical_sessions() { return 1; }
  android_acceptance_install_cell_apks() { fail "failed readiness reached APK installation"; }
  install_private_file_on() { fail "failed readiness reached credential staging"; }
  : >"$fixture/readiness-app.apk"
  : >"$fixture/readiness-test.apk"
  result=0
  run_android_peer_to_peer \
    "$artifacts/failed-cell" "$fixture/readiness-app.apk" "$fixture/readiness-test.apk" client-build \
    "$fixture/readiness-app.apk" "$fixture/readiness-test.apk" provider-build device-002 || result=$?
  [ "$result:$readiness_checks:$app_mutations" = 1:1:0 ] || \
    fail "owned peer with stale api-unavailable readiness bypassed preparation before app mutation"
  [ -f "$artifacts/failed-cell/provider-readiness/readiness.txt" ] || \
    fail "early peer preparation failure did not retain a cell-local readiness artifact"
  [ "$(cat "$artifacts/failed-cell/provider-readiness/readiness.txt")" = status=api-unavailable ] || \
    fail "failed cell did not retain its exact preparation failure"
  grep -Fq '"reason":"peer-readiness-failed"' "$artifacts/failed-cell/p2p-first-failure.json" || \
    fail "early peer preparation failure did not retain finite infrastructure provenance"

  readiness_state=ready
  boot_peer_emulator || fail "same owned peer could not recover for the next flavor"
  [ "$readiness_checks" = 2 ] || fail "recovered peer did not repeat readiness"
  [ "$(cat "$artifacts/peer-emulator/readiness.txt")" = status=ready ] || \
    fail "recovered peer did not publish current readiness"
  boot_peer_emulator || fail "healthy peer reuse was rejected"
  [ "$readiness_checks" = 3 ] || fail "successful prior readiness bypassed reuse validation"

  readiness_state=api-unavailable
  if boot_peer_emulator; then fail "stale ready receipt allowed a newly unavailable peer"; fi
  [ "$readiness_checks" = 4 ] || fail "new readiness failure was not sampled"
  receipt_count="$(find "$artifacts/peer-emulator" -type f -path '*/readiness-attempt.*/readiness.txt' | wc -l | tr -d ' ')"
  [ "$receipt_count" = 4 ] || fail "peer readiness attempts were not retained separately"
  failed_receipt_count="$(find "$artifacts/peer-emulator" -type f -path '*/readiness-attempt.*/readiness.txt' \
    -exec grep -l '^status=api-unavailable$' {} \; | wc -l | tr -d ' ')"
  [ "$failed_receipt_count" = 2 ] || fail "later readiness erased original API failure evidence"

  ownership_valid=0
  if boot_peer_emulator; then fail "unowned existing peer was accepted"; fi
  [ "$readiness_checks:$app_mutations" = 4:0 ] || fail "unowned peer was prepared or mutated"
  ownership_valid=1
  peer_emulator_pid=''
  if boot_peer_emulator; then fail "partial peer identity was accepted"; fi
  [ "$readiness_checks:$app_mutations" = 4:0 ] || fail "incomplete identity reached preparation or app mutation"
  [ "$ownership_checks" = 6 ] || fail "peer reuse skipped exact ownership verification"
  peer_serial=''
  peer_emulator_pid=424242
  if boot_peer_emulator; then fail "orphaned peer PID was accepted"; fi
  [ "$readiness_checks:$app_mutations" = 4:0 ] || fail "orphaned peer PID reached preparation or app mutation"
  peer_serial=emulator-5556
  readiness_state=missing-receipt
  if boot_peer_emulator; then fail "zero preparation exit without a current readiness receipt passed"; fi
  [ "$readiness_checks:$ownership_checks:$app_mutations" = 5:8:0 ] || \
    fail "missing current readiness receipt did not fail closed"
  [ ! -s "$artifacts/peer-emulator/readiness.txt" ] || \
    fail "missing current receipt published stale readiness"
) || fail "peer-emulator readiness reuse gate"

# The same preparation boundary must still handle a newly launched exact
# child. This launcher is an immediately exiting shell child, never an AVD.
for readiness_state in ready api-unavailable; do
  (
    artifacts="$fixture/peer-fresh-$readiness_state"
    run_dir="$fixture/peer-fresh-state-$readiness_state"
    peer_serial=''
    peer_emulator_pid=''
    peer_emulator_owner_token=''
    timestamp=fixture-time
    headless=1
    emulator=never-contact-emulator
    adb=never-contact-adb
    avd_name=urnetwork-acceptance
    readiness_checks=0
    available_emulator_console_port() {
      [ "$1:$2" = 5556:5584 ] || fail "fresh peer port range changed"
      printf '5556\n'
    }
    runner_owns_peer_emulator() { fail "fresh peer was classified as a reused child"; }
    run_android_acceptance_shared_avd_emulator() {
      [ "$1" = never-contact-emulator ] || fail "fresh peer selected a real emulator"
      [ "$2" = "$artifacts/peer-emulator/emulator.log" ] || fail "fresh peer lost launch diagnostics"
      case "$3" in peer-fixture-time-*) ;; *) fail "fresh peer lacks its unique ownership token" ;; esac
      shift 3
      [ "$*" = '-avd urnetwork-acceptance -read-only -gpu host -port 5556 -no-snapshot -no-boot-anim -netdelay none -netspeed full -no-window' ] || \
        fail "fresh peer launch changed its read-only/headless/host-renderer contract"
      printf 'launched\n' >"$artifacts/launch.txt"
    }
    android_acceptance_prepare_owned_emulator() {
      [ "$1:$2:$3:$4" = "never-contact-adb:emulator-5556:urnetwork-acceptance:$peer_emulator_pid" ] || \
        fail "fresh peer preparation lost the exact launched child"
      [ -n "$peer_emulator_pid" ] && [ "$ANDROID_ACCEPTANCE_EMULATOR_OWNER_TOKEN" = "$peer_emulator_owner_token" ] || \
        fail "fresh peer preparation lacks its PID/token pair"
      readiness_checks=$((readiness_checks + 1))
      printf 'status=%s\n' "$readiness_state" >"$6"
      printf 'result=%s\n' "$readiness_state" >"$7"
      [ "$readiness_state" = ready ]
    }
    result=0
    boot_peer_emulator || result=$?
    wait "$peer_emulator_pid" || fail "fake peer launcher failed"
    [ "$(cat "$artifacts/launch.txt")" = launched ] || fail "fresh peer did not launch"
    [ "$peer_serial:$readiness_checks" = emulator-5556:1 ] || fail "fresh peer did not prepare exactly once"
    if [ "$readiness_state" = ready ]; then
      [ "$result" = 0 ] || fail "ready fresh peer rejected"
    else
      [ "$result" = 1 ] || fail "unready fresh peer accepted"
    fi
  ) || fail "fresh peer readiness control: $readiness_state"
done

# A peer that loses ownership before preparation must not attach the previous
# flavor's successful receipt. This executes the caller's early-return path.
(
  artifacts="$fixture/peer-lost-before-preparation"
  run_dir="$fixture/peer-lost-before-preparation-state"
  peer_serial=emulator-5556
  peer_emulator_pid=424242
  peer_readiness_attempt_dir="$artifacts/peer-emulator/readiness-attempt.stale"
  mkdir -p "$peer_readiness_attempt_dir"
  printf 'status=ready\n' >"$peer_readiness_attempt_dir/readiness.txt"
  runner_owns_peer_emulator() { return 1; }
  android_acceptance_prepare_owned_emulator() { fail "unowned peer reached preparation"; }
  uninstall_acceptance_packages() { fail "unowned peer reached package mutation"; }
  if run_android_peer_to_peer \
      "$artifacts/failed-cell" "$fixture/readiness-app.apk" "$fixture/readiness-test.apk" client-build \
      "$fixture/readiness-app.apk" "$fixture/readiness-test.apk" provider-build device-002; then
    fail "unowned peer cell passed"
  fi
  [ -z "$peer_readiness_attempt_dir" ] || fail "unowned peer retained a stale attempt pointer"
  [ "$(cat "$artifacts/failed-cell/provider-readiness/readiness.txt")" = status=preparation-not-started ] || \
    fail "unowned peer borrowed a prior flavor's ready receipt"
) || fail "early ownership failure provenance"

for invalid_receipt in oversized symlink foreign; do
  (
    artifacts="$fixture/peer-invalid-$invalid_receipt"
    peer_readiness_attempt_dir="$artifacts/peer-emulator/readiness-attempt.fixture"
    mkdir -p "$peer_readiness_attempt_dir"
    printf 'status=api-unavailable\n' >"$peer_readiness_attempt_dir/readiness.txt"
    printf 'result=unknown\n' >"$peer_readiness_attempt_dir/interactive.txt"
    printf 'exit_status=1\n' >"$peer_readiness_attempt_dir/result.txt"
    case "$invalid_receipt" in
      oversized) printf '%4097s' x >"$peer_readiness_attempt_dir/readiness.txt" ;;
      symlink)
        mv "$peer_readiness_attempt_dir/readiness.txt" "$peer_readiness_attempt_dir/real.txt"
        ln -s real.txt "$peer_readiness_attempt_dir/readiness.txt" ;;
      foreign) peer_readiness_attempt_dir="$fixture" ;;
    esac
    if retain_peer_readiness_failure "$artifacts/failed-cell"; then
      fail "invalid peer readiness receipt was copied: $invalid_receipt"
    fi
    [ -f "$artifacts/failed-cell/p2p-first-failure.json" ] || fail "invalid diagnostic erased finite failure cause"
    [ ! -e "$artifacts/failed-cell/provider-readiness/readiness.txt" ] || fail "invalid readiness content was published"
  ) || fail "bounded cell-local readiness control: $invalid_receipt"
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
for child_result in 0 7 255; do
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
    node -e 'const fs=require("node:fs"); const r=JSON.parse(fs.readFileSync(process.argv[1])); if(r.childPid!==Number(process.argv[2])||r.waitExitCode!==Number(process.argv[3])||r.joinedBy!=="finish"||!Number.isFinite(Date.parse(r.joinedAt))) process.exit(1)' \
      "$out/client-instrumentation-exit.json" "$actual_child" "$child_result" || fail "exact real-child wait status not retained"
    if [ "$child_result" = 0 ]; then
      [ "$result" = 0 ] || fail "real child natural exit rejected"
    else
      [ "$result" != 0 ] || fail "real nonzero child exit hidden by successful receipt"
    fi
  ) || fail "real retained child exit control"
done

mkdir -p "$fixture/logs"
printf 'bounded app log\n' >"$fixture/logs/app.log"
printf '%032768d' 0 >"$fixture/timeout-partial-logcat"
for mode in recovered persistent unrelated-error invalid-status invalid-png ownership-lost ownership-lost-after-read diagnostics-recovered diagnostics-error diagnostics-empty startup timeout-recovered timeout-persistent timeout-ownership-lost-after-read exit-125 exit-137 exit-143 exit-7 exit-255 invalid-status-after-transport-warning glog-timeout-recovered; do
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
         { { [ "$mode" = ownership-lost-after-read ] || [ "$mode" = timeout-ownership-lost-after-read ]; } && [ "$ownership_checks" -gt 1 ]; }; then return 1; fi
    }
    fake_adb() {
      [ "$1:$2" = '-s:emulator-5556' ] || fail "collector contacted another device"
      shift 2
      printf '%s\n' "$*" >>"$fixture/reads-$mode"
      case "$*" in
        'logcat -d -t 12000')
          if [ "$mode" = timeout-persistent ] || \
             { [ "$attempt" = 1 ] && { [ "$mode" = timeout-recovered ] || [ "$mode" = timeout-ownership-lost-after-read ]; }; }; then
            # Exact shape of the retained GitHub failure: 32768 partial bytes,
            # empty stderr, and an explicit timeout exit. Size alone is not a
            # timeout signal; the exit controls below emit identical bytes.
            printf '%032768d' 0
            return 124
          fi
          case "$mode" in
            # F-Droid diagnostic 20260924-063941: the host daemon independently
            # recorded a transport read failure during this 73728-byte partial
            # read. The exit alone must not fabricate transport provenance or
            # silently enable a retry; its instrumentation stream was lost too.
            exit-255) printf '%073728d' 0; return 255 ;;
            exit-*) printf '%032768d' 0; return "${mode#exit-}" ;;
          esac
          if [ "$mode" = invalid-status-after-transport-warning ]; then
            printf 'adb: device offline (old warning on successful read)\n' >&2
          fi
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
          if [ "$mode" = invalid-status ] || [ "$mode" = invalid-status-after-transport-warning ]; then
            printf '{'
          elif [ "$mode" = startup ]; then
            printf '{"phase":"startup","state":"error","commandId":"0","extra":{"stage":"auth-discovery","failure":"auth-discovery-failed"}}\n'
          else
            printf '{"phase":"provider-proof","state":"complete","commandId":"provider-proof","extra":{}}\n'
          fi ;;
        'exec-out run-as com.bringyour.network cat files/acceptance/physical-memory.ndjson')
          [ "$mode" != startup ] || fail "startup failure requested a sampler that never started"
          printf '{"type":"sample","timeUnixMs":1790229593000,"phase":"probe"}\n' ;;
        'exec-out run-as com.bringyour.network cat files/acceptance/physical-diagnostics.ndjson')
          [ "$mode" != startup ] || fail "startup failure requested diagnostics that never started"
          if [ "$mode" = diagnostics-error ]; then
            printf 'diagnostic read failed\n' >&2
            return 1
          elif [ "$mode" = diagnostics-empty ]; then
            return 0
          elif [ "$mode" = diagnostics-recovered ] && [ "$attempt" = 1 ]; then
            printf '{"part":"state","partial":'
            printf 'adb: device offline\n' >&2
            return 1
          fi
          printf '{"part":"state","unix_millis":1790229593000,"p2p":{"FastReadMessageCount":6}}\n' ;;
        'exec-out run-as com.bringyour.network cat files/acceptance/physical-startup-goroutines.txt') return 1 ;;
        'exec-out run-as com.bringyour.network tar -C files/logs -cf - .')
          if [ "$mode" = glog-timeout-recovered ] && [ "$attempt" = 1 ]; then
            printf 'partial archive'
            return 124
          fi
          command tar -C "$fixture/logs" -cf - . ;;
        *) fail "collector attempted a mutation or unexpected read: $*" ;;
      esac
    }
    result=0
    collect_physical_artifacts emulator-5556 "$out" || result=$?
    case "$mode" in
      timeout-recovered)
        [ "$result" = 0 ] && [ "$ownership_checks" = 2 ] || fail "explicit timeout with empty stderr did not retry under the same owner"
        [ "$(wc -c <"$out/attempt-1/logcat.txt" | tr -d ' ')" = 32768 ] || fail "timeout partial logcat bytes changed"
        cmp -s "$fixture/timeout-partial-logcat" "$out/attempt-1/logcat.txt" || fail "timeout changed first-attempt bytes"
        [ ! -s "$out/attempt-1/collection.stderr" ] || fail "timeout fabricated an ADB error message"
        grep -Eq '^logcat[[:space:]]124$' "$out/attempt-1/collection-commands.tsv" || fail "timeout exit status was lost"
        grep -Eq '^1[[:space:]]124$' "$out/collection-attempts.tsv" || fail "attempt receipt flattened timeout status"
        grep -Fq 'complete retained log' "$out/logcat.txt" || fail "recovered timeout did not publish the successful attempt" ;;
      timeout-persistent)
        [ "$result" != 0 ] && [ "$ownership_checks" = 3 ] || fail "timeout escaped the three-attempt bound"
        [ "$(wc -l <"$fixture/reads-$mode" | tr -d ' ')" = 3 ] || fail "timeout continued past failing logcat"
        for failed_attempt in 1 2 3; do
          [ "$(wc -c <"$out/attempt-$failed_attempt/logcat.txt" | tr -d ' ')" = 32768 ] || fail "persistent timeout lost partial bytes"
          cmp -s "$fixture/timeout-partial-logcat" "$out/attempt-$failed_attempt/logcat.txt" || fail "persistent timeout changed partial bytes"
          grep -Eq '^logcat[[:space:]]124$' "$out/attempt-$failed_attempt/collection-commands.tsv" || fail "persistent timeout lost exact status"
        done ;;
      glog-timeout-recovered)
        [ "$result" = 0 ] && [ "$ownership_checks" = 2 ] || fail "archive extraction hid the ADB timeout"
        grep -Eq '^glog[[:space:]]124$' "$out/attempt-1/collection-commands.tsv" || fail "ADB side of pipeline lost its exact timeout"
        [ -s "$out/glog/app.log" ] || fail "archive timeout did not recover complete logs" ;;
      timeout-ownership-lost-after-read)
        [ "$result" != 0 ] && [ "$ownership_checks" = 3 ] || fail "timeout allowed changed ownership"
        [ "$(wc -l <"$fixture/reads-$mode" | tr -d ' ')" = 1 ] || fail "timeout retried an unowned device" ;;
      exit-*)
        [ "$result" != 0 ] && [ "$ownership_checks" = 1 ] || fail "non-timeout exit was retried: $mode"
        grep -Eq "^logcat[[:space:]]${mode#exit-}$" "$out/attempt-1/collection-commands.tsv" || fail "non-timeout exit status was flattened"
        if [ "$mode" = exit-255 ]; then
          [ "$(wc -c <"$out/attempt-1/logcat.txt" | tr -d ' ')" = 73728 ] || fail "disconnect discarded partial stdout"
          cmp -s "$out/attempt-1/logcat.txt" "$out/logcat.txt" || fail "disconnect changed retained original bytes"
          [ ! -s "$out/attempt-1/read-logcat.stderr" ] && [ ! -s "$out/collection.stderr" ] || fail "exit255 fabricated a stderr transport signature"
          [ "$(wc -l <"$fixture/reads-$mode" | tr -d ' ')" = 1 ] || fail "exit255 continued to device reads"
          [ ! -e "$out/attempt-2" ] || fail "unclassified exit255 was retried"
          grep -Eq '^1[[:space:]]255$' "$out/collection-attempts.tsv" || fail "disconnect attempt receipt lost exit255"
        fi ;;
      diagnostics-recovered)
        [ "$result" = 0 ] && [ "$ownership_checks" = 2 ] || fail "diagnostic transport loss did not retry with the same owner"
        grep -Fq '"partial":' "$out/attempt-1/physical-diagnostics.ndjson" || fail "partial diagnostic evidence was erased"
        grep -Fq 'adb: device offline' "$out/attempt-1/collection.stderr" || fail "diagnostic transport failure was hidden" ;;
      startup)
        [ "$result" = 0 ] && [ "$ownership_checks" = 1 ] || fail "pre-sampler startup evidence required absent diagnostics" ;;
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
    if [ "$result" = 0 ] && [ "$mode" != startup ]; then
      grep -Fq '"type":"sample"' "$out/physical-memory.ndjson" || fail "physical memory timeline was not retained"
      grep -Fq '"FastReadMessageCount":6' "$out/physical-diagnostics.ndjson" || fail "transfer boundary counters were not retained"
      node -e 'const fs=require("node:fs"); for (const path of process.argv.slice(1)) if (fs.statSync(path).mode & 0o077) process.exit(1)' \
        "$out/physical-memory.ndjson" "$out/physical-diagnostics.ndjson" || fail "physical diagnostic artifacts are not private"
    fi
    if [ "$mode" = diagnostics-error ] || [ "$mode" = diagnostics-empty ]; then
      [ -s "$out/collection.stderr" ] || fail "diagnostic collection failure has no retained cause"
    fi
    if [ "$mode" = timeout-recovered ]; then
      if collect_physical_artifacts emulator-5556 "$out" 2>/dev/null; then
        fail "second collection silently reused existing attempt evidence"
      fi
      cmp -s "$fixture/timeout-partial-logcat" "$out/attempt-1/logcat.txt" || fail "second collection overwrote the original partial log"
      [ "$ownership_checks" = 2 ] || fail "second collection reached a device read before preserving prior evidence"
    fi
  ) || fail "artifact collector control $mode"
done

# Model child liveness with a virtual tick counter. The actual production grace
# helper polls it; no sleeps, processes, ADB or connected devices are required.
for mode in natural artifact-error lost-stream transport-255-lost-client genuine-crash timeout identity-loss identity-loss-at-force child-exit-failed provenance-write-failed finish-command-failed finish-ack-failed stuck-host; do
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
    if [ "$mode" = transport-255-lost-client ]; then alive=0; child_code=255; fi
    if [ "$mode" = provenance-write-failed ]; then record_p2p_failure() { return 1; }; fi
    if [ "$mode" = genuine-crash ]; then alive=0; child_code=1; fi
    if [ "$mode" = child-exit-failed ]; then child_code=1; fi
    sed -n '1,5p' "$fixture/success.log" >"$out/$role-instrumentation.log"
    if [ "$mode" = genuine-crash ]; then
      printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n' >>"$out/$role-instrumentation.log"
    elif [ "$mode" = artifact-error ]; then
      record_p2p_failure "$out" provider artifact-collection
    elif [ "$mode" = transport-255-lost-client ]; then
      # Even if later ADB ownership, finish acknowledgement and the other role
      # recover, the original missing client terminal remains a hard failure.
      success_transcript >"$out/provider-instrumentation.log"
      record_p2p_failure "$out" client artifact-collection
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
      if [ "$mode" = lost-stream ] || [ "$mode" = transport-255-lost-client ] || \
         [ "$mode" = genuine-crash ] || [ "$mode" = provenance-write-failed ]; then
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
    node -e 'const fs=require("node:fs"); const r=JSON.parse(fs.readFileSync(process.argv[1])); if(r.childPid!==424242||r.waitExitCode!==Number(process.argv[2])) process.exit(1)' \
      "$out/$role-instrumentation-exit.json" "$child_code" || fail "teardown flattened the exact wait status"
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
      transport-255-lost-client)
        [ "$result:$forced:$ticks:$sent" = 1:0:0:1 ] || fail "recovered finish or provider success repaired the lost client terminal"
        grep -Fq '"role":"client","reason":"artifact-collection","classification":"infrastructure"' "$out/p2p-first-failure.json" || \
          fail "transport break was relabeled as app crash/timeout or its first failure was erased"
        if android_acceptance_verify_p2p_instrumentation "$out/client-instrumentation.log"; then fail "start-only client stream became success"; fi ;;
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
