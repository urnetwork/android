#!/usr/bin/env bash
# Offline completion gate tests: fake reads and ownership only, never a device.
set -euo pipefail
umask 077
here="$(cd "$(dirname "$0")" && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }
fixture="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-completion.test.XXXXXX")"
trap 'rm -rf "$fixture"' EXIT
# Only load the production boundary, never the runner startup.
# shellcheck disable=SC2294
eval "$(sed -n '/^read_acceptance_completion_record()/,/^}/p' "$here/test-main.sh")"

run_case() (
  mode="$1" expected_success="$2" expected_reads="$3" expected_proofs="$4" expected_outcomes="$5"
  case_dir="$fixture/$mode"
  receipt="$case_dir/receipt"
  mkdir "$case_dir"
  adb=fake_completion_adb
  ownership_checks=0
  backoffs=0
  # Exact fixture bytes, stderr and exits are independent of the gate under
  # test. The first timeout has no transport-error text to trigger a retry.
  for attempt in 1 2 3; do
    printf 'build-fixture\n2\n' >"$case_dir/expected-$attempt.bin"
    : >"$case_dir/expected-$attempt.stderr"
    status=0
    case "$mode" in
      recover-partial|lose-owner)
        if [ "$attempt" = 1 ]; then
          printf 'build-fixture\n' >"$case_dir/expected-$attempt.bin"
          status=124
        fi ;;
      recover-correct)
        [ "$attempt" != 1 ] || status=124 ;;
      timeout-partial|capture-timeout)
        printf 'build-fixture\n' >"$case_dir/expected-$attempt.bin"
        status=124 ;;
      timeout-correct) status=124 ;;
      wrong-build)
        printf 'SECRET-COMPLETION-CONTENT\n2\n' >"$case_dir/expected-$attempt.bin" ;;
      wrong-repeat)
        printf 'build-fixture\n1\n' >"$case_dir/expected-$attempt.bin" ;;
      empty) : >"$case_dir/expected-$attempt.bin" ;;
      malformed) printf 'build-fixture' >"$case_dir/expected-$attempt.bin" ;;
      binary)
        printf 'build-\000fixture\n2\n' >"$case_dir/expected-$attempt.bin" ;;
      missing)
        : >"$case_dir/expected-$attempt.bin"
        printf 'cat: files/acceptance/result: No such file or directory\n' >"$case_dir/expected-$attempt.stderr"
        status=1 ;;
      transport-error)
        printf 'error: device offline\n' >"$case_dir/expected-$attempt.stderr"
        status=1 ;;
      exit-*) status="${mode#exit-}" ;;
      oversize|oversize-timeout)
        command node -e 'process.stdout.write(Buffer.concat([Buffer.from("build-fixture\n2\n"), Buffer.alloc(65536, 0x61)]))' \
          >"$case_dir/expected-$attempt.bin"
        [ "$mode" != oversize-timeout ] || status=124 ;;
      crlf) printf 'build-fixture\r\n2\r\n' >"$case_dir/expected-$attempt.bin" ;;
      extra-line)
        printf 'build-fixture\n2\nignored-by-existing-gate\n' >"$case_dir/expected-$attempt.bin" ;;
      no-final-newline) printf 'build-fixture\n2' >"$case_dir/expected-$attempt.bin" ;;
    esac
    printf '%s\n' "$status" >"$case_dir/expected-$attempt.status"
  done
  authorize_selected_device() {
    [ "$1" = emulator-5554 ] || fail "completion selected a different device"
    ownership_checks=$((ownership_checks + 1))
    case "$mode:$ownership_checks" in no-owner:*|lose-owner:2) return 77 ;; esac
    return 0
  }
  sleep() {
    [ "$1" = 1 ] || fail "completion retry changed bounded backoff"
    backoffs=$((backoffs + 1))
  }
  timeout() {
    [ "$1" = 30 ] || fail "completion read changed its deadline"
    shift
    "$@"
  }
  fake_completion_adb() {
    [ "$*" = '-s emulator-5554 exec-out run-as com.bringyour.network cat files/acceptance/result' ] || \
      fail "completion read changed its exact selected device/path"
    printf 'read\n' >>"$case_dir/reads.txt"
    local read_number
    read_number="$(wc -l <"$case_dir/reads.txt" | tr -d ' ')"
    [ "$read_number" -le 3 ] || fail "completion read exceeded three attempts"
    cat "$case_dir/expected-$read_number.bin"
    cat "$case_dir/expected-$read_number.stderr" >&2
    return "$(cat "$case_dir/expected-$read_number.status")"
  }
  node() {
    case "$mode" in
      capture-failed|capture-timeout) cat >/dev/null; return 74 ;;
      missing-capture-receipt) cat >/dev/null; return 0 ;;
    esac
    command node "$@"
  }

  result=0
  read_acceptance_completion_record emulator-5554 "$receipt" build-fixture 2 \
    >"$case_dir/console.stdout" 2>"$case_dir/console.stderr" || result=$?
  if [ "$expected_success" = 1 ]; then
    [ "$result" = 0 ] || fail "$mode: expected a valid completion read"
  else
    [ "$result" != 0 ] || fail "$mode: invalid/failed completion read passed"
  fi
  reads=0
  [ ! -f "$case_dir/reads.txt" ] || reads="$(wc -l <"$case_dir/reads.txt" | tr -d ' ')"
  [ "$reads:$ownership_checks:$backoffs" = "$expected_reads:$expected_proofs:$((expected_proofs - 1))" ] || \
    fail "$mode: reads/ownership proofs/backoff not bounded or renewed ($reads/$ownership_checks/$backoffs)"
  [ "$(cut -f4 "$receipt/completion-attempts.tsv" | tr '\n' ',')" = "$expected_outcomes" ] || \
    fail "$mode: completion failure provenance changed"
  [ ! -s "$case_dir/console.stdout" ] && [ ! -s "$case_dir/console.stderr" ] || \
    fail "$mode: private completion bytes or stderr reached the runner console"

  for ((attempt = 1; attempt <= expected_reads; attempt++)); do
    attempt_dir="$receipt/attempt-$attempt"
    cmp "$case_dir/expected-$attempt.stderr" "$attempt_dir/read.stderr" || \
      fail "$mode: read stderr changed"
    cmp "$case_dir/expected-$attempt.status" "$attempt_dir/read-status.txt" || \
      fail "$mode: read exit status was flattened or replaced by capture status"
    [ "$(cat "$attempt_dir/ownership-status.txt")" = 0 ] || fail "$mode: read without ownership"
    case "$mode" in
      capture-failed|capture-timeout)
        [ "$(cat "$attempt_dir/capture-status.txt")" = 74 ] || fail "$mode: lost local capture status"
        continue ;;
      missing-capture-receipt)
        [ "$(cat "$attempt_dir/capture-status.txt")" = 0 ] || fail "$mode: capture receipt changed command exit"
        continue ;;
    esac
    [ "$(cat "$attempt_dir/capture-status.txt")" = 0 ] || fail "$mode: capture failed"
    command node - "$case_dir/expected-$attempt.bin" "$attempt_dir" <<'NODE'
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const [expectedPath, directory] = process.argv.slice(2);
const expected = fs.readFileSync(expectedPath);
assert.deepEqual(fs.readFileSync(path.join(directory, 'result.bin')), expected.subarray(0, 4096));
assert.equal(fs.readFileSync(path.join(directory, 'received-bytes.txt'), 'utf8'), `${expected.length}\n`);
NODE
  done
  if [ "$expected_proofs" -gt "$expected_reads" ]; then
    denied="$receipt/attempt-$expected_proofs"
    [ "$(cat "$denied/ownership-status.txt")" = 77 ] || fail "$mode: lost ownership failure receipt"
    [ ! -f "$denied/read-status.txt" ] || fail "$mode: fabricated an ADB read after lost ownership"
  fi
  command node - "$receipt" <<'NODE'
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
function checkPrivate(entry) {
  const info = fs.lstatSync(entry);
  assert.equal(info.mode & 0o077, 0, 'completion receipts must be private');
  assert.equal(info.isSymbolicLink(), false, 'completion receipts must be fresh files');
  if (info.isDirectory()) for (const child of fs.readdirSync(entry)) checkPrivate(path.join(entry, child));
}
checkPrivate(process.argv[2]);
NODE
  if [ "$mode" = recover-partial ]; then
    cp "$receipt/completion-attempts.tsv" "$case_dir/retained-attempts.tsv"
    cp "$receipt/attempt-1/result.bin" "$case_dir/retained-partial.bin"
    if read_acceptance_completion_record emulator-5554 "$receipt" build-fixture 2 >/dev/null 2>&1; then
      fail "a second invocation overwrote prior completion evidence"
    fi
    [ "$ownership_checks" = 2 ] || fail "reused receipt performed another ownership/read attempt"
    [ "$(wc -l <"$case_dir/reads.txt" | tr -d ' ')" = 2 ] || fail "reused receipt performed another read"
    cmp "$case_dir/retained-attempts.tsv" "$receipt/completion-attempts.tsv" || fail "reentry overwrote receipts"
    cmp "$case_dir/retained-partial.bin" "$receipt/attempt-1/result.bin" || fail "reentry overwrote partial bytes"
  fi
) || fail "completion case: $1"

