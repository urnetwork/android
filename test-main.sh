#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
#
# Product acceptance test for the LOCAL Android app against the production
# ("main") environment.  It builds and installs each shipping target, drives
# the real Compose UI, creates or restores an instant account, logs out, logs
# back in with its 24-word secret key, then uses the acceptance account to
# connect, verify a changed public egress IP from a second UID, disconnect, and
# log out.
#
# Targets: github, play, solana_dapp, and the ungoogled github source transform
# shipped by F-Droid. GitHub and F-Droid run on every eligible supported-ARM
# device, Play requires Google Play services, and Solana requires Seeker or
# Saga hardware. All share one application id, so each compatible device/target
# cell starts from a clean install while securely restoring the same
# recoverable account fixture.
#
# Usage:
#   ./test-main.sh                         all targets on every eligible device
#   ./test-main.sh --repeat=5              five full passes per target
#   ./test-main.sh --flavor=github         one target (repeatable/comma-separated)
#   ./test-main.sh --smoke                  install and launch every compatible cell only
#   ./test-main.sh --skip-build            reuse APKs and build-id sidecars
#   ./test-main.sh --headless              start the AVD without a window
#   ./test-main.sh --keep-emulator         leave an emulator started here running
#   ./test-main.sh --keep-fixture          retain the recoverable account for another app
#   ./test-main.sh --diagnostic-device=<serial> --diagnostic-case=peer-to-peer --flavor=github
#                                             one diagnostic P2P cell; never final proof
#
# Environment:
#   UR_ACCEPT_VAULT=<path>                 alternate tests.yml fixture vault
#   UR_ACCEPT_FIXTURE=<path>               persistent private secret-key fixture
#   UR_ACCEPT_REPEAT=<n>                   repetition count
#   UR_ACCEPT_KEEP_FIXTURE=1               retain the account after a successful run
#   UR_ACCEPT_ANDROID_AVD=<name>            AVD (default urnetwork-acceptance)
#   UR_ACCEPT_ANDROID_TOOLS=<path>          setup-managed Go mobile tools/cache
#   ANDROID_SDK_ROOT=<path>                 Android SDK
#
# The private device unlock code is read from android.unlock_code in tests.yml.
#
# Reserved performance devices are always excluded from acceptance:
#   Pixel 8 Pro (3B161FDJG001KT)
#   Galaxy S24 Ultra (R5CX21FY6ND)
set -euo pipefail
umask 077

here="$(cd "$(dirname "$0")" && pwd)"
root="${URNETWORK_ROOT:-$(dirname "$here")}"
source "$here/test-main-lib.sh"
vault="${UR_ACCEPT_VAULT:-$root/vault/main/tests.yml}"
fixture="${UR_ACCEPT_FIXTURE:-$here/tests/__acceptance__/fixtures/android-main.secret}"
repeat_count="${UR_ACCEPT_REPEAT:-1}"
skip_build="${SKIP_BUILD:-0}"
headless="${HEADLESS:-0}"
keep_emulator=0
keep_fixture="${UR_ACCEPT_KEEP_FIXTURE:-0}"
result_matrix="${UR_ACCEPT_RESULT_FILE:-}"
smoke_only=0
targets="github play solana_dapp fdroid"
selected_targets=""
selected_flavor_value=""
flavor_selector_count=0
diagnostic_device=""
diagnostic_case=""
diagnostic_device_seen=0
diagnostic_case_seen=0
reserved_device_serials=(3B161FDJG001KT R5CX21FY6ND)

for arg in "$@"; do
  case "$arg" in
    --skip-build) skip_build=1 ;;
    --headless) headless=1 ;;
    --keep-emulator) keep_emulator=1 ;;
    --keep-fixture) keep_fixture=1 ;;
    --smoke) smoke_only=1 ;;
    --repeat=*) repeat_count="${arg#*=}" ;;
    --flavor=*)
      flavor_selector_count=$((flavor_selector_count + 1))
      selected_flavor_value="${arg#*=}"
      selected_targets="$selected_targets $selected_flavor_value"
      ;;
    --diagnostic-device=*)
      [ "$diagnostic_device_seen" -eq 0 ] || {
        echo "--diagnostic-device may be supplied only once" >&2
        exit 2
      }
      diagnostic_device_seen=1
      diagnostic_device="${arg#*=}"
      ;;
    --diagnostic-case=*)
      [ "$diagnostic_case_seen" -eq 0 ] || {
        echo "--diagnostic-case may be supplied only once" >&2
        exit 2
      }
      diagnostic_case_seen=1
      diagnostic_case="${arg#*=}"
      ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

execution_mode="$(android_acceptance_execution_mode \
  "$diagnostic_device_seen" "$diagnostic_case_seen")" || exit $?

if [ "$smoke_only" -eq 1 ] && [ -n "$result_matrix" ]; then
  echo "--smoke cannot write the canonical acceptance result matrix" >&2
  exit 2
fi

case "$repeat_count" in
  ''|*[!0-9]*) echo "--repeat must be a positive integer" >&2; exit 2 ;;
  0) echo "--repeat must be at least 1" >&2; exit 2 ;;
esac
if [ "$execution_mode" = diagnostic ]; then
  android_acceptance_validate_diagnostic_request \
    "$diagnostic_device" "$diagnostic_case" \
    "$flavor_selector_count" "$selected_flavor_value" \
    "$repeat_count" "$skip_build" "$smoke_only" \
    "$keep_emulator" "$keep_fixture" "$result_matrix" \
    "${reserved_device_serials[@]}" || exit $?
fi
acceptance_timeout_seconds=$((900 + repeat_count * 900))

if [ -n "$selected_targets" ]; then
  targets="$(printf '%s\n' "$selected_targets" | tr ',' ' ')"
fi
for target in $targets; do
  case "$target" in github|play|solana_dapp|fdroid) ;; *) echo "unknown flavor: $target" >&2; exit 2 ;; esac
done
build_targets="$targets"
if [ "$smoke_only" -ne 1 ]; then
  case " $targets " in
    *" play "*|*" solana_dapp "*)
      case " $targets " in
        *" github "*) ;;
        *) build_targets="github $targets" ;;
      esac
      ;;
  esac
fi

die() { echo "[android acceptance] ERROR: $*" >&2; exit 1; }
network_test_gate="$root/tests/network-intensive-suite-lock.sh"
if [ ! -x "$network_test_gate" ]; then
  echo "Android acceptance suite gate is missing or not executable: $network_test_gate" >&2
  exit 127
fi
if [ "${URNETWORK_NETWORK_TEST_LOCK_HELD:-}" != 1 ]; then
  exec "$network_test_gate" main-acceptance android-acceptance -- \
    "$here/test-main.sh" "$@"
fi
if ! "$network_test_gate" --verify-held main-acceptance; then
  echo "Android acceptance inherited an invalid network-intensive lock" >&2
  exit 70
fi

