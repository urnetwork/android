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

timeout() {
  shift
  "$@"
}

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

echo "android/test-main.sh runner tests passed"
