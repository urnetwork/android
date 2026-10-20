#!/usr/bin/env bash
# Exercise the real preinstall rejection and result publisher with finite fake
# ADB reads. Never load runner startup, contact devices or build applications.
set -euo pipefail
umask 077
here="$(cd "$(dirname "$0")" && pwd)"
source "$here/test-main-lib.sh"
fail() { echo "FAIL: $*" >&2; exit 1; }
fixture="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-authorization.test.XXXXXX")"
trap 'rm -rf "$fixture"' EXIT

for helper in authorize_selected_device write_target_diagnostic record_acceptance_result \
    record_device_cases record_full_cases record_smoke_result uninstall_acceptance_packages; do
  # shellcheck disable=SC2294
  eval "$(sed -n "/^$helper()/,/^}/p" "$here/test-main.sh")"
done
preinstall_boundary="$(sed -n \
  '/^    if ! authorize_selected_device "\$serial".*; then$/,/^    fi$/p' "$here/test-main.sh")"
[ -n "$preinstall_boundary" ] || fail "missing production preinstall authorization boundary"
[ "$(printf '%s\n' "$preinstall_boundary" | grep -c '^    if ! authorize_selected_device ')" = 1 ] || \
  fail "ambiguous production preinstall authorization boundary"

# Only the configured constant is substituted. The actual serial equality,
# capability predicates and caller dispatch remain the production functions.
android_acceptance_canonical_solana_serial() { printf 'synthetic-canonical-saga\n'; }
timeout() {
  [ "$1:$2" = 15:authorization_fake_adb ] || fail "authorization changed its command bound or tool"
  shift
  "$@"
}
android_acceptance_runner_owns_emulator() {
  [ "$1:$2:$3:$4:$5" = "authorization_fake_adb:emulator-5554:synthetic-avd:42:synthetic-owner" ] || \
    fail "changed emulator ownership inputs"
  printf 'owned-emulator\n' >>"$authorization_reads"
  return 1
}
runner_owns_peer_emulator() {
  printf 'peer-emulator\n' >>"$authorization_reads"
  return 1
}
android_acceptance_uninstall_package() {
  printf 'mutation\n' >>"$authorization_mutations"
  return 99
}

# The fake only recognizes the eleven read-only commands in the identity proof.
# Subprocess command substitution cannot hide reads because the receipt is a file.
authorization_fake_adb() {
  [ "$1" = -s ] && [ "$2" = "$serial" ] || fail "authorization substituted a selected device"
  shift 2
  local stage value
  case "$*" in
    get-state) stage=adb-state; value=device ;;
    get-serialno) stage=serial; value="$canonical_solana_serial" ;;
    'shell getprop ro.kernel.qemu') stage=qemu; value=0 ;;
    'shell getprop ro.boot.qemu') stage=boot-qemu; value=0 ;;
    'shell getprop ro.product.manufacturer') stage=identity-manufacturer; value=synthetic-vendor ;;
    'shell getprop ro.product.brand') stage=identity-brand; value=synthetic-brand ;;
    'shell getprop ro.product.model') stage=identity-model; value=Saga ;;
    'shell getprop ro.product.name') stage=identity-name; value=synthetic-product ;;
    'shell getprop ro.product.device') stage=identity-device; value=synthetic-device ;;
    'shell getprop ro.build.version.sdk') stage=api; value=34 ;;
    'shell getprop ro.product.cpu.abilist') stage=abi; value=arm64-v8a,armeabi-v7a ;;
    *) printf 'unexpected-command\n' >>"$authorization_mutations"; return 99 ;;
  esac
  printf '%s\n' "$stage" >>"$authorization_reads"
  if [ "$authorization_fault" = command ] && [ "$stage" = "$authorization_fault_stage" ]; then
    printf 'synthetic-private-output\n'
    printf 'synthetic-private-error\n' >&2
    return "$authorization_fault_status"
  fi
  if [ "$authorization_fault" = value ] && [ "$stage" = "$authorization_fault_stage" ]; then
    value="$authorization_fault_value"
  fi
  printf '%s\r\n' "$value"
}