command -v timeout >/dev/null 2>&1 || die "GNU timeout is required (brew install coreutils)"
android_acceptance_timeout_executable="$(type -P timeout)"
case "$android_acceptance_timeout_executable" in
  /*) [ -x "$android_acceptance_timeout_executable" ] || die "GNU timeout is not executable" ;;
  *) die "GNU timeout did not resolve to an absolute executable" ;;
esac
timeout() { run_android_acceptance_timeout "$android_acceptance_timeout_executable" "$@"; }
node "$root/build/all/acceptance/preflight-main.mjs" || exit 1
[ -f "$vault" ] || die "no acceptance vault at $vault"
config_reader="$root/tests/read-tests-config.sh"
[ -x "$config_reader" ] || die "test config reader is missing: $config_reader"
UR_ACCEPT_VAULT="$vault" "$config_reader" --ready validate
android_unlock_code="$(UR_ACCEPT_VAULT="$vault" "$config_reader" get android.unlock_code)"
gradle_worker_args=()
if android_parallelism="$(android_acceptance_positive_parallelism "${GOMAXPROCS:-}")"; then
  gradle_worker_args=(--max-workers "$android_parallelism")
fi
acc_user=""
acc_pass=""
if [ "$smoke_only" -ne 1 ]; then
  acc_user="$(UR_ACCEPT_VAULT="$vault" "$config_reader" get data_plane_account.email)"
  acc_pass="$(UR_ACCEPT_VAULT="$vault" "$config_reader" get data_plane_account.password)"
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk_root/platform-tools/adb"
emulator="$sdk_root/emulator/emulator"
avd_name="${UR_ACCEPT_ANDROID_AVD:-urnetwork-acceptance}"
tools_dir="${UR_ACCEPT_ANDROID_TOOLS:-$root/build/all/android/.acceptance-tools}"
case "$tools_dir" in
  /*) ;;
  *) tools_dir="$root/$tools_dir" ;;
esac
[ -x "$adb" ] && [ -x "$emulator" ] || die "Android SDK tools not found under $sdk_root"
available_avds="$("$emulator" -list-avds)" || die "could not list Android virtual devices"
grep -Fxq "$avd_name" <<<"$available_avds" || \
  die "AVD $avd_name is missing; run $root/build/all/android/setup.sh"

timestamp="$(date +%Y%m%d-%H%M%S)"
artifacts="$here/tests/__acceptance__/$timestamp"
mkdir -p "$artifacts" "$(dirname "$fixture")"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/urnetwork-android-acceptance.XXXXXX")"
chmod 700 "$run_dir"
serial=""
emulator_pid=""
started_emulator=0
started_emulator_serial=""
peer_serial=""
peer_emulator_pid=""
provider_session_pid=""
client_session_pid=""
p2p_cleanup_failed=0
fdroid_tree=""
private_staging=""
private_staging_serial=""
credentials=""
captured_device_serials="$run_dir/captured-device-serials"
device_serials="$run_dir/device-serials"
excluded_devices="$artifacts/excluded-devices.tsv"
device_records="$run_dir/device-records"
device_inventory="$artifacts/devices.tsv"
device_capabilities="$run_dir/device-capabilities"
device_plan="$artifacts/device-flavor-plan.tsv"
device_skips="$artifacts/device-flavor-skips.tsv"
device_results="$artifacts/device-flavor-results.tsv"
smoke_results="$artifacts/device-flavor-smoke-results.tsv"
if [ "$execution_mode" = diagnostic ]; then
  device_results="$artifacts/diagnostic-peer-to-peer-results.tsv"
fi
: >"$device_results"
: >"$smoke_results"
chmod 600 "$device_results" "$smoke_results"

sdk_output_dir="$root/sdk/build"
sdk_output_gate="$sdk_output_dir/sdk-android-output-lock.sh"
sdk_build_owner="android-acceptance-$timestamp-$$-$RANDOM"
[ -r "$sdk_output_gate" ] || die "Android SDK output gate is missing: $sdk_output_gate"

pull_fixture() {
  local temporary="$run_dir/guest-secret-key"
  if timeout 30 "$adb" -s "$serial" exec-out run-as com.bringyour.network cat files/acceptance/guest-secret-key >"$temporary" 2>/dev/null; then
    [ "$(wc -w <"$temporary" | tr -d ' ')" = 24 ] || return 1
    chmod 600 "$temporary"
    mv "$temporary" "$fixture"
    chmod 600 "$fixture"
  else
    rm -f "$temporary"
    return 1
  fi
}

pull_active_clients() {
  pull_android_acceptance_active_clients "$adb" "$serial" "$run_dir" "$1"
}

release_active_clients() {
  local directory="$1" active timeout_seconds
  local -a active_clients=()
  [ -n "$credentials" ] || return 1
  while IFS= read -r active; do
    [ -s "$active" ] || continue
    active_clients+=("$active")
  done < <(find "$directory" -type f \
    \( -name 'active-client-id-*' -o -name 'physical-active-client-id' \) \
    -print 2>/dev/null | LC_ALL=C sort)
  [ "${#active_clients[@]}" -gt 0 ] || return 0

  # Each unique client makes two independently bounded API calls. The shared
  # CLI groups aliases before those calls and continues across independent
  # client groups, so give it a finite outer guard without reverting to one
  # process (and one destructive remove) per marker.
  timeout_seconds=$((30 + 60 * ${#active_clients[@]}))
  UR_ACCEPT_CREDENTIALS_FILE="$credentials" \
    timeout "$timeout_seconds" node \
    "$root/build/all/acceptance/client-cleanup.mjs" "${active_clients[@]}"
}

# Invoked through the EXIT/INT/TERM traps installed below.
# shellcheck disable=SC2329
cleanup() {
  exit_status=$?
  local package_label
  local_device_count=0
  local_pair_count=0
  for session_pid in "$provider_session_pid" "$client_session_pid"; do
    [ -n "$session_pid" ] || continue
    if kill -0 "$session_pid" 2>/dev/null; then
      kill -TERM "$session_pid" 2>/dev/null || true
    fi
    wait "$session_pid" 2>/dev/null || true
  done
  if [ -n "$private_staging_serial" ] && [ -n "$private_staging" ]; then
    timeout 15 "$adb" -s "$private_staging_serial" shell rm -f "$private_staging" >/dev/null 2>&1 || true
  fi

  mkdir -p "$artifacts/cleanup-clients"
  if [ -f "$device_records" ]; then
    while IFS=$'\t' read -r device_id target_serial extra <&3; do
      [ -n "$device_id" ] && [ -n "$target_serial" ] && [ -z "${extra:-}" ] || continue
      local_device_count=$((local_device_count + 1))
      serial="$target_serial"
      device_cleanup="$artifacts/cleanup-clients/$device_id"
      state_file="$run_dir/devices/$device_id/animation-scales"
      mkdir -p "$device_cleanup"
      if android_acceptance_adb_device_ready "$adb" "$serial"; then
        if [ "$smoke_only" -ne 1 ]; then
          if android_acceptance_manages_account_fixture "$execution_mode" "$smoke_only" && \
             [ ! -f "$fixture" ]; then
            pull_fixture || true
          fi
          pull_active_clients "$device_cleanup" || exit_status=1
          pull_android_acceptance_private_client_id \
            "$adb" "$serial" com.bringyour.network \
            files/acceptance/physical-active-client-id \
            "$device_cleanup/physical-active-client-id" || exit_status=1
        fi
        for package_name in com.bringyour.network com.bringyour.network.test; do
          package_label="${package_name##*.}"
          if ! android_acceptance_uninstall_package \
              "$android_acceptance_timeout_executable" "$adb" "$serial" "$package_name" \
              "$device_cleanup/uninstall-$package_label.log"; then
            echo "[android acceptance] could not verify removal of $package_name from $serial" >&2
            exit_status=1
          fi
        done
        if [ -d "$state_file" ] && \
           ! android_acceptance_restore_animations "$adb" "$serial" "$state_file"; then
          echo "[android acceptance] could not restore Android animation scales on $serial" >&2
          exit_status=1
        fi
        if ! android_acceptance_restore_network \
            "$adb" "$serial" "$run_dir/devices/$device_id/network-state"; then
          echo "[android acceptance] could not restore Android network state on $serial" >&2
          exit_status=1
        fi
      else
        echo "[android acceptance] selected device $serial is unreachable during cleanup" >&2
        exit_status=1
      fi
    done 3<"$device_records"
  fi

  if [ -n "$peer_serial" ]; then
    peer_cleanup="$artifacts/cleanup-clients/peer"
    mkdir -p "$peer_cleanup"
    if android_acceptance_adb_device_ready "$adb" "$peer_serial"; then
      pull_android_acceptance_active_clients \
        "$adb" "$peer_serial" "$run_dir" "$peer_cleanup" || exit_status=1
      pull_android_acceptance_private_client_id \
        "$adb" "$peer_serial" com.bringyour.network \
        files/acceptance/physical-active-client-id \
        "$peer_cleanup/physical-active-client-id" || exit_status=1
      for package_name in com.bringyour.network com.bringyour.network.test; do
        package_label="${package_name##*.}"
        if ! android_acceptance_uninstall_package \
            "$android_acceptance_timeout_executable" "$adb" "$peer_serial" "$package_name" \
            "$peer_cleanup/uninstall-$package_label.log"; then
          echo "[android acceptance] could not verify removal of $package_name from $peer_serial" >&2
          exit_status=1
        fi
      done
      timeout 15 "$adb" -s "$peer_serial" emu kill >/dev/null 2>&1 || true
    else
      echo "[android acceptance] peer emulator is unreachable during cleanup" >&2
      exit_status=1
    fi
  fi
  if [ -n "$credentials" ] && ! release_active_clients "$artifacts"; then
    echo "[android acceptance] could not release every retained network client" >&2
    exit_status=1
  fi
  if [ -n "$peer_emulator_pid" ]; then
    for _ in $(seq 1 150); do
      kill -0 "$peer_emulator_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$peer_emulator_pid" 2>/dev/null; then
      kill -KILL "$peer_emulator_pid" 2>/dev/null || true
      exit_status=1
    fi
    wait "$peer_emulator_pid" 2>/dev/null || true
  fi
  if [ "$started_emulator" -eq 1 ] && [ "$keep_emulator" -ne 1 ] && [ -n "$emulator_pid" ]; then
    if [ -n "$started_emulator_serial" ]; then
      timeout 15 "$adb" -s "$started_emulator_serial" emu kill >/dev/null 2>&1 || true
    fi
    for _ in $(seq 1 150); do
      kill -0 "$emulator_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$emulator_pid" 2>/dev/null; then
      echo "[android acceptance] emulator did not stop after adb emu kill" >&2
      kill -TERM "$emulator_pid" 2>/dev/null || true
      for _ in $(seq 1 50); do
        kill -0 "$emulator_pid" 2>/dev/null || break
        sleep 0.2
      done
      if kill -0 "$emulator_pid" 2>/dev/null; then
        kill -KILL "$emulator_pid" 2>/dev/null || true
      fi
      exit_status=1
    fi
    wait "$emulator_pid" 2>/dev/null || true
  elif [ -n "$emulator_pid" ] && ! kill -0 "$emulator_pid" 2>/dev/null; then
    wait "$emulator_pid" 2>/dev/null || true
  fi

  if [ -s "$device_plan" ]; then
    local_pair_count="$(wc -l <"$device_plan" | tr -d ' ')"
    if [ "$execution_mode" = diagnostic ]; then
      if ! android_acceptance_verify_diagnostic_result \
          "$device_plan" "$device_results" "$diagnostic_case"; then
        echo "[android acceptance] diagnostic result is incomplete or failed" >&2
        exit_status=1
      fi
    elif [ "$smoke_only" -eq 1 ]; then
      if ! android_acceptance_verify_device_flavor_smoke_results "$device_plan" "$smoke_results"; then
        echo "[android acceptance] device/flavor smoke result matrix is incomplete or failed" >&2
        exit_status=1
      fi
    elif ! android_acceptance_verify_device_flavor_results "$device_plan" "$device_results"; then
      echo "[android acceptance] device/flavor result matrix is incomplete or failed" >&2
      exit_status=1
    fi
  fi
  if ! remove_android_acceptance_run_dir "$run_dir"; then
    echo "[android acceptance] could not remove $run_dir" >&2
    exit_status=1
  fi
  if [ -n "$result_matrix" ]; then
    mkdir -p "$(dirname "$result_matrix")"
    matrix_status=PASS
    matrix_detail="$local_pair_count device/flavor cells completed across $local_device_count eligible Android devices"
    if [ "$exit_status" -ne 0 ]; then
      matrix_status=FAIL
      matrix_detail="Android device/flavor acceptance failed; see $device_results"
    fi
    for matrix_case in email phone instant password data-plane peer-to-peer; do
      printf 'android\t%s\t%s\t%s\n' "$matrix_case" "$matrix_status" "$matrix_detail" >>"$result_matrix"
    done
    chmod 600 "$result_matrix"
  fi
  echo
  if [ "$exit_status" -eq 0 ]; then
    if [ "$execution_mode" = diagnostic ]; then
      echo "[android acceptance] ✓ DIAGNOSTIC PASSED (not final proof; artifacts: $artifacts)"
    elif [ "$smoke_only" -eq 1 ]; then
      echo "[android acceptance] ✓ SMOKE PASSED (artifacts: $artifacts)"
    else
      echo "[android acceptance] ✓ ACCEPTANCE PASSED (artifacts: $artifacts)"
    fi
  else
    if [ "$execution_mode" = diagnostic ]; then
      echo "[android acceptance] ✗ DIAGNOSTIC FAILED (not final proof; artifacts: $artifacts)"
    elif [ "$smoke_only" -eq 1 ]; then
      echo "[android acceptance] ✗ SMOKE FAILED (artifacts: $artifacts)"
    else
      echo "[android acceptance] ✗ ACCEPTANCE FAILED (artifacts: $artifacts)"
    fi
  fi
  exit "$exit_status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

if [ "$skip_build" -ne 1 ]; then
  for tool in gomobile gobind checksec; do
    [ -x "$tools_dir/go-bin/$tool" ] || \
      die "$tool is missing; run $root/build/all/android/setup.sh"
  done
  echo "[android acceptance] building the local Android SDK"
  mkdir -p "$run_dir/go-cache" "$run_dir/go-mod-cache"
  (
    cd "$here/app"
    BRINGYOUR_HOME="$root" WARP_HOME="$root" \
      GOCACHE="$run_dir/go-cache" \
      GOMODCACHE="$run_dir/go-mod-cache" \
      GOPATH="$tools_dir/go-path" \
      GOBIN="$tools_dir/go-bin" \
      PATH="$tools_dir/go-bin:$PATH" \
      URNETWORK_ANDROID_SDK_BUILD_OWNER="$sdk_build_owner" \
      timeout 3600 ./gradlew :app:buildSdkAcceptance "${gradle_worker_args[@]}"
  ) 2>&1 | tee "$artifacts/sdk-build.log"
fi

# Retain descriptor 8 until this runner exits. The canonical suite's outer
# network-intensive lock owns descriptor 9; the two ownership domains must
# remain independently kernel-held. A writer that wins the small interval
# after Gradle's SDK task either remains busy here or leaves a different owner
# sidecar, both of which fail before fleet capture or device mutation.
sdk_previous_directory="$(pwd -P)"
cd "$sdk_output_dir" || die "could not enter Android SDK output directory"
# shellcheck source=/dev/null
source "$sdk_output_gate"
sdk_lock_status=0
sdk_android_output_lock_acquire android-acceptance-consumer || sdk_lock_status=$?
cd "$sdk_previous_directory" || exit 1
[ "$sdk_lock_status" -eq 0 ] || \
  die "Android SDK output is owned by another build (gate status $sdk_lock_status)"
[ -s "$sdk_output_dir/android/URnetworkSdk.aar" ] || \
  die "Android SDK artifact is missing; run without --skip-build"
[ -s "$sdk_output_dir/android/URnetworkSdk-sources.jar" ] || \
  die "Android SDK sources are missing; run without --skip-build"
if [ "$skip_build" -ne 1 ] &&
   ! android_acceptance_verify_sdk_build_owner \
     "$sdk_output_dir/android/.build-owner" "$sdk_build_owner"; then
  die "fresh Android SDK output provenance does not match this acceptance run"
fi

find_avd_serial() {
  local candidate name devices
  devices="$(timeout 15 "$adb" devices)" || return 1
  while read -r candidate state _; do
    case "$candidate" in emulator-*) ;; *) continue ;; esac
    [ "$state" = device ] || continue
    name="$(timeout 10 "$adb" -s "$candidate" emu avd name 2>/dev/null | sed -n '1p' | tr -d '\r')"
    [ "$name" = "$avd_name" ] && { printf '%s\n' "$candidate"; return 0; }
  done <<<"$devices"
  return 1
}

capture_device_fleet() {
  local raw="$run_dir/adb-devices.raw" selected_output="$device_serials"
  if [ "$execution_mode" = diagnostic ]; then
    selected_output="$captured_device_serials"
  fi
  timeout 15 "$adb" devices -l >"$raw" || return 1
  android_acceptance_select_adb_devices \
    "$raw" "$selected_output" "$excluded_devices" "${reserved_device_serials[@]}"
}

# Only the exact fallback child started by this invocation uses the
# credential-free AVD path. Attached hardware and arbitrary pre-existing
# emulators retain the strict private-PIN contract.
runner_owns_fallback_emulator() {
  local target_serial="$1"

  [ "$started_emulator" -eq 1 ] && [ -n "$emulator_pid" ] && \
    [ -n "$started_emulator_serial" ] && \
    [ "$target_serial" = "$started_emulator_serial" ]
}

prepare_selected_device() {
  local target_serial="$1" state_dir="$2" status_file="$3"
  local diagnostic_device_id="$4"
  local diagnostic_file="${status_file%.txt}-interactive.txt"

  if runner_owns_fallback_emulator "$target_serial"; then
    android_acceptance_prepare_owned_emulator \
      "$adb" "$target_serial" "$avd_name" "$emulator_pid" \
      "$state_dir" "$status_file" "$diagnostic_file"
  else
    android_acceptance_prepare_device \
      "$adb" "$target_serial" "$android_unlock_code" "$state_dir" "$status_file" \
      "$diagnostic_file" "$diagnostic_device_id" readiness 180 24 20
  fi
}

selected_device_interactive() {
  local target_serial="$1" diagnostic_device_id="$2" role="$3"
  local diagnostic_file="$4"

  if runner_owns_fallback_emulator "$target_serial"; then
    android_acceptance_runner_owned_emulator_interactive \
      "$adb" "$target_serial" "$avd_name" "$emulator_pid" "$diagnostic_file"
  else
    android_acceptance_unlock_device \
      "$adb" "$target_serial" "$android_unlock_code" "$diagnostic_file" \
      "$diagnostic_device_id" "$role" 20
  fi
}

run_after_selected_device_interactive() {
  local target_serial="$1" diagnostic_device_id="$2" role="$3"
  local interactive_file="$4" preflight_file="$5" renderer_evidence=''
  shift 5

  [ "$#" -gt 0 ] || return 2
  if runner_owns_fallback_emulator "$target_serial"; then
    renderer_evidence="$artifacts/emulator.log"
    android_acceptance_run_after_owned_emulator_interactive \
      "$adb" "$target_serial" "$avd_name" "$emulator_pid" \
      "$interactive_file" run_after_android_preflight \
      "$target_serial" "$diagnostic_device_id" "$role" "$preflight_file" \
      "$renderer_evidence" "$@"
  else
    android_acceptance_run_after_unlock \
      "$adb" "$target_serial" "$android_unlock_code" "$interactive_file" \
      "$diagnostic_device_id" "$role" 20 \
      run_after_android_preflight \
      "$target_serial" "$diagnostic_device_id" "$role" "$preflight_file" \
      "$renderer_evidence" "$@"
  fi
}

# Invoked by the interactive wrappers above as their command argument.
# shellcheck disable=SC2329
run_after_android_preflight() {
  local target_serial="$1" diagnostic_device_id="$2" role="$3"
  local diagnostic_file="$4" renderer_evidence="$5"
  shift 5

  [ "$#" -gt 0 ] || return 2
  if ! android_acceptance_preflight_device \
      "$adb" "$target_serial" "$diagnostic_device_id" "$role" \
      "$diagnostic_file" "$renderer_evidence"; then
    echo "Android preflight rejected $diagnostic_device_id role $role; see $diagnostic_file" >&2
    return 1
  fi
  "$@"
}

capture_device_fleet || die "could not enumerate the attached Android device fleet"
if [ "$execution_mode" = diagnostic ]; then
  android_acceptance_select_diagnostic_device \
    "$captured_device_serials" "$device_serials" "$diagnostic_device" \
    "${reserved_device_serials[@]}" || \
    die "requested diagnostic device is not an eligible member of the captured fleet"
  cp "$captured_device_serials" "$artifacts/diagnostic-captured-device-serials.txt"
  chmod 600 "$artifacts/diagnostic-captured-device-serials.txt"
elif [ ! -s "$device_serials" ]; then
  echo "[android acceptance] no eligible attached device; starting fallback AVD $avd_name"
  emulator_args=(-avd "$avd_name" -read-only -gpu host -no-snapshot -no-boot-anim -netdelay none -netspeed full)
  [ "$headless" -eq 1 ] && emulator_args+=(-no-window)
  run_android_acceptance_shared_avd_emulator \
    "$emulator" "$artifacts/emulator.log" "${emulator_args[@]}" &
  emulator_pid=$!
  started_emulator=1
  for _ in $(seq 1 60); do
    started_emulator_serial="$(find_avd_serial || true)"
    [ -n "$started_emulator_serial" ] && break
    sleep 1
  done
  [ -n "$started_emulator_serial" ] || die "could not find emulator for AVD $avd_name"
  capture_device_fleet || die "could not capture the Android fleet after starting the fallback AVD"
fi
[ -s "$device_serials" ] || die "no eligible Android device is attached"
android_acceptance_write_device_records "$device_serials" "$device_records" || \
  die "could not create the Android device inventory"

printf 'device_id\tserial\tmanufacturer\tbrand\tmodel\tproduct\tdevice\tapi\tandroid_release\tabis\tkind\tplay_services\tsolana_device\n' >"$device_inventory"
: >"$device_capabilities"
while IFS=$'\t' read -r device_id target_serial extra <&3; do
  [ -n "$device_id" ] && [ -n "$target_serial" ] && [ -z "${extra:-}" ] || \
    die "malformed Android device record"
  serial="$target_serial"
  echo "[android acceptance] preparing attached device $serial ($device_id)"
  readiness_diagnostic_id="$(android_acceptance_sanitized_device_id "$device_id")" || \
    die "could not derive a sanitized readiness ID for $device_id"
  device_state_dir="$run_dir/devices/$device_id"
  mkdir -p "$device_state_dir"
  readiness_file="$artifacts/device-readiness/$device_id.txt"
  if ! prepare_selected_device \
      "$serial" "$device_state_dir" "$readiness_file" "$readiness_diagnostic_id"; then
    readiness_status="$(sed -n 's/^status=//p' "$readiness_file" 2>/dev/null || true)"
    die "Android device $serial readiness failed: ${readiness_status:-unknown}; see $readiness_file"
  fi
  case "$serial" in
    emulator-*) device_kind=emulator ;;
    *) device_kind=physical ;;
  esac
  android_acceptance_disable_animations \
    "$adb" "$serial" "$device_state_dir/animation-scales" \
    "$artifacts/device-readiness/${device_id}-animations.txt" || \
    die "could not disable Android animations on $serial"
  manufacturer="$(timeout 15 "$adb" -s "$serial" shell getprop ro.product.manufacturer | tr '\t\r\n' '   ')"
  brand="$(timeout 15 "$adb" -s "$serial" shell getprop ro.product.brand | tr '\t\r\n' '   ')"
  model="$(timeout 15 "$adb" -s "$serial" shell getprop ro.product.model | tr '\t\r\n' '   ')"
  product="$(timeout 15 "$adb" -s "$serial" shell getprop ro.product.name | tr '\t\r\n' '   ')"
  product_device="$(timeout 15 "$adb" -s "$serial" shell getprop ro.product.device | tr '\t\r\n' '   ')"
  api_level="$(timeout 15 "$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\t\r\n')"
  android_release="$(timeout 15 "$adb" -s "$serial" shell getprop ro.build.version.release | tr '\t\r\n' '   ')"
  abi_list="$(timeout 15 "$adb" -s "$serial" shell getprop ro.product.cpu.abilist | tr -d '\t\r\n')"
  play_services=0
  solana_device=0
  android_acceptance_device_has_play_services "$adb" "$serial" && play_services=1
  android_acceptance_is_solana_device \
    "$manufacturer" "$brand" "$model" "$product" "$product_device" && solana_device=1
  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$device_id" "$serial" "$play_services" "$solana_device" "$api_level" >>"$device_capabilities"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$device_id" "$serial" "$manufacturer" "$brand" "$model" "$product" \
    "$product_device" "$api_level" "$android_release" "$abi_list" "$device_kind" \
    "$play_services" "$solana_device" >>"$device_inventory"
done 3<"$device_records"
chmod 600 "$device_inventory" "$device_capabilities" "$excluded_devices"

# The target list is validated above and intentionally expanded into arguments.
# shellcheck disable=SC2086
android_acceptance_write_device_flavor_plan \
  "$device_capabilities" "$device_plan" "$device_skips" $targets || \
  die "could not create the Android device/flavor execution plan"
# shellcheck disable=SC2086
android_acceptance_require_target_coverage "$device_plan" $targets || \
  die "the attached Android fleet cannot cover every requested shipping flavor"
chmod 600 "$device_plan" "$device_skips"

eligible_device_count="$(wc -l <"$device_records" | tr -d ' ')"
planned_cell_count="$(wc -l <"$device_plan" | tr -d ' ')"
if [ "$execution_mode" = diagnostic ] && \
   { [ "$eligible_device_count" != 1 ] || [ "$planned_cell_count" != 1 ]; }; then
  die "diagnostic selection did not resolve to exactly one capability-valid device/flavor cell"
fi
echo "[android acceptance] fleet: $eligible_device_count eligible device(s), $planned_cell_count device/flavor cell(s)"
if [ "$execution_mode" = diagnostic ]; then
  printf '%s\t%s\n' \
    mode diagnostic-only \
    final_proof forbidden \
    device "$diagnostic_device" \
    flavor "$selected_flavor_value" \
    case "$diagnostic_case" \
    build fresh-paired-apks \
    fixture unmanaged \
    >"$artifacts/diagnostic-request.tsv"
  chmod 600 "$artifacts/diagnostic-request.tsv"
  echo "[android acceptance] DIAGNOSTIC ONLY: this selected cell is forbidden as final proof"
fi
if [ -s "$device_skips" ]; then
  echo "[android acceptance] incompatible device/flavor cells were skipped; see $device_skips"
fi
if [ -s "$excluded_devices" ]; then
  echo "[android acceptance] reserved performance devices were excluded; see $excluded_devices"
fi

if [ "$smoke_only" -ne 1 ]; then
  credentials="$run_dir/credentials"
  printf '%s\n%s\n' "$acc_user" "$acc_pass" >"$credentials"
  chmod 600 "$credentials"
  unset acc_user acc_pass
  tests_json="$run_dir/tests.json"
  if [ "$execution_mode" = canonical ]; then
    UR_ACCEPT_VAULT="$vault" "$config_reader" write-json "$tests_json"
    chmod 600 "$tests_json"
  fi
fi

safe_target_name() { printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '_'; }
gradle_flavor() {
  case "$1" in
    github|fdroid) printf 'Github' ;;
    play) printf 'Play' ;;
    solana_dapp) printf 'Solana_dapp' ;;
  esac
}

prepare_fdroid_tree() {
  [ -n "$fdroid_tree" ] && return
  fdroid_tree="$run_dir/android-fdroid"
  mkdir -p "$fdroid_tree"
  timeout 300 rsync -a \
    --exclude '.git' --exclude '.gradle' --exclude 'build' --exclude '__acceptance__' \
    "$here/" "$fdroid_tree/"
  sed -i.acceptance 's|.*/\* *build: *google *\*/.*|/*ungoogled*/|g' \
    "$fdroid_tree/app/app/build.gradle" "$fdroid_tree/app/settings.gradle"
  rm -f \
    "$fdroid_tree/app/app/build.gradle.acceptance" \
    "$fdroid_tree/app/settings.gradle.acceptance"
}