run_case recover-partial 1 2 2 'read-timeout,success,'
run_case recover-correct 1 2 2 'read-timeout,success,'
run_case timeout-partial 0 3 3 'read-timeout,read-timeout,read-timeout,'
run_case timeout-correct 0 3 3 'read-timeout,read-timeout,read-timeout,'
run_case lose-owner 0 1 2 'read-timeout,ownership-unavailable,'
run_case no-owner 0 0 1 'ownership-unavailable,'
for mode in wrong-build wrong-repeat empty malformed binary; do
  run_case "$mode" 0 1 1 'record-mismatch,'
done
for mode in missing transport-error exit-1 exit-7 exit-125 exit-137 exit-143; do
  run_case "$mode" 0 1 1 'read-failed,'
done
for mode in oversize oversize-timeout; do
  run_case "$mode" 0 1 1 'record-oversize,'
done
for mode in capture-failed capture-timeout missing-capture-receipt; do
  run_case "$mode" 0 1 1 'capture-failed,'
done
for mode in success crlf extra-line no-final-newline; do
  run_case "$mode" 1 1 1 'success,'
done

# Keep the actual caller on the evidence-preserving boundary before package
# removal; do not reintroduce the old swallowed status/stderr inline read.
# shellcheck disable=SC2016
grep -Fq 'if ! read_acceptance_completion_record "$serial" "$out/completion" "$build_id" "$repeat_count"; then' \
  "$here/test-main.sh" || fail "runner bypasses the completion receipt gate"
if grep -Fq 'result_text="$(timeout 30' "$here/test-main.sh"; then
  fail "runner still discards the completion read status/stderr"
fi
echo "android completion record tests passed (27 cases; no device/network)"
