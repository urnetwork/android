#!/usr/bin/env bash
# Offline selector/ownership tests: no SDK build, account API, or real ADB.
set -euo pipefail
umask 077

here="$(cd "$(dirname "$0")" && pwd)"
source "$here/test-main-lib.sh"
fail() { echo "FAIL: $*" >&2; exit 1; }
test_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-diagnostic-avd.test.XXXXXX")"
trap 'rm -rf "$test_dir"' EXIT

# Evaluate only the production CLI parser, stopping before any gate, build,
# credentials, artifacts, or device operation can run.
parser_source="$(sed -n '/^diagnostic_device=""/,/^acceptance_timeout_seconds=/p' "$here/test-main.sh")"
parse_request() (
  repeat_count=1 skip_build=0 headless=0 keep_emulator=0 keep_fixture=0
  result_matrix='' profile=full smoke_only=0 targets='github play solana_dapp fdroid'
  selected_targets='' selected_flavor_value='' flavor_selector_count=0 run_peer_to_peer=1
  # shellcheck disable=SC2294
  eval "$parser_source"
  printf '%s\t%s\t%s\n' "$execution_mode" "${diagnostic_owned_avd:-0}" "$diagnostic_device"
)

for target in github play fdroid; do
  [ "$(parse_request --diagnostic-owned-avd --diagnostic-case=peer-to-peer --flavor="$target")" = $'diagnostic\t1\t' ] || \
    fail "fresh owned-AVD $target diagnostic was rejected before device startup"
done
[ "$(parse_request)" = $'canonical\t0\t' ] || fail "canonical default changed"
[ "$(parse_request --diagnostic-device=nonreserved-phone --diagnostic-case=peer-to-peer --flavor=github)" = $'diagnostic\t0\tnonreserved-phone' ] || \
  fail "existing exact physical diagnostic changed"
for bad_flag in --diagnostic-owned-avd --diagnostic-device=nonreserved-phone \
    --repeat=2 --skip-build --smoke --keep-emulator --keep-fixture \
    --profile=smoke --profile=flavor --flavor=fdroid; do
  if parse_request --diagnostic-owned-avd --diagnostic-case=peer-to-peer --flavor=play \
      "$bad_flag" >"$test_dir/rejected.log" 2>&1; then
    fail "owned diagnostic accepted incompatible option $bad_flag"
  fi
done
if parse_request --diagnostic-owned-avd --flavor=play >"$test_dir/rejected.log" 2>&1 || \
   parse_request --diagnostic-case=peer-to-peer --flavor=play >"$test_dir/rejected.log" 2>&1 || \
   parse_request --diagnostic-owned-avd --diagnostic-case=password --flavor=play >"$test_dir/rejected.log" 2>&1 || \
   parse_request --diagnostic-owned-avd --diagnostic-case=peer-to-peer --flavor=solana_dapp >"$test_dir/rejected.log" 2>&1 || \
   parse_request --diagnostic-device=nonreserved-phone --diagnostic-owned-avd \
      --diagnostic-case=peer-to-peer --flavor=play >"$test_dir/rejected.log" 2>&1; then
  fail "unpaired, non-P2P, physical Solana, or conflicting diagnostic request was accepted"
fi
if android_acceptance_validate_owned_avd_diagnostic_request \
    peer-to-peer 1 play 1 0 0 0 0 canonical-results.tsv >"$test_dir/rejected.log" 2>&1; then
  fail "owned diagnostic may write canonical results"
fi
for bad_serial in emulator-5610 3B161FDJG001KT R5CX21FY6ND; do
  if parse_request --diagnostic-device="$bad_serial" --diagnostic-case=peer-to-peer \
      --flavor=play >"$test_dir/rejected.log" 2>&1; then
    fail "physical selector may borrow emulator/reserved serial $bad_serial"
  fi
done

capture_source="$(sed -n '/^capture_device_fleet()/,/^}/p' "$here/test-main.sh")"
fleet_source="$(sed -n '/^capture_device_fleet ||/,/^\[ -s "\$device_serials" \]/p' "$here/test-main.sh")"
[ -n "$fleet_source" ] || fail "production fleet startup boundary not found"