locate_apks() {
  local tree="$1" target="$2" flavor="$3"
  target_apk="$(find "$tree/app/app/build/outputs/apk/$target/debug" -type f -name '*universal*debug.apk' -print | sort | sed -n '1p')"
  [ -n "$target_apk" ] || target_apk="$(find "$tree/app/app/build/outputs/apk/$target/debug" -type f -name '*.apk' -print | sort | sed -n '1p')"
  test_apk="$(find "$tree/app/app/build/outputs/apk/androidTest/$target/debug" -type f -name '*.apk' -print | sort | sed -n '1p')"
  [ -f "$target_apk" ] && [ -f "$test_apk" ] || return 1
}

apk_version() {
  local apk="$1" analyzer aapt
  analyzer="$(find "$sdk_root/cmdline-tools" -type f -path '*/bin/apkanalyzer' 2>/dev/null | sort | tail -1)"
  if [ -x "$analyzer" ]; then
    timeout 60 "$analyzer" manifest version-name "$apk"
    return
  fi
  aapt="$(find "$sdk_root/build-tools" -type f -name aapt 2>/dev/null | sort | tail -1)"
  [ -x "$aapt" ] || return 1
  timeout 60 "$aapt" dump badging "$apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | sed -n '1p'
}

find_apk_analyzer() {
  find "$sdk_root/cmdline-tools" -type f -path '*/bin/apkanalyzer' 2>/dev/null | sort | tail -1
}