# Run the actual rejection branch, including its continue, matrix rows and
# compact result publisher. A rejected cell must not reach the install boundary.
authorization_preinstall_case() (
  local label="$1" route="$2" expected_stage="$3" expected_outcome="$4" expected_status="$5" expected_reads="$6"
  local out="$fixture/$label" serial=synthetic-canonical-saga device_id=synthetic-device
  local adb=authorization_fake_adb canonical_solana_serial=synthetic-canonical-saga
  local execution_mode=canonical diagnostic_owned_avd=0 diagnostic_device=synthetic-diagnostic
  local started_emulator_serial=emulator-5554 peer_serial=emulator-5556
  local emulator_pid=42 emulator_owner_token=synthetic-owner avd_name=synthetic-avd
  local -a reserved_device_serials=(synthetic-reserved-a synthetic-reserved-b)
  local target=solana_dapp profile=full smoke_only=0 run_peer_to_peer=1
  local diagnostic_case=peer-to-peer diagnostic_selector=--diagnostic-device=synthetic-diagnostic
  local build_id=synthetic-build input_fingerprint=synthetic-input timestamp=synthetic-run
  local main_test_scope=synthetic.AcceptanceTest result_summary_script="$here/scripts/acceptance-result.mjs"
  local overall=0 reached_install=0 expected_rows=7 one
  local authorization_reads="$out/reads.txt" authorization_mutations="$out/mutations.txt"
  local device_results="$out/matrix.tsv" smoke_results="$out/smoke.tsv" results_ndjson="$out/results.ndjson"
  local android_acceptance_timeout_executable=never-run-timeout
  mkdir -p "$out"
  : >"$authorization_reads"
  case "$route" in
    canonical) ;;
    reserved) serial=synthetic-reserved-a ;;
    foreign) serial=synthetic-foreign ;;
    emulator) serial=emulator-5554 ;;
    peer) serial=emulator-5556 ;;
    diagnostic) execution_mode=diagnostic; serial="$diagnostic_device"; expected_rows=1 ;;
    smoke) smoke_only=1; expected_rows=1 ;;
    *) fail "unknown synthetic route" ;;
  esac
  for one in once; do
    # shellcheck disable=SC2294
    eval "$preinstall_boundary" >"$out/boundary.stdout" 2>"$out/boundary.stderr"
    reached_install=1
  done
  [ ! -e "$authorization_mutations" ] || fail "$label: identity proof performed a mutation"
  [ "$(wc -l <"$authorization_reads" | tr -d ' ')" = "$expected_reads" ] || \
    fail "$label: changed read count, retry or substitution"
  if [ "$expected_stage" = none ]; then
    [ "$reached_install:$overall" = 1:0 ] || fail "$label: valid identity was rejected"
    [ ! -e "$out/authorization-failure.txt" ] && [ ! -e "$out/result-device.json" ] || \
      fail "$label: success manufactured a failed-stage receipt"
    return 0
  fi
  [ "$reached_install:$overall" = 0:1 ] || fail "$label: failed authority reached install or passed"
  if [ "$smoke_only" = 1 ]; then
    [ "$(wc -l <"$smoke_results" | tr -d ' ')" = "$expected_rows" ] || fail "$label: smoke failure lost"
  else
    [ "$(wc -l <"$device_results" | tr -d ' ')" = "$expected_rows" ] || fail "$label: selected failure rows lost"
    awk -F '\t' '$5 != "FAIL" { exit 1 }' "$device_results" || fail "$label: refusal became a pass or skip"
  fi
  # Validate the old publisher too, before the causal missing-receipt oracle.
  # A missing/broken Node command must not masquerade as the semantic old red.
  AUTHORIZATION_OUT="$out" node --input-type=module <<'JS'
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
const root = process.env.AUTHORIZATION_OUT;
const text = fs.readFileSync(path.join(root, "result-device.json"), "utf8");
const result = JSON.parse(text);
assert.equal(result.status, "failed");
assert.equal(result.exitCode, 1);
assert.equal(fs.readFileSync(path.join(root, "failure.json"), "utf8"), text);
assert.equal(fs.readFileSync(path.join(root, "results.ndjson"), "utf8"), text);
JS
  [ -f "$out/authorization-failure.txt" ] || \
    fail "preinstall authorization discarded the actual failed stage"
  grep -Fxq "stage=$expected_stage" "$out/authorization-failure.txt" || fail "$label: wrong failed stage"
  grep -Fxq "outcome=$expected_outcome" "$out/authorization-failure.txt" || fail "$label: wrong failed outcome"
  grep -Fxq "command_exit_status=$expected_status" "$out/authorization-failure.txt" || fail "$label: lost exact command status"
  AUTHORIZATION_OUT="$out" AUTHORIZATION_ROUTE="$route" node --input-type=module <<'JS'
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
const root = process.env.AUTHORIZATION_OUT;
const receiptPath = path.join(root, "authorization-failure.txt");
const receipt = fs.readFileSync(receiptPath, "utf8");
assert.ok(Buffer.byteLength(receipt) <= 512);
assert.equal(fs.statSync(receiptPath).mode & 0o777, 0o600);
assert.equal(fs.statSync(receiptPath).nlink, 1);
assert.match(receipt, /^version=1\nboundary=preinstall-authorization\nstage=[a-z-]+\noutcome=[a-z-]+\ncommand_exit_status=(?:[0-9]+|not-run|unknown)\nobserved_utc=\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ\n$/);
assert.doesNotMatch(receipt, /synthetic|Saga|arm64|authorization_fake_adb/);
const result = JSON.parse(fs.readFileSync(path.join(root, "result-device.json"), "utf8"));
assert.equal(result.status, "failed");
assert.equal(result.exitCode, 1);
assert.equal(result.failure.classification, "device");
assert.notEqual(result.failure.signature, "no recognizable failure line");
assert.ok(result.artifacts.some(a => a.path === "authorization-failure.txt"));
assert.equal(result.reproCommand, process.env.AUTHORIZATION_ROUTE === "diagnostic" ?
  "./test-main.sh --diagnostic-device=synthetic-diagnostic --diagnostic-case=peer-to-peer --flavor=solana_dapp" :
  "./test-main.sh --profile=full --flavor=solana_dapp");