# The real startup/selection block is exercised with a fake process launcher
# and read-only fake ADB. A same-name/port guest is never sufficient proof.
exercise_fleet() (
  local scenario="$1" cohort="$2"
  run_dir="$test_dir/$scenario-$cohort"
  mkdir -p "$run_dir/artifacts"
  artifacts="$run_dir/artifacts"
  device_serials="$run_dir/selected"
  captured_device_serials="$run_dir/captured"
  excluded_devices="$run_dir/excluded"
  execution_mode=diagnostic diagnostic_owned_avd=1 diagnostic_device=''
  canonical_solana_serial='' started_emulator_serial=''
  started_emulator=0 emulator_pid='' emulator_owner_token=''
  reserved_device_serials=(3B161FDJG001KT R5CX21FY6ND)
  adb=fake_adb emulator=fake-emulator avd_name=test-owned-avd
  timestamp=test headless=1
  die() { echo "$*" >&2; exit 1; }
  timeout() { shift; "$@"; }
  fake_adb() {
    [ "$*" = 'devices -l' ] || fail "unexpected device action: $*"
    printf 'List of devices attached\n'
    if [ "$cohort" = mixed ]; then
      printf '%s\n' '3B161FDJG001KT device' 'R5CX21FY6ND offline' \
        'O1N1XT172304047 device' 'foreign-phone device' 'emulator-5554 device'
    fi
    if [ -e "$run_dir/launched" ]; then
      case "$scenario" in
        missing) ;;
        offline) printf 'emulator-5610 offline\n' ;;
        duplicate) printf 'emulator-5610 device\nemulator-5610 device\n' ;;
        *) printf 'emulator-5610 device\n' ;;
      esac
    fi
  }
  available_emulator_console_port() { printf '5610\n'; }
  run_android_acceptance_shared_avd_emulator() {
    [ "$1" = fake-emulator ] && [ "$2" = "$artifacts/emulator.log" ] && [ -n "$3" ] || return 91
    shift 3
    [ "$*" = '-avd test-owned-avd -read-only -gpu host -port 5610 -no-snapshot -no-boot-anim -netdelay none -netspeed full -no-window' ] || return 92
    printf 'launched\n' >"$run_dir/launched"
  }
  android_acceptance_wait_for_runner_owned_emulator() {
    [ "$1:$2:$3:$4:$5:$6" = "$adb:emulator-5610:$avd_name:$emulator_pid:$emulator_owner_token:120" ] || return 93
    wait "$emulator_pid" || return 94
    [ "$scenario" != startup-ownership-lost ]
  }
  android_acceptance_runner_owns_emulator() {
    [ "$1:$2:$3:$4:$5" = "$adb:emulator-5610:$avd_name:$emulator_pid:$emulator_owner_token" ] || return 95
    [ "$scenario" != selection-ownership-lost ]
  }
  # shellcheck disable=SC2294
  eval "$capture_source"
  # shellcheck disable=SC2294
  eval "$fleet_source"
  [ "$started_emulator" = 1 ] && [ -n "$emulator_pid" ] && [ -n "$emulator_owner_token" ] || \
    fail "owned diagnostic did not enroll its exact child for mandatory cleanup"
  [ "$diagnostic_device" = emulator-5610 ] && [ "$(cat "$device_serials")" = emulator-5610 ] || \
    fail "owned diagnostic selected another attached device"
  [ "$(cat "$artifacts/diagnostic-captured-device-serials.txt")" = emulator-5610 ] || \
    fail "owned diagnostic captured an unowned device"
)
for cohort in empty mixed; do
  exercise_fleet success "$cohort" >"$test_dir/startup.log" 2>&1 || \
    { cat "$test_dir/startup.log" >&2; fail "owned diagnostic failed with $cohort attached fleet"; }
  for scenario in missing offline duplicate startup-ownership-lost selection-ownership-lost; do
    if exercise_fleet "$scenario" "$cohort" >"$test_dir/rejected.log" 2>&1; then
      fail "owned diagnostic accepted $scenario with $cohort attached fleet"
    fi
  done
done