install_private_file_on() {
  local target_serial="$1" source="$2" destination="$3" staging="/data/local/tmp/urnetwork-acceptance-$RANDOM" copy_status=0
  case "$destination" in
    credentials|guest-secret-key|tests.json|physical-command|physical-expected-peer-id) ;;
    *) echo "refusing unsafe acceptance destination: $destination" >&2; return 1 ;;
  esac

  # adb push cannot write directly into the app sandbox. Always remove the
  # temporary copy, including when run-as or chmod fails partway through.
  private_staging="$staging"
  private_staging_serial="$target_serial"
  if ! timeout 60 "$adb" -s "$target_serial" push "$source" "$staging" >/dev/null; then
    timeout 15 "$adb" -s "$target_serial" shell rm -f "$staging" >/dev/null 2>&1 || true
    private_staging=""
    private_staging_serial=""
    return 1
  fi
  timeout 30 "$adb" -s "$target_serial" shell run-as com.bringyour.network mkdir -p files/acceptance || copy_status=$?
  if [ "$copy_status" -eq 0 ]; then
    timeout 30 "$adb" -s "$target_serial" shell run-as com.bringyour.network cp "$staging" "files/acceptance/$destination" || copy_status=$?
  fi
  if [ "$copy_status" -eq 0 ]; then
    timeout 30 "$adb" -s "$target_serial" shell run-as com.bringyour.network chmod 600 "files/acceptance/$destination" || copy_status=$?
  fi
  timeout 15 "$adb" -s "$target_serial" shell rm -f "$staging" >/dev/null 2>&1 || true
  private_staging=""
  private_staging_serial=""
  return "$copy_status"
}

