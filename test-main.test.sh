#!/usr/bin/env bash
# Deterministic runner tests that never build or contact main.
set -euo pipefail
umask 077

here="$(cd "$(dirname "$0")" && pwd)"
source "$here/test-main-lib.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ "$(android_acceptance_positive_parallelism 1)" = 1 ] || \
  fail "positive GOMAXPROCS did not produce a worker count"
[ "$(android_acceptance_positive_parallelism 32)" = 32 ] || \
  fail "multi-digit GOMAXPROCS did not produce a worker count"
[ "$(android_acceptance_positive_parallelism 0007)" = 7 ] || \
  fail "positive GOMAXPROCS was not normalized"
for invalid_parallelism in '' 0 00 -1 +1 1.5 one '1 '; do
  if android_acceptance_positive_parallelism "$invalid_parallelism" >/dev/null; then
    fail "invalid GOMAXPROCS was accepted: '$invalid_parallelism'"
  fi
done
android_acceptance_session_running "" || fail "empty session PID was not optional"
android_acceptance_session_running "$$" || fail "live session PID was rejected"
(exit 0) & completed_session_pid=$!
wait "$completed_session_pid"
if android_acceptance_session_running "$completed_session_pid"; then
  fail "completed instrumentation PID was treated as live"
fi

workflow_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-workflow.test.XXXXXX")"
mkdir -p "$workflow_dir/ui/screenshots"
for workflow_iteration in 1 2; do
  for workflow_suffix in \
    email-signup email-login phone-signup phone-login \
    instant-account secret-key-login connected disconnected; do
    printf '\211PNG\015\012\032\012proof\n' \
      >"$workflow_dir/ui/screenshots/$workflow_iteration-$workflow_suffix.png"
  done
done
android_acceptance_verify_workflow_artifacts "$workflow_dir" 2 || \
  fail "complete workflow screenshot evidence was rejected"
rm "$workflow_dir/ui/screenshots/2-disconnected.png"
if android_acceptance_verify_workflow_artifacts "$workflow_dir" 2; then
  fail "a missing workflow screenshot was accepted"
fi
printf '\211PNG\015\012\032\012proof\n' \
  >"$workflow_dir/ui/screenshots/2-disconnected.png"
printf '\211PNG\015\012\032\012unexpected\n' \
  >"$workflow_dir/ui/screenshots/2-failure.png"
if android_acceptance_verify_workflow_artifacts "$workflow_dir" 2; then
  fail "an unexpected success-cell screenshot was accepted"
fi
rm "$workflow_dir/ui/screenshots/2-failure.png"
printf 'not a png\n' >"$workflow_dir/ui/screenshots/2-disconnected.png"
if android_acceptance_verify_workflow_artifacts "$workflow_dir" 2; then
  fail "a malformed workflow screenshot was accepted"
fi
rm "$workflow_dir/ui/screenshots/2-disconnected.png"
ln -s "$workflow_dir/ui/screenshots/1-disconnected.png" \
  "$workflow_dir/ui/screenshots/2-disconnected.png"
if android_acceptance_verify_workflow_artifacts "$workflow_dir" 2; then
  fail "a symlinked workflow screenshot was accepted"
fi
if android_acceptance_verify_workflow_artifacts "$workflow_dir" 0; then
  fail "a non-positive workflow repetition count was accepted"
fi
rm -rf "$workflow_dir"
# shellcheck disable=SC2016
grep -Fq 'gradle_worker_args=(--max-workers "$android_parallelism")' "$here/test-main.sh" || \
  fail "positive GOMAXPROCS is not mapped to Gradle workers"
# shellcheck disable=SC2016
[ "$(grep -Fc '"${gradle_worker_args[@]}"' "$here/test-main.sh")" -eq 2 ] || \
  fail "both Android Gradle invocations do not consume the worker override"
# shellcheck disable=SC2016
if grep -Eq 'emulator_core_args|(^|[[:space:]])-cores([=[:space:]]|$)' \
    "$here/test-main.sh"; then
  fail "host GOMAXPROCS still changes acceptance AVD guest CPUs"
fi
[ "$(grep -Ec '_(args|args)=\(-avd .* -gpu host ' "$here/test-main.sh")" -eq 2 ] || \
  fail "fallback and peer acceptance AVDs do not both require host rendering"
for physical_artifact in \
  foreground.png activity.txt processes.txt status.json files/logs \
  physical-startup-goroutines.txt; do
  grep -Fq "$physical_artifact" "$here/test-main.sh" || \
    fail "physical teardown does not retain $physical_artifact"
done
# shellcheck disable=SC2016
grep -Fq 'android_acceptance_verify_physical_startup_evidence \' "$here/test-main.sh" || \
  fail "physical collection does not enforce Pending startup evidence"
physical_timeout_source="$(sed -n \
  '/private fun loginWithPassword(/,/private fun retainActiveClient/p' \
  "$here/app/app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt")"
grep -Fq 'throwLoginStartupTimeout(' <<<"$physical_timeout_source" || \
  fail "physical login does not route its exact timeout through the evidence boundary"
grep -Fq 'Sdk.writeGoroutineStacks(startupGoroutinesFile.absolutePath)' \
  <<<"$physical_timeout_source" || \
  fail "physical login timeout does not synchronously request Go stacks"
grep -Fq 'main-startup-goroutines.txt' "$here/test-main.sh" || \
  fail "main acceptance collection does not retain optional Pending startup evidence"
main_timeout_source="$(sed -n \
  '/private fun waitForMain()/,/private fun createInstantAccount()/p' \
  "$here/app/app/src/androidTest/java/com/bringyour/network/acceptance/MainAcceptanceTest.kt")"
grep -Fq 'throwLoginStartupTimeout(' <<<"$main_timeout_source" || \
  fail "main login does not route its Pending timeout through the evidence boundary"
grep -Fq 'Sdk.writeGoroutineStacks(startupGoroutinesFile.absolutePath)' \
  <<<"$main_timeout_source" || \
  fail "main login timeout does not synchronously request Go stacks"

startup_evidence_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-startup-evidence.test.XXXXXX")"
startup_status="$startup_evidence_dir/status.json"
startup_stacks="$startup_evidence_dir/goroutines.txt"
printf '%s\n' '{"state":"ready","extra":{}}' >"$startup_status"
android_acceptance_verify_physical_startup_evidence \
  "$startup_status" "$startup_stacks" || \
  fail "successful physical status incorrectly required startup stacks"
printf '%s\n' \
  '{"state":"error","extra":{"stage":"password-auth","failure":"password-auth-rejected"}}' \
  >"$startup_status"
android_acceptance_verify_physical_startup_evidence \
  "$startup_status" "$startup_stacks" || \
  fail "non-timeout physical failure incorrectly required startup stacks"
printf '%s\n' \
  '{"state":"error","extra":{"stage":"password-auth","failure":"startup-timeout"}}' \
  >"$startup_status"
if android_acceptance_verify_physical_startup_evidence \
    "$startup_status" "$startup_stacks"; then
  fail "physical startup timeout passed without Go stacks"
fi
: >"$startup_stacks"
if android_acceptance_verify_physical_startup_evidence \
    "$startup_status" "$startup_stacks"; then
  fail "physical startup timeout accepted empty Go stacks"
fi
printf '%s\n' 'not a Go stack' >"$startup_stacks"
if android_acceptance_verify_physical_startup_evidence \
    "$startup_status" "$startup_stacks"; then
  fail "physical startup timeout accepted malformed Go stacks"
fi
rm "$startup_stacks"
printf '%s\n' 'goroutine 7 [running]:' >"$startup_evidence_dir/real-stacks"
ln -s "$startup_evidence_dir/real-stacks" "$startup_stacks"
if android_acceptance_verify_physical_startup_evidence \
    "$startup_status" "$startup_stacks"; then
  fail "physical startup timeout accepted symlinked Go stacks"
fi
rm "$startup_stacks"
printf '%s\n' 'goroutine 7 [running]:' >"$startup_stacks"
android_acceptance_verify_physical_startup_evidence \
  "$startup_status" "$startup_stacks" || \
  fail "physical startup timeout rejected complete Go stacks"
rm -rf "$startup_evidence_dir"
# shellcheck disable=SC2016
grep -Fq 'android_acceptance_session_running "$session_pid"' "$here/test-main.sh" || \
  fail "physical status waits do not fail when instrumentation exits"
# shellcheck disable=SC2016
grep -Fq 'release_active_clients "$artifacts"' "$here/test-main.sh" || \
  fail "EXIT cleanup does not retry every retained P2P ownership ledger"
release_cleanup_source="$(sed -n \
  '/^release_active_clients()/,/^# Invoked through the EXIT/p' \
  "$here/test-main.sh")"
grep -Fq -- "-name 'active-client-id-*' -o -name 'physical-active-client-id'" \
  <<<"$release_cleanup_source" || \
  fail "Android cleanup does not collect both ledger and physical marker aliases"
# All collected paths must cross one CLI boundary so duplicate IDs are grouped
# by build/all/acceptance/client-cleanup.mjs before any destructive API call.
# shellcheck disable=SC2016
grep -Fq '"${active_clients[@]}"' <<<"$release_cleanup_source" || \
  fail "Android cleanup still invokes the client cleanup CLI once per marker"
[ "$(grep -Fc 'client-cleanup.mjs' <<<"$release_cleanup_source")" -eq 1 ] || \
  fail "Android cleanup has more than one client-cleanup CLI boundary"
physical_cleanup_source="$(sed -n \
  '/^cleanup()/,/^record_smoke_result()/p' "$here/test-main.sh")"
[ "$(grep -Fc 'client-cleanup.mjs' <<<"$physical_cleanup_source")" -eq 0 ] || \
  fail "EXIT cleanup separately releases physical aliases after the batch"
grep -Fq 'p2p_cleanup_failed=1' "$here/test-main.sh" || \
  fail "P2P cleanup failures are not retained as an unsafe-stop condition"
# shellcheck disable=SC2016
grep -Fq '[ "$parser_status" -eq 4 ]' "$here/test-main.sh" || \
  fail "terminal cleanup-ledger failures do not force the unsafe-stop condition"
grep -Fq 'client_cleanup_failed=1' "$here/test-main.sh" || \
  fail "P2P cleanup failure cannot stop subsequent account allocations"
# shellcheck disable=SC2016
grep -Fq 'android_acceptance_verify_workflow_artifacts "$out" "$repeat_count"' \
  "$here/test-main.sh" || fail "the live runner can pass without workflow screenshot evidence"
# This is a literal Kotlin source-contract assertion.
# shellcheck disable=SC2016
grep -Fq 'throw AssertionError("Could not capture workflow screenshot $name")' \
  "$here/app/app/src/androidTest/java/com/bringyour/network/acceptance/MainAcceptanceTest.kt" || \
  fail "instrumentation still silently ignores unavailable screenshots"

fleet_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-fleet.test.XXXXXX")"
fleet_raw="$fleet_dir/adb-devices.raw"
fleet_selected="$fleet_dir/selected"
fleet_excluded="$fleet_dir/excluded"
printf '%s\n' \
  '* daemon not running; starting now at tcp:5037' \
  '* daemon started successfully' \
  'List of devices attached' \
  $'R5CX21FY6ND\toffline product:e3q model:SM_S928U device:e3q' \
  $'emulator-5554\tdevice product:sdk model:Pixel_7 device:emu transport_id:2' \
  $'3B161FDJG001KT\tdevice product:husky model:Pixel_8_Pro device:husky' \
  $'partner-serial\tdevice product:partner model:Partner_Device device:partner' \
  >"$fleet_raw"
android_acceptance_select_adb_devices \
  "$fleet_raw" "$fleet_selected" "$fleet_excluded" \
  3B161FDJG001KT R5CX21FY6ND || fail "valid attached fleet was rejected"
