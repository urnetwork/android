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
fleet_plan="$fleet_dir/plan"
fleet_results="$fleet_dir/results"
android_acceptance_write_device_records "$fleet_selected" "$fleet_records" || \
  fail "could not create device records"
[ "$(cat "$fleet_records")" = $'device-001-emulator-5554\temulator-5554\ndevice-002-partner-serial\tpartner-serial' ] || \
  fail "device artifact ids were not deterministic"
android_acceptance_write_device_flavor_plan \
  "$fleet_records" "$fleet_plan" github play solana_dapp ethos_dapp fdroid || \
  fail "could not create fleet plan"
[ "$(wc -l <"$fleet_plan" | tr -d ' ')" = 10 ] || \
  fail "device/flavor plan did not contain the full Cartesian product"
[ "$(sort -u "$fleet_plan" | wc -l | tr -d ' ')" = 10 ] || \
  fail "device/flavor plan contains a duplicate"
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
grep -Fq 'reserved_device_serials=(3B161FDJG001KT R5CX21FY6ND)' "$here/test-main.sh" || \
  fail "the two performance devices are not mandatory runner exclusions"
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

emulator_log="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-emulator.test.XXXXXX")"
if (run_android_acceptance_shared_avd_emulator \
    /usr/bin/true "$emulator_log" -avd urnetwork-acceptance) >/dev/null 2>&1; then
  fail "shared AVD launcher accepted a writable emulator"
fi
(run_android_acceptance_shared_avd_emulator \
  /usr/bin/true "$emulator_log" -avd urnetwork-acceptance -read-only) || \
  fail "shared AVD launcher rejected a read-only emulator"
rm -f "$emulator_log"

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

install_calls="$apk_test_dir/install-calls"
fake_install_timeout() {
  shift
  "$@"
}
fake_install_adb() {
  printf '%s\n' "$*" >>"$install_calls"
  case "$*" in
    *app.apk) return 42 ;;
  esac
}
if android_acceptance_install_apks \
  fake_install_timeout fake_install_adb emulator-5558 \
  "$apk_test_dir/cache/app.apk" "$apk_test_dir/cache/test.apk"; then
  fail "peer APK installer accepted a failed app install"
fi
[ "$(wc -l <"$install_calls" | tr -d ' ')" = 1 ] || \
  fail "peer APK installer continued after its first failure"
rm -rf "$apk_test_dir"

timeout() {
  shift
  "$@"
}

animation_calls="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-animation-calls.test.XXXXXX")"
animation_state="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-animation-state.test.XXXXXX")"
fake_animation_adb() {
  [ "$1" = -s ] && [ "$2" = emulator-5554 ] && [ "$3 $4" = "shell settings" ] || \
    fail "unexpected animation adb invocation: $*"
  printf '%s%s%s%s\n' \
    "${5:-}" "${6:+ ${6}}" "${7:+ ${7}}" "${8:+ ${8}}" >>"$animation_calls"
  if [ "${5:-} ${6:-}" = "get global" ]; then
    case "${7:-}" in
      window_animation_scale) printf '1.0\n' ;;
      transition_animation_scale) printf '0.5\n' ;;
      animator_duration_scale) printf 'null\n' ;;
      *) return 91 ;;
    esac
  elif [ "${FAKE_ANIMATION_DRAIN_STDIN:-0}" -eq 1 ]; then
    # Real adb may read its inherited stdin. This must not consume the state
    # file that drives android_acceptance_restore_animations.
    while IFS= read -r _; do :; done
  fi
}

android_acceptance_disable_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "could not disable Android animations"
[ "$(cat "$animation_state")" = "window_animation_scale=1.0
transition_animation_scale=0.5
animator_duration_scale=null" ] || \
  fail "animation scale state was not captured exactly"
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
  grep -Fxq "put global $key 0" "$animation_calls" || \
    fail "$key was not disabled"
done

: >"$animation_calls"
FAKE_ANIMATION_DRAIN_STDIN=1
android_acceptance_restore_animations \
  fake_animation_adb emulator-5554 "$animation_state" || \
  fail "could not restore Android animations"
unset FAKE_ANIMATION_DRAIN_STDIN
grep -Fxq 'put global window_animation_scale 1.0' "$animation_calls" || \
  fail "window animation scale was not restored"
grep -Fxq 'put global transition_animation_scale 0.5' "$animation_calls" || \
  fail "transition animation scale was not restored"
grep -Fxq 'delete global animator_duration_scale' "$animation_calls" || \
  fail "an originally absent animator duration scale was not deleted"
rm -f "$animation_calls" "$animation_state"

fake_apkanalyzer() {
  [ "$1 $2 $3 $4" = "dex code --class com.bringyour.network.acceptance.EgressProbeActivity" ] || \
    fail "unexpected APK analyzer invocation"
  if [ "${FAKE_PROBE_RUNTIME:-java}" = kotlin ]; then
    printf 'invoke-static {}, Lkotlin/jvm/internal/Intrinsics;->checkNotNull()V\n'
  else
    printf 'invoke-virtual {}, Ljava/net/URL;->openConnection()Ljava/net/URLConnection;\n'
  fi
}

FAKE_PROBE_RUNTIME=java
verify_android_acceptance_egress_probe fake_apkanalyzer test.apk || \
  fail "platform-only standalone egress probe was rejected"
FAKE_PROBE_RUNTIME=kotlin
if verify_android_acceptance_egress_probe fake_apkanalyzer test.apk >/dev/null 2>&1; then
  fail "standalone egress probe with an unavailable Kotlin reference was accepted"
fi
FAKE_PROBE_RUNTIME=java

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
      elif [ "${2:-}" = pm ] && [ "${3:-}" = path ]; then
        if [ "${FAKE_ADB_PACKAGE:-missing}" = installed ]; then
          printf 'package:/data/app/com.bringyour.network/base.apk\n'
        else
          return 1
        fi
      else
        return 91
      fi
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

adb_run_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-acceptance.test.XXXXXX")"
FAKE_ADB_FILE=missing
pull_android_acceptance_active_clients fake_adb emulator-5554 "$adb_run_dir" "$adb_run_dir/pulled"
if find "$adb_run_dir/pulled" -type f -print -quit 2>/dev/null | grep -q .; then
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
android_acceptance_package_absent fake_adb emulator-5554 com.bringyour.network || \
  fail "an already-absent package was reported as unverifiable"
FAKE_ADB_PACKAGE=installed
if android_acceptance_package_absent fake_adb emulator-5554 com.bringyour.network; then
  fail "an installed package was reported absent"
fi

FAKE_ADB_DEVICE=offline
FAKE_ADB_PACKAGE=missing
if android_acceptance_package_absent fake_adb emulator-5554 com.bringyour.network; then
  fail "an offline device was reported as verified"
fi
FAKE_ADB_DEVICE=device

remove_android_acceptance_run_dir "$adb_run_dir"

system_dialog_calls="$(mktemp "${TMPDIR:-/tmp}/urnetwork-android-system-dialogs.test.XXXXXX")"
fake_system_dialog_adb() {
  printf '%s\n' "$*" >>"$system_dialog_calls"
}
android_acceptance_suppress_system_error_dialogs \
  fake_system_dialog_adb emulator-5558 || \
  fail "could not suppress system error dialogs"
[ "$(cat "$system_dialog_calls")" = \
  "-s emulator-5558 shell settings put global hide_error_dialogs 1" ] || \
  fail "system error dialog suppression did not target the requested AVD"
rm -f "$system_dialog_calls"

echo "android/test-main.sh runner tests passed"