install_private_file() {
  install_private_file_on "$serial" "$1" "$2"
}

collect_target_artifacts() {
  local out="$1"
  timeout 30 "$adb" -s "$serial" logcat -d >"$out/logcat.txt" 2>&1 || true
  timeout 30 "$adb" -s "$serial" exec-out screencap -p >"$out/final.png" 2>/dev/null || true
  mkdir -p "$out/ui"
  timeout 30 "$adb" -s "$serial" exec-out run-as com.bringyour.network \
    tar -C files/acceptance -cf - screenshots 2>/dev/null | tar -xf - -C "$out/ui" 2>/dev/null || true
  if ! timeout 30 "$adb" -s "$serial" exec-out run-as com.bringyour.network \
      cat files/acceptance/main-startup-goroutines.txt \
      >"$out/main-startup-goroutines.txt" 2>/dev/null; then
    rm -f "$out/main-startup-goroutines.txt"
  elif ! android_acceptance_has_goroutine_stacks "$out/main-startup-goroutines.txt"; then
    rm -f "$out/main-startup-goroutines.txt"
  fi
  pull_fixture || true
  pull_active_clients "$out"
}

collect_smoke_artifacts() {
  local out="$1"
  timeout 30 "$adb" -s "$serial" logcat -d >"$out/logcat.txt" 2>&1 || true
  timeout 30 "$adb" -s "$serial" exec-out screencap -p >"$out/final.png" 2>/dev/null || true
  timeout 30 "$adb" -s "$serial" exec-out uiautomator dump /dev/tty >"$out/ui.xml" 2>/dev/null || true
}

collect_physical_artifacts() {
  local target_serial="$1" out="$2" artifact_status=0
  mkdir -p "$out/glog" || return 1
  timeout 30 "$adb" -s "$target_serial" logcat -d >"$out/logcat.txt" 2>&1 || \
    artifact_status=1
  timeout 30 "$adb" -s "$target_serial" exec-out screencap -p \
    >"$out/foreground.png" 2>/dev/null || artifact_status=1
  timeout 30 "$adb" -s "$target_serial" shell dumpsys activity activities \
    >"$out/activity.txt" 2>&1 || artifact_status=1
  timeout 30 "$adb" -s "$target_serial" shell ps -A \
    >"$out/processes.txt" 2>&1 || artifact_status=1
  timeout 30 "$adb" -s "$target_serial" exec-out run-as com.bringyour.network \
    cat files/acceptance/physical-status >"$out/status.json" 2>/dev/null || artifact_status=1
  if ! timeout 30 "$adb" -s "$target_serial" exec-out run-as com.bringyour.network \
      cat files/acceptance/physical-startup-goroutines.txt \
      >"$out/physical-startup-goroutines.txt" 2>/dev/null; then
    rm -f "$out/physical-startup-goroutines.txt"
  elif ! android_acceptance_has_goroutine_stacks "$out/physical-startup-goroutines.txt"; then
    rm -f "$out/physical-startup-goroutines.txt"
  fi
  timeout 30 "$adb" -s "$target_serial" exec-out run-as com.bringyour.network \
    tar -C files/logs -cf - . 2>/dev/null | \
    tar -xf - -C "$out/glog" 2>/dev/null || artifact_status=1
  [ -s "$out/foreground.png" ] || artifact_status=1
  [ -s "$out/status.json" ] || artifact_status=1
  android_acceptance_verify_physical_startup_evidence \
    "$out/status.json" "$out/physical-startup-goroutines.txt" || artifact_status=1
  return "$artifact_status"
}

boot_peer_emulator() {
  local devices port=""
  if [ -n "$peer_serial" ] && android_acceptance_adb_device_ready "$adb" "$peer_serial"; then
    return 0
  fi
  devices="$(timeout 15 "$adb" devices)"
  for candidate_port in $(seq 5556 2 5584); do
    if ! grep -q "^emulator-${candidate_port}[[:space:]]" <<<"$devices"; then
      port="$candidate_port"
      break
    fi
  done
  if [ -z "$port" ]; then
    echo "no free Android emulator console port for peer-to-peer acceptance" >&2
    return 1
  fi
  peer_serial="emulator-$port"
  peer_args=(-avd "$avd_name" -read-only -gpu host -port "$port" -no-snapshot -no-boot-anim -netdelay none -netspeed full)
  [ "$headless" -eq 1 ] && peer_args+=(-no-window)
  mkdir -p "$artifacts/peer-emulator"
  run_android_acceptance_shared_avd_emulator \
    "$emulator" "$artifacts/peer-emulator/emulator.log" "${peer_args[@]}" &
  peer_emulator_pid=$!
  if ! android_acceptance_prepare_owned_emulator \
      "$adb" "$peer_serial" "$avd_name" "$peer_emulator_pid" \
      "$run_dir/peer-device" "$artifacts/peer-emulator/readiness.txt" \
      "$artifacts/peer-emulator/interactive.txt"; then
    echo "peer Android emulator did not become ready; see $artifacts/peer-emulator/readiness.txt" >&2
    return 1
  fi
}

wait_physical_status() {
  local target_serial="$1" command_id="$2" state="$3" proof="$4" timeout_seconds="$5"
  local session_pid="${6:-}" status parser_output parser_status
  for _ in $(seq 1 "$timeout_seconds"); do
    status="$(timeout 15 "$adb" -s "$target_serial" exec-out run-as com.bringyour.network \
      cat files/acceptance/physical-status 2>/dev/null | tr -d '\r' || true)"
    if parser_output="$(printf '%s' "$status" | \
      node "$here/scripts/p2p-status.mjs" "$command_id" "$state" "$proof" 2>&1)"; then
      return 0
    else
      parser_status=$?
    fi
    if [ "$parser_status" -eq 3 ]; then
      echo "Android physical session reported $parser_output" >&2
      return 3
    fi
    if [ "$parser_status" -eq 4 ]; then
      p2p_cleanup_failed=1
      echo "Android physical session reported $parser_output" >&2
      return 3
    fi
    if [ "$parser_status" -eq 2 ]; then
      echo "Android physical session wrote an invalid terminal status" >&2
      return 2
    fi
    if ! android_acceptance_session_running "$session_pid"; then
      echo "Android physical instrumentation exited before $command_id reached $state" >&2
      return 4
    fi
    sleep 1
  done
  return 1
}

send_physical_command() {
  local target_serial="$1" record="$2" command_file="$run_dir/physical-command"
  printf '%s\n' "$record" >"$command_file"
  chmod 600 "$command_file"
  install_private_file_on "$target_serial" "$command_file" physical-command
}

pull_physical_client() {
  local target_serial="$1" destination="$2"
  pull_android_acceptance_private_client_id \
    "$adb" "$target_serial" com.bringyour.network \
    files/acceptance/physical-active-client-id "$destination" || return 1
  [ -s "$destination" ]
}

stop_peer_emulator() {
  local peer_cleanup_status=0 package_label
  [ -n "$peer_serial" ] || return 0
  mkdir -p "$artifacts/cleanup-clients/peer"
  for package_name in com.bringyour.network com.bringyour.network.test; do
    package_label="${package_name##*.}"
    android_acceptance_uninstall_package \
      "$android_acceptance_timeout_executable" "$adb" "$peer_serial" "$package_name" \
      "$artifacts/cleanup-clients/peer/uninstall-$package_label.log" || \
      peer_cleanup_status=1
  done
  timeout 15 "$adb" -s "$peer_serial" emu kill >/dev/null 2>&1 || true
  if [ -n "$peer_emulator_pid" ]; then
    for _ in $(seq 1 150); do
      kill -0 "$peer_emulator_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$peer_emulator_pid" 2>/dev/null; then
      kill -KILL "$peer_emulator_pid" 2>/dev/null || true
      return 1
    fi
    wait "$peer_emulator_pid" 2>/dev/null || true
  fi
  peer_serial=""
  peer_emulator_pid=""
  return "$peer_cleanup_status"
}