[ "$(cat "$fleet_selected")" = "emulator-5554
partner-serial" ] || fail "eligible devices were not selected and sorted exactly"
expected_excluded=$'3B161FDJG001KT\tdevice\treserved-for-performance\nR5CX21FY6ND\toffline\treserved-for-performance'
[ "$(cat "$fleet_excluded")" = "$expected_excluded" ] || \
  fail "reserved performance devices were not excluded exactly"

fleet_records="$fleet_dir/records"
fleet_capabilities="$fleet_dir/capabilities"
fleet_plan="$fleet_dir/plan"
fleet_skips="$fleet_dir/skips"
fleet_results="$fleet_dir/results"
android_acceptance_write_device_records "$fleet_selected" "$fleet_records" || \
  fail "could not create device records"
[ "$(cat "$fleet_records")" = $'device-001-emulator-5554\temulator-5554\ndevice-002-partner-serial\tpartner-serial' ] || \
  fail "device artifact ids were not deterministic"
printf '%s\n' \
  $'device-001-emulator-5554\temulator-5554\t1\t0\t26' \
  $'device-002-partner-serial\tpartner-serial\t0\t1\t34' \
  >"$fleet_capabilities"
android_acceptance_write_device_flavor_plan \
  "$fleet_capabilities" "$fleet_plan" "$fleet_skips" \
  github play solana_dapp fdroid || \
  fail "could not create fleet plan"
[ "$(wc -l <"$fleet_plan" | tr -d ' ')" = 6 ] || \
  fail "device/flavor plan did not contain every compatible cell"
[ "$(sort -u "$fleet_plan" | wc -l | tr -d ' ')" = 6 ] || \
  fail "device/flavor plan contains a duplicate"
expected_skips=$'device-002-partner-serial\tpartner-serial\tplay\trequires-google-play-services\ndevice-001-emulator-5554\temulator-5554\tsolana_dapp\trequires-solana-seeker-or-saga'
[ "$(cat "$fleet_skips")" = "$expected_skips" ] || \
  fail "device/flavor incompatibilities were not recorded exactly"
for general_target in github fdroid; do
  [ "$(awk -F '\t' -v target="$general_target" '$3 == target { count++ } END { print count + 0 }' "$fleet_plan")" = 2 ] || \
    fail "$general_target did not cover every supported-ARM fleet device"
done
[ "$(awk -F '\t' '$3 == "play" { print $1 FS $2 }' "$fleet_plan")" = \
  $'device-001-emulator-5554\temulator-5554' ] || \
  fail "Play eligibility did not follow the Google Play services capability"
[ "$(awk -F '\t' '$3 == "solana_dapp" { print $1 FS $2 }' "$fleet_plan")" = \
  $'device-002-partner-serial\tpartner-serial' ] || \
  fail "Solana eligibility did not follow the Saga/Seeker capability"
android_acceptance_require_target_coverage \
  "$fleet_plan" github play solana_dapp fdroid || \
  fail "complete flavor coverage was rejected"
if android_acceptance_require_target_coverage "$fleet_plan" missing >/dev/null 2>&1; then
  fail "a requested flavor with no compatible device was accepted"
fi
printf '%s\n' $'device-bad\tbad-serial\t1\t0\tnot-an-api' >"$fleet_dir/bad-capabilities"
if android_acceptance_write_device_flavor_plan \
    "$fleet_dir/bad-capabilities" "$fleet_dir/bad-plan" "$fleet_dir/bad-skips" github; then
  fail "a malformed Android API capability was accepted"
fi
dropped_target="ethos""_dapp"
if android_acceptance_write_device_flavor_plan \
    "$fleet_capabilities" "$fleet_dir/dropped-plan" "$fleet_dir/dropped-skips" \
    "$dropped_target" >/dev/null 2>&1; then
  fail "a removed Android acceptance target was still planned"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 "$dropped_target" 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a removed Android acceptance target was accepted for diagnostics"
fi
while IFS=$'\t' read -r device_id device_serial flavor; do
  for result_case in email phone instant password data-plane peer-to-peer; do
    printf '%s\t%s\t%s\t%s\tPASS\tcovered\n' \
      "$device_id" "$device_serial" "$flavor" "$result_case" >>"$fleet_results"
  done
done <"$fleet_plan"
android_acceptance_verify_device_flavor_results "$fleet_plan" "$fleet_results" || \
  fail "complete passing device/flavor results were rejected"
sed '$d' "$fleet_results" >"$fleet_dir/incomplete-results"
if android_acceptance_verify_device_flavor_results "$fleet_plan" "$fleet_dir/incomplete-results"; then
  fail "an incomplete device/flavor result matrix was accepted"
fi
cp "$fleet_results" "$fleet_dir/failed-results"
sed -i.acceptance '1s/\tPASS\t/\tFAIL\t/' "$fleet_dir/failed-results"
rm -f "$fleet_dir/failed-results.acceptance"
if android_acceptance_verify_device_flavor_results "$fleet_plan" "$fleet_dir/failed-results"; then
  fail "a failing device/flavor result cell was accepted"
fi

fleet_smoke_results="$fleet_dir/smoke-results"
while IFS=$'\t' read -r device_id device_serial flavor; do
  printf '%s\t%s\t%s\tPASS\tlaunched\n' \
    "$device_id" "$device_serial" "$flavor" >>"$fleet_smoke_results"
done <"$fleet_plan"
android_acceptance_verify_device_flavor_smoke_results \
  "$fleet_plan" "$fleet_smoke_results" || fail "complete smoke matrix was rejected"
sed '$d' "$fleet_smoke_results" >"$fleet_dir/incomplete-smoke-results"
if android_acceptance_verify_device_flavor_smoke_results \
    "$fleet_plan" "$fleet_dir/incomplete-smoke-results"; then
  fail "an incomplete smoke matrix was accepted"
fi

printf '%s\n' 'List of devices attached' $'unreserved\tunauthorized usb:1-1' >"$fleet_raw"
if android_acceptance_select_adb_devices \
    "$fleet_raw" "$fleet_selected" "$fleet_excluded" \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "an unavailable non-reserved attached device was silently skipped"
fi
printf '%s\n' 'List of devices attached' $'same\tdevice' $'same\tdevice' >"$fleet_raw"
if android_acceptance_select_adb_devices \
    "$fleet_raw" "$fleet_selected" "$fleet_excluded" \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "duplicate attached device serials were accepted"
fi

[ "$(android_acceptance_execution_mode 0 0)" = canonical ] || \
  fail "no selectors no longer preserve canonical execution"
[ "$(android_acceptance_execution_mode 1 1)" = diagnostic ] || \
  fail "the paired diagnostic selectors did not select diagnostic execution"
if android_acceptance_execution_mode 1 0 >/dev/null 2>&1 || \
   android_acceptance_execution_mode 0 1 >/dev/null 2>&1; then
  fail "an unpaired diagnostic selector was accepted"
fi

android_acceptance_validate_diagnostic_request \
  0B111JEC200229 peer-to-peer 1 github 1 0 0 0 0 '' \
  3B161FDJG001KT R5CX21FY6ND || \
  fail "the exact bounded GitHub P2P diagnostic request was rejected"
if android_acceptance_validate_diagnostic_request \
    3B161FDJG001KT peer-to-peer 1 github 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a reserved performance device was accepted for diagnostic execution"
fi
if android_acceptance_validate_diagnostic_request \
    emulator-5554 peer-to-peer 1 github 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a peer emulator was accepted as the diagnostic physical device"
fi
if android_acceptance_validate_diagnostic_request \
    'unsafe serial' peer-to-peer 1 github 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "an unsafe diagnostic adb serial was accepted"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 password 1 github 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a non-P2P diagnostic case was accepted"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 2 github 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1 || \
   android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github,play 1 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a diagnostic request with more than one flavor was accepted"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github 3 0 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "in-process diagnostic repetition was accepted instead of independent runs"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github 1 1 0 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a diagnostic request was allowed to reuse stale APKs"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github 1 0 1 0 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1 || \
   android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github 1 0 0 1 0 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1 || \
   android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github 1 0 0 0 1 '' \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a diagnostic request weakened smoke, emulator, or fixture cleanup"
fi
if android_acceptance_validate_diagnostic_request \
    0B111JEC200229 peer-to-peer 1 github 1 0 0 0 0 proof.tsv \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "diagnostic selectors were accepted for a canonical result file"
fi

diagnostic_captured="$fleet_dir/diagnostic-captured"
diagnostic_selected="$fleet_dir/diagnostic-selected"
printf '%s\n' 0B111JEC200229 partner-serial >"$diagnostic_captured"
android_acceptance_select_diagnostic_device \
  "$diagnostic_captured" "$diagnostic_selected" 0B111JEC200229 \
  3B161FDJG001KT R5CX21FY6ND || \
  fail "the requested physical serial was not selected from the immutable fleet"
[ "$(cat "$diagnostic_selected")" = 0B111JEC200229 ] || \
  fail "diagnostic selection changed the exact requested serial"
if android_acceptance_select_diagnostic_device \
    "$diagnostic_captured" "$diagnostic_selected" missing-serial \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a serial absent from the captured fleet was accepted"
fi
if android_acceptance_select_diagnostic_device \
    "$diagnostic_captured" "$diagnostic_selected" 3B161FDJG001KT \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "the immutable-fleet selector bypassed reserved-device exclusion"
fi
printf '%s\n' 0B111JEC200229 0B111JEC200229 >"$diagnostic_captured"
if android_acceptance_select_diagnostic_device \
    "$diagnostic_captured" "$diagnostic_selected" 0B111JEC200229 \
    3B161FDJG001KT R5CX21FY6ND >/dev/null 2>&1; then
  fail "a duplicated diagnostic serial was accepted"
fi

diagnostic_plan="$fleet_dir/diagnostic-plan"
diagnostic_results="$fleet_dir/diagnostic-results"
printf '%s\n' $'device-001-0B111JEC200229\t0B111JEC200229\tgithub' >"$diagnostic_plan"
printf '%s\n' $'device-001-0B111JEC200229\t0B111JEC200229\tgithub\tpeer-to-peer\tPASS\tcovered' \
  >"$diagnostic_results"
android_acceptance_verify_diagnostic_result \
  "$diagnostic_plan" "$diagnostic_results" peer-to-peer || \
  fail "one passing focused P2P result was rejected"
sed 's/\tPASS\t/\tFAIL\t/' "$diagnostic_results" >"$fleet_dir/diagnostic-failed"
if android_acceptance_verify_diagnostic_result \
    "$diagnostic_plan" "$fleet_dir/diagnostic-failed" peer-to-peer; then
  fail "a failing focused P2P result was accepted"
fi
sed -n '1p' "$diagnostic_results" >"$fleet_dir/diagnostic-duplicate"
sed -n '1p' "$diagnostic_results" >>"$fleet_dir/diagnostic-duplicate"
if android_acceptance_verify_diagnostic_result \
    "$diagnostic_plan" "$fleet_dir/diagnostic-duplicate" peer-to-peer; then
  fail "duplicate focused P2P results were accepted"
fi

android_acceptance_manages_account_fixture canonical 0 || \
  fail "canonical full acceptance stopped owning its fixture lifecycle"
if android_acceptance_manages_account_fixture diagnostic 0 || \
   android_acceptance_manages_account_fixture canonical 1; then
  fail "diagnostic or smoke execution was allowed to mutate the shared fixture"
fi

sdk_owner_file="$fleet_dir/sdk-build-owner"
sdk_owner='android-acceptance-20260905-123-456'
printf '%s\n' "$sdk_owner" >"$sdk_owner_file"
android_acceptance_verify_sdk_build_owner "$sdk_owner_file" "$sdk_owner" || \
  fail "the exact fresh Android SDK build owner was rejected"
if android_acceptance_verify_sdk_build_owner "$sdk_owner_file" different-owner; then
  fail "a different Android SDK writer was accepted"
fi
rm -f "$sdk_owner_file"
if android_acceptance_verify_sdk_build_owner "$sdk_owner_file" "$sdk_owner"; then
  fail "a missing Android SDK build owner was accepted"
fi
: >"$sdk_owner_file"
if android_acceptance_verify_sdk_build_owner "$sdk_owner_file" "$sdk_owner"; then
  fail "an empty Android SDK build owner was accepted"
fi
printf '%s' "$sdk_owner" >"$sdk_owner_file"
if android_acceptance_verify_sdk_build_owner "$sdk_owner_file" "$sdk_owner"; then
  fail "an unterminated Android SDK build owner was accepted"
fi
printf '%s\nextra-owner\n' "$sdk_owner" >"$sdk_owner_file"
if android_acceptance_verify_sdk_build_owner "$sdk_owner_file" "$sdk_owner"; then
  fail "a multi-line Android SDK build owner was accepted"
fi
printf '%s\n' "$sdk_owner" >"$fleet_dir/sdk-build-owner-target"
rm -f "$sdk_owner_file"
ln -s "$fleet_dir/sdk-build-owner-target" "$sdk_owner_file"
if android_acceptance_verify_sdk_build_owner "$sdk_owner_file" "$sdk_owner"; then
  fail "a symlinked Android SDK build owner was accepted"
fi
if android_acceptance_verify_sdk_build_owner \
    "$fleet_dir/sdk-build-owner-target" 'unsafe owner'; then
  fail "a malformed expected Android SDK build owner was accepted"
fi

network_gate="$here/../tests/network-intensive-suite-lock.sh"
[ -x "$network_gate" ] || fail "the shared network-intensive suite gate is unavailable"
network_gate_dir="$fleet_dir/network-gate"
network_gate_lock="$network_gate_dir/network.lock"
network_gate_ready="$network_gate_dir/ready"
network_gate_release="$network_gate_dir/release"
network_gate_output="$network_gate_dir/owner.log"
network_gate_node_marker="$network_gate_dir/node-called"
network_gate_fake_bin="$network_gate_dir/bin"
mkdir -p "$network_gate_fake_bin"
mkfifo "$network_gate_release"
# These expressions belong to the generated fake, not this test shell.
# shellcheck disable=SC2016
printf '%s\n' \
  '#!/bin/sh' \
  'printf "%s\n" "${URNETWORK_NETWORK_TEST_LOCK_ROLE:-missing}" >"$ANDROID_GATE_NODE_MARKER"' \
  'exit 99' \
  >"$network_gate_fake_bin/node"
chmod 700 "$network_gate_fake_bin/node"

android_gate_missing_status=0
env -u URNETWORK_NETWORK_TEST_LOCK_HELD \
  -u URNETWORK_NETWORK_TEST_LOCK_ROLE \
  -u URNETWORK_NETWORK_TEST_LOCK_SCOPE \
  PATH="$network_gate_fake_bin:$PATH" \
  ANDROID_GATE_NODE_MARKER="$network_gate_node_marker" \
  URNETWORK_ROOT="$network_gate_dir/missing-root" \
  "$here/test-main.sh" --headless \
    >"$network_gate_dir/missing.log" 2>&1 || android_gate_missing_status=$?
[ "$android_gate_missing_status" -eq 127 ] || \
  fail "Android did not reject a missing shared gate with status 127"
[ ! -e "$network_gate_node_marker" ] || \
  fail "Android reached preflight before validating the shared gate path"

network_gate_owner_pid=""
cleanup_android_network_gate_owner() {
  if [ -n "$network_gate_owner_pid" ] && kill -0 "$network_gate_owner_pid" 2>/dev/null; then
    kill -TERM "$network_gate_owner_pid" 2>/dev/null || true
    wait "$network_gate_owner_pid" 2>/dev/null || true
  fi
}
trap cleanup_android_network_gate_owner EXIT
# Positional parameters in the owner program belong to its child shell.
# shellcheck disable=SC2016
env -u URNETWORK_NETWORK_TEST_LOCK_HELD \
  -u URNETWORK_NETWORK_TEST_LOCK_ROLE \
  -u URNETWORK_NETWORK_TEST_LOCK_SCOPE \
  URNETWORK_NETWORK_TESTING=1 \
  URNETWORK_NETWORK_TEST_LOCK_PATH="$network_gate_lock" \
  "$network_gate" main-acceptance android-gate-test-owner -- /bin/bash -c \
    'printf "ready\n" >"$1"; IFS= read -r _ <"$2"' \
    bash "$network_gate_ready" "$network_gate_release" \
    >"$network_gate_output" 2>&1 &
network_gate_owner_pid=$!
for _ in $(seq 1 500); do
  [ -f "$network_gate_ready" ] && break
  kill -0 "$network_gate_owner_pid" 2>/dev/null || break
  sleep 0.01
done
if [ ! -f "$network_gate_ready" ]; then
  cleanup_android_network_gate_owner
  network_gate_owner_pid=""
  fail "the real network gate owner did not become ready"
fi

android_gate_diagnostic_status=0
env -u URNETWORK_NETWORK_TEST_LOCK_HELD \
  -u URNETWORK_NETWORK_TEST_LOCK_ROLE \
  -u URNETWORK_NETWORK_TEST_LOCK_SCOPE \
  PATH="$network_gate_fake_bin:$PATH" \
  ANDROID_GATE_NODE_MARKER="$network_gate_node_marker" \
  URNETWORK_ROOT="$here/.." \
  URNETWORK_NETWORK_TESTING=1 \
  URNETWORK_NETWORK_TEST_LOCK_PATH="$network_gate_lock" \
  "$here/test-main.sh" --headless --flavor=github \
    --diagnostic-device=0B111JEC200229 --diagnostic-case=peer-to-peer \
    >"$network_gate_dir/diagnostic.log" 2>&1 || android_gate_diagnostic_status=$?
[ "$android_gate_diagnostic_status" -eq 75 ] || \
  fail "a direct Android diagnostic did not reject live shared ownership with status 75"

android_gate_full_status=0
env -u URNETWORK_NETWORK_TEST_LOCK_HELD \
  -u URNETWORK_NETWORK_TEST_LOCK_ROLE \
  -u URNETWORK_NETWORK_TEST_LOCK_SCOPE \
  PATH="$network_gate_fake_bin:$PATH" \
  ANDROID_GATE_NODE_MARKER="$network_gate_node_marker" \
  URNETWORK_ROOT="$here/.." \
  URNETWORK_NETWORK_TESTING=1 \
  URNETWORK_NETWORK_TEST_LOCK_PATH="$network_gate_lock" \
  "$here/test-main.sh" --headless \
    >"$network_gate_dir/full.log" 2>&1 || android_gate_full_status=$?
[ "$android_gate_full_status" -eq 75 ] || \
  fail "a direct full Android run did not reject live shared ownership with status 75"
[ ! -e "$network_gate_node_marker" ] || \
  fail "a contending Android runner reached preflight before acquiring the shared gate"

printf 'release\n' >"$network_gate_release"
wait "$network_gate_owner_pid" || fail "the real network gate owner failed"
network_gate_owner_pid=""

android_gate_forged_status=0
# Positional parameters in the forged-marker probe belong to its child shell.
# shellcheck disable=SC2016
env PATH="$network_gate_fake_bin:$PATH" \
  ANDROID_GATE_NODE_MARKER="$network_gate_node_marker" \
  URNETWORK_ROOT="$here/.." \
  URNETWORK_NETWORK_TESTING=1 \
  URNETWORK_NETWORK_TEST_LOCK_PATH="$network_gate_lock" \
  /bin/bash -c \
    'exec 9>&-; URNETWORK_NETWORK_TEST_LOCK_HELD=1 exec "$1" --headless' \
    bash "$here/test-main.sh" \
    >"$network_gate_dir/forged.log" 2>&1 || android_gate_forged_status=$?
[ "$android_gate_forged_status" -eq 70 ] || \
  fail "a forged Android network-lock marker did not fail closed with status 70"
[ ! -e "$network_gate_node_marker" ] || \
  fail "a forged Android network-lock marker reached preflight"

android_gate_nested_status=0
env -u URNETWORK_NETWORK_TEST_LOCK_HELD \
  -u URNETWORK_NETWORK_TEST_LOCK_ROLE \
  -u URNETWORK_NETWORK_TEST_LOCK_SCOPE \
  PATH="$network_gate_fake_bin:$PATH" \
  ANDROID_GATE_NODE_MARKER="$network_gate_node_marker" \
  URNETWORK_ROOT="$here/.." \
  URNETWORK_NETWORK_TESTING=1 \
  URNETWORK_NETWORK_TEST_LOCK_PATH="$network_gate_lock" \
  "$network_gate" main-acceptance android-p2p-diagnostic -- \
    "$here/test-main.sh" --headless \
    >"$network_gate_dir/nested.log" 2>&1 || android_gate_nested_status=$?
[ "$android_gate_nested_status" -eq 1 ] || \
  fail "Android rejected valid inherited shared ownership before preflight"
[ "$(cat "$network_gate_node_marker")" = android-p2p-diagnostic ] || \
  fail "Android replaced the explicit outer diagnostic ownership role"
rm -f "$network_gate_node_marker"
trap - EXIT

grep -Fq 'reserved_device_serials=(3B161FDJG001KT R5CX21FY6ND)' "$here/test-main.sh" || \
  fail "the two performance devices are not mandatory runner exclusions"
grep -Fq 'targets="github play solana_dapp fdroid"' "$here/test-main.sh" || \
  fail "the canonical no-selector flavor matrix changed"
if grep -Fq "$dropped_target" \
    "$here/test-main.sh" "$here/test-main-lib.sh" "$here/build.sh" \
    "$here/.github/workflows/build-and-test.yml"; then
  fail "the removed Android target remains in acceptance or aggregate build dispatch"
fi
for aggregate_build_task in \
    assemblePlayRelease bundlePlayRelease assembleSolana_dappRelease \
    compileGithubReleaseKotlin; do
  [ "$(grep -Fo "$aggregate_build_task" "$here/build.sh" | wc -l | tr -d ' ')" -eq 1 ] || \
    fail "aggregate Android build does not dispatch $aggregate_build_task exactly once"
done
# These checks intentionally assert literal runner source expressions.
# shellcheck disable=SC2016
grep -Fq 'exec "$network_test_gate" main-acceptance android-acceptance --' \
  "$here/test-main.sh" || \
  fail "direct Android acceptance does not enter the shared network gate"
# shellcheck disable=SC2016
grep -Fq '"$network_test_gate" --verify-held main-acceptance' "$here/test-main.sh" || \
  fail "Android acceptance does not verify inherited shared ownership"
# This intentionally asserts a literal runner source expression.
# shellcheck disable=SC2016
grep -Fq 'URNETWORK_ANDROID_SDK_BUILD_OWNER="$sdk_build_owner"' "$here/test-main.sh" || \
  fail "the Android SDK build does not publish this run's owner"
grep -Fq 'sdk_android_output_lock_acquire android-acceptance-consumer' \
  "$here/test-main.sh" || \
  fail "the Android runner does not retain consumer ownership of the SDK output"
grep -Fq 'android_acceptance_verify_sdk_build_owner' "$here/test-main.sh" || \
  fail "the Android runner does not verify fresh SDK provenance"
if grep -Eq 'exec[[:space:]]+9|>&9|<&9' "$here/test-main.sh"; then
  fail "the Android SDK consumer clobbers the canonical suite's descriptor 9"
fi
if grep -Fq 'exec 8>&-' "$here/test-main.sh"; then
  fail "the Android runner releases SDK consumer ownership before it exits"
fi
sdk_build_line="$(grep -n -m1 'timeout 3600 ./gradlew :app:buildSdkAcceptance' \
  "$here/test-main.sh" | cut -d: -f1)"
# The following locators intentionally match literal runner expressions.
# shellcheck disable=SC2016
network_gate_line="$(grep -n -m1 \
  'exec "$network_test_gate" main-acceptance android-acceptance' \
  "$here/test-main.sh" | cut -d: -f1)"
# shellcheck disable=SC2016
network_verify_line="$(grep -n -m1 '"$network_test_gate" --verify-held main-acceptance' \
  "$here/test-main.sh" | cut -d: -f1)"
# shellcheck disable=SC2016
preflight_line="$(grep -n -m1 'node "$root/build/all/acceptance/preflight-main.mjs"' \
  "$here/test-main.sh" | cut -d: -f1)"
# shellcheck disable=SC2016
artifact_mutation_line="$(grep -n -m1 'mkdir -p "$artifacts"' \
  "$here/test-main.sh" | cut -d: -f1)"
sdk_lock_line="$(grep -n -m1 \
  'sdk_android_output_lock_acquire android-acceptance-consumer' \
  "$here/test-main.sh" | cut -d: -f1)"
sdk_owner_line="$(grep -n -m1 'android_acceptance_verify_sdk_build_owner' \
  "$here/test-main.sh" | cut -d: -f1)"
fleet_capture_line="$(grep -n -m1 '^capture_device_fleet ||' \
  "$here/test-main.sh" | cut -d: -f1)"
if [ -z "$network_gate_line" ] || [ -z "$network_verify_line" ] ||
   [ -z "$preflight_line" ] || [ -z "$artifact_mutation_line" ] ||
   [ "$network_gate_line" -ge "$preflight_line" ] ||
   [ "$network_verify_line" -ge "$preflight_line" ] ||
   [ "$network_gate_line" -ge "$artifact_mutation_line" ] ||
   [ "$network_verify_line" -ge "$artifact_mutation_line" ] ||
   [ "$network_gate_line" -ge "$sdk_build_line" ] ||
   [ "$network_verify_line" -ge "$sdk_build_line" ] ||
   [ "$network_gate_line" -ge "$fleet_capture_line" ] ||
   [ "$network_verify_line" -ge "$fleet_capture_line" ]; then
  fail "the shared network gate runs after Android preflight, build, fleet, or artifact mutation"
fi
if [ -z "$sdk_build_line" ] || [ -z "$sdk_lock_line" ] ||
   [ -z "$sdk_owner_line" ] || [ -z "$fleet_capture_line" ] ||
   [ "$sdk_build_line" -ge "$sdk_lock_line" ] ||
   [ "$sdk_lock_line" -ge "$sdk_owner_line" ] ||
   [ "$sdk_owner_line" -ge "$fleet_capture_line" ]; then
  fail "SDK build, retained lock, provenance check, and fleet capture are out of order"
fi
grep -Fq "commandLine 'make', 'build_android'" "$here/app/app/build.gradle" || \
  fail "Gradle bypasses the public kernel-gated Android SDK build target"
grep -Fq 'android_acceptance_verify_diagnostic_result' "$here/test-main.sh" || \
  fail "the diagnostic result can masquerade as a partial canonical matrix"
# These two checks intentionally assert literal runner source expressions.
# shellcheck disable=SC2016
grep -Fq 'device_results="$artifacts/diagnostic-peer-to-peer-results.tsv"' "$here/test-main.sh" || \
  fail "diagnostic results are not visibly separated from canonical results"
# shellcheck disable=SC2016
grep -Fq 'android_acceptance_manages_account_fixture "$execution_mode" "$smoke_only"' \
  "$here/test-main.sh" || \
  fail "the runner does not enforce mode-specific fixture ownership"
grep -Fq 'get android.unlock_code' "$here/test-main.sh" || \
  fail "the Android runner does not read the private unlock code"
grep -Fq 'android_acceptance_prepare_device' "$here/test-main.sh" || \
  fail "the Android runner bypasses structured device readiness"
grep -Fq 'android_acceptance_prepare_owned_emulator' "$here/test-main.sh" || \
  fail "runner-owned AVDs still consume physical-device readiness"
grep -Fq 'android_acceptance_restore_network' "$here/test-main.sh" || \
  fail "the Android runner does not restore runner-owned Wi-Fi state"
# This intentionally asserts the literal source expression.
# shellcheck disable=SC2016
grep -Fq '"$artifacts/device-readiness/${device_id}-animations.txt"' "$here/test-main.sh" || \
  fail "the Android runner does not retain per-device animation diagnostics"
grep -Fq "readiness failed: \${readiness_status:-unknown}" "$here/test-main.sh" || \
  fail "the Android runner hides the specific readiness failure"
# This is a literal source-contract assertion, not a local variable expansion.
# shellcheck disable=SC2016
grep -Fq 'case "$target" in play|solana_dapp) provider_target=github' "$here/test-main.sh" || \
  fail "restricted client flavors can still reach the generic peer AVD"
if awk '
  FNR == 1 { logical = "" }
  {
    line = $0
    continued = sub(/[[:space:]]*\\[[:space:]]*$/, "", line)
    logical = logical " " line
    if (continued) next
    if (logical ~ /\|[[:space:]]*grep[[:space:]]+-[^[:space:]]*q/) found = 1
    logical = ""
  }
  END { exit found ? 0 : 1 }
' "$here/test-main.sh" "$here/test-main-lib.sh"; then
  fail "the Android runner pipes a producer into an early-exit grep under pipefail"
fi
rm -rf "$fleet_dir"

foreground_timeout_seen=0
fake_foreground_timeout() {
  [ "$1" = --foreground ] || fail "timeout child was not kept in the foreground process group"
  foreground_timeout_seen=1
  shift
  shift
  "$@"
}
run_android_acceptance_timeout fake_foreground_timeout 30 true
[ "$foreground_timeout_seen" -eq 1 ] || fail "foreground timeout wrapper was not called"
system_timeout_executable="$(type -P timeout)"
[ -x "$system_timeout_executable" ] || fail "test requires an external GNU timeout"

emulator_log="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-emulator.test.XXXXXX")"
if (run_android_acceptance_shared_avd_emulator \
    /usr/bin/true "$emulator_log" -avd urnetwork-acceptance -gpu host) >/dev/null 2>&1; then
  fail "shared AVD launcher accepted a writable emulator"