assert.equal(fs.readFileSync(path.join(root, "failure.json"), "utf8"),
  fs.readFileSync(path.join(root, "result-device.json"), "utf8"));
for (const name of ["authorization-failure.txt", "runner-error.log", "result-device.json", "boundary.stderr"]) {
  assert.doesNotMatch(fs.readFileSync(path.join(root, name), "utf8"), /synthetic-private-(?:output|error)/);
}
JS
  # The adjacent cleanup gate must still refuse; recording evidence is not
  # authorization to uninstall or to substitute another device.
  if uninstall_acceptance_packages "$serial" "$out/cleanup" >"$out/cleanup.stdout" 2>"$out/cleanup.stderr"; then
    fail "$label: failed authorization permitted package cleanup"
  fi
  [ ! -e "$authorization_mutations" ] || fail "$label: failed authority mutated packages"
)

test_preinstall_retains_exact_failed_command() {
  local authorization_fault=command authorization_fault_stage=api authorization_fault_status=73 authorization_fault_value=''
  authorization_preinstall_case causal-api-command canonical api command-failed 73 10
  echo "PASS test_preinstall_retains_exact_failed_command"
}

test_preinstall_authorization_stage_controls() {
  local authorization_fault=command authorization_fault_stage authorization_fault_status=73 authorization_fault_value=''
  local stage outcome count=0 value_case=0
  for stage in adb-state serial qemu boot-qemu identity-manufacturer identity-brand identity-model identity-name identity-device api abi; do
    count=$((count + 1))
    authorization_fault_stage="$stage"
    authorization_preinstall_case "command-$stage" canonical "$stage" command-failed 73 "$count"
  done
  authorization_fault_stage=adb-state
  for authorization_fault_status in 1 124 127 137 255; do
    authorization_preinstall_case "status-$authorization_fault_status" canonical adb-state command-failed "$authorization_fault_status" 1
  done
  authorization_fault=value
  while IFS='|' read -r authorization_fault_stage authorization_fault_value stage outcome count; do
    value_case=$((value_case + 1))
    authorization_preinstall_case "value-$value_case-$authorization_fault_stage" canonical "$stage" "$outcome" 0 "$count"
  done <<'CASES'
adb-state|offline|adb-state|value-mismatch|1
serial|synthetic-other|serial|value-mismatch|2
qemu|1|emulator-flags|value-mismatch|4
boot-qemu|1|emulator-flags|value-mismatch|4
identity-model|synthetic-other|hardware|unsupported|9
api||api|value-mismatch|10
api|synthetic-nonnumeric|api|value-mismatch|10
api|25|api|unsupported|10
abi|x86_64|abi|unsupported|11
CASES
  authorization_fault=none
  authorization_preinstall_case valid canonical none none none 11
  echo "PASS test_preinstall_authorization_stage_controls"
}

