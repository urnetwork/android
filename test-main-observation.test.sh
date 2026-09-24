#!/usr/bin/env bash
# Offline observation contracts: fake ADB only, no devices, SDK or network.
set -euo pipefail
umask 077
here="$(cd "$(dirname "$0")" && pwd)"
source "$here/test-main-lib.sh"
fail() { echo "FAIL: $*" >&2; exit 1; }
fixture="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-observation.test.XXXXXX")"
trap 'rm -rf "$fixture"' EXIT
for helper in observe_p2p_owned_guest collect_physical_adb_read collect_physical_artifacts_once collect_physical_artifacts record_p2p_failure finish_physical_session run_android_peer_to_peer; do
  # Load definitions only; runner startup must never execute in this test.
  eval "$(sed -n "/^$helper()/,/^}/p" "$here/test-main.sh")"
done

setup_observation() {
  p2p_observation_out="$case_dir/guest-observation"
  p2p_observation_avd=fixture-avd
  p2p_observation_client_serial=emulator-5554 p2p_observation_client_pid=$$ p2p_observation_client_token=client-owner
  p2p_observation_provider_serial=emulator-5556 p2p_observation_provider_pid=$$ p2p_observation_provider_token=provider-owner
  adb=fake_adb
  mkdir -p "$case_dir"
}

timeout() {
  if [ "$1" = -k ]; then
    [ "$2:$3" = 1:10 ] || fail "observation lost its forced-exit bound"
    shift 2
  elif [ "$1" = 10 ]; then
    fail "observation timeout omitted its forced-exit bound"
  fi
  case "$1" in 10|15|30) ;; *) fail "unbounded/unexpected fake command timeout" ;; esac
  printf '%s\n' "$1" >>"$case_dir/timeouts"
  shift
  "$@"
}

fake_adb() {
  [ "$1" = -s ] || fail "observation enumerated or restarted ADB"
  case "$2" in emulator-5554) local owner=client-owner ;; emulator-5556) local owner=provider-owner ;; *) fail "observation touched a foreign/reserved device" ;; esac
  local selected="$2"
  shift 2
  printf '%s\t%s\n' "$selected" "$1" >>"$case_dir/adb-calls"
  case "$*" in
    get-state) printf 'device\n' ;;
    'emu avd id')
      if [ "$mode" = token-lost ] && [ -f "$case_dir/broken" ]; then printf 'foreign-owner\nOK\n'
      else printf '%s\nOK\n' "$owner"; fi ;;
    'emu avd name')
      if [ "$mode" = avd-lost ] && [ -f "$case_dir/broken" ]; then printf 'foreign-avd\nOK\n'
      else printf 'fixture-avd\nOK\n'; fi ;;
    'logcat -d -t 12000')
      printf 'broken\n' >"$case_dir/broken"
      printf '%057344d' 0
      return 255 ;;
    'shell am instrument -w -r '*)
      if [ "$execution_mode:$diagnostic_owned_avd" = diagnostic:1 ]; then
        [ -f "$case_dir/guest-observation/client/before-workflow/ownership-status.txt" ] || fail "client baseline did not precede instrumentation"
        [ -f "$case_dir/guest-observation/provider/before-workflow/ownership-status.txt" ] || fail "provider baseline did not precede instrumentation"
      else
        [ ! -e "$case_dir/guest-observation" ] || fail "observations escaped owned-AVD diagnostics"
      fi
      if [ "$selected" = emulator-5554 ]; then
        success_transcript | sed -n '1,3p'
        return 255
      fi
      success_transcript ;;
    'shell pm grant '*|'shell appops set '*) return 0 ;;
    shell*)
      [ "$#" = 2 ] || fail "guest read did not use one fixed shell stream"
      if [ "${workflow:-0}" = 1 ] && [ -f "$case_dir/broken" ]; then
        [ -f "$case_dir/p2p-first-failure.json" ] || fail "observation delayed first-failure provenance"
      fi
      printf '%s\n' "$selected" >>"$case_dir/guest-reads"
      # Execute the actual remote command text against explicit fake tools.
      # These exports exist only in this command's subshell.
      (
        cat() {
          case "$*" in
            /proc/sys/kernel/random/boot_id)
              if [ "$mode" = invalid-identity ]; then printf 'invalid\n'
              elif [ "$mode" = reboot ] && [ -f "$case_dir/broken" ]; then printf 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\n'
              else printf 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\n'; fi ;;
            /proc/uptime)
              if [ "$mode" = reboot ] && [ -f "$case_dir/broken" ]; then printf '2.00 1.00\n'
              else printf '123.45 200.00\n'; fi ;;
            *) fail "guest command read an unexpected/private path" ;;
          esac
        }
        pidof() {
          [ "$*" = adbd ] || fail "guest command requested an app PID"
          if [ "$mode" = adbd-restart ] && [ -f "$case_dir/broken" ]; then printf '202\n'
          else printf '101\n'; fi
        }
        logcat() {
          [ "$*" = '-b main -b system -b crash -d -t 256 -v epoch adbd:V init:V lmkd:V lowmemorykiller:V logd:V tombstoned:V crash_dump32:V crash_dump64:V *:S' ] || fail "guest log collection widened its filters/bound"
          if [ "$mode" = oversize ]; then printf '%0100000d' 0
          else printf '1790250198.448 1 1 I init: fixture OS event\n'; fi
          [ "$mode" != read-255 ] || return 255
          [ "$mode" != read-timeout ] || return 124
        }
        export mode case_dir
        export -f cat pidof logcat fail
        bash -c "$2"
      ) ;;
    *) fail "unexpected fake ADB action: $*" ;;
  esac
}