fi
(run_android_acceptance_shared_avd_emulator \
  /usr/bin/true "$emulator_log" -avd urnetwork-acceptance -read-only -gpu host) || \
  fail "shared AVD launcher rejected a read-only host-rendered emulator"
if (run_android_acceptance_shared_avd_emulator \
    /usr/bin/true "$emulator_log" -avd urnetwork-acceptance -read-only) \
    >/dev/null 2>&1; then
  fail "shared AVD launcher accepted an implicit renderer"
fi
if (run_android_acceptance_shared_avd_emulator \
    /usr/bin/true "$emulator_log" -avd urnetwork-acceptance -read-only -gpu auto) \
    >/dev/null 2>&1; then
  fail "shared AVD launcher accepted a renderer that can select software"
fi
if (run_android_acceptance_shared_avd_emulator \
    /usr/bin/true "$emulator_log" -avd urnetwork-acceptance -read-only -gpu host -cores 1) \
    >/dev/null 2>&1; then
  fail "shared AVD launcher accepted a guest CPU override"
fi
rm -f "$emulator_log"

preflight_fixture_dir="$here/tests/fixtures/android-preflight"
assert_preflight_classification() {
  local classifier="$1" expected="$2" fixture="$3" actual

  actual="$($classifier "$(cat "$fixture")")"
  [ "$actual" = "$expected" ] || \
    fail "$fixture classified as $actual, expected $expected"
}
assert_preflight_classification \
  android_acceptance_classify_cpu_evidence insufficient \
  "$preflight_fixture_dir/captured-cpu.txt"
assert_preflight_classification \
  android_acceptance_classify_renderer_evidence software \
  "$preflight_fixture_dir/captured-renderer.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window error-dialog \
  "$preflight_fixture_dir/captured-window.txt"
assert_preflight_classification \
  android_acceptance_classify_cpu_evidence ready \
  "$preflight_fixture_dir/clean-cpu.txt"
assert_preflight_classification \
  android_acceptance_classify_renderer_evidence host \
  "$preflight_fixture_dir/clean-renderer.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window clear \
  "$preflight_fixture_dir/clean-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window clear \
  "$preflight_fixture_dir/duplicate-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window clear \
  "$preflight_fixture_dir/equivalent-window-syntax.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window clear \
  "$preflight_fixture_dir/transient-empty-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window error-dialog \
  "$preflight_fixture_dir/transient-empty-error-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window error-dialog \
  "$preflight_fixture_dir/transient-empty-current-error-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window malformed \
  "$preflight_fixture_dir/only-empty-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window malformed \
  "$preflight_fixture_dir/only-empty-current-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window malformed \
  "$preflight_fixture_dir/empty-null-window.txt"
assert_preflight_classification \
  android_acceptance_classify_focused_window ambiguous \
  "$preflight_fixture_dir/empty-conflicting-window.txt"
for fixture_kind in cpu renderer window; do
  classifier="android_acceptance_classify_${fixture_kind}_evidence"
  [ "$fixture_kind" != window ] || classifier=android_acceptance_classify_focused_window
  assert_preflight_classification \
    "$classifier" malformed "$preflight_fixture_dir/malformed-$fixture_kind.txt"
  assert_preflight_classification \
    "$classifier" ambiguous "$preflight_fixture_dir/ambiguous-$fixture_kind.txt"
done
[ "$(android_acceptance_classify_cpu_evidence \
  "$(cat "$preflight_fixture_dir/missing-evidence.txt")")" = missing ] || \
  fail "missing CPU evidence was not classified distinctly"
[ "$(android_acceptance_classify_renderer_evidence \
  "$(cat "$preflight_fixture_dir/missing-evidence.txt")")" = missing ] || \
  fail "missing renderer evidence was not classified distinctly"
[ "$(android_acceptance_classify_focused_window \
  "$(cat "$preflight_fixture_dir/missing-evidence.txt")")" = missing ] || \
  fail "missing focused-window evidence was not classified distinctly"
[ "$(android_acceptance_focused_error_subject \
  "$(cat "$preflight_fixture_dir/captured-window.txt")")" = com.android.systemui ] || \
  fail "captured SystemUI ANR subject was not retained"
[ "$(android_acceptance_focused_error_subject \
  "$(cat "$preflight_fixture_dir/transient-empty-error-window.txt")")" = com.android.systemui ] || \
  fail "SystemUI ANR subject after a transient empty window was not retained"
[ "$(android_acceptance_focused_error_subject \
  "$(cat "$preflight_fixture_dir/transient-empty-current-error-window.txt")")" = com.android.systemui ] || \
  fail "SystemUI ANR subject after a transient empty current focus was not retained"
[ "$(android_acceptance_focused_error_subject \
  "$(cat "$preflight_fixture_dir/only-empty-current-window.txt")")" = unknown ] || \
  fail "an empty-only current focus produced a trustworthy error subject"
[ "$(android_acceptance_classify_renderer_evidence \
  'GLES: Google (Apple), Android Emulator OpenGL ES Translator (Apple M1 Max), OpenGL ES 3.0')" = host ] || \
  fail "a live host-rendered SurfaceFlinger identity was rejected"
[ "$(android_acceptance_sanitized_device_id device-001-emulator-5558)" = device-001 ] || \
  fail "device diagnostic ID retained its raw emulator serial"
if android_acceptance_sanitized_device_id emulator-5558 >/dev/null; then
  fail "an unstructured serial was accepted as a diagnostic device ID"
fi

# Preflight owns an explicit timeout at runtime. This fake keeps its adb
# interaction deterministic without launching a process or touching a device.
timeout() {
  shift
  "$@"
}
preflight_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-preflight.test.XXXXXX")"
preflight_diagnostic="$preflight_test_dir/provider-preflight.txt"
FAKE_PREFLIGHT_CPU="$preflight_fixture_dir/clean-cpu.txt"
FAKE_PREFLIGHT_WINDOW="$preflight_fixture_dir/clean-window.txt"
fake_preflight_adb() {
  [ "$1" = -s ] && [ "$2" = emulator-5558 ] && [ "$3" = shell ] || return 90
  case "$4 $5" in
    'cat /sys/devices/system/cpu/online') sed 's/^online=//' "$FAKE_PREFLIGHT_CPU" ;;
    'dumpsys window') cat "$FAKE_PREFLIGHT_WINDOW" ;;
    *) return 91 ;;
  esac
}
android_acceptance_preflight_device \
  fake_preflight_adb emulator-5558 peer-avd provider "$preflight_diagnostic" \
  "$preflight_fixture_dir/clean-renderer.txt" || \
  fail "clean host-rendered provider preflight was rejected"
grep -Fxq 'device_id=peer-avd' "$preflight_diagnostic" || \
  fail "preflight diagnostic omitted the sanitized device identity"
grep -Fxq 'role=provider' "$preflight_diagnostic" || \
  fail "preflight diagnostic omitted the execution role"
grep -Fxq 'cpu_count=4' "$preflight_diagnostic" || \
  fail "preflight diagnostic omitted the verified guest CPU count"
grep -Fxq 'renderer=host' "$preflight_diagnostic" || \
  fail "preflight diagnostic omitted the verified renderer"
grep -Fxq 'focus=clear' "$preflight_diagnostic" || \
  fail "preflight diagnostic omitted the verified foreground state"
grep -Fxq 'result=ready' "$preflight_diagnostic" || \
  fail "clean preflight did not publish a ready terminal state"
if grep -Eq 'emulator-5558|urnetwork-acceptance|pid=' "$preflight_diagnostic"; then
  fail "preflight diagnostic exposed a serial, AVD name, or PID"
fi

FAKE_PREFLIGHT_CPU="$preflight_fixture_dir/captured-online-cpu.txt"
FAKE_PREFLIGHT_WINDOW="$preflight_fixture_dir/captured-window.txt"
if android_acceptance_preflight_device \
    fake_preflight_adb emulator-5558 peer-avd provider "$preflight_diagnostic" \
    "$preflight_fixture_dir/captured-renderer.txt"; then
  fail "captured one-CPU/software-rendered/SystemUI-ANR state passed preflight"
fi
grep -Fxq 'cpu=insufficient' "$preflight_diagnostic" || \
  fail "captured one-CPU rejection was not retained"
grep -Fxq 'renderer=software' "$preflight_diagnostic" || \
  fail "captured software renderer rejection was not retained"
grep -Fxq 'focus=error-dialog' "$preflight_diagnostic" || \
  fail "captured focused SystemUI ANR rejection was not retained"
grep -Fxq 'focused_error_subject=com.android.systemui' "$preflight_diagnostic" || \
  fail "captured SystemUI error-dialog subject was not retained"
grep -Fxq 'result=failed' "$preflight_diagnostic" || \
  fail "bad preflight did not publish a failed terminal state"

FAKE_PREFLIGHT_WINDOW="$preflight_fixture_dir/clean-window.txt"
android_acceptance_preflight_device \
  fake_preflight_adb emulator-5558 device-001 client \
  "$preflight_test_dir/client-preflight.txt" || \
  fail "clean physical-client focused-dialog preflight was rejected"
grep -Fxq 'cpu=not-applicable' "$preflight_test_dir/client-preflight.txt" || \
  fail "physical client unexpectedly required emulator CPU evidence"
grep -Fxq 'role=client' "$preflight_test_dir/client-preflight.txt" || \
  fail "physical-client role was not retained"
if android_acceptance_preflight_device \
    fake_preflight_adb emulator-5558 device-001-emulator-5558 client \
    "$preflight_test_dir/raw-id-preflight.txt"; then
  fail "preflight accepted a device ID containing a raw serial"
fi
rm -rf "$preflight_test_dir"
unset -f timeout

apk_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-apk-cache.test.XXXXXX")"
mkdir -p "$apk_test_dir/build" "$apk_test_dir/cache"
printf 'app payload\n' >"$apk_test_dir/build/app.apk"
printf 'test payload\n' >"$apk_test_dir/build/test.apk"
android_acceptance_cache_apks \
  "$apk_test_dir/build/app.apk" "$apk_test_dir/build/test.apk" "$apk_test_dir/cache" || \
  fail "could not cache an acceptance APK pair"
rm -f "$apk_test_dir/build/app.apk" "$apk_test_dir/build/test.apk"
[ "$(cat "$apk_test_dir/cache/app.apk")" = "app payload" ] || \
  fail "cached app APK did not survive removal of the build output"
[ "$(cat "$apk_test_dir/cache/test.apk")" = "test payload" ] || \
  fail "cached test APK did not survive removal of the build output"

cache_fingerprint="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
android_acceptance_write_cache_metadata "$apk_test_dir/cache" acceptance-build "$cache_fingerprint" || \
  fail "could not write cache metadata"
android_acceptance_cache_is_current "$apk_test_dir/cache" "$cache_fingerprint" || \
  fail "fresh APK cache was not accepted"
printf '%s\n' "different" >"$apk_test_dir/cache/input.sha256"
if android_acceptance_cache_is_current "$apk_test_dir/cache" "$cache_fingerprint"; then
  fail "mismatched APK cache fingerprint was accepted"
fi
android_acceptance_write_cache_metadata "$apk_test_dir/cache" acceptance-build "$cache_fingerprint" || \
  fail "could not restore cache metadata"

install_calls="$apk_test_dir/install-calls"
install_timeout_calls="$apk_test_dir/install-timeout-calls"
install_app_log="$apk_test_dir/install-app.log"
install_test_log="$apk_test_dir/install-test.log"
reset_install_fake() {
  : >"$install_calls"
  : >"$install_timeout_calls"
  FAKE_INSTALL_RESULT=success
  FAKE_INSTALL_TIMEOUT=0
}
fake_install_timeout() {
  printf '%s\n' "$*" >>"$install_timeout_calls"
  [ "${1:-}" = --foreground ] && [ "${2:-}" = --signal=TERM ] && \
    [ "${3:-}" = --kill-after=10s ] && [ "${4:-}" = 180s ] || \
    fail "APK install does not have an explicit TERM-to-KILL bound: $*"
  shift 4
  [ "$FAKE_INSTALL_TIMEOUT" -eq 0 ] || return 124
  "$@"
}
fake_install_adb() {
  printf '%s\n' "$*" >>"$install_calls"
  case "$FAKE_INSTALL_RESULT:$*" in
    unsupported:*--no-streaming*)
      printf 'adb: unknown option --no-streaming\n' >&2
      return 2
      ;;
    device-failure:*)
      printf 'Failure [INSTALL_FAILED_INVALID_APK]\n' >&2
      return 42
      ;;
    test-failure:*test.apk)
      printf 'Failure [INSTALL_FAILED_TEST_ONLY]\n' >&2
      return 43
      ;;
  esac
  printf 'Success\n'
}

# Reproduce the exact dynamic-scope shape that crashed the live runner, but in
# an externally bounded child with a small stack. The old installer called the
# timeout helper with the function name `timeout`; its local `timeout_bin` then
# shadowed the wrapper's global and recursively selected the function again.
recursion_timeout="$apk_test_dir/timeout-backend"
recursion_adb="$apk_test_dir/adb"
recursion_timeout_calls="$apk_test_dir/timeout-backend-calls"
: >"$recursion_timeout_calls"
# These are literal lines of the isolated fake timeout executable.
# shellcheck disable=SC2016
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "call\\n" >>"$RECURSION_TIMEOUT_CALLS"' \
  'while [ "$#" -gt 0 ]; do' \
  '  case "$1" in' \
  '    --foreground|--signal=*|--kill-after=*) shift ;;' \
  '    *s) shift; break ;;' \
  '    *) exit 90 ;;' \
  '  esac' \
  'done' \
  '[ "$#" -gt 0 ] || exit 91' \
  'exec "$@"' >"$recursion_timeout"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "Success\\n"' >"$recursion_adb"