test_preinstall_authorization_scope_controls() {
  local authorization_fault=none authorization_fault_stage='' authorization_fault_status=0 authorization_fault_value=''
  authorization_preinstall_case reserved reserved selection not-authorized not-run 0
  authorization_preinstall_case foreign foreign selection not-authorized not-run 0
  authorization_preinstall_case lost-emulator emulator owned-emulator ownership-unverified unknown 1
  authorization_preinstall_case lost-peer peer peer-emulator ownership-unverified unknown 1
  authorization_fault=command
  authorization_fault_stage=adb-state
  authorization_fault_status=124
  authorization_preinstall_case diagnostic diagnostic adb-state command-failed 124 1
  authorization_preinstall_case smoke smoke adb-state command-failed 124 1
  echo "PASS test_preinstall_authorization_scope_controls"
}

test_authorization_failure_receipt_refusals() {
  local out="$fixture/receipt-refusals" stage outcome status destination
  mkdir -p "$out"
  while IFS='|' read -r stage outcome status; do
    if android_acceptance_authorization_failure "$out/invalid.txt" "$stage" "$outcome" "$status"; then
      fail "invalid fixed-schema authorization receipt accepted"
    fi
    [ ! -e "$out/invalid.txt" ] || fail "invalid schema published a receipt"
  done <<'CASES'
synthetic-private-stage|command-failed|1
api|synthetic-private-outcome|1
api|command-failed|0
api|command-failed|256
api|command-failed|synthetic-private-status
api|value-mismatch|1
api|unsupported|not-run
CASES
  printf 'preserve-existing\n' >"$out/existing"
  ln -s "$out/existing" "$out/alias"
  mkdir "$out/directory"
  for destination in existing alias directory; do
    if android_acceptance_authorization_failure "$out/$destination" api command-failed 73; then
      fail "receipt overwrote an existing destination"
    fi
    [ "$(cat "$out/existing")" = preserve-existing ] || fail "existing diagnostic changed"
  done
  if android_acceptance_authorization_failure "$out/missing/receipt" api command-failed 73; then
    fail "receipt manufactured an unowned parent"
  fi
  ln -s "$out/directory" "$out/parent-alias"
  if android_acceptance_authorization_failure "$out/parent-alias/receipt" api command-failed 73; then
    fail "receipt followed a parent alias"
  fi
  [ ! -e "$out/directory/receipt" ] || fail "aliased parent was changed"
  # No optional sink must remain a no-op, without broadening authorization.
  android_acceptance_authorization_failure '' api command-failed 73 || fail "optional absent sink failed"
  echo "PASS test_authorization_failure_receipt_refusals"
}

case "${1:-all}" in
  --causal) test_preinstall_retains_exact_failed_command ;;
  all)
    test_preinstall_retains_exact_failed_command
    test_preinstall_authorization_stage_controls
    test_preinstall_authorization_scope_controls
    test_authorization_failure_receipt_refusals
    ;;
  *) fail "unknown authorization test selector" ;;
esac