run_android_peer_to_peer() {
  local out="$1" client_app_apk="$2" client_instrumentation_apk="$3" client_build_id="$4"
  local provider_app_apk="$5" provider_instrumentation_apk="$6" provider_build_id="$7"
  local client_diagnostic_id="$8"
  local provider_id_file="$run_dir/provider-client-id" session_status=0
  local provider_started=0 client_started=0 target_serial iteration app_apk instrumentation_apk install_role
  p2p_cleanup_failed=0
  mkdir -p "$out"
  if [ ! -f "$client_app_apk" ] || [ ! -f "$client_instrumentation_apk" ] || \
     [ ! -f "$provider_app_apk" ] || [ ! -f "$provider_instrumentation_apk" ] || \
     [ -z "$client_build_id" ] || [ -z "$provider_build_id" ]; then
    echo "no locally built Android target is available for peer-to-peer acceptance" >&2
    return 1
  fi
  if ! boot_peer_emulator; then
    return 1
  fi

  for target_serial in "$serial" "$peer_serial"; do
    if [ "$target_serial" = "$serial" ]; then
      app_apk="$client_app_apk"
      instrumentation_apk="$client_instrumentation_apk"
      install_role=client
    else
      app_apk="$provider_app_apk"
      instrumentation_apk="$provider_instrumentation_apk"
      install_role=provider
    fi
    if ! uninstall_acceptance_packages \
        "$target_serial" "$out/$install_role-preinstall-cleanup"; then
      echo "could not remove stale peer-to-peer packages from $target_serial" >&2
      session_status=1
      break
    fi
    if ! android_acceptance_install_cell_apks \
      full "$android_acceptance_timeout_executable" \
      "$adb" "$target_serial" "$app_apk" "$instrumentation_apk" \
      "$out/$install_role-install-app.log" "$out/$install_role-install-test.log"; then
      echo "could not install peer-to-peer APKs on $target_serial" >&2
      session_status=1
      break
    fi
    if ! install_private_file_on "$target_serial" "$credentials" credentials; then
      echo "could not install peer-to-peer credentials on $target_serial" >&2
      session_status=1
      break
    fi
    timeout 30 "$adb" -s "$target_serial" shell pm grant com.bringyour.network android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
    timeout 30 "$adb" -s "$target_serial" shell appops set com.bringyour.network ACTIVATE_VPN allow >/dev/null 2>&1 || true
  done

  if [ "$session_status" -eq 0 ]; then
    if ! android_acceptance_runner_owned_emulator_interactive \
        "$adb" "$peer_serial" "$avd_name" "$peer_emulator_pid" \
        "$out/provider-interactive.txt"; then
      echo "Android peer provider did not reach credential-free interactive state" >&2
      session_status=1
    elif ! android_acceptance_preflight_device \
        "$adb" "$peer_serial" peer-avd provider \
        "$out/provider-preflight.txt" "$artifacts/peer-emulator/emulator.log"; then
      echo "Android peer provider failed its final preflight; see $out/provider-preflight.txt" >&2
      session_status=1
    else
      "$adb" -s "$peer_serial" shell am instrument -w -r \
        -e class com.bringyour.network.acceptance.PhysicalLowbarSessionTest \
        -e acceptanceBuildId "$provider_build_id" \
        com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner \
        >"$out/provider-instrumentation.log" 2>&1 &
      provider_session_pid=$!
      provider_started=1
      if ! wait_physical_status "$peer_serial" 0 ready none 180 "$provider_session_pid"; then
        echo "Android peer provider session did not become ready" >&2
        session_status=1
      fi
    fi
  fi

  if [ "$session_status" -eq 0 ]; then
    if ! selected_device_interactive \
        "$serial" "$client_diagnostic_id" client "$out/client-interactive.txt"; then
      echo "Android peer client did not reach its required interactive state" >&2
      session_status=1
    elif ! android_acceptance_preflight_device \
        "$adb" "$serial" "$client_diagnostic_id" client \
        "$out/client-preflight.txt"; then
      echo "Android peer client failed its final preflight; see $out/client-preflight.txt" >&2
      session_status=1
    else
      "$adb" -s "$serial" shell am instrument -w -r \
        -e class com.bringyour.network.acceptance.PhysicalLowbarSessionTest \
        -e acceptanceBuildId "$client_build_id" \
        com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner \
        >"$out/client-instrumentation.log" 2>&1 &
      client_session_pid=$!
      client_started=1
      if ! wait_physical_status "$serial" 0 ready none 180 "$client_session_pid"; then
        echo "Android peer client session did not become ready" >&2
        session_status=1
      fi
    fi
  fi

  if [ "$session_status" -eq 0 ]; then
    if ! pull_physical_client "$peer_serial" "$provider_id_file" ||
       ! install_private_file_on "$serial" "$provider_id_file" physical-expected-peer-id; then
      echo "Android peer provider returned no usable client ID" >&2
      session_status=1
    fi
  fi

  if [ "$session_status" -eq 0 ]; then
    if ! send_physical_command "$peer_serial" 'provider-start|provide|' ||
       ! wait_physical_status "$peer_serial" provider-start complete none 180 "$provider_session_pid"; then
      echo "Android peer provider did not enter Network provide mode" >&2
      session_status=1
    fi
  fi
  if [ "$session_status" -eq 0 ]; then
    for iteration in $(seq 1 "$repeat_count"); do
      if ! send_physical_command "$serial" "client-connect-${iteration}|peer-connect|h1" ||
         ! wait_physical_status "$serial" "client-connect-${iteration}" complete none 240 "$client_session_pid"; then
        echo "Android client did not connect to the exact peer provider" >&2
        session_status=1
        break
      fi
      if ! send_physical_command "$serial" "client-probe-${iteration}|probe|" ||
         ! wait_physical_status "$serial" "client-probe-${iteration}" complete client 90 "$client_session_pid"; then
        echo "Android client produced no bidirectional peer traffic proof" >&2
        session_status=1
        break
      fi
    done
  fi
  if [ "$session_status" -eq 0 ]; then
    if ! send_physical_command "$peer_serial" 'provider-proof|provider-proof|' ||
       ! wait_physical_status "$peer_serial" provider-proof complete provider 90 "$provider_session_pid"; then
      echo "Android provider produced no bidirectional peer traffic proof" >&2
      session_status=1
    fi
  fi

  if [ "$session_status" -eq 0 ]; then
    collect_physical_artifacts "$serial" "$out/client-before-teardown" || session_status=1
    collect_physical_artifacts "$peer_serial" "$out/provider-before-teardown" || session_status=1
    send_physical_command "$serial" 'client-finish|finish|' || session_status=1
    wait_physical_status "$serial" client-finish complete none 120 "$client_session_pid" || session_status=1
    send_physical_command "$peer_serial" 'provider-finish|finish|' || session_status=1
    wait_physical_status "$peer_serial" provider-finish complete none 120 "$provider_session_pid" || session_status=1
  fi
  if [ "$session_status" -ne 0 ]; then
    collect_physical_artifacts "$serial" "$out/client-before-force-stop" || session_status=1
    collect_physical_artifacts "$peer_serial" "$out/provider-before-force-stop" || session_status=1
    timeout 30 "$adb" -s "$serial" shell am force-stop com.bringyour.network >/dev/null 2>&1 || true
    timeout 30 "$adb" -s "$peer_serial" shell am force-stop com.bringyour.network >/dev/null 2>&1 || true
  fi

  if [ "$client_started" -eq 1 ]; then
    forced_session_stop=0
    for _ in $(seq 1 150); do
      kill -0 "$client_session_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$client_session_pid" 2>/dev/null; then
      forced_session_stop=1
      kill -TERM "$client_session_pid" 2>/dev/null || true
    fi
    if wait "$client_session_pid"; then :; elif [ "$session_status" -eq 0 ]; then session_status=1; fi
    [ "$forced_session_stop" -eq 0 ] || session_status=1
    client_session_pid=""
  fi
  if [ "$provider_started" -eq 1 ]; then
    forced_session_stop=0
    for _ in $(seq 1 150); do
      kill -0 "$provider_session_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$provider_session_pid" 2>/dev/null; then
      forced_session_stop=1
      kill -TERM "$provider_session_pid" 2>/dev/null || true
    fi
    if wait "$provider_session_pid"; then :; elif [ "$session_status" -eq 0 ]; then session_status=1; fi
    [ "$forced_session_stop" -eq 0 ] || session_status=1
    provider_session_pid=""
  fi
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' \
      "$out/client-instrumentation.log" "$out/provider-instrumentation.log" 2>/dev/null; then
    session_status=1
  fi

  pull_android_acceptance_active_clients \
    "$adb" "$serial" "$run_dir" "$out/client-ownership" || {
      session_status=1
      p2p_cleanup_failed=1
    }
  pull_android_acceptance_active_clients \
    "$adb" "$peer_serial" "$run_dir" "$out/provider-ownership" || {
      session_status=1
      p2p_cleanup_failed=1
    }
  pull_android_acceptance_private_client_id \
    "$adb" "$serial" com.bringyour.network files/acceptance/physical-active-client-id \
    "$out/active-client-id-1" || p2p_cleanup_failed=1
  pull_android_acceptance_private_client_id \
    "$adb" "$peer_serial" com.bringyour.network files/acceptance/physical-active-client-id \
    "$out/active-client-id-2" || p2p_cleanup_failed=1
  if ! release_active_clients "$out"; then
    session_status=1
    p2p_cleanup_failed=1
  else
    timeout 30 "$adb" -s "$serial" shell run-as com.bringyour.network \
      rm -f files/acceptance/physical-active-client-id \
      files/acceptance/active-client-ids >/dev/null 2>&1 || true
    timeout 30 "$adb" -s "$peer_serial" shell run-as com.bringyour.network \
      rm -f files/acceptance/physical-active-client-id \
      files/acceptance/active-client-ids >/dev/null 2>&1 || true
  fi
  [ "$session_status" -eq 0 ]
}