chmod 0700 "$recursion_timeout" "$recursion_adb"
# This is an isolated child program; its positional parameters and dynamic
# timeout_bin deliberately belong to that child rather than this test shell.
# shellcheck disable=SC2016
if ! RECURSION_TIMEOUT_CALLS="$recursion_timeout_calls" \
    "$system_timeout_executable" --foreground --signal=TERM --kill-after=1s 3s \
    /bin/bash -c '
      set -euo pipefail
      ulimit -S -s 1024
      source "$1"
      timeout_bin="$2"
      timeout() { run_android_acceptance_timeout "$timeout_bin" "$@"; }
      android_acceptance_install_apk timeout "$3" emulator-5558 "$4" "$5"
    ' bash "$here/test-main-lib.sh" "$recursion_timeout" "$recursion_adb" \
    "$apk_test_dir/cache/app.apk" "$apk_test_dir/recursion-install.log"; then
  fail "APK install timeout dispatch recursed or exceeded its outer bound"
fi
[ "$(wc -l <"$recursion_timeout_calls" | tr -d ' ')" = 1 ] || \
  fail "timeout dispatch did not reach its external backend exactly once"
grep -Fxq 'Success' "$apk_test_dir/recursion-install.log" || \
  fail "bounded timeout-dispatch regression did not execute the installer"

if [ "$(android_acceptance_cell_install_mode 0)" != full ] || \
    [ "$(android_acceptance_cell_install_mode 1)" != smoke ]; then
  fail "smoke/full install mode selection is inverted"
fi
if android_acceptance_cell_install_mode 2 >/dev/null 2>&1; then
  fail "invalid APK install mode was accepted"
fi
grep -Fq "install_mode=\"\$(android_acceptance_cell_install_mode \"\$smoke_only\")\"" \
  "$here/test-main.sh" || \
  fail "the live matrix does not derive APK install mode from --smoke"
# These three checks intentionally assert literal runner source expressions.
# shellcheck disable=SC2016
grep -Fq 'android_acceptance_timeout_executable="$(type -P timeout)"' \
  "$here/test-main.sh" || \
  fail "the live runner does not resolve a dedicated external timeout backend"
# shellcheck disable=SC2016
grep -Fq '"$install_mode" "$android_acceptance_timeout_executable"' \
  "$here/test-main.sh" || \
  fail "the live matrix passes a shell timeout wrapper into the APK installer"
# shellcheck disable=SC2016
grep -Fq 'full "$android_acceptance_timeout_executable"' \
  "$here/test-main.sh" || \
  fail "peer-to-peer setup passes a shell timeout wrapper into the APK installer"
if grep -Eq 'timeout[[:space:]]+180[^[:cntrl:]]+[$]adb[^[:cntrl:]]+install' \
    "$here/test-main.sh"; then
  fail "the live matrix bypasses the bounded APK installer"
fi

reset_install_fake
android_acceptance_install_cell_apks \
  smoke fake_install_timeout fake_install_adb emulator-5558 \
  "$apk_test_dir/cache/app.apk" "$apk_test_dir/cache/test.apk" \
  "$install_app_log" "$install_test_log" || \
  fail "shipping-only smoke APK install was rejected"
if [ "$(wc -l <"$install_calls" | tr -d ' ')" != 1 ] || \
    ! grep -Fq 'app.apk' "$install_calls" || \
    grep -Fq 'test.apk' "$install_calls"; then
  fail "launch smoke installed its unused instrumentation APK"
fi
grep -Fq 'instrumentation APK not installed' "$install_test_log" || \
  fail "launch smoke did not retain the instrumentation-install decision"

reset_install_fake
android_acceptance_install_cell_apks \
  full fake_install_timeout fake_install_adb emulator-5558 \
  "$apk_test_dir/cache/app.apk" "$apk_test_dir/cache/test.apk" \
  "$install_app_log" "$install_test_log" || \
  fail "full acceptance APK pair install was rejected"
[ "$(wc -l <"$install_calls" | tr -d ' ')" = 2 ] || \
  fail "full acceptance did not install both APKs"
if grep -Ev -- '--no-streaming' "$install_calls" >/dev/null; then
  fail "ordinary full acceptance used the unreliable streamed transport"
fi

reset_install_fake
FAKE_INSTALL_RESULT=unsupported
android_acceptance_install_apk \
  fake_install_timeout fake_install_adb emulator-5558 \
  "$apk_test_dir/cache/app.apk" "$install_app_log" || \
  fail "an adb without --no-streaming did not use its bounded fallback"
[ "$(wc -l <"$install_calls" | tr -d ' ')" = 2 ] || \
  fail "unsupported --no-streaming did not make exactly one fallback attempt"
sed -n '1p' "$install_calls" | grep -Fq -- '--no-streaming' || \
  fail "push-based APK install was not attempted first"
if sed -n '2p' "$install_calls" | grep -Fq -- '--no-streaming'; then
  fail "streamed fallback retained its unsupported option"
fi
grep -Fq 'bounded streamed fallback' "$install_app_log" || \
  fail "APK transport fallback was not retained in diagnostics"

reset_install_fake
FAKE_INSTALL_TIMEOUT=1
if android_acceptance_install_apk \
    fake_install_timeout fake_install_adb emulator-5558 \
    "$apk_test_dir/cache/app.apk" "$install_app_log"; then
  fail "timed-out APK install was accepted"
fi
[ ! -s "$install_calls" ] && \
  [ "$(wc -l <"$install_timeout_calls" | tr -d ' ')" = 1 ] || \
  fail "timed-out APK install was retried or reached an uncontrolled child"

reset_install_fake
FAKE_INSTALL_RESULT=device-failure
if android_acceptance_install_cell_apks \
    full fake_install_timeout fake_install_adb emulator-5558 \
    "$apk_test_dir/cache/app.apk" "$apk_test_dir/cache/test.apk" \
    "$install_app_log" "$install_test_log"; then
  fail "full acceptance accepted a failed app install"
fi
[ "$(wc -l <"$install_calls" | tr -d ' ')" = 1 ] || \
  fail "APK installer retried a device rejection or continued to the test APK"

reset_install_fake
FAKE_INSTALL_RESULT=test-failure
if android_acceptance_install_cell_apks \
    full fake_install_timeout fake_install_adb emulator-5558 \
    "$apk_test_dir/cache/app.apk" "$apk_test_dir/cache/test.apk" \
    "$install_app_log" "$install_test_log"; then
  fail "full acceptance ignored its required instrumentation APK failure"
fi
[ "$(wc -l <"$install_calls" | tr -d ' ')" = 2 ] || \
  fail "full acceptance did not stop at the instrumentation APK failure"
rm -rf "$apk_test_dir"

fingerprint_tree="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-fingerprint.test.XXXXXX")"
mkdir -p "$fingerprint_tree/app" "$fingerprint_tree/tests/__acceptance__/old-run"
printf 'source one\n' >"$fingerprint_tree/app/Main.kt"
printf 'gradle input\n' >"$fingerprint_tree/app/build.gradle"
printf 'generated output\n' >"$fingerprint_tree/tests/__acceptance__/old-run/noise"
printf 'sdk aar\n' >"$fingerprint_tree/sdk.aar"
printf 'sdk sources\n' >"$fingerprint_tree/sdk-sources.jar"
first_fingerprint="$(android_acceptance_input_fingerprint \
  "$fingerprint_tree" "$fingerprint_tree/sdk.aar" "$fingerprint_tree/sdk-sources.jar" github Github)" || \
  fail "could not fingerprint fallback source tree"
second_fingerprint="$(android_acceptance_input_fingerprint \
  "$fingerprint_tree" "$fingerprint_tree/sdk.aar" "$fingerprint_tree/sdk-sources.jar" github Github)" || \
  fail "could not repeat fingerprint fallback source tree"
[ "$first_fingerprint" = "$second_fingerprint" ] || \
  fail "unchanged source tree received a different fingerprint"
printf 'new generated output\n' >"$fingerprint_tree/tests/__acceptance__/old-run/noise"
[ "$(android_acceptance_input_fingerprint \
  "$fingerprint_tree" "$fingerprint_tree/sdk.aar" "$fingerprint_tree/sdk-sources.jar" github Github)" = "$first_fingerprint" ] || \
  fail "generated acceptance artifacts changed the input fingerprint"
printf 'source two\n' >"$fingerprint_tree/app/Main.kt"
changed_fingerprint="$(android_acceptance_input_fingerprint \
  "$fingerprint_tree" "$fingerprint_tree/sdk.aar" "$fingerprint_tree/sdk-sources.jar" github Github)" || \
  fail "could not fingerprint changed source tree"
[ "$changed_fingerprint" != "$first_fingerprint" ] || \
  fail "source change did not invalidate the input fingerprint"
printf 'warp.version=1\n' >"$fingerprint_tree/app/local.properties"
local_properties_fingerprint="$(android_acceptance_input_fingerprint \
  "$fingerprint_tree" "$fingerprint_tree/sdk.aar" "$fingerprint_tree/sdk-sources.jar" github Github)" || \
  fail "could not fingerprint local.properties"
[ "$local_properties_fingerprint" != "$changed_fingerprint" ] || \
  fail "local.properties change did not invalidate the input fingerprint"
rm -rf "$fingerprint_tree"

timeout() {
  shift
  "$@"
}

# Larger than Darwin's pipe buffer, with the authoritative signal at the
# beginning. The old producer-to-quiet-grep checks deterministically returned
# SIGPIPE instead of the match under `set -o pipefail`.
large_android_output="$(awk 'BEGIN {
  for (i = 0; i < 8192; i++) printf "0123456789abcdef0123456789abcdef"
}')"

readiness_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-readiness.test.XXXXXX")"
readiness_calls="$readiness_dir/calls"
readiness_probe_count="$readiness_dir/probe-count"
readiness_trust_count="$readiness_dir/trust-count"
readiness_status="$readiness_dir/status"
readiness_state="$readiness_dir/state/network-state"
readiness_interactive="$readiness_dir/interactive.txt"

reset_readiness_fake() {
  : >"$readiness_calls"
  printf '0\n' >"$readiness_probe_count"
  printf '0\n' >"$readiness_trust_count"
  rm -rf "$readiness_dir/state"
  rm -f "$readiness_status" "$readiness_interactive"
  FAKE_READY_ADB_AVAILABLE=1
  FAKE_READY_BOOT=1
  FAKE_READY_API=34
  FAKE_READY_API_AVAILABLE=1
  FAKE_READY_ABI=arm64-v8a,armeabi-v7a
  FAKE_READY_ABI_AVAILABLE=1
  FAKE_READY_AVD=urnetwork-acceptance
  FAKE_READY_LOCK_DISABLED=true
  FAKE_READY_DREAMING_LOCKSCREEN=false
  FAKE_READY_WINDOW_AVAILABLE=1
  FAKE_READY_UNLOCK_STATE=unlocked
  FAKE_READY_TRUST_SUCCEEDS_AFTER=0
  FAKE_READY_POWER_STATE=awake
  FAKE_READY_LARGE_POWER=0
  FAKE_READY_LARGE_CONNECTIVITY=0
  FAKE_READY_LARGE_PROBE=0
  FAKE_READY_TCP_SUCCEEDS_AFTER=0
  FAKE_READY_TCP_FAILURE=network
  FAKE_READY_WIFI=enabled
  FAKE_READY_WIFI_ENABLE=1
  FAKE_READY_SCAN=1
}

fake_readiness_adb() {
  local count
  [ "$1" = -s ] || return 90
  case "$2" in readiness-device|emulator-5558) ;; *) return 90 ;; esac
  shift 2
  printf '%s\n' "$*" >>"$readiness_calls"
  case "$*" in
    wait-for-device)
      [ "$FAKE_READY_ADB_AVAILABLE" -eq 1 ]
      ;;
    get-state)
      printf 'device\n'
      ;;
    'emu avd name')
      printf '%s\nOK\n' "$FAKE_READY_AVD"
      ;;
    'shell getprop sys.boot_completed')
      printf '%s\n' "$FAKE_READY_BOOT"
      ;;
    'shell getprop ro.build.version.sdk')
      [ "$FAKE_READY_API_AVAILABLE" -eq 1 ] || return 1
      printf '%s\n' "$FAKE_READY_API"
      ;;
    'shell getprop ro.product.cpu.abilist')
      [ "$FAKE_READY_ABI_AVAILABLE" -eq 1 ] || return 1
      printf '%s\n' "$FAKE_READY_ABI"
      ;;
    'shell input keyevent KEYCODE_WAKEUP') ;;
    'shell wm dismiss-keyguard') ;;
    'shell cmd lock_settings get-disabled')
      printf '%s\n' "$FAKE_READY_LOCK_DISABLED"
      ;;
    'shell dumpsys window')
      [ "$FAKE_READY_WINDOW_AVAILABLE" -eq 1 ] || return 1
      [ "$FAKE_READY_DREAMING_LOCKSCREEN" = absent ] || \
        printf '  mDreamingLockscreen=%s\n' "$FAKE_READY_DREAMING_LOCKSCREEN"
      ;;
    'shell dumpsys power')
      case "$FAKE_READY_POWER_STATE" in
        awake) printf '  mWakefulness=Awake\n' ;;
        asleep) printf '  mWakefulness=Asleep\n' ;;
        unknown) printf 'wake state unavailable\n' ;;
      esac
      [ "$FAKE_READY_LARGE_POWER" -eq 0 ] || printf '%s\n' "$large_android_output"
      ;;
    'shell dumpsys trust')
      count="$(tr -d '\r\n' <"$readiness_trust_count")"
      count=$((count + 1))
      printf '%s\n' "$count" >"$readiness_trust_count"
      if [ "$count" -le "$FAKE_READY_TRUST_SUCCEEDS_AFTER" ]; then
        printf 'lock state unavailable\n'
      else
        case "$FAKE_READY_UNLOCK_STATE" in
          unlocked) printf 'User (id=0) (current): deviceLocked=0\n' ;;
          locked) printf 'User (id=0) (current): deviceLocked=1\n' ;;
          *) printf 'lock state unavailable\n' ;;
        esac
      fi
      ;;
    'shell toybox nc -w 5 -q 1 api.bringyour.com 443')
      count="$(tr -d '\r\n' <"$readiness_probe_count")"
      count=$((count + 1))
      printf '%s\n' "$count" >"$readiness_probe_count"
      if [ "$count" -gt "$FAKE_READY_TCP_SUCCEEDS_AFTER" ]; then
        return 0
      fi
      case "$FAKE_READY_TCP_FAILURE" in
        dns) printf 'nc: api.bringyour.com:443: No address associated with hostname\n' >&2 ;;
        tcp) printf 'nc: connect failed: Connection refused\n' >&2 ;;
        network) printf 'nc: connect failed: Network is unreachable\n' >&2 ;;
      esac
      [ "$FAKE_READY_LARGE_PROBE" -eq 0 ] || printf '%s\n' "$large_android_output" >&2
      return 1
      ;;
    'shell dumpsys connectivity')
      if [ "$FAKE_READY_TCP_FAILURE" = network ]; then
        printf 'Active default network: none\n'
      else
        printf 'Active default network: 101\n'
      fi
      [ "$FAKE_READY_LARGE_CONNECTIVITY" -eq 0 ] || printf '%s\n' "$large_android_output"
      ;;
    'shell settings get global wifi_on')
      case "$FAKE_READY_WIFI" in
        enabled) printf '1\n' ;;
        disabled) printf '0\n' ;;
        *) printf 'null\n' ;;
      esac
      ;;
    'shell svc wifi enable')
      [ "$FAKE_READY_WIFI_ENABLE" -eq 1 ]
      ;;
    'shell svc wifi disable') ;;
    'shell cmd wifi start-scan') [ "$FAKE_READY_SCAN" -eq 1 ] ;;
    *) return 91 ;;
  esac
}

readiness_status_is() {
  [ "$(cat "$readiness_status" 2>/dev/null || true)" = "status=$1" ] || \
    fail "readiness status was not $1"
}

owned_diagnostic_field_is() {
  local diagnostic_file="$1" field="$2" expected="$3"

  [ "$(grep -Fxc "$field=$expected" "$diagnostic_file" 2>/dev/null || true)" -eq 1 ] || \
    fail "owned AVD diagnostic $field was not $expected"
}

prepare_physical_readiness() {
  [ "$#" -eq 7 ] || return 2
  android_acceptance_prepare_device \
    "$1" "$2" "$3" "$4" "$5" \
    "$readiness_interactive" device-001 readiness "$6" "$7" 3
}

reset_readiness_fake
FAKE_READY_LARGE_POWER=1
prepare_physical_readiness \
  fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1 || \
  fail "a ready supported-ARM device with working API TCP was rejected"
readiness_status_is ready
if grep -Fq 'shell settings get global wifi_on' "$readiness_calls"; then
  fail "a working control-plane connection unnecessarily changed or inspected Wi-Fi"
fi
grep -Fq 'shell toybox nc -w 5 -q 1 api.bringyour.com 443' "$readiness_calls" || \
  fail "device readiness did not probe the API TCP port"
non_comment_readiness_source="$(sed '/^[[:space:]]*#/d' "$here/test-main-lib.sh")"
if grep -Eq '(^|[;&|[:space:]])(toybox[[:space:]]+)?ping([[:space:]]|$)' \
    <<<"$non_comment_readiness_source"; then
  fail "device readiness still treats ICMP as control-plane availability"
fi

reset_readiness_fake
FAKE_READY_ADB_AVAILABLE=0
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "an unavailable adb device passed readiness"
fi
readiness_status_is adb-unavailable

reset_readiness_fake
FAKE_READY_BOOT=0
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "an unbooted device passed readiness"
fi
readiness_status_is boot-incomplete

reset_readiness_fake
FAKE_READY_API_AVAILABLE=0
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "a device with unreadable Android API level passed readiness"
fi
readiness_status_is api-unavailable

reset_readiness_fake
FAKE_READY_API=25
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "a device older than the base shipping SDK passed readiness"
fi
readiness_status_is shipping-api-unsupported