# Exercise the real guest-to-child proof as well as the orchestration above.
# A live PID, exact port and matching AVD name cannot substitute for its token.
(
  captured="$test_dir/proof-captured" selected="$test_dir/proof-selected"
  printf 'emulator-5610\n' >"$captured"
  fake_owner_token=test-token fake_owner_avd=test-owned-avd fake_owner_state=device
  timeout() { shift; "$@"; }
  fake_owner_adb() {
    [ "$1:$2" = '-s:emulator-5610' ] || return 91
    shift 2
    case "$*" in
      get-state) printf '%s\n' "$fake_owner_state" ;;
      'emu avd id') printf '%s\nOK\n' "$fake_owner_token" ;;
      'emu avd name') printf '%s\nOK\n' "$fake_owner_avd" ;;
      *) fail "ownership proof attempted a mutation: $*" ;;
    esac
  }
  prove_owned_selection() {
    android_acceptance_select_owned_avd_diagnostic_device \
      "$captured" "$selected" fake_owner_adb emulator-5610 test-owned-avd "$1" test-token
  }
  prove_owned_selection "$$" || fail "exact owned guest was rejected"
  for bad_proof in token avd offline missing-token dead-child; do
    fake_owner_token=test-token fake_owner_avd=test-owned-avd fake_owner_state=device fake_owner_pid=$$
    case "$bad_proof" in
      token) fake_owner_token=foreign ;;
      avd) fake_owner_avd=foreign ;;
      offline) fake_owner_state=offline ;;
      missing-token) fake_owner_token='' ;;
      dead-child) (exit 0) & fake_owner_pid=$!; wait "$fake_owner_pid" ;;
    esac
    if prove_owned_selection "$fake_owner_pid"; then fail "owned selector accepted $bad_proof"; fi
  done
)

# The shared production mutation gate must not fall through to physical ADB
# readiness when ownership is lost, even though the diagnostic serial matches.
(
  # shellcheck disable=SC2294
  eval "$(sed -n '/^authorize_selected_device()/,/^}/p' "$here/test-main.sh")"
  execution_mode=diagnostic diagnostic_owned_avd=1 diagnostic_device=emulator-5610
  started_emulator_serial=emulator-5610 peer_serial='' canonical_solana_serial=''
  reserved_device_serials=(3B161FDJG001KT R5CX21FY6ND)
  adb=fake-adb avd_name=test-owned-avd emulator_pid=123 emulator_owner_token=test-token
  android_acceptance_runner_owns_emulator() { return 1; }
  android_acceptance_adb_device_ready() { fail "lost ownership fell through to physical readiness"; }
  if authorize_selected_device emulator-5610; then fail "lost ownership retained mutation authority"; fi
  started_emulator_serial=''
  if authorize_selected_device emulator-5610; then fail "lost launch identity retained mutation authority"; fi
)

# Run the actual diagnostic case body with mocked P2P and cleanup endpoints.
# It must continue before canonical signup code, and its receipt must describe
# a new owned AVD invocation, not permission to borrow this temporary serial.
cell_source="$(awk '
  /echo "\[android acceptance\] peer-to-peer diagnostic:/ { selected = 1 }
  selected { print }
  selected && /^      continue$/ { exit }