record_device_cases() {
  local device_id="$1" target_serial="$2" target="$3" status="$4" detail="$5"
  shift 5
  local matrix_case
  detail="$(printf '%s' "$detail" | tr '\t\r\n' '   ')"
  for matrix_case in "$@"; do
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$device_id" "$target_serial" "$target" "$matrix_case" "$status" "$detail" >>"$device_results"
  done
}

record_smoke_result() {
  local device_id="$1" target_serial="$2" target="$3" status="$4" detail="$5"
  detail="$(printf '%s' "$detail" | tr '\t\r\n' '   ')"
  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$device_id" "$target_serial" "$target" "$status" "$detail" >>"$smoke_results"
}

record_target_failure() {
  local failed_target="$1" detail="$2" device_id target_serial planned_target extra
  while IFS=$'\t' read -r device_id target_serial planned_target extra <&3; do
    [ "$planned_target" = "$failed_target" ] || continue
    if [ "$execution_mode" = diagnostic ]; then
      record_device_cases "$device_id" "$target_serial" "$failed_target" FAIL "$detail" \
        "$diagnostic_case"
    elif [ "$smoke_only" -eq 1 ]; then
      record_smoke_result "$device_id" "$target_serial" "$failed_target" FAIL "$detail"
    else
      record_device_cases "$device_id" "$target_serial" "$failed_target" FAIL "$detail" \
        email phone instant password data-plane peer-to-peer
    fi
  done 3<"$device_plan"
}

uninstall_acceptance_packages() {
  local target_serial="$1" log_dir="$2" uninstall_status=0 package_name package_label
  mkdir -p "$log_dir" || return 2
  for package_name in com.bringyour.network com.bringyour.network.test; do
    package_label="${package_name##*.}"
    if ! android_acceptance_uninstall_package \
        "$android_acceptance_timeout_executable" "$adb" "$target_serial" "$package_name" \
        "$log_dir/uninstall-$package_label.log"; then
      echo "[android acceptance] could not verify removal of $package_name from $target_serial" >&2
      uninstall_status=1
    fi
  done
  return "$uninstall_status"
}