reset_readiness_fake
FAKE_READY_ABI_AVAILABLE=0
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "a device with unreadable ABI state passed readiness"
fi
readiness_status_is abi-unavailable

reset_readiness_fake
FAKE_READY_ABI=armeabi-v7a,armeabi
prepare_physical_readiness \
  fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1 || \
  fail "a shipping 32-bit ARM device was rejected"
readiness_status_is ready

reset_readiness_fake
FAKE_READY_ABI=x86_64,x86
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "a device without a general shipping ARM ABI passed readiness"
fi
readiness_status_is shipping-abi-unsupported

reset_readiness_fake
FAKE_READY_UNLOCK_STATE=unknown
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "a device with unknown unlock state passed readiness"
fi
readiness_status_is unlock-failed

# A fresh, read-only AVD can publish sys.boot_completed before its current-user
# trust state settles. The runner owns this exact child and knows the setup AVD
# has no credential, so wait for every non-secret platform signal without
# ever sending the physical-device PIN. The state transition is counter-driven,
# not timing-dependent: the first two trust reads are deliberately unknown.
reset_readiness_fake
FAKE_READY_TRUST_SUCCEEDS_AFTER=2
android_acceptance_prepare_owned_emulator \
  fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
  "$readiness_dir/state" "$readiness_status" "$readiness_interactive" 1 1 3 || \
  fail "a runner-owned credential-free AVD was rejected while trust state settled"
readiness_status_is ready
owned_diagnostic_field_is "$readiness_interactive" attempt 3
owned_diagnostic_field_is "$readiness_interactive" serial valid
owned_diagnostic_field_is "$readiness_interactive" owner live
owned_diagnostic_field_is "$readiness_interactive" adb ready
owned_diagnostic_field_is "$readiness_interactive" avd match
owned_diagnostic_field_is "$readiness_interactive" wake complete
owned_diagnostic_field_is "$readiness_interactive" dismiss complete
owned_diagnostic_field_is "$readiness_interactive" credential disabled
owned_diagnostic_field_is "$readiness_interactive" window not-dreaming
owned_diagnostic_field_is "$readiness_interactive" power awake
owned_diagnostic_field_is "$readiness_interactive" trust unlocked
owned_diagnostic_field_is "$readiness_interactive" result ready
if grep -Fq 'emulator-5558' "$readiness_interactive" || \
   grep -Fq 'urnetwork-acceptance' "$readiness_interactive" || \
   grep -Fq "$$" "$readiness_interactive"; then
  fail "owned AVD diagnostics retained a serial, AVD name, or owner PID"
fi
[ "$(cat "$readiness_trust_count")" -eq 3 ] || \
  fail "owned AVD readiness did not poll the transient trust state"
grep -Fq 'shell wm dismiss-keyguard' "$readiness_calls" || \
  fail "owned AVD readiness did not dismiss its credential-free keyguard"
if grep -Eq 'shell wm size|shell input swipe|KEYCODE_[0-9]' "$readiness_calls"; then
  fail "owned AVD readiness entered the physical-device PIN path"
fi

reset_readiness_fake
FAKE_READY_DREAMING_LOCKSCREEN=absent
android_acceptance_prepare_owned_emulator \
  fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
  "$readiness_dir/state" "$readiness_status" "$readiness_interactive" 1 1 1 || \
  fail "an API-36 owned AVD without the legacy window diagnostic was rejected"
readiness_status_is ready
owned_diagnostic_field_is "$readiness_interactive" window absent
owned_diagnostic_field_is "$readiness_interactive" result ready

# Ownership is an explicit gate, not an inference from an emulator-* serial.
# A dead child, a different AVD name, or permanently unknown trust state must
# fail without attempting a credential or reaching the network probe.
reset_readiness_fake
FAKE_READY_AVD=foreign-avd
if android_acceptance_prepare_owned_emulator \
    fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
    "$readiness_dir/state" "$readiness_status" "$readiness_interactive" 1 1 1; then
  fail "an arbitrary emulator was accepted as the runner-owned AVD"
fi
readiness_status_is owned-emulator-interactive-failed
owned_diagnostic_field_is "$readiness_interactive" avd mismatch
owned_diagnostic_field_is "$readiness_interactive" wake unchecked
owned_diagnostic_field_is "$readiness_interactive" result failed
if grep -Eq 'shell wm dismiss-keyguard|shell toybox nc' "$readiness_calls"; then
  fail "an unowned emulator was mutated or network-probed"
fi

reset_readiness_fake
if android_acceptance_prepare_owned_emulator \
    fake_readiness_adb emulator-5558 urnetwork-acceptance 99999999 \
    "$readiness_dir/state" "$readiness_status" "$readiness_interactive" 1 1 1; then
  fail "an emulator without the runner's live child was accepted as owned"
fi
readiness_status_is owned-emulator-interactive-failed
owned_diagnostic_field_is "$readiness_interactive" owner not-live
owned_diagnostic_field_is "$readiness_interactive" adb unchecked
owned_diagnostic_field_is "$readiness_interactive" result failed
if grep -Eq 'shell wm dismiss-keyguard|shell toybox nc' "$readiness_calls"; then
  fail "an emulator without a live owner was mutated or network-probed"
fi

reset_readiness_fake
FAKE_READY_UNLOCK_STATE=unknown
if android_acceptance_prepare_owned_emulator \
    fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
    "$readiness_dir/state" "$readiness_status" "$readiness_interactive" 1 1 1; then
  fail "an owned AVD with unverifiable trust state passed readiness"
fi
readiness_status_is owned-emulator-interactive-failed
owned_diagnostic_field_is "$readiness_interactive" trust unknown
owned_diagnostic_field_is "$readiness_interactive" result failed
if grep -Eq 'shell wm size|shell input swipe|KEYCODE_[0-9]' "$readiness_calls"; then
  fail "an unverifiable owned AVD received the physical-device PIN"
fi

for owned_avd_incomplete_state in credential lockscreen window-command power; do
  reset_readiness_fake
  case "$owned_avd_incomplete_state" in
    credential) FAKE_READY_LOCK_DISABLED=false ;;
    lockscreen) FAKE_READY_DREAMING_LOCKSCREEN=true ;;
    window-command) FAKE_READY_WINDOW_AVAILABLE=0 ;;
    power) FAKE_READY_POWER_STATE=asleep ;;
  esac
  if android_acceptance_prepare_owned_emulator \
      fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
      "$readiness_dir/state" "$readiness_status" "$readiness_interactive" 1 1 1; then
    fail "an owned AVD passed with incomplete $owned_avd_incomplete_state state"
  fi
  readiness_status_is owned-emulator-interactive-failed
  case "$owned_avd_incomplete_state" in
    credential) owned_diagnostic_field_is "$readiness_interactive" credential enabled ;;
    lockscreen) owned_diagnostic_field_is "$readiness_interactive" window dreaming ;;
    window-command) owned_diagnostic_field_is "$readiness_interactive" window unavailable ;;
    power) owned_diagnostic_field_is "$readiness_interactive" power not-awake ;;
  esac
  owned_diagnostic_field_is "$readiness_interactive" result failed
done

owned_execution_marker="$readiness_dir/owned-execution"
mark_owned_execution() {
  printf '%s\n' "$1" >"$owned_execution_marker"
}
reset_readiness_fake
android_acceptance_run_after_owned_emulator_interactive \
  fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
  "$readiness_interactive" mark_owned_execution launched || \
  fail "owned AVD execution did not use its credential-free interactive gate"
[ "$(cat "$owned_execution_marker")" = launched ] || \
  fail "owned AVD execution did not reach the product command"
owned_diagnostic_field_is "$readiness_interactive" result ready
if grep -Eq 'shell wm size|shell input swipe|KEYCODE_[0-9]' "$readiness_calls"; then
  fail "owned AVD execution entered the physical-device PIN path"
fi

owned_execution_ready_diagnostic="$readiness_dir/execution-ready-interactive.txt"
owned_execution_failed_diagnostic="$readiness_dir/execution-failed-interactive.txt"
reset_readiness_fake
android_acceptance_run_after_owned_emulator_interactive \
  fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
  "$owned_execution_ready_diagnostic" mark_owned_execution first-boundary || \
  fail "the first owned AVD execution boundary was rejected"
owned_execution_ready_snapshot="$(cat "$owned_execution_ready_diagnostic")"
rm -f "$owned_execution_marker"
reset_readiness_fake
FAKE_READY_AVD=foreign-avd
if android_acceptance_run_after_owned_emulator_interactive \
    fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
    "$owned_execution_failed_diagnostic" mark_owned_execution should-not-run; then
  fail "an unowned AVD reached the post-install product command"
fi
[ ! -e "$owned_execution_marker" ] || \
  fail "the product command ran after the owned AVD gate failed"
[ "$(cat "$owned_execution_ready_diagnostic")" = "$owned_execution_ready_snapshot" ] || \
  fail "a later owned AVD boundary overwrote earlier diagnostic evidence"
owned_diagnostic_field_is "$owned_execution_ready_diagnostic" result ready
owned_diagnostic_field_is "$owned_execution_failed_diagnostic" avd mismatch
owned_diagnostic_field_is "$owned_execution_failed_diagnostic" result failed
if android_acceptance_run_after_owned_emulator_interactive \
    fake_readiness_adb emulator-5558 urnetwork-acceptance "$$" \
    "$readiness_interactive"; then
  fail "an empty owned AVD execution command was accepted"
else
  [ "$?" -eq 2 ] || fail "an empty owned AVD execution command returned the wrong status"
fi

for failure_case in network dns tcp; do
  reset_readiness_fake
  FAKE_READY_TCP_SUCCEEDS_AFTER=99
  FAKE_READY_TCP_FAILURE="$failure_case"
  case "$failure_case" in
    network) FAKE_READY_LARGE_CONNECTIVITY=1 ;;
    dns) FAKE_READY_LARGE_PROBE=1 ;;
  esac
  if prepare_physical_readiness \
      fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
    fail "$failure_case failure passed device readiness"
  fi
  case "$failure_case" in
    network) readiness_status_is network-unavailable ;;
    dns) readiness_status_is dns-unavailable ;;
    tcp) readiness_status_is api-tcp-unreachable ;;
  esac
done

reset_readiness_fake
FAKE_READY_TCP_SUCCEEDS_AFTER=1
FAKE_READY_TCP_FAILURE=tcp
prepare_physical_readiness \
  fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1 || \
  fail "an already-enabled network did not recover on a later TCP probe"
readiness_status_is ready
if grep -Eq 'shell svc wifi (enable|disable)' "$readiness_calls"; then
  fail "already-enabled Wi-Fi was toggled during transient TCP recovery"
fi

reset_readiness_fake
FAKE_READY_TCP_SUCCEEDS_AFTER=1
FAKE_READY_TCP_FAILURE=network
FAKE_READY_WIFI=disabled
FAKE_READY_SCAN=0
prepare_physical_readiness \
  fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1 || \
  fail "an initially offline device did not recover through its saved Wi-Fi configuration"
readiness_status_is ready
[ "$(cat "$readiness_state")" = 'restore=wifi-disabled' ] || \
  fail "Wi-Fi recovery did not record its cleanup ownership before mutation"
wake_line="$(grep -n -m1 'shell input keyevent KEYCODE_WAKEUP' "$readiness_calls" | cut -d: -f1)"
probe_line="$(grep -n -m1 'shell toybox nc' "$readiness_calls" | cut -d: -f1)"
[ -n "$wake_line" ] && [ -n "$probe_line" ] && [ "$wake_line" -lt "$probe_line" ] || \
  fail "device network probing ran before wake/unlock preparation"
grep -Fq 'shell svc wifi enable' "$readiness_calls" || \
  fail "disabled Wi-Fi was not enabled for a saved-network recovery"
grep -Fq 'shell cmd wifi start-scan' "$readiness_calls" || \
  fail "saved Wi-Fi networks were not scanned after recovery"
android_acceptance_restore_network \
  fake_readiness_adb readiness-device "$readiness_state" || \
  fail "runner-owned Wi-Fi state was not restored"
grep -Fq 'shell svc wifi disable' "$readiness_calls" || \
  fail "initially disabled Wi-Fi was not restored"

reset_readiness_fake
FAKE_READY_TCP_SUCCEEDS_AFTER=99
FAKE_READY_WIFI=disabled
FAKE_READY_WIFI_ENABLE=0
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "a failed Wi-Fi enable passed readiness"
fi
readiness_status_is wifi-enable-failed
[ "$(cat "$readiness_state")" = 'restore=wifi-disabled' ] || \
  fail "failed Wi-Fi enable did not retain its pre-mutation ownership record"

reset_readiness_fake
FAKE_READY_TCP_SUCCEEDS_AFTER=99
FAKE_READY_WIFI=unknown
if prepare_physical_readiness \
    fake_readiness_adb readiness-device 010181 "$readiness_dir/state" "$readiness_status" 1 1; then
  fail "an unknown Wi-Fi state passed readiness"
fi
readiness_status_is wifi-state-unavailable

printf 'restore=wifi-disabled\nforeign-edit=true\n' >"$readiness_state"
: >"$readiness_calls"
if android_acceptance_restore_network \
    fake_readiness_adb readiness-device "$readiness_state"; then
  fail "malformed Wi-Fi ownership state was accepted"
fi
if grep -Eq 'shell svc wifi (enable|disable)' "$readiness_calls"; then
  fail "malformed Wi-Fi ownership state mutated the device"
fi
rm -rf "$readiness_dir"

device_helper_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-device-helper.test.XXXXXX")"
unlock_state="$device_helper_dir/unlock-state"
unlock_calls="$device_helper_dir/unlock-calls"
unlock_keys="$device_helper_dir/unlock-keys"
unlock_submissions="$device_helper_dir/unlock-submissions"
unlock_wake_polls="$device_helper_dir/unlock-wake-polls"
unlock_trust_polls="$device_helper_dir/unlock-trust-polls"
unlock_pending_polls="$device_helper_dir/unlock-pending-polls"
unlock_pending="$device_helper_dir/unlock-pending"
unlock_surface="$device_helper_dir/unlock-surface"
unlock_diagnostic="$device_helper_dir/unlock-diagnostic.txt"

reset_unlock_fake() {
  printf '1\n' >"$unlock_state"
  printf '0\n' >"$unlock_submissions"
  printf '0\n' >"$unlock_wake_polls"
  printf '0\n' >"$unlock_trust_polls"
  printf '0\n' >"$unlock_pending_polls"
  : >"$unlock_calls"
  : >"$unlock_keys"
  rm -f "$unlock_pending" "$unlock_surface" "$unlock_diagnostic"
  FAKE_UNLOCK_AWAKE_AFTER=1
  FAKE_UNLOCK_POWER_UNKNOWN_BEFORE=0
  FAKE_UNLOCK_PRE_SUBMIT_UNKNOWN_BEFORE=0
  FAKE_UNLOCK_UNLOCK_AFTER=1
  FAKE_UNLOCK_POST_SUBMIT_UNKNOWN_BEFORE=0
  FAKE_UNLOCK_SIZE='Physical size: 1080x2400'
}

fake_unlock_adb() {
  local current_state eval_status known_count pending_count remote_script submission_count
  local trust_count wake_count
  [ "$1" = -s ] && [ -n "$2" ] || return 90
  shift 2
  printf '%s\n' "$*" >>"$unlock_calls"
  case "$1" in
    get-state) printf 'device\n' ;;
    shell)
      shift
      case "${1:-} ${2:-}" in
        'dumpsys power')
          wake_count="$(tr -d '\r\n' <"$unlock_wake_polls")"
          wake_count=$((wake_count + 1))
          printf '%s\n' "$wake_count" >"$unlock_wake_polls"
          if [ "$wake_count" -le "$FAKE_UNLOCK_POWER_UNKNOWN_BEFORE" ]; then
            printf 'wake state unavailable\n'
          elif [ "$wake_count" -ge "$FAKE_UNLOCK_AWAKE_AFTER" ]; then
            printf '  mWakefulness=Awake\n'
          else
            printf '  mWakefulness=Asleep\n'
          fi
          ;;
        'dumpsys trust')
          if [ -e "$unlock_pending" ]; then
            pending_count="$(tr -d '\r\n' <"$unlock_pending_polls")"
            pending_count=$((pending_count + 1))
            printf '%s\n' "$pending_count" >"$unlock_pending_polls"
            if [ "$pending_count" -le "$FAKE_UNLOCK_POST_SUBMIT_UNKNOWN_BEFORE" ]; then
              printf 'lock state unavailable\n'
              return 0
            fi
            known_count=$((pending_count - FAKE_UNLOCK_POST_SUBMIT_UNKNOWN_BEFORE))
            if [ "$known_count" -ge "$FAKE_UNLOCK_UNLOCK_AFTER" ]; then
              printf '0\n' >"$unlock_state"
            fi
          else
            trust_count="$(tr -d '\r\n' <"$unlock_trust_polls")"
            trust_count=$((trust_count + 1))
            printf '%s\n' "$trust_count" >"$unlock_trust_polls"
            if [ "$trust_count" -le "$FAKE_UNLOCK_PRE_SUBMIT_UNKNOWN_BEFORE" ]; then
              printf 'lock state unavailable\n'
              return 0
            fi
          fi
          current_state="$(tr -d '\r\n' <"$unlock_state")"
          printf 'User "Owner" (id=0) (current): deviceLocked=%s, strongAuthRequired=0x0\n' \
            "$current_state"
          ;;
        'wm size') printf '%s\n' "$FAKE_UNLOCK_SIZE" ;;
        'wm dismiss-keyguard') ;;
        'input keyevent')
          [ "${3:-}" = KEYCODE_WAKEUP ] || return 91
          ;;
        'input swipe')
          [ "${3:-} ${4:-} ${5:-} ${6:-} ${7:-}" = '540 1920 540 480 300' ] || return 91
          : >"$unlock_surface"
          ;;
        *)
          case "${1:-}" in
            'IFS= read -r ur_accept_unlock_code || exit 2;'*)
              remote_script="$1"
              # The fake already owns the right side of the helper's stdin
              # pipeline. Evaluate in that command context so the simulated
              # remote read has the same stdin ownership as adb shell.
              # The fixed script invokes this function indirectly via eval.
              # shellcheck disable=SC2329
              input() {
                [ "${1:-}" = keyevent ] && [ -n "${2:-}" ] || return 1
                printf '%s\n' "$2" >>"$unlock_keys"
                if [ "$2" = KEYCODE_ENTER ]; then
                  submission_count="$(tr -d '\r\n' <"$unlock_submissions")"
                  printf '%s\n' "$((submission_count + 1))" >"$unlock_submissions"
                  [ -e "$unlock_surface" ] || return 1
                  [ "$(paste -sd, "$unlock_keys")" = \
                    'KEYCODE_0,KEYCODE_1,KEYCODE_0,KEYCODE_1,KEYCODE_8,KEYCODE_1,KEYCODE_ENTER' ] || \
                    return 1
                  : >"$unlock_pending"
                fi
              }
              # The evaluated value is the fixed remote script supplied by
              # the helper under test, never external or fixture input.
              # shellcheck disable=SC2294
              if eval "$remote_script"; then
                eval_status=0
              else
                eval_status=$?
              fi
              unset -f input
              return "$eval_status"
              ;;
            *) return 91 ;;
          esac
          ;;
      esac
      ;;
    *) return 92 ;;
  esac
  if [ "${FAKE_UNLOCK_DRAIN_STDIN:-0}" -eq 1 ]; then
    while IFS= read -r _; do :; done
  fi
}