for mode in normal token-lost avd-lost dead-owner reboot adbd-restart read-255 read-timeout oversize invalid-identity; do
  (
    case_dir="$fixture/$mode"
    setup_observation
    observe_p2p_owned_guest emulator-5554 before-workflow baseline \
      >"$case_dir/console.stdout" 2>"$case_dir/console.stderr" || true
    printf 'broken\n' >"$case_dir/broken"
    if [ "$mode" = dead-owner ]; then
      (exit 0) & p2p_observation_client_pid=$!
      wait "$p2p_observation_client_pid"
    fi
    result=0
    observe_p2p_owned_guest emulator-5554 after-break artifact-read \
      >>"$case_dir/console.stdout" 2>>"$case_dir/console.stderr" || result=$?
    observation="$p2p_observation_out/client/after-break"
    reads="$(wc -l <"$case_dir/guest-reads" | tr -d ' ')"
    case "$mode" in
      token-lost|avd-lost|dead-owner)
        [ "$result:$reads" = 1:1 ] || fail "$mode permitted an unowned guest read"
        [ ! -f "$observation/read-status.txt" ] && [ ! -f "$observation/guest.txt" ] || fail "$mode fabricated read evidence"
        [ "$(cat "$observation/observation-status.txt")" = ownership-unavailable ] || fail "$mode lost ownership failure"
        ;;
      read-255|read-timeout)
        [ "$result:$reads" = 1:2 ] || fail "$mode was retried or accepted"
        expected=255; [ "$mode" != read-timeout ] || expected=124
        [ "$(cat "$observation/read-status.txt")" = "$expected" ] || fail "$mode flattened ADB status"
        [ "$(cat "$observation/capture-status.txt")" = 0 ] || fail "$mode confused capture and ADB exits"
        ;;
      invalid-identity)
        [ "$result:$reads" = 1:2 ] || fail "invalid identity accepted"
        [ "$(cat "$observation/read-status.txt")" = 0 ] && [ "$(cat "$observation/capture-status.txt")" = 1 ] || fail "invalid identity altered read status"
        ;;
      *) [ "$result:$reads" = 0:2 ] || fail "$mode valid observation failed" ;;
    esac
    # Every attempt, including denied/unavailable ones, is single-shot.
    calls="$(wc -l <"$case_dir/adb-calls" | tr -d ' ')"
    observe_p2p_owned_guest emulator-5554 after-break child-exit-failed
    [ "$calls" = "$(wc -l <"$case_dir/adb-calls" | tr -d ' ')" ] || fail "repeated observation performed a retry"
    [ "$(cat "$observation/trigger.txt")" = artifact-read ] || fail "later failure overwrote the first trigger"
    [ ! -s "$case_dir/console.stdout" ] && [ ! -s "$case_dir/console.stderr" ] || fail "guest evidence leaked to console"
    node - "$case_dir" "$mode" <<'NODE'
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const [dir, mode] = process.argv.slice(2);
const before = path.join(dir, 'guest-observation/client/before-workflow');
const after = path.join(dir, 'guest-observation/client/after-break');
const read = (d, n) => fs.readFileSync(path.join(d, n), 'utf8');
for (const d of [before, after]) {
  assert.ok(Date.parse(read(d, 'ended-at.txt').trim()) >= Date.parse(read(d, 'started-at.txt').trim()));
  for (const entry of fs.readdirSync(d)) assert.equal(fs.statSync(path.join(d, entry)).mode & 0o077, 0);
}
if (['token-lost', 'avd-lost', 'dead-owner'].includes(mode)) process.exit(0);
assert.equal(read(after, 'owner.tsv'), read(before, 'owner.tsv'));
const old = JSON.parse(read(before, 'capture.json'));
const now = JSON.parse(read(after, 'capture.json'));
assert.equal(now.retainedBytes, fs.statSync(path.join(after, 'guest.txt')).size);
assert.ok(now.retainedBytes <= 65536);
assert.equal(now.truncated, mode === 'oversize');
if (mode === 'oversize') {
  assert.ok(now.receivedBytes > now.retainedBytes);
  assert.equal(now.retainedBytes, 65536);
}
if (mode === 'invalid-identity') assert.equal(now.identity, null);
else {
  assert.equal(now.identity.bootId !== old.identity.bootId, mode === 'reboot');
  assert.equal(now.identity.adbdPid !== old.identity.adbdPid, mode === 'adbd-restart');
  assert.equal(now.identity.uptimeSeconds, mode === 'reboot' ? '2.00' : '123.45');
}
NODE
  ) || fail "owned guest observation $mode"