overall=0
unsafe_stop=0
for target in $build_targets; do
  target="$(safe_target_name "$target")"
  build_out="$artifacts/build/$target"
  mkdir -p "$build_out"
  build_id="${timestamp}-${target}"
  tree="$here"
  source_target="$target"
  [ "$target" = fdroid ] && { prepare_fdroid_tree; tree="$fdroid_tree"; source_target=github; }
  flavor="$(gradle_flavor "$target")"
  target_cache="$here/tests/__acceptance__/build/$target"
  sidecar="$target_cache/build-id"
  target_ready=1

  echo
  echo "[android acceptance] ════════ build $target ════════"
  if [ "$skip_build" -ne 1 ]; then
    mkdir -p "$target_cache"
    set +e
    (
      cd "$tree/app"
      BRINGYOUR_HOME="$root" timeout 3600 ./gradlew \
        ":app:assemble${flavor}Debug" \
        ":app:assemble${flavor}DebugAndroidTest" \
        -PurnetworkAcceptanceBuildId="$build_id" \
        "${gradle_worker_args[@]}"
    ) 2>&1 | tee "$build_out/build.log"
    build_status=${PIPESTATUS[0]}
    set -e
    if [ "$build_status" -ne 0 ]; then
      target_ready=0
    elif ! locate_apks "$tree" "$source_target" "$flavor"; then
      echo "could not locate target and test APKs for $target" >&2
      target_ready=0
    elif ! android_acceptance_cache_apks "$target_apk" "$test_apk" "$target_cache"; then
      echo "could not cache target and test APKs for $target" >&2
      target_ready=0
    else
      printf '%s\n' "$build_id" >"$sidecar"
    fi
  else
    if [ ! -f "$sidecar" ]; then
      echo "missing build-id sidecar $sidecar" >&2
      target_ready=0
    else
      build_id="$(tr -d '\r\n' <"$sidecar")"
    fi
  fi
  target_apk="$target_cache/app.apk"
  test_apk="$target_cache/test.apk"
  if [ "$target_ready" -eq 1 ] && { [ ! -f "$target_apk" ] || [ ! -f "$test_apk" ]; }; then
    echo "missing cached target and test APKs for $target" >&2
    target_ready=0
  fi
  if [ "$target_ready" -eq 1 ]; then
    analyzer="$(find_apk_analyzer)"
    if [ ! -x "$analyzer" ]; then
      echo "APK analyzer is unavailable for $target" >&2
      target_ready=0
    elif ! verify_android_acceptance_egress_probe "$analyzer" "$test_apk"; then
      echo "test APK cannot run its second-UID egress probe for $target" >&2
      target_ready=0
    elif ! verify_android_acceptance_legacy_application_linkage \
        "$analyzer" "$target_apk"; then
      echo "app APK cannot link MainApplication on the supported legacy API range for $target" >&2
      target_ready=0
    elif ! verify_android_acceptance_shipping_apk_abis \
        "$analyzer" "$target_apk" "$target"; then
      echo "app APK does not contain the required shipping ABIs for $target" >&2
      target_ready=0
    elif ! verify_android_acceptance_shipping_apk_min_sdk \
        "$analyzer" "$target_apk" "$target"; then
      echo "app APK minimum SDK does not match the shipping contract for $target" >&2
      target_ready=0
    fi
  fi
  if [ "$target_ready" -ne 1 ]; then
    record_target_failure "$target" "build or APK contract failed"
    overall=1
    continue
  fi
  expected_version="$(apk_version "$target_apk")"

  # A support-only GitHub build supplies the unrestricted peer app when a
  # focused Play or Solana run cannot install its client flavor on the generic
  # peer AVD. It is not itself an extra acceptance matrix cell.
  if ! awk -F '\t' -v target="$target" '$3 == target { found = 1 } END { exit !found }' "$device_plan"; then
    continue
  fi

  while IFS=$'\t' read -r device_id target_serial planned_target extra <&3; do
    [ "$planned_target" = "$target" ] || continue
    serial="$target_serial"
    diagnostic_device_id="$(android_acceptance_sanitized_device_id "$device_id")" || \
      die "could not derive a sanitized diagnostic ID for $device_id"
    out="$artifacts/device-runs/$device_id/$target"
    mkdir -p "$out"
    test_status=0
    p2p_status=0
    p2p_cleanup_failed=0
    fixture_missing=0
    client_cleanup_failed=0

    echo
    echo "[android acceptance] ════════ $target on $serial ($device_id) ════════"
    if ! android_acceptance_adb_device_ready "$adb" "$serial"; then
      echo "selected Android device $serial is no longer available" >&2
      if [ "$execution_mode" = diagnostic ]; then
        record_device_cases "$device_id" "$serial" "$target" FAIL "device became unavailable" \
          "$diagnostic_case"
      elif [ "$smoke_only" -eq 1 ]; then
        record_smoke_result "$device_id" "$serial" "$target" FAIL "device became unavailable"
      else
        record_device_cases "$device_id" "$serial" "$target" FAIL "device became unavailable" \
          email phone instant password data-plane peer-to-peer
      fi
      overall=1
      continue
    fi
    if [ "$execution_mode" = diagnostic ]; then
      echo "[android acceptance] peer-to-peer diagnostic: $target on $serial"
      provider_target="$target"
      case "$target" in play|solana_dapp) provider_target=github ;; esac
      provider_cache="$here/tests/__acceptance__/build/$provider_target"
      provider_build_id="$(tr -d '\r\n' <"$provider_cache/build-id" 2>/dev/null || true)"
      if run_android_peer_to_peer \
          "$out/peer-to-peer" "$target_apk" "$test_apk" "$build_id" \
          "$provider_cache/app.apk" "$provider_cache/test.apk" "$provider_build_id" \
          "$diagnostic_device_id"; then
        p2p_status=0
      else
        echo "peer-to-peer diagnostic rejected for $target on $serial" >&2
        p2p_status=1
      fi
      if ! uninstall_acceptance_packages "$serial" "$out/post-diagnostic-cleanup"; then
        echo "could not clean diagnostic packages from $serial" >&2
        p2p_status=1
      fi
      if [ "$p2p_status" -eq 0 ]; then
        record_device_cases "$device_id" "$serial" "$target" PASS \
          "bidirectional peer proof and full cleanup completed" "$diagnostic_case"
      else
        record_device_cases "$device_id" "$serial" "$target" FAIL \
          "peer-to-peer diagnostic or cleanup failed" "$diagnostic_case"
        overall=1
      fi
      continue
    fi
    if ! uninstall_acceptance_packages "$serial" "$out/preinstall-cleanup"; then
      echo "stale acceptance package cleanup failed for $target on $serial" >&2
      if [ "$smoke_only" -eq 1 ]; then
        record_smoke_result "$device_id" "$serial" "$target" FAIL "stale package cleanup failed"
      else
        record_device_cases "$device_id" "$serial" "$target" FAIL "stale package cleanup failed" \
          email phone instant password data-plane peer-to-peer
      fi
      overall=1
      continue
    fi
    install_mode="$(android_acceptance_cell_install_mode "$smoke_only")"
    if ! android_acceptance_install_cell_apks \
        "$install_mode" "$android_acceptance_timeout_executable" \
        "$adb" "$serial" "$target_apk" "$test_apk" \
        "$out/install-app.log" "$out/install-test.log"; then
      echo "install failed for $target on $serial" >&2
      if [ "$smoke_only" -eq 1 ]; then
        record_smoke_result "$device_id" "$serial" "$target" FAIL "APK install failed"
      else
        record_device_cases "$device_id" "$serial" "$target" FAIL "APK install failed" \
          email phone instant password data-plane peer-to-peer
      fi
      overall=1
      if ! uninstall_acceptance_packages "$serial" "$out/install-failure-cleanup"; then
        echo "install failure cleanup could not verify package removal on $serial" >&2
        unsafe_stop=1
      fi
      [ "$unsafe_stop" -eq 0 ] || break
      continue
    fi

    installed_version="$(timeout 30 "$adb" -s "$serial" shell dumpsys package com.bringyour.network | sed -n 's/.*versionName=//p' | sed -n '1p' | tr -d '\r')"
    if [ -z "$expected_version" ] || [ "$installed_version" != "$expected_version" ]; then
      echo "installed version mismatch on $serial: APK=$expected_version installed=$installed_version" >&2
      test_status=1
    fi
    timeout 30 "$adb" -s "$serial" logcat -c || test_status=1

    if [ "$smoke_only" -eq 1 ]; then
      if [ "$test_status" -eq 0 ] && \
         ! run_after_selected_device_interactive \
           "$serial" "$diagnostic_device_id" smoke \
           "$out/smoke-interactive.txt" "$out/smoke-preflight.txt" \
           android_acceptance_launch_smoke \
           "$adb" "$serial" com.bringyour.network "$out/launch.log"; then
        echo "launch smoke failed for $target on $serial" >&2
        test_status=1
      fi
      collect_smoke_artifacts "$out"
      timeout 30 "$adb" -s "$serial" shell am force-stop com.bringyour.network \
        </dev/null >/dev/null 2>&1 || test_status=1
      uninstall_acceptance_packages "$serial" "$out/post-smoke-cleanup" || test_status=1
      if [ "$test_status" -eq 0 ]; then
        record_smoke_result "$device_id" "$serial" "$target" PASS "local APK installed, launched, and remained alive"
        echo "[android acceptance] $target smoke accepted on $serial"
      else
        record_smoke_result "$device_id" "$serial" "$target" FAIL "install, launch, version, or cleanup failed"
        overall=1
      fi
      continue
    fi

    if ! install_private_file "$credentials" credentials ||
       ! install_private_file "$tests_json" tests.json ||
       { [ -f "$fixture" ] && ! install_private_file "$fixture" guest-secret-key; }; then
      echo "could not install private acceptance inputs for $target on $serial" >&2
      test_status=1
    fi
    timeout 30 "$adb" -s "$serial" shell pm grant com.bringyour.network android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
    timeout 30 "$adb" -s "$serial" shell appops set com.bringyour.network ACTIVATE_VPN allow >/dev/null 2>&1 || true

    if [ "$test_status" -eq 0 ]; then
      set +e
      run_after_selected_device_interactive \
        "$serial" "$diagnostic_device_id" instrumentation \
        "$out/instrumentation-interactive.txt" "$out/instrumentation-preflight.txt" \
        timeout "$acceptance_timeout_seconds" \
        "$adb" -s "$serial" shell am instrument -w -r \
          -e class com.bringyour.network.acceptance.EgressProbeRequestTest,com.bringyour.network.acceptance.MainAcceptanceTest \
          -e acceptanceBuildId "$build_id" \
          -e repeat "$repeat_count" \
          com.bringyour.network.test/androidx.test.runner.AndroidJUnitRunner \
          2>&1 | tee "$out/instrumentation.log"
      test_status=${PIPESTATUS[0]}
      set -e
    else
      : >"$out/instrumentation.log"
    fi
    collect_target_artifacts "$out" || test_status=1
    if [ "$test_status" -eq 0 ] && \
       ! android_acceptance_verify_workflow_artifacts "$out" "$repeat_count"; then
      echo "instrumentation did not retain every workflow screenshot for $target on $serial" >&2
      test_status=1
    fi
    if release_active_clients "$out"; then
      timeout 30 "$adb" -s "$serial" shell run-as com.bringyour.network rm -f files/acceptance/active-client-ids >/dev/null 2>&1 || true
    else
      echo "could not release every retained network client for $target on $serial" >&2
      test_status=1
      client_cleanup_failed=1
    fi
    if [ ! -f "$fixture" ]; then
      echo "instrumentation retained no recoverable instant-account fixture for $target on $serial" >&2
      test_status=1
      fixture_missing=1
    fi
    result_text="$(timeout 30 "$adb" -s "$serial" exec-out run-as com.bringyour.network cat files/acceptance/result 2>/dev/null | tr -d '\r' || true)"
    result_build="$(printf '%s\n' "$result_text" | sed -n '1p')"
    result_repeat="$(printf '%s\n' "$result_text" | sed -n '2p')"
    if [ "$result_build" != "$build_id" ] || [ "$result_repeat" != "$repeat_count" ]; then
      echo "instrumentation did not write the expected completion record for $target on $serial" >&2
      test_status=1
    fi
    if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$out/instrumentation.log"; then
      echo "instrumentation reported a failure for $target on $serial" >&2
      test_status=1
    fi

    if [ "$fixture_missing" -eq 0 ] && [ "$client_cleanup_failed" -eq 0 ]; then
      echo "[android acceptance] peer-to-peer: $target on $serial"
      provider_target="$target"
      case "$target" in play|solana_dapp) provider_target=github ;; esac
      provider_cache="$here/tests/__acceptance__/build/$provider_target"
      provider_build_id="$(tr -d '\r\n' <"$provider_cache/build-id" 2>/dev/null || true)"
      if ! run_android_peer_to_peer \
        "$out/peer-to-peer" "$target_apk" "$test_apk" "$build_id" \
        "$provider_cache/app.apk" "$provider_cache/test.apk" "$provider_build_id" \
        "$diagnostic_device_id"; then
        echo "peer-to-peer rejected for $target on $serial" >&2
        p2p_status=1
      fi
      if [ "$p2p_cleanup_failed" -eq 1 ]; then
        echo "peer-to-peer cleanup ownership was not discharged for $target on $serial" >&2
        client_cleanup_failed=1
        p2p_status=1
      fi
    else
      p2p_status=1
    fi
    if ! uninstall_acceptance_packages "$serial" "$out/post-acceptance-cleanup"; then
      echo "could not clean acceptance packages from $serial" >&2
      test_status=1
      p2p_status=1
    fi

    if [ "$test_status" -eq 0 ]; then
      record_device_cases "$device_id" "$serial" "$target" PASS "instrumentation and cleanup completed" \
        email phone instant password data-plane
      echo "[android acceptance] $target accepted on $serial"
    else
      record_device_cases "$device_id" "$serial" "$target" FAIL "instrumentation or cleanup failed" \
        email phone instant password data-plane
      overall=1
    fi
    if [ "$p2p_status" -eq 0 ]; then
      record_device_cases "$device_id" "$serial" "$target" PASS "bidirectional peer proof and cleanup completed" peer-to-peer
    else
      record_device_cases "$device_id" "$serial" "$target" FAIL "peer-to-peer or cleanup failed" peer-to-peer
      overall=1
    fi
    if [ "$fixture_missing" -eq 1 ] || [ "$client_cleanup_failed" -eq 1 ]; then
      echo "[android acceptance] stopping: continuing could leak or replace the shared account fixture" >&2
      unsafe_stop=1
      break
    fi
  done 3<"$device_plan"
  [ "$unsafe_stop" -eq 0 ] || break
done

if [ -n "$peer_serial" ] && ! stop_peer_emulator; then
  echo "[android acceptance] peer emulator cleanup failed" >&2
  overall=1
fi

if android_acceptance_manages_account_fixture "$execution_mode" "$smoke_only" && \
   [ "$overall" -eq 0 ] && [ -f "$fixture" ] && [ "$keep_fixture" -ne 1 ]; then
  if timeout 90 node "$root/build/all/acceptance/fixture.mjs" delete "$fixture"; then
    rm -f "$fixture"
  else
    echo "could not delete instant-account fixture; retained at $fixture" >&2
    overall=1
  fi
fi

exit "$overall"