# Lock-state parsing must cover the formats observed across Pixel, Samsung,
# OnePlus, Saga, and Sony fleet devices while selecting only the current user.
fake_trust_adb() {
  [ "$1" = -s ] && [ "$2" = trust-device ] && \
    [ "$3 $4 $5" = 'shell dumpsys trust' ] || return 90
  printf '%s\n' "$FAKE_TRUST_OUTPUT"
}
FAKE_TRUST_OUTPUT='User "Owner" (id=0, flags=0x4c13) (current): trusted=0, deviceLocked=1, strongAuthRequired=0x40'
if android_acceptance_device_unlocked fake_trust_adb trust-device; then
  fail "the OnePlus numeric locked state was accepted as unlocked"
else
  [ "$?" -eq 1 ] || fail "the OnePlus numeric lock state was not recognized"
fi
FAKE_TRUST_OUTPUT='User "Owner" (id=0) (current): deviceLocked=false, strongAuthRequired=0x0'
android_acceptance_device_unlocked fake_trust_adb trust-device || \
  fail "the Pixel boolean unlocked state was rejected"
FAKE_TRUST_OUTPUT="User \"Owner\" (id=0) (current): deviceLocked=false, padding=$large_android_output"
android_acceptance_device_unlocked fake_trust_adb trust-device || \
  fail "a large current-user trust dump lost its early unlocked signal"
FAKE_TRUST_OUTPUT='Device locked: false'
android_acceptance_device_unlocked fake_trust_adb trust-device || \
  fail "the legacy global unlocked state was rejected"
FAKE_TRUST_OUTPUT=$'User (id=10): deviceLocked=false\nUser (id=0) (current): deviceLocked=true'
if android_acceptance_device_unlocked fake_trust_adb trust-device; then
  fail "an unlocked background user hid the locked current user"
else
  [ "$?" -eq 1 ] || fail "the current-user lock state was not recognized"
fi
FAKE_TRUST_OUTPUT='lock state unavailable'
if android_acceptance_device_unlocked fake_trust_adb trust-device; then
  fail "an unknown OEM lock state was accepted"
else
  [ "$?" -eq 2 ] || fail "an unknown OEM lock state was not fail-closed"
fi

# Poll-count tests are state driven; wall-clock sleeping would add no coverage.
android_acceptance_interactive_poll_sleep() { :; }

reset_unlock_fake
FAKE_UNLOCK_AWAKE_AFTER=3
FAKE_UNLOCK_POWER_UNKNOWN_BEFORE=2
FAKE_UNLOCK_PRE_SUBMIT_UNKNOWN_BEFORE=2
FAKE_UNLOCK_POST_SUBMIT_UNKNOWN_BEFORE=1
FAKE_UNLOCK_UNLOCK_AFTER=2
unlock_log="$device_helper_dir/unlock-log"
android_acceptance_unlock_device \
  fake_unlock_adb test-device 010181 "$unlock_diagnostic" device-011 readiness 4 \
  >"$unlock_log" 2>&1 || \
  fail "a locked authorized device was not unlocked"
[ "$(cat "$unlock_state")" = 0 ] || fail "unlock completion was not verified"
[ "$(cat "$unlock_wake_polls")" -eq 3 ] || \
  fail "the transient unknown OEM wake transition was not polled"
[ "$(grep -Fxc 'shell input keyevent KEYCODE_WAKEUP' "$unlock_calls")" -eq 1 ] || \
  fail "the wake command was submitted more than once"
[ "$(cat "$unlock_trust_polls")" -eq 3 ] || \
  fail "the transient unknown pre-credential trust state was not polled"
[ "$(cat "$unlock_pending_polls")" -eq 3 ] || \
  fail "the mixed unknown/locked OEM unlock transition was not polled"
[ "$(cat "$unlock_submissions")" -eq 1 ] || \
  fail "the private credential was not submitted exactly once"
grep -Fxq 'stage=complete' "$unlock_diagnostic" || \
  fail "successful physical unlock did not retain its completed stage"
grep -Fxq 'credential=submitted' "$unlock_diagnostic" || \
  fail "successful physical unlock did not retain its credential state"
grep -Fxq 'result=ready' "$unlock_diagnostic" || \
  fail "successful physical unlock did not retain its terminal result"
[ "$(sed -n '1p' "$unlock_keys")" = KEYCODE_0 ] || \
  fail "the leading-zero unlock code was not preserved as a digit key"
if grep -Fq 010181 "$unlock_log" "$unlock_calls" "$unlock_keys"; then
  fail "the private Android unlock code was printed"
fi
if grep -Fq 'input text' "$unlock_calls"; then
  fail "the OEM-incompatible text injection path was used"
fi
submission_count="$(cat "$unlock_submissions")"
swipe_count="$(grep -Fc 'shell input swipe 540 1920 540 480 300' "$unlock_calls")"
android_acceptance_unlock_device \
  fake_unlock_adb test-device 010181 "$unlock_diagnostic" device-011 readiness 4 \
  >/dev/null 2>&1 || \
  fail "an already-unlocked device was rejected"
[ "$(cat "$unlock_submissions")" = "$submission_count" ] || \
  fail "an already-unlocked device received the unlock code"
[ "$(grep -Fc 'shell input swipe 540 1920 540 480 300' "$unlock_calls")" = "$swipe_count" ] || \
  fail "an already-unlocked device reopened its credential surface"
if android_acceptance_unlock_device \
    fake_unlock_adb test-device '01 0181' \
    "$unlock_diagnostic" device-011 readiness 4 >/dev/null 2>&1; then
  fail "a malformed Android unlock code was accepted"
fi
[ "$(cat "$unlock_submissions")" = "$submission_count" ] || \
  fail "a malformed Android unlock code reached SystemUI"

# A trust signal can settle directly from unknown to unlocked. That path must
# return ready without ever opening or submitting to the credential surface.
reset_unlock_fake
printf '0\n' >"$unlock_state"
FAKE_UNLOCK_PRE_SUBMIT_UNKNOWN_BEFORE=2
android_acceptance_unlock_device \
  fake_unlock_adb test-device 010181 \
  "$unlock_diagnostic" device-011 readiness 3 >/dev/null 2>&1 || \
  fail "an unknown-to-unlocked pre-credential transition was rejected"
[ "$(cat "$unlock_trust_polls")" -eq 3 ] || \
  fail "unknown-to-unlocked trust observations did not reach the known state"
[ "$(cat "$unlock_submissions")" -eq 0 ] || \
  fail "an unknown-to-unlocked device received the credential"
grep -Fxq 'credential=not-required' "$unlock_diagnostic" || \
  fail "unknown-to-unlocked diagnostics did not record the credential-free result"

reset_unlock_fake
FAKE_UNLOCK_SIZE='size unavailable'
if android_acceptance_unlock_device \
    fake_unlock_adb test-device 010181 \
    "$unlock_diagnostic" device-011 readiness 4 >/dev/null 2>&1; then
  fail "an unlock with unknown display geometry was accepted"
fi
[ "$(cat "$unlock_submissions")" -eq 0 ] || \
  fail "unknown display geometry sent a credential to an unlocated surface"

# Unknown pre-credential observations are retried, but exhaustion fails before
# any credential reaches an unverified surface.
reset_unlock_fake
FAKE_UNLOCK_PRE_SUBMIT_UNKNOWN_BEFORE=99
if android_acceptance_unlock_device \
    fake_unlock_adb test-device 010181 \
    "$unlock_diagnostic" device-011 readiness 3 >/dev/null 2>&1; then
  fail "an unknown pre-credential OEM state was accepted"
fi
[ "$(cat "$unlock_trust_polls")" -eq 3 ] || \
  fail "unknown pre-credential observations were not retried to the bound"
[ "$(cat "$unlock_submissions")" -eq 0 ] || \
  fail "an unknown pre-credential state received the credential"
grep -Fxq 'stage=pre-credential' "$unlock_diagnostic" || \
  fail "unknown pre-credential diagnostics collapsed the failing stage"
grep -Fxq 'trust=unknown' "$unlock_diagnostic" || \
  fail "unknown pre-credential diagnostics omitted the trust state"
grep -Fxq 'credential=not-submitted' "$unlock_diagnostic" || \
  fail "unknown pre-credential diagnostics misreported PIN submission"

# A post-submit state that stays untrustworthy must fail closed after bounded
# observation retries without ever resubmitting the credential.
reset_unlock_fake
FAKE_UNLOCK_POST_SUBMIT_UNKNOWN_BEFORE=99
if android_acceptance_unlock_device \
    fake_unlock_adb test-device 010181 \
    "$unlock_diagnostic" device-011 instrumentation 3 >/dev/null 2>&1; then
  fail "an unverifiable OEM unlock was accepted"
fi
[ "$(cat "$unlock_pending_polls")" -eq 3 ] || \
  fail "unknown post-credential observations were not retried to the bound"
[ "$(cat "$unlock_submissions")" -eq 1 ] || \
  fail "an unverifiable OEM unlock repeated the credential"
grep -Fxq 'stage=post-credential' "$unlock_diagnostic" || \
  fail "unknown post-credential diagnostics collapsed the failing stage"
grep -Fxq 'trust=unknown' "$unlock_diagnostic" || \
  fail "unknown post-credential diagnostics omitted the trust state"
grep -Fxq 'credential=submitted' "$unlock_diagnostic" || \
  fail "unknown post-credential diagnostics lost the one PIN submission"

# Power observations use the same bounded tri-state contract and must fail
# before trust or credential handling if no authoritative signal arrives.
reset_unlock_fake
FAKE_UNLOCK_POWER_UNKNOWN_BEFORE=99
if android_acceptance_unlock_device \
    fake_unlock_adb test-device 010181 \
    "$unlock_diagnostic" device-011 client 3 >/dev/null 2>&1; then
  fail "an unverifiable OEM wake was accepted"
fi
[ "$(cat "$unlock_wake_polls")" -eq 3 ] || \
  fail "unknown power observations were not retried to the bound"
[ "$(cat "$unlock_submissions")" -eq 0 ] || \
  fail "an unknown power state received the credential"

# The durable artifact contains only finite classifications and caller-supplied
# sanitized attribution. It must never retain the adb serial or PIN.
grep -Fxq 'device_id=device-011' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic omitted its sanitized device ID"
grep -Fxq 'role=client' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic omitted its role"
grep -Fxq 'stage=wake-observe' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic collapsed the failing wake stage"
grep -Fxq 'attempt=3' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic omitted the terminal retry count"
grep -Fxq 'wake_command=sent' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic omitted its one successful wake command"
grep -Fxq 'power=unknown' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic omitted unknown power state"
grep -Fxq 'credential=not-submitted' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic misreported credential submission"
grep -Fxq 'result=failed' "$unlock_diagnostic" || \
  fail "physical interactive diagnostic omitted its terminal result"
if unlock_diagnostic_mode="$(stat -c '%a' "$unlock_diagnostic" 2>/dev/null)"; then
  :
else
  unlock_diagnostic_mode="$(stat -f '%Lp' "$unlock_diagnostic")"
fi
[ "$unlock_diagnostic_mode" = 600 ] || \
  fail "physical interactive diagnostic mode was not 0600"
if grep -Eq 'test-device|010181' "$unlock_diagnostic"; then
  fail "physical interactive diagnostic exposed a serial or credential"
fi

# Real adb may drain inherited stdin. Each helper invocation must own /dev/null
# so a fleet loop cannot silently lose every row after its first device.
reset_unlock_fake
printf '0\n' >"$unlock_state"
printf '%s\n' test-device second-device >"$device_helper_dir/device-rows"
: >"$device_helper_dir/device-seen"
FAKE_UNLOCK_DRAIN_STDIN=1
while IFS= read -r helper_serial; do
  android_acceptance_unlock_device \
    fake_unlock_adb "$helper_serial" 010181 \
    "$unlock_diagnostic" device-011 readiness 3 >/dev/null 2>&1 || \
    fail "stdin-draining adb rejected $helper_serial"
  printf '%s\n' "$helper_serial" >>"$device_helper_dir/device-seen"
done <"$device_helper_dir/device-rows"
unset FAKE_UNLOCK_DRAIN_STDIN
[ "$(wc -l <"$device_helper_dir/device-seen" | tr -d ' ')" = 2 ] || \
  fail "an adb helper consumed the remaining device fleet from stdin"

# A large APK install may let an OEM's delayed keyguard alarm expire after the
# cell's initial readiness check. Reproduce that state transition and require
# the execution boundary to wake/unlock once before invoking the product
# command. An unverifiable unlock must remain fail-closed and must not launch.
execution_marker="$device_helper_dir/execution-marker"
run_if_execution_unlocked() {
  [ "$(tr -d '\r\n' <"$unlock_state")" = 0 ] || return 93
  printf '%s\n' "$1" >"$execution_marker"
}
reset_unlock_fake
printf '0\n' >"$unlock_state"
# Simulated install delay expires the device's prior interactive/unlock state.
printf '1\n' >"$unlock_state"
android_acceptance_run_after_unlock \
  fake_unlock_adb test-device 010181 \
  "$unlock_diagnostic" device-011 instrumentation 3 \
  run_if_execution_unlocked launched || \
  fail "a device re-locked during install was not refreshed before execution"
[ "$(cat "$execution_marker")" = launched ] || \
  fail "the post-install execution command did not run"
[ "$(cat "$unlock_submissions")" -eq 1 ] || \
  fail "post-install execution did not use exactly one credential submission"

rm -f "$execution_marker"
reset_unlock_fake
FAKE_UNLOCK_POST_SUBMIT_UNKNOWN_BEFORE=99
if android_acceptance_run_after_unlock \
    fake_unlock_adb test-device 010181 \
    "$unlock_diagnostic" device-011 instrumentation 3 \
    run_if_execution_unlocked should-not-run >/dev/null 2>&1; then
  fail "an unverifiable post-install unlock was accepted"
fi
[ ! -e "$execution_marker" ] || \
  fail "the product command ran after an unverifiable unlock"
if android_acceptance_run_after_unlock \
    fake_unlock_adb test-device 010181 \
    "$unlock_diagnostic" device-011 instrumentation 3 >/dev/null 2>&1; then
  fail "an empty post-unlock execution command was accepted"
else
  [ "$?" -eq 2 ] || fail "an empty execution command returned the wrong status"
fi

# Pin the production placement as well as the helper behavior. Every install
# completes before the final interactive boundary, and no potentially blocking
# setup step may sit between that boundary and its UI/instrumentation launch.
runner_source="$here/test-main.sh"
owned_selection_source="$(sed -n \
  '/^runner_owns_fallback_emulator()/,/^capture_device_fleet ||/p' "$runner_source")"
# This intentionally asserts the literal runner ownership expression.
# shellcheck disable=SC2016
grep -Fq '[ "$target_serial" = "$started_emulator_serial" ]' \
  <<<"$owned_selection_source" || \
  fail "fallback AVD ownership is inferred without the captured runner serial"
grep -Fq 'android_acceptance_prepare_owned_emulator' \
  <<<"$owned_selection_source" || \
  fail "the runner-started fallback AVD uses physical-device readiness"
grep -Fq "local diagnostic_file=\"\${status_file%.txt}-interactive.txt\"" \
  <<<"$owned_selection_source" || \
  fail "fallback AVD readiness lacks its own durable diagnostic artifact"
grep -Fq 'android_acceptance_prepare_device' <<<"$owned_selection_source" || \
  fail "non-owned targets no longer use strict physical-device readiness"
grep -Fq 'android_acceptance_unlock_device' <<<"$owned_selection_source" || \
  fail "non-owned execution no longer uses the private-PIN gate"
grep -Fq '"$diagnostic_file" "$diagnostic_device_id" readiness 180 24 20' \
  <<<"$owned_selection_source" || \
  fail "physical readiness lacks sanitized role-attributed interactive diagnostics"

peer_boot_source="$(sed -n '/^boot_peer_emulator()/,/^wait_physical_status()/p' "$runner_source")"
grep -Fq 'android_acceptance_prepare_owned_emulator' <<<"$peer_boot_source" || \
  fail "peer AVD readiness still uses the physical-device PIN contract"
# This intentionally asserts the literal runner child PID expression.
# shellcheck disable=SC2016
grep -Fq '"$peer_emulator_pid"' <<<"$peer_boot_source" || \
  fail "peer AVD readiness is not tied to the runner's live child"
grep -Fq 'peer-emulator/interactive.txt' <<<"$peer_boot_source" || \
  fail "peer AVD readiness lacks its own durable diagnostic artifact"
if grep -Fq 'android_unlock_code' <<<"$peer_boot_source"; then
  fail "peer AVD readiness still receives the physical-device PIN"
fi

peer_source="$(sed -n '/^run_android_peer_to_peer()/,/^record_device_cases()/p' "$runner_source")"
peer_install_line="$(grep -Fn -m1 'android_acceptance_install_cell_apks' <<<"$peer_source" | cut -d: -f1)"
peer_provider_gate_line="$(grep -Fn -m1 \
  'android_acceptance_runner_owned_emulator_interactive' <<<"$peer_source" | cut -d: -f1)"