done

# Disabled context, other emulators, phones and malformed serials never read.
(
  case_dir="$fixture/selection" mode=normal
  setup_observation
  for target in emulator-7777 emulator-bad 3B161FDJG001KT R5CX21FY6ND foreign-phone; do
    if observe_p2p_owned_guest "$target" after-break artifact-read; then fail "accepted out-of-context target $target"; fi
  done
  p2p_observation_out=''
  observe_p2p_owned_guest emulator-5554 before-workflow baseline
  [ ! -e "$case_dir/adb-calls" ] || fail "disabled/out-of-context observation called ADB"
)

# Capture failure must retain a distinct exact ADB exit, never imply success.
(
  case_dir="$fixture/capture-failed" mode=read-255
  setup_observation
  node() {
    if [ "${1:-}" = "$here/test-main-guest-observation.mjs" ]; then
      command cat >/dev/null
      return 74
    fi
    command node "$@"
  }
  if observe_p2p_owned_guest emulator-5554 after-break artifact-read; then fail "capture failure passed"; fi
  observation="$p2p_observation_out/client/after-break"
  [ "$(cat "$observation/read-status.txt"):$(cat "$observation/capture-status.txt")" = 255:74 ] || fail "capture error hid exact read exit"
)

success_transcript() {
  printf '%s\n' \
    'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
    'INSTRUMENTATION_STATUS: test=physicalLowbarSession' \
    'INSTRUMENTATION_STATUS_CODE: 1' \
    'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
    'INSTRUMENTATION_STATUS: test=physicalLowbarSession' \
    'INSTRUMENTATION_STATUS_CODE: 0' \
    'OK (1 test)' 'INSTRUMENTATION_CODE: -1'
}