' "$here/test-main.sh")"
[ -n "$cell_source" ] || fail "production diagnostic case boundary not found"
(
  original_here="$here"
  here="$test_dir/cell-runner"
  mkdir -p "$here/tests/__acceptance__/build/github" "$here/tests/__acceptance__/build/fdroid"
  printf 'provider-build\n' >"$here/tests/__acceptance__/build/github/build-id"
  printf 'provider-build\n' >"$here/tests/__acceptance__/build/fdroid/build-id"
  diagnostic_selector=--diagnostic-owned-avd diagnostic_case=peer-to-peer
  serial=emulator-5610 device_id=device-001-emulator-5610 diagnostic_device_id=device-001
  target_apk=client-app test_apk=client-test build_id=client-build input_fingerprint=inputs
  overall=0
  run_android_peer_to_peer() {
    [ "$#" = 8 ] && [ "$1" = "$out/peer-to-peer" ] && \
      [ "$2:$3:$4:$7:$8" = 'client-app:client-test:client-build:provider-build:device-001' ] || \
      fail "diagnostic changed the P2P case arguments"
    [ "$5:$6" = "$provider_cache/app.apk:$provider_cache/test.apk" ] || \
      fail "diagnostic changed the exact provider APK pair"
    [ "$cell_outcome" != p2p-failed ]
  }
  uninstall_acceptance_packages() {
    [ "$1:$2" = "emulator-5610:$out/post-diagnostic-cleanup" ] || \
      fail "diagnostic cleaned a different device"
    [ "$cell_outcome" != cleanup-failed ]
  }
  record_acceptance_result() {
    [ "$6" = "./test-main.sh --diagnostic-owned-avd --diagnostic-case=peer-to-peer --flavor=$target" ] || \
      fail "diagnostic receipt advertises borrowing a temporary emulator"
    [ "${10}" = com.bringyour.network.acceptance.PhysicalLowbarSessionTest ] || \
      fail "diagnostic changed the product test scope"
  }
  record_device_cases() {
    [ "$1:$2:$3:$6" = "$device_id:$serial:$target:peer-to-peer" ] || \
      fail "diagnostic recorded a different device/flavor/case"
    if [ "$cell_outcome" = passed ]; then
      [ "$4" = PASS ] || fail "passing diagnostic case lost its result"
    else
      [ "$4" = FAIL ] || fail "failed P2P/cleanup masqueraded as diagnostic success"
    fi
  }
  for target in play fdroid; do
    for cell_outcome in passed p2p-failed cleanup-failed; do
      out="$test_dir/cell-$target-$cell_outcome"
      # shellcheck disable=SC2294
      eval "$cell_source" >"$test_dir/cell.log" 2>&1
      fail "diagnostic fell through to canonical signup"
    done
  done
  [ "$overall" = 1 ] || fail "failed diagnostics did not fail overall result"
  # Keep this extraction independent from the temporary fixture tree.
  [ -f "$original_here/test-main.sh" ] || fail "production runner disappeared"
)

# Honest result and fixture boundaries are unchanged. No instant-account
# creation/deletion can be reached through the diagnostic case branch.
if android_acceptance_manages_account_fixture diagnostic 0; then fail "diagnostic owns account fixture"; fi
android_acceptance_manages_account_fixture canonical 0 || fail "canonical fixture lifecycle changed"
receipt_source="$(awk '
  /^    mode diagnostic-only/ { selected = 1; print previous }
  selected { print }
  selected && /^  echo "\[android acceptance\] DIAGNOSTIC ONLY:/ { exit }
  { previous = $0 }
' "$here/test-main.sh")"
[ -n "$receipt_source" ] || fail "production diagnostic receipt not found"
(
  artifacts="$test_dir/receipt"
  mkdir -p "$artifacts"
  diagnostic_device=emulator-5610 diagnostic_case=peer-to-peer selected_flavor_value=play
  for diagnostic_device_origin in runner-owned-avd attached-physical; do
    # shellcheck disable=SC2294
    eval "$receipt_source" >"$test_dir/receipt.log"
    expected_receipt=$'mode\tdiagnostic-only\nfinal_proof\tforbidden\ndevice\temulator-5610\ndevice_origin\t'
    expected_receipt+="$diagnostic_device_origin"$'\nflavor\tplay\ncase\tpeer-to-peer\nbuild\tfresh-paired-apks\nfixture\tunmanaged'
    [ "$(cat "$artifacts/diagnostic-request.tsv")" = "$expected_receipt" ] || \
      fail "diagnostic receipt weakened final-proof, fixture, or target-origin boundary"
  done
)
cleanup_source="$(sed -n '/^cleanup()/,/^record_smoke_result()/p' "$here/test-main.sh")"
# shellcheck disable=SC2016
grep -Fq '[ "$started_emulator" -eq 1 ] && [ "$keep_emulator" -ne 1 ]' <<<"$cleanup_source" || \
  fail "owned child lost mandatory cleanup"
echo 'android owned-AVD diagnostic offline tests passed'