peer_client_gate_line="$(grep -Fn -m1 \
  'selected_device_interactive' <<<"$peer_source" | cut -d: -f1)"
# These locators intentionally match literal runner expressions.
# shellcheck disable=SC2016
peer_provider_preflight_line="$(grep -Fn -m1 \
  '"$adb" "$peer_serial" peer-avd provider' <<<"$peer_source" | cut -d: -f1)"
# shellcheck disable=SC2016
peer_client_preflight_line="$(grep -Fn -m1 \
  '"$adb" "$serial" "$client_diagnostic_id" client' <<<"$peer_source" | cut -d: -f1)"
peer_launch_lines="$(grep -Fn 'shell am instrument -w -r' <<<"$peer_source" | cut -d: -f1)"
[ -n "$peer_provider_gate_line" ] && [ -n "$peer_client_gate_line" ] && \
  [ -n "$peer_provider_preflight_line" ] && [ -n "$peer_client_preflight_line" ] && \
  [ "$(wc -l <<<"$peer_launch_lines" | tr -d ' ')" -eq 2 ] || \
  fail "peer instrumentation does not have one final interactive/preflight gate per role"
peer_provider_launch_line="$(sed -n '1p' <<<"$peer_launch_lines")"
peer_client_launch_line="$(sed -n '2p' <<<"$peer_launch_lines")"
[ "$peer_install_line" -lt "$peer_provider_gate_line" ] && \
  [ "$peer_provider_gate_line" -lt "$peer_provider_preflight_line" ] && \
  [ "$peer_provider_preflight_line" -lt "$peer_provider_launch_line" ] && \
  [ "$peer_provider_launch_line" -lt "$peer_client_gate_line" ] && \
  [ "$peer_client_gate_line" -lt "$peer_client_preflight_line" ] && \
  [ "$peer_client_preflight_line" -lt "$peer_client_launch_line" ] || \
  fail "peer provider/client final gates are not ordered immediately before instrumentation"
if grep -Fq 'android_unlock_code' <<<"$peer_source"; then
  fail "the runner-owned peer provider still receives the physical-device PIN"
fi
grep -Fq 'provider-interactive.txt' <<<"$peer_source" || \
  fail "peer provider launch lacks its own durable diagnostic artifact"
grep -Fq 'client-interactive.txt' <<<"$peer_source" || \
  fail "peer client launch lacks its own durable diagnostic artifact"
grep -Fq '"$serial" "$client_diagnostic_id" client "$out/client-interactive.txt"' \
  <<<"$peer_source" || \
  fail "peer client interactive evidence lacks sanitized client attribution"
grep -Fq 'provider-preflight.txt' <<<"$peer_source" || \
  fail "peer provider launch lacks role-attributed preflight evidence"
grep -Fq 'client-preflight.txt' <<<"$peer_source" || \
  fail "peer client launch lacks role-attributed preflight evidence"

# The sed range intentionally matches the runner's literal $peer_serial source.
# shellcheck disable=SC2016
cell_source="$(sed -n '/^overall=0$/,/^if \[ -n "\$peer_serial" \]/p' "$runner_source")"
cell_install_line="$(grep -Fn -m1 'android_acceptance_install_cell_apks' <<<"$cell_source" | cut -d: -f1)"
cell_unlock_lines="$(grep -Fn 'run_after_selected_device_interactive' <<<"$cell_source" | cut -d: -f1)"
smoke_launch_line="$(grep -Fn -m1 'android_acceptance_launch_smoke' <<<"$cell_source" | cut -d: -f1)"
full_launch_line="$(grep -Fn -m1 'shell am instrument -w -r' <<<"$cell_source" | cut -d: -f1)"
[ "$(wc -l <<<"$cell_unlock_lines" | tr -d ' ')" -eq 2 ] || \
  fail "smoke/full execution does not have one final unlock per launch"
cell_smoke_unlock_line="$(sed -n '1p' <<<"$cell_unlock_lines")"
cell_full_unlock_line="$(sed -n '2p' <<<"$cell_unlock_lines")"
[ "$cell_install_line" -lt "$cell_smoke_unlock_line" ] && \
  [ "$cell_smoke_unlock_line" -lt "$smoke_launch_line" ] && \
  [ "$smoke_launch_line" -lt "$cell_full_unlock_line" ] && \
  [ "$cell_full_unlock_line" -lt "$full_launch_line" ] || \
  fail "smoke/full unlocks are not ordered at the post-install launch boundary"
if grep -Fq 'android_acceptance_unlock_device' <<<"$cell_source"; then
  fail "the cell still consumes its only unlock before package installation"
fi
grep -Fq 'smoke-interactive.txt' <<<"$cell_source" || \
  fail "smoke launch lacks its own durable diagnostic artifact"
grep -Fq 'instrumentation-interactive.txt' <<<"$cell_source" || \
  fail "full launch lacks its own durable diagnostic artifact"
grep -Fq 'smoke-preflight.txt' <<<"$cell_source" || \
  fail "smoke launch lacks its own role-attributed preflight evidence"
grep -Fq 'instrumentation-preflight.txt' <<<"$cell_source" || \
  fail "full launch lacks its own role-attributed preflight evidence"

preflight_wrapper_source="$(sed -n \
  '/^run_after_selected_device_interactive()/,/^capture_device_fleet ||/p' \
  "$runner_source")"
grep -Fq '"$diagnostic_device_id" "$role" 20' <<<"$preflight_wrapper_source" || \
  fail "physical launch boundaries do not attribute interactive diagnostics"
preflight_command_line="$(grep -Fn -m1 'android_acceptance_preflight_device' \
  <<<"$preflight_wrapper_source" | cut -d: -f1)"
preflight_execute_line="$(grep -Fn -m1 '  "$@"' \
  <<<"$preflight_wrapper_source" | cut -d: -f1)"
[ -n "$preflight_command_line" ] && [ -n "$preflight_execute_line" ] && \
  [ "$preflight_command_line" -lt "$preflight_execute_line" ] || \
  fail "smoke/full commands can execute before their final preflight"

fake_capability_adb() {
  [ "$1" = -s ] || return 90
  shift 2
  [ "$1 $2 $3" = "shell pm path" ] && [ "$4" = com.google.android.gms ] || return 91
  [ "${FAKE_PLAY_SERVICES:-0}" -eq 1 ] || return 1
  printf 'package:/system/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk\n'
  [ "${FAKE_PLAY_SERVICES_LARGE:-0}" -eq 0 ] || printf '%s\n' "$large_android_output"
}
FAKE_PLAY_SERVICES=1
FAKE_PLAY_SERVICES_LARGE=1
android_acceptance_device_has_play_services fake_capability_adb play-device || \
  fail "a large package dump lost its early Google Play services path"
FAKE_PLAY_SERVICES=0
FAKE_PLAY_SERVICES_LARGE=0
if android_acceptance_device_has_play_services fake_capability_adb ungoogled-device; then
  fail "an Android device without Google Play services was marked compatible"
fi
android_acceptance_is_solana_device "Solana Mobile" OSOM Saga ingot || \
  fail "Saga was not recognized as Solana hardware"
android_acceptance_is_solana_device Solana Seeker seeker seeker || \
  fail "Seeker was not recognized as Solana hardware"
if android_acceptance_is_solana_device OnePlus OnePlus LE2117 OnePlus9TMO; then
  fail "generic Android hardware was marked Solana-compatible"
fi

smoke_launch_log="$device_helper_dir/smoke-launch.log"
fake_smoke_adb() {
  [ "$1" = -s ] && [ "$2" = smoke-device ] || return 90
  shift 2
  [ "$1" = shell ] || return 91
  shift
  case "${1:-} ${2:-}" in
    'cmd package') printf 'com.bringyour.network/.MainActivity\n' ;;
    'am start')
      printf 'Starting: Intent\nStatus: %s\n' "${FAKE_SMOKE_STATUS:-ok}"
      [ "${FAKE_SMOKE_LARGE:-0}" -eq 0 ] || printf '%s\n' "$large_android_output"
      ;;
    'dumpsys activity')
      case "${FAKE_SMOKE_FOREGROUND:-expected}" in
        expected)
          printf '  mResumedActivity: ActivityRecord{123 u0 com.bringyour.network/.MainActivity t8}\n'
          ;;
        full-name)
          printf '  topResumedActivity=ActivityRecord{123 u0 com.bringyour.network/com.bringyour.network.MainActivity}\n'
          ;;
        historical-only)
          printf '  Hist #0: ActivityRecord{123 u0 com.bringyour.network/.MainActivity t8}\n'
          printf '  mResumedActivity: ActivityRecord{456 u0 com.example.other/.MainActivity t9}\n'
          ;;
        wrong)
          printf '  mResumedActivity: ActivityRecord{456 u0 com.example.other/.MainActivity t9}\n'
          ;;
      esac
      ;;
    'pidof com.bringyour.network')
      [ "${FAKE_SMOKE_PROCESS:-alive}" != alive ] || printf '1234\n'
      ;;
    *) return 92 ;;
  esac
}
FAKE_SMOKE_STATUS=ok
FAKE_SMOKE_FOREGROUND=expected
FAKE_SMOKE_PROCESS=alive
FAKE_SMOKE_LARGE=1
android_acceptance_launch_smoke \
  fake_smoke_adb smoke-device com.bringyour.network "$smoke_launch_log" 1 || \
  fail "a healthy installed app failed its launch smoke"
grep -Fxq 'Status: ok' "$smoke_launch_log" || fail "launch evidence was not retained"
grep -Fq 'foreground=expected process=alive' "$smoke_launch_log" || \
  fail "independent launch evidence was not retained"

FAKE_SMOKE_STATUS=timeout
FAKE_SMOKE_FOREGROUND=full-name
android_acceptance_launch_smoke \
  fake_smoke_adb smoke-device com.bringyour.network "$smoke_launch_log" 1 || \
  fail "an OEM wait timeout hid a verified foreground app"
grep -Fq 'launch-status=timeout foreground=expected process=alive' "$smoke_launch_log" || \
  fail "accepted OEM wait timeout was not retained distinctly"

FAKE_SMOKE_STATUS=ok
FAKE_SMOKE_FOREGROUND=historical-only
if android_acceptance_launch_smoke \
    fake_smoke_adb smoke-device com.bringyour.network "$smoke_launch_log" 1; then
  fail "a historical app record was mistaken for the foreground activity"
fi
FAKE_SMOKE_FOREGROUND=wrong
if android_acceptance_launch_smoke \
    fake_smoke_adb smoke-device com.bringyour.network "$smoke_launch_log" 1; then
  fail "a different foreground application passed launch smoke"
fi
FAKE_SMOKE_FOREGROUND=expected
FAKE_SMOKE_PROCESS=missing
if android_acceptance_launch_smoke \
    fake_smoke_adb smoke-device com.bringyour.network "$smoke_launch_log" 1; then
  fail "a dead app process passed launch smoke"
fi
FAKE_SMOKE_PROCESS=alive
FAKE_SMOKE_STATUS=error
if android_acceptance_launch_smoke \
    fake_smoke_adb smoke-device com.bringyour.network "$smoke_launch_log" 1; then
  fail "an arbitrary ActivityManager launch error passed smoke"
fi
if android_acceptance_pid_list_valid '   '; then
  fail "whitespace-only pidof output was treated as a surviving process"
fi
rm -rf "$device_helper_dir"

animation_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-animation.test.XXXXXX")"
animation_calls="$animation_test_dir/calls"
animation_device="$animation_test_dir/device"
animation_state="$animation_test_dir/ownership"
animation_diagnostic="$animation_test_dir/diagnostic"

animation_key_selected() {
  local key="$1" list="$2"
  case " $list " in *" $key "*) return 0 ;; esac
  return 1
}

reset_animation_fake() {
  rm -rf "$animation_device" "$animation_state"
  rm -f "$animation_diagnostic"
  mkdir "$animation_device"
  printf '1.0\n' >"$animation_device/window_animation_scale"
  printf '0.5\n' >"$animation_device/transition_animation_scale"
  printf 'null\n' >"$animation_device/animator_duration_scale"
  : >"$animation_calls"
  FAKE_ANIMATION_DENY_KEYS=""
  FAKE_ANIMATION_FAIL_AFTER_WRITE_KEYS=""
  FAKE_ANIMATION_UNEXPECTED_WRITE_FAILURE_KEYS=""
  FAKE_ANIMATION_SILENT_IGNORE_KEYS=""
  FAKE_ANIMATION_VERIFY_FAIL_ONCE_KEYS=""
  FAKE_ANIMATION_RESTORE_FAIL_KEYS=""
  FAKE_ANIMATION_DRAIN_STDIN=0
}

fake_animation_adb() {
  local operation="${5:-}" key="${7:-}" value="${8:-}"
  local state_path verify_marker
  [ "$1" = -s ] && [ "$2" = emulator-5554 ] && [ "$3 $4" = "shell settings" ] || \
    fail "unexpected animation adb invocation: $*"
  [ "${6:-}" = global ] || return 91
  case "$key" in
    window_animation_scale|transition_animation_scale|animator_duration_scale) ;;
    *) return 91 ;;
  esac
  state_path="$animation_device/$key"
  verify_marker="$animation_device/$key.verify-fail-once"
  printf '%s global %s%s\n' "$operation" "$key" "${value:+ $value}" >>"$animation_calls"

  case "$operation" in
    get)
      if [ -e "$verify_marker" ]; then
        rm -f "$verify_marker"
        return 42
      fi
      cat "$state_path"
      return 0
      ;;
    put)
      [ -n "$value" ] || return 91
      if android_acceptance_animation_value_zero "$value"; then
        if animation_key_selected "$key" "$FAKE_ANIMATION_DENY_KEYS"; then
          echo 'SecurityException: Permission denial: WRITE_SECURE_SETTINGS' >&2
          return 255
        fi
        if animation_key_selected "$key" "$FAKE_ANIMATION_UNEXPECTED_WRITE_FAILURE_KEYS"; then
          echo 'transport unavailable' >&2
          return 42
        fi
        if animation_key_selected "$key" "$FAKE_ANIMATION_SILENT_IGNORE_KEYS"; then
          return 0
        fi
      elif animation_key_selected "$key" "$FAKE_ANIMATION_RESTORE_FAIL_KEYS"; then
        echo 'restore transport unavailable' >&2
        return 42
      fi
      printf '%s\n' "$value" >"$state_path"
      if android_acceptance_animation_value_zero "$value" && \
          animation_key_selected "$key" "$FAKE_ANIMATION_VERIFY_FAIL_ONCE_KEYS"; then
        : >"$verify_marker"
      fi
      if android_acceptance_animation_value_zero "$value" && \
          animation_key_selected "$key" "$FAKE_ANIMATION_FAIL_AFTER_WRITE_KEYS"; then
        echo 'transport failed after write' >&2
        return 42
      fi
      ;;
    delete)
      if animation_key_selected "$key" "$FAKE_ANIMATION_RESTORE_FAIL_KEYS"; then
        echo 'restore transport unavailable' >&2
        return 42
      fi
      printf 'null\n' >"$state_path"
      ;;
    *) return 91 ;;
  esac
  if [ "$FAKE_ANIMATION_DRAIN_STDIN" -eq 1 ]; then
    # Real adb may read its inherited stdin. This must not consume the state
    # file that drives android_acceptance_restore_animations.
    while IFS= read -r _; do :; done
  fi
}

assert_animation_value() {
  [ "$(cat "$animation_device/$1")" = "$2" ] || \
    fail "$1 was not $2"
}

reset_animation_fake
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "could not disable Android animations"
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
  [ -f "$animation_state/$key.owned" ] || fail "$key ownership was not recorded"
  grep -Fxq "put global $key 0" "$animation_calls" || \
    fail "$key was not disabled"
done
grep -Fxq 'status=disabled' "$animation_diagnostic" || \
  fail "successful animation optimization was not diagnosed"

: >"$animation_calls"
FAKE_ANIMATION_DRAIN_STDIN=1
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "could not restore Android animations"
FAKE_ANIMATION_DRAIN_STDIN=0
assert_animation_value window_animation_scale 1.0
assert_animation_value transition_animation_scale 0.5
assert_animation_value animator_duration_scale null
[ ! -e "$animation_state" ] || fail "completed animation ownership was retained"
grep -Fxq 'put global window_animation_scale 1.0' "$animation_calls" || \
  fail "window animation scale was not restored"
grep -Fxq 'put global transition_animation_scale 0.5' "$animation_calls" || \
  fail "transition animation scale was not restored"
grep -Fxq 'delete global animator_duration_scale' "$animation_calls" || \
  fail "an originally absent animator duration scale was not deleted"

: >"$animation_calls"
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "a completed animation restore was not idempotent"
[ ! -s "$animation_calls" ] || fail "an idempotent restore touched the device"

reset_animation_fake
FAKE_ANIMATION_DENY_KEYS='window_animation_scale transition_animation_scale animator_duration_scale'
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "an OEM WRITE_SECURE_SETTINGS denial rejected optional animation suppression"
[ ! -e "$animation_state" ] || fail "denied animation writes claimed ownership"
[ "$(grep -c '=permission-denied$' "$animation_diagnostic")" -eq 3 ] || \
  fail "permission denials were not retained as finite diagnostics"
grep -Fxq 'status=best-effort' "$animation_diagnostic" || \
  fail "permission-denied animation suppression was not classified best-effort"
assert_animation_value window_animation_scale 1.0
assert_animation_value transition_animation_scale 0.5
assert_animation_value animator_duration_scale null
: >"$animation_calls"
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "no-ownership animation cleanup failed"
[ ! -s "$animation_calls" ] || fail "no-ownership cleanup touched the device"

reset_animation_fake
FAKE_ANIMATION_DENY_KEYS=transition_animation_scale
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "a partial OEM animation denial rejected the optional optimization"
[ -f "$animation_state/window_animation_scale.owned" ] || \
  fail "a changed window scale lost ownership"
[ ! -e "$animation_state/transition_animation_scale.owned" ] && \
  [ ! -e "$animation_state/transition_animation_scale.pending" ] || \
  fail "a denied transition scale claimed ownership"
[ -f "$animation_state/animator_duration_scale.owned" ] || \
  fail "a changed animator scale lost ownership"