# Exercise the production P2P orchestration and actual child waits. The next
# diagnostic may capture a recovered guest; its original 255 and missing
# terminal must still fail with one original logcat read, even if finish and
# the provider complete. A swapped instance must only leave a denied receipt.
for mode in normal token-lost canonical physical-diagnostic; do
  (
    case_dir="$fixture/workflow-$mode"
    mkdir -p "$case_dir"
    out="$case_dir" run_dir="$case_dir" artifacts="$case_dir"
    : >"$case_dir/app.apk"; : >"$case_dir/test.apk"
    serial=emulator-5554 peer_serial=emulator-5556 adb=fake_adb
    avd_name=fixture-avd emulator_pid=$$ peer_emulator_pid=$$
    emulator_owner_token=client-owner peer_emulator_owner_token=provider-owner
    execution_mode=diagnostic diagnostic_owned_avd=1 credentials=unused repeat_count=1 workflow=1
    case "$mode" in
      canonical) execution_mode=canonical diagnostic_owned_avd=0 ;;
      physical-diagnostic) diagnostic_owned_avd=0 ;;
    esac
    android_acceptance_timeout_executable=unused
    boot_peer_emulator() { return 0; }
    uninstall_acceptance_packages() { return 0; }
    android_acceptance_install_cell_apks() { return 0; }
    install_private_file_on() { return 0; }
    android_acceptance_runner_owned_emulator_interactive() { return 0; }
    selected_device_interactive() { return 0; }
    android_acceptance_preflight_device() { return 0; }
    wait_physical_status() { return 0; }
    pull_physical_client() { return 0; }
    cleanup_physical_sessions() { return 0; }
    authorize_selected_device() { case "$1" in emulator-5554|emulator-5556) return 0 ;; *) fail "workflow changed target" ;; esac; }
    send_physical_command() {
      case "$2" in
        *-finish*)
          if [ "$execution_mode:$diagnostic_owned_avd" = diagnostic:1 ]; then
            [ -f "$case_dir/guest-observation/client/after-break/observation-status.txt" ] || fail "finish preceded post-break observation"
          fi ;;
      esac
      return 0
    }
    eval "$(declare -f collect_physical_artifacts | sed '1s/collect_physical_artifacts/collect_client_artifacts/')"
    collect_physical_artifacts() {
      if [ "$1" = emulator-5554 ]; then collect_client_artifacts "$@"; else return 0; fi
    }
    result=0
    run_android_peer_to_peer "$out" "$out/app.apk" "$out/test.apk" client-build \
      "$out/app.apk" "$out/test.apk" provider-build device-001 \
      >"$case_dir/console.stdout" 2>"$case_dir/console.stderr" || result=$?
    [ "$result" = 1 ] || fail "recovered observation repaired a lost instrumentation stream"
    [ ! -s "$case_dir/console.stdout" ] && [ ! -s "$case_dir/console.stderr" ] || fail "workflow observation leaked private output"
    [ "$(wc -c <"$out/client-before-teardown/logcat.txt" | tr -d ' ')" = 57344 ] || fail "observation changed original partial logcat"
    [ "$(cat "$out/client-before-teardown/collection-attempts.tsv")" = $'1\t255' ] || fail "observation added an artifact retry"
    node - "$out" "$mode" <<'NODE'
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const [out, mode] = process.argv.slice(2);
const read = name => fs.readFileSync(path.join(out, name), 'utf8');
assert.deepEqual(JSON.parse(read('p2p-first-failure.json')), {
  schemaVersion: 1, role: 'client', reason: 'artifact-collection', classification: 'infrastructure',
});
assert.equal(JSON.parse(read('client-instrumentation-exit.json')).waitExitCode, 255);
assert.equal(JSON.parse(read('provider-instrumentation-exit.json')).waitExitCode, 0);
if (['canonical', 'physical-diagnostic'].includes(mode)) {
  assert.equal(fs.existsSync(path.join(out, 'guest-observation')), false);
  assert.equal(fs.existsSync(path.join(out, 'guest-reads')), false);
  process.exit(0);
}
assert.equal(read('guest-observation/client/after-break/observation-status.txt').trim(),
  mode === 'normal' ? 'captured' : 'ownership-unavailable');
assert.equal(fs.existsSync(path.join(out, 'guest-observation/provider/after-break')), false);
assert.equal(read('guest-reads').trim().split('\n').length, mode === 'normal' ? 3 : 2);
NODE
  ) || fail "production observation orchestration $mode"
done

# Execute the exact EXIT child-join loop, stopping before any device cleanup.
# Already-exited children retain their actual status instead of an inferred
# instrumentation result. A later receipt cannot overwrite the original.
(
  out="$fixture/trap-join"
  mkdir -p "$out"
  (exit 255) & client_session_pid=$!
  (exit 0) & provider_session_pid=$!
  client_session_out="$out" provider_session_out="$out"
  android_acceptance_wait_for_session_exit "$client_session_pid" 25 || fail "fixture child did not exit"
  android_acceptance_wait_for_session_exit "$provider_session_pid" 25 || fail "fixture child did not exit"
  eval "$(sed -n '/^  for session_role in provider client; do/,/^  if \[ -n "\$private_staging_serial"/p' "$here/test-main.sh" | sed '$d')"
  if android_acceptance_record_session_exit "$out" client "$client_session_pid" 0 finish 2>/dev/null; then fail "later join overwrote original status"; fi
  node -e 'const r=require(process.argv[1]); if(r.waitExitCode!==255||r.joinedBy!=="trap") process.exit(1)' \
    "$out/client-instrumentation-exit.json" || fail "trap wait status lost"
  node -e 'const r=require(process.argv[1]); if(r.waitExitCode!==0||r.joinedBy!=="trap") process.exit(1)' \
    "$out/provider-instrumentation-exit.json" || fail "trap provider wait status lost"
)

echo 'android owned-AVD observation tests passed (17 controls; no device/network)'