: >"$animation_calls"
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "partial animation ownership was not restored"
assert_animation_value window_animation_scale 1.0
assert_animation_value transition_animation_scale 0.5
assert_animation_value animator_duration_scale null
if grep -Eq '^(put|delete) global transition_animation_scale' "$animation_calls"; then
  fail "cleanup rewrote a permission-denied unowned setting"
fi

reset_animation_fake
FAKE_ANIMATION_FAIL_AFTER_WRITE_KEYS=window_animation_scale
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "a command failure after an applied write lost verified ownership"
[ -f "$animation_state/window_animation_scale.owned" ] || \
  fail "failure-after-write was not owned after value verification"
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "failure-after-write ownership was not restored"

reset_animation_fake
FAKE_ANIMATION_UNEXPECTED_WRITE_FAILURE_KEYS=transition_animation_scale
if android_acceptance_disable_animations \
    fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic"; then
  fail "an unexpected animation write failure was masked as best-effort"
fi
[ -f "$animation_state/window_animation_scale.owned" ] || \
  fail "partial ownership was lost before an unexpected write failure"
[ -f "$animation_state/transition_animation_scale.pending" ] || \
  fail "an uncertain failed write did not retain its pending journal"
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "partial ownership after write failure was not reconciled"
assert_animation_value window_animation_scale 1.0
assert_animation_value transition_animation_scale 0.5

reset_animation_fake
FAKE_ANIMATION_VERIFY_FAIL_ONCE_KEYS=window_animation_scale
if android_acceptance_disable_animations \
    fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic"; then
  fail "an interrupted post-write verification was accepted"
fi
[ -f "$animation_state/window_animation_scale.pending" ] || \
  fail "an interrupted write lost its pending ownership journal"
assert_animation_value window_animation_scale 0
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "an interrupted animation mutation was not recovered"
assert_animation_value window_animation_scale 1.0

reset_animation_fake
printf '0\n' >"$animation_device/window_animation_scale"
printf '0.0\n' >"$animation_device/transition_animation_scale"
printf '.0\n' >"$animation_device/animator_duration_scale"
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "already-zero animation settings failed best-effort suppression"
[ ! -e "$animation_state" ] || fail "already-zero settings claimed ownership"
if grep -Eq '^(put|delete) global' "$animation_calls"; then
  fail "already-zero animation settings were rewritten"
fi
grep -Fxq 'status=no-change' "$animation_diagnostic" || \
  fail "already-zero animation settings were not diagnosed"

reset_animation_fake
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "cleanup-failure fixture could not disable animations"
FAKE_ANIMATION_RESTORE_FAIL_KEYS=animator_duration_scale
if android_acceptance_restore_animations \
    fake_animation_adb emulator-5554 "$animation_state"; then
  fail "an unexpected animation cleanup failure was masked"
fi
[ -f "$animation_state/animator_duration_scale.owned" ] || \
  fail "failed cleanup discarded its ownership journal"
FAKE_ANIMATION_RESTORE_FAIL_KEYS=""
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "animation cleanup could not recover after a transient failure"

reset_animation_fake
android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" "$animation_diagnostic" || \
  fail "foreign-edit fixture could not disable animations"
printf '0.25\n' >"$animation_device/transition_animation_scale"
: >"$animation_calls"
if android_acceptance_restore_animations \
    fake_animation_adb emulator-5554 "$animation_state"; then
  fail "animation cleanup overwrote a foreign setting edit"
fi
if grep -Eq '^(put|delete) global' "$animation_calls"; then
  fail "foreign animation state was detected only after cleanup mutated the device"
fi
printf '0\n' >"$animation_device/transition_animation_scale"
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "foreign-edit fixture could not be safely cleaned"

rm -rf "$animation_test_dir"

fake_apkanalyzer() {
  local fake_abi
  local -a fake_abis

  case "${1:-} ${2:-}" in
    'dex code')
      [ "${3:-}" = --class ] || fail "unexpected APK analyzer dex invocation"
      case "${4:-}" in
        com.bringyour.network.acceptance.EgressProbeActivity)
          if [ "${FAKE_PROBE_RUNTIME:-java}" = kotlin ]; then
            printf 'invoke-static {}, Lkotlin/jvm/internal/Intrinsics;->checkNotNull()V\n'
          else
            printf 'invoke-virtual {}, Ljava/net/URL;->openConnection()Ljava/net/URLConnection;\n'
          fi
          [ "${FAKE_PROBE_LARGE:-0}" -eq 0 ] || printf '%s\n' "$large_android_output"
          ;;
        com.bringyour.network.MainApplication)
          if [ "${FAKE_MAIN_APPLICATION_LINKAGE:-safe}" = legacy-thermal ]; then
            printf '%s\n' \
              "iget-object v0, Lcom/bringyour/network/MainApplication;->listener:Landroid/os/PowerManager\$OnThermalStatusChangedListener;"
          else
            printf 'iget-object v0, Lcom/bringyour/network/MainApplication;->registration:Lcom/bringyour/network/ThermalStatusRegistration;\n'
          fi
          [ "${FAKE_MAIN_APPLICATION_LARGE:-0}" -eq 0 ] || printf '%s\n' "$large_android_output"
          ;;
        *) fail "unexpected APK analyzer class ${4:-missing}" ;;
      esac
      ;;
    'files list')
      IFS=',' read -r -a fake_abis <<<"$FAKE_APP_ABIS"
      for fake_abi in "${fake_abis[@]}"; do
        printf '/lib/%s/libgojni.so\n' "$fake_abi"
      done
      ;;
    'manifest min-sdk')
      printf '%s\n' "$FAKE_APP_MIN_SDK"
      ;;
    *) fail "unexpected APK analyzer invocation" ;;
  esac
}

FAKE_PROBE_RUNTIME=java
FAKE_PROBE_LARGE=0
FAKE_MAIN_APPLICATION_LINKAGE=safe
FAKE_MAIN_APPLICATION_LARGE=0
verify_android_acceptance_egress_probe fake_apkanalyzer test.apk || \
  fail "platform-only standalone egress probe was rejected"
FAKE_PROBE_RUNTIME=kotlin
FAKE_PROBE_LARGE=1
if verify_android_acceptance_egress_probe fake_apkanalyzer test.apk >/dev/null 2>&1; then
  fail "a large bytecode dump hid its early unavailable Kotlin reference"
fi
FAKE_PROBE_RUNTIME=java
FAKE_PROBE_LARGE=0
verify_android_acceptance_legacy_application_linkage fake_apkanalyzer app.apk || \
  fail "API-neutral MainApplication linkage was rejected"
FAKE_MAIN_APPLICATION_LINKAGE=legacy-thermal
FAKE_MAIN_APPLICATION_LARGE=1
if verify_android_acceptance_legacy_application_linkage \
    fake_apkanalyzer app.apk >/dev/null 2>&1; then
  fail "a large MainApplication bytecode dump hid its API-29-only thermal type"
fi
FAKE_MAIN_APPLICATION_LINKAGE=safe
FAKE_MAIN_APPLICATION_LARGE=0

for flavor_abi_contract in \
  'github:arm64-v8a,armeabi-v7a:26' \
  'fdroid:arm64-v8a,armeabi-v7a:26' \
  'play:arm64-v8a,armeabi-v7a,x86_64:26' \
  'solana_dapp:arm64-v8a:26'; do
  flavor="${flavor_abi_contract%%:*}"
  flavor_contract="${flavor_abi_contract#*:}"
  FAKE_APP_ABIS="${flavor_contract%%:*}"
  FAKE_APP_MIN_SDK="${flavor_contract##*:}"
  verify_android_acceptance_shipping_apk_abis fake_apkanalyzer app.apk "$flavor" || \
    fail "$flavor shipping APK ABI contract was rejected"
  verify_android_acceptance_shipping_apk_min_sdk fake_apkanalyzer app.apk "$flavor" || \
    fail "$flavor shipping APK minimum SDK contract was rejected"
done

gradle_product_flavor_block() {
  local flavor="$1"
  awk -v wanted="$flavor" '
    /^    productFlavors \{$/ { in_products = 1; next }
    in_products && !capture && $0 == "        " wanted " {" { capture = 1 }
    capture {
      print
      line = $0
      opens = gsub(/\{/, "{", line)
      line = $0
      closes = gsub(/\}/, "}", line)
      depth += opens - closes
      if (depth == 0) exit
    }
  ' "$here/app/app/build.gradle"
}

assert_gradle_flavor_abis() {
  local flavor="$1" expected="$2" block actual
  block="$(gradle_product_flavor_block "$flavor")"
  actual="$(printf '%s\n' "$block" | sed -n \
    's/^[[:space:]]*abiFilters = \[\(.*\)\][[:space:]]*$/\1/p')"
  [ "$actual" = "$expected" ] || \
    fail "$flavor Gradle ABI filters were '$actual', expected '$expected'"
}

assert_gradle_flavor_abis github "'armeabi-v7a', 'arm64-v8a'"
assert_gradle_flavor_abis play "'x86_64', 'armeabi-v7a', 'arm64-v8a'"
assert_gradle_flavor_abis solana_dapp "'arm64-v8a'"

run_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-acceptance.test.XXXXXX")"
mkdir -p "$run_dir/go-mod-cache/example@v1.0.0/nested"
printf 'cached module\n' >"$run_dir/go-mod-cache/example@v1.0.0/nested/file.go"
chmod 0555 "$run_dir/go-mod-cache/example@v1.0.0/nested"
chmod 0444 "$run_dir/go-mod-cache/example@v1.0.0/nested/file.go"

remove_android_acceptance_run_dir "$run_dir"
[ ! -e "$run_dir" ] || fail "read-only Go module cache was not removed"

unsafe_dir="$(mktemp -d "${TMPDIR:-/tmp}/unrelated-android-test.XXXXXX")"
if remove_android_acceptance_run_dir "$unsafe_dir" >/dev/null 2>&1; then
  fail "cleanup accepted a directory outside its private naming convention"
fi
[ -d "$unsafe_dir" ] || fail "cleanup removed an unrelated directory"
rmdir "$unsafe_dir"

fake_adb() {
  [ "$1" = -s ] || return 90
  shift 2
  case "$1" in
    get-state)
      [ "${FAKE_ADB_DEVICE:-device}" = device ] || return 1
      printf 'device\n'
      ;;
    shell)
      if [ "${2:-}" = run-as ]; then
        [ "${FAKE_ADB_FILE:-missing}" = exists ]
      elif [ "${2:-}" = pm ] && [ "${3:-}" = list ] && [ "${4:-}" = packages ]; then
        [ "${FAKE_ADB_DEVICE:-device}" = device ] || return 1
        [ "${FAKE_ADB_PACKAGE_OUTPUT:-valid}" = malformed ] && {
          printf 'package manager unavailable\n'
          return 0
        }
        if [ "${FAKE_ADB_PACKAGE:-missing}" = installed ]; then
          printf 'package:%s\n' "${5:-}"
          [ "${FAKE_ADB_LARGE_PACKAGE:-0}" -eq 0 ] || \
            printf 'package:com.example.%s\n' "$large_android_output"
        fi
      else
        return 91
      fi
      ;;
    uninstall)
      printf '%s\n' "$*" >>"$package_uninstall_calls"
      case "${FAKE_ADB_UNINSTALL:-success}" in
        success) FAKE_ADB_PACKAGE=missing ;;
        failed-after-remove) FAKE_ADB_PACKAGE=missing; return 42 ;;
        retained) return 0 ;;
        *) return 92 ;;
      esac
      ;;
    exec-out)
      if [ "${FAKE_ADB_FILE:-missing}" = missing ]; then
        printf "run-as: unknown package: com.bringyour.network\n"
      elif [ "${5:-}" = files/acceptance/physical-active-client-id ]; then
        printf 'physical-client\n'
      else
        printf 'client-one\nclient_two\nclient-one\n'
      fi
      ;;
    *) return 91 ;;
  esac
}

package_timeout_calls="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-package-timeout.test.XXXXXX")"
package_uninstall_calls="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-package-uninstall.test.XXXXXX")"
fake_package_timeout() {
  printf '%s\n' "$*" >>"$package_timeout_calls"
  [ "${1:-}" = --foreground ] && [ "${2:-}" = --signal=TERM ] && \
    case "${3:-} ${4:-}" in
      '--kill-after=5s 15s'|'--kill-after=5s 30s') ;;
      *) fail "package cleanup lacks a bounded TERM-to-KILL policy: $*" ;;
    esac
  shift 4
  case "${FAKE_PACKAGE_TIMEOUT:-none}:$*" in
    query:*' shell pm list packages '*) return 124 ;;
    uninstall:*' uninstall '*) return 124 ;;
  esac
  "$@"
}

adb_run_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-acceptance.test.XXXXXX")"
FAKE_ADB_FILE=missing
pull_android_acceptance_active_clients fake_adb emulator-5554 "$adb_run_dir" "$adb_run_dir/pulled"
pulled_file=""
if [ -d "$adb_run_dir/pulled" ]; then
  pulled_file="$(find "$adb_run_dir/pulled" -type f -print -quit)" || \
    fail "could not inspect the retained-client destination"
fi
if [ -n "$pulled_file" ]; then
  fail "missing active-client file produced a retained client ID"
fi
pull_android_acceptance_private_client_id \
  fake_adb emulator-5554 com.bringyour.network \
  files/acceptance/physical-active-client-id "$adb_run_dir/physical-client"
[ ! -e "$adb_run_dir/physical-client" ] || \
  fail "run-as diagnostic was retained as a physical client ID"

FAKE_ADB_FILE=exists
pull_android_acceptance_active_clients fake_adb emulator-5554 "$adb_run_dir" "$adb_run_dir/pulled"
[ "$(find "$adb_run_dir/pulled" -type f | wc -l | tr -d ' ')" = 2 ] || \
  fail "valid retained client IDs were not deduplicated and pulled"
pull_android_acceptance_private_client_id \
  fake_adb emulator-5554 com.bringyour.network \
  files/acceptance/physical-active-client-id "$adb_run_dir/physical-client"
[ "$(cat "$adb_run_dir/physical-client")" = physical-client ] || \
  fail "valid physical client ID was not pulled"

FAKE_ADB_PACKAGE=missing
android_acceptance_package_absent \
  fake_package_timeout fake_adb emulator-5554 com.bringyour.network || \
  fail "an already-absent package was reported as unverifiable"
FAKE_ADB_PACKAGE=installed
FAKE_ADB_LARGE_PACKAGE=1
if android_acceptance_package_absent \
    fake_package_timeout fake_adb emulator-5554 com.bringyour.network; then
  fail "a large package dump hid its early installed-package path"
fi
FAKE_ADB_LARGE_PACKAGE=0

FAKE_ADB_DEVICE=offline
FAKE_ADB_PACKAGE=missing
if android_acceptance_package_absent \
    fake_package_timeout fake_adb emulator-5554 com.bringyour.network; then
  fail "an offline device was reported as verified"
fi
FAKE_ADB_DEVICE=device

FAKE_ADB_PACKAGE_OUTPUT=malformed
if android_acceptance_package_absent \
    fake_package_timeout fake_adb emulator-5554 com.bringyour.network; then
  fail "unrecognized package-manager output was reported as verified absence"
fi
FAKE_ADB_PACKAGE_OUTPUT=valid

FAKE_PACKAGE_TIMEOUT=query
if android_acceptance_package_absent \
    fake_package_timeout fake_adb emulator-5554 com.bringyour.network; then
  fail "a timed-out package query was reported as verified absence"
fi
FAKE_PACKAGE_TIMEOUT=none

package_cleanup_log="$adb_run_dir/uninstall-test-package.log"
FAKE_ADB_PACKAGE=installed
FAKE_ADB_UNINSTALL=retained
if android_acceptance_uninstall_package \
    fake_package_timeout fake_adb emulator-5554 com.bringyour.network.test \
    "$package_cleanup_log"; then
  fail "cleanup reported success while the stale test package remained installed"
fi
grep -Fq 'absence verification=1' "$package_cleanup_log" || \
  fail "retained stale-package state was not preserved in cleanup diagnostics"

FAKE_PACKAGE_TIMEOUT=uninstall
if android_acceptance_uninstall_package \
    fake_package_timeout fake_adb emulator-5554 com.bringyour.network.test \
    "$package_cleanup_log"; then
  fail "a timed-out uninstall with a retained package was reported clean"
fi
FAKE_PACKAGE_TIMEOUT=none

FAKE_ADB_UNINSTALL=failed-after-remove
android_acceptance_uninstall_package \
  fake_package_timeout fake_adb emulator-5554 com.bringyour.network.test \
  "$package_cleanup_log" || \
  fail "cleanup rejected independently verified absence after an uninstall error"
grep -Fq 'absence independently verified' "$package_cleanup_log" || \
  fail "uninstall-error recovery was not retained in cleanup diagnostics"

# This intentionally asserts the literal live-runner call expression.
# shellcheck disable=SC2016
if grep -Fq 'uninstall_acceptance_packages "$serial" || true' "$here/test-main.sh"; then
  fail "the live runner silently ignores stale pre-cell packages"
fi

remove_android_acceptance_run_dir "$adb_run_dir"
rm -f "$package_timeout_calls" "$package_uninstall_calls"

if rg -n 'hide_error_dialogs|suppress_system_error_dialogs' \
    "$here/test-main.sh" "$here/test-main-lib.sh" >/dev/null; then
  fail "acceptance still hides Android system or product crash dialogs globally"
fi

setup_source="$here/../build/all/android/setup.sh"
[ -f "$setup_source" ] || fail "Android setup source is missing"
# This intentionally asserts the literal setup launch expression.
# shellcheck disable=SC2016
grep -Fq 'args=(-avd "$avd_name" -gpu host ' "$setup_source" || \
  fail "Android setup smoke does not launch with host rendering"
setup_preflight_line="$(grep -n -m1 'android_acceptance_preflight_device' \
  "$setup_source" | cut -d: -f1)"
setup_success_line="$(grep -n -m1 'SMOKE TEST PASSED' "$setup_source" | cut -d: -f1)"
[ -n "$setup_preflight_line" ] && [ -n "$setup_success_line" ] && \
  [ "$setup_preflight_line" -lt "$setup_success_line" ] || \
  fail "Android setup can claim smoke success before resource/dialog preflight"

echo "android/test-main.sh runner tests passed"
