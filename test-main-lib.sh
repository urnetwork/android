#!/usr/bin/env bash
# Shared, side-effect-free helpers for the Android acceptance runner.

# GNU timeout normally moves its child into a separate process group. Under an
# interactive runner that group is in the terminal background, so Gradle can
# be stopped by SIGTTIN even after a successful build. Keep every bounded
# Android command in the runner's foreground process group.
run_android_acceptance_timeout() {
  local timeout_bin="$1"
  shift
  "$timeout_bin" --foreground "$@"
}

# Every emulator that opens the same acceptance AVD must use read-only mode.
# Android's emulator rejects a read-only peer when the first instance holds the
# AVD writable, which otherwise prevents the peer-to-peer phase from starting.
run_android_acceptance_shared_avd_emulator() {
  local emulator="$1" log_file="$2" argument
  local read_only=0
  shift 2

  for argument in "$@"; do
    [ "$argument" = -read-only ] && read_only=1
  done
  if [ "$read_only" -ne 1 ]; then
    echo "refusing to start a shared acceptance AVD without -read-only" >&2
    return 2
  fi
  exec "$emulator" "$@" >"$log_file" 2>&1
}

# Copy one build's app/test pair into the runner-owned cache before another
# flavor is built. Gradle may replace its output directory on the next build;
# peer-to-peer acceptance must never retain those ephemeral paths.
android_acceptance_cache_apks() {
  local app_apk="$1" test_apk="$2" cache_dir="$3"

  [ -f "$app_apk" ] && [ -f "$test_apk" ] || return 1
  mkdir -p "$cache_dir" || return 1
  cp "$app_apk" "$cache_dir/app.apk" || return 1
  cp "$test_apk" "$cache_dir/test.apk" || return 1
  [ -s "$cache_dir/app.apk" ] && [ -s "$cache_dir/test.apk" ]
}

# Install the app and its instrumentation package as one fail-fast operation.
# Bash disables errexit for a function invoked as an `if` condition, so every
# command here must propagate failure explicitly or P2P setup can continue
# against an absent package and obscure the first useful error.
android_acceptance_install_apks() {
  local timeout_command="$1" adb="$2" serial="$3" app_apk="$4" test_apk="$5"

  "$timeout_command" 180 "$adb" -s "$serial" install -r -t "$app_apk" >/dev/null || return 1
  "$timeout_command" 180 "$adb" -s "$serial" install -r -t "$test_apk" >/dev/null || return 1
}

# Verifies that the second-UID egress activity can run as the test APK's
# standalone application. Instrumentation dependencies that are also present
# in the target APK are not copied into the test APK, so a Kotlin reference in
# this activity compiles successfully but fails at runtime before the probe can
# make its first request.
verify_android_acceptance_egress_probe() {
  local analyzer="$1" test_apk="$2" bytecode

  if ! bytecode="$(timeout 60 "$analyzer" dex code \
    --class com.bringyour.network.acceptance.EgressProbeActivity "$test_apk")"; then
    echo "could not inspect the standalone Android egress probe" >&2
    return 1
  fi
  if printf '%s\n' "$bytecode" | grep -q 'Lkotlin/'; then
    echo "standalone Android egress probe depends on the unavailable Kotlin runtime" >&2
    return 1
  fi
  return 0
}

# Removes only the private run directory created by test-main.sh. Go module
# downloads are intentionally read-only, so ask Go to clean its cache first
# and make the remaining private tree writable before the final removal.
remove_android_acceptance_run_dir() {
  local run_dir="$1"

  case "$run_dir" in
    /*/urnetwork-android-acceptance.*) ;;
    *)
      echo "refusing to remove non-acceptance directory: $run_dir" >&2
      return 2
      ;;
  esac
  if [ -L "$run_dir" ]; then
    echo "refusing to remove symlinked acceptance directory: $run_dir" >&2
    return 2
  fi
  [ -e "$run_dir" ] || return 0

  if [ -d "$run_dir/go-mod-cache" ] && command -v go >/dev/null 2>&1; then
    GOMODCACHE="$run_dir/go-mod-cache" go clean -modcache >/dev/null 2>&1 || true
  fi
  chmod -R u+w "$run_dir" 2>/dev/null || true
  rm -rf -- "$run_dir"
}

android_acceptance_adb_device_ready() {
  local adb="$1" serial="$2" state

  if ! state="$(timeout 15 "$adb" -s "$serial" get-state 2>/dev/null)"; then
    return 1
  fi
  [ "$(printf '%s' "$state" | tr -d '\r\n')" = device ]
}

# Compose cannot become idle while an app-owned infinite animation advances on
# every frame. Acceptance uses Android's reduced-motion contract so those
# animations stay static, then restores all host settings exactly for a reused
# emulator. An absent setting is represented by Android as "null" and must be
# deleted rather than restored as that literal string.
android_acceptance_disable_animations() {
  local adb="$1" serial="$2" state_file="$3"
  local temporary="${state_file}.tmp.$$" key value

  : >"$temporary" || return 1
  chmod 600 "$temporary" || { rm -f "$temporary"; return 1; }
  for key in \
    window_animation_scale \
    transition_animation_scale \
    animator_duration_scale; do
    if ! value="$(timeout 15 "$adb" -s "$serial" shell settings get global "$key")"; then
      rm -f "$temporary"
      return 1
    fi
    value="$(printf '%s' "$value" | tr -d '\r\n')"
    if ! [[ "$value" =~ ^(null|[0-9]+([.][0-9]+)?|[.][0-9]+)$ ]]; then
      echo "invalid Android animation scale for $key" >&2
      rm -f "$temporary"
      return 1
    fi
    printf '%s=%s\n' "$key" "$value" >>"$temporary" || {
      rm -f "$temporary"
      return 1
    }
  done
  mv "$temporary" "$state_file" || { rm -f "$temporary"; return 1; }

  for key in \
    window_animation_scale \
    transition_animation_scale \
    animator_duration_scale; do
    timeout 15 "$adb" -s "$serial" shell settings put global "$key" 0 || return 1
  done
}

android_acceptance_restore_animations() {
  local adb="$1" serial="$2" state_file="$3"
  local key value seen_window=0 seen_transition=0 seen_animator=0

  [ -f "$state_file" ] || return 0
  while IFS='=' read -r key value; do
    case "$key" in
      window_animation_scale)
        [ "$seen_window" -eq 0 ] || return 1
        seen_window=1
        ;;
      transition_animation_scale)
        [ "$seen_transition" -eq 0 ] || return 1
        seen_transition=1
        ;;
      animator_duration_scale)
        [ "$seen_animator" -eq 0 ] || return 1
        seen_animator=1
        ;;
      *) return 1 ;;
    esac
    if [ "$value" = null ]; then
      # adb may consume stdin even when the remote command does not need it.
      # Without an explicit redirect it drains the remainder of state_file,
      # so the loop restores only its first setting and then reaches EOF.
      timeout 15 "$adb" -s "$serial" shell settings delete global "$key" \
        </dev/null >/dev/null || return 1
    elif [[ "$value" =~ ^([0-9]+([.][0-9]+)?|[.][0-9]+)$ ]]; then
      timeout 15 "$adb" -s "$serial" shell settings put global "$key" "$value" \
        </dev/null || return 1
    else
      return 1
    fi
  done <"$state_file"
  [ "$seen_window" -eq 1 ] && [ "$seen_transition" -eq 1 ] && [ "$seen_animator" -eq 1 ]
}

# Returns 0 when the app-private file exists, 1 when the device is reachable
# and the file does not exist, and 2 when its state cannot be verified.
android_acceptance_private_file_status() {
  local adb="$1" serial="$2" package_name="$3" relative_path="$4"

  if timeout 30 "$adb" -s "$serial" shell run-as "$package_name" \
    test -f "$relative_path" >/dev/null 2>&1; then
    return 0
  fi
  if android_acceptance_adb_device_ready "$adb" "$serial"; then
    return 1
  fi
  return 2
}

# Pulls one private client ID without accepting adb/run-as diagnostics as data.
# Missing packages and files are ordinary cleanup states; an unreachable device
# or malformed existing file remains an error.
pull_android_acceptance_private_client_id() {
  local adb="$1" serial="$2" package_name="$3" relative_path="$4" destination="$5"
  local file_status temporary client_id line_count

  rm -f "$destination"
  if android_acceptance_private_file_status \
    "$adb" "$serial" "$package_name" "$relative_path"; then
    file_status=0
  else
    file_status=$?
  fi
  case "$file_status" in
    0) ;;
    1) return 0 ;;
    *) return 1 ;;
  esac

  mkdir -p "$(dirname "$destination")"
  temporary="$(mktemp "${destination}.tmp.XXXXXX")"
  if ! timeout 30 "$adb" -s "$serial" exec-out run-as "$package_name" \
    cat "$relative_path" >"$temporary" 2>/dev/null; then
    rm -f "$temporary"
    return 1
  fi
  line_count="$(awk 'END { print NR }' "$temporary")"
  IFS= read -r client_id <"$temporary" || true
  client_id="${client_id%$'\r'}"
  case "$line_count:$client_id" in
    1:|1:*[!A-Za-z0-9._-]*)
      rm -f "$temporary"
      return 1
      ;;
    1:*) ;;
    *)
      rm -f "$temporary"
      return 1
      ;;
  esac
  printf '%s\n' "$client_id" >"$temporary"
  chmod 600 "$temporary"
  mv "$temporary" "$destination"
}

# Pulls retained client IDs only when their private file exists. Some adb
# versions print a remote cat error on stdout while returning success; probing
# first prevents that diagnostic from being mistaken for a client ID.
pull_android_acceptance_active_clients() {
  local adb="$1" serial="$2" run_dir="$3" destination="$4"
  local temporary="$run_dir/active-client-ids" file_status client_id index=0

  if android_acceptance_private_file_status \
    "$adb" "$serial" com.bringyour.network files/acceptance/active-client-ids; then
    file_status=0
  else
    file_status=$?
  fi
  case "$file_status" in
    0) ;;
    1) rm -f "$temporary"; return 0 ;;
    *) rm -f "$temporary"; return 1 ;;
  esac

  if ! timeout 30 "$adb" -s "$serial" exec-out run-as com.bringyour.network \
    cat files/acceptance/active-client-ids >"$temporary" 2>/dev/null; then
    rm -f "$temporary"
    return 1
  fi
  mkdir -p "$destination"
  while read -r client_id; do
    [ -n "$client_id" ] || continue
    case "$client_id" in
      *[!A-Za-z0-9._-]*)
        echo "invalid retained client ID from Android" >&2
        rm -f "$temporary"
        return 1
        ;;
    esac
    index=$((index + 1))
    printf '%s\n' "$client_id" >"$destination/active-client-id-$index"
    chmod 600 "$destination/active-client-id-$index"
  done < <(sort -u "$temporary")
  rm -f "$temporary"
}

# Returns 0 when a package is absent, 1 when it is still installed, and 2 when
# the device stopped responding and absence cannot be established. `pm path`
# commonly exits nonzero for an already-absent package; that is success here.
android_acceptance_package_absent() {
  local adb="$1" serial="$2" package_name="$3" package_path status=0

  if package_path="$(timeout 15 "$adb" -s "$serial" shell pm path "$package_name" 2>/dev/null)"; then
    status=0
  else
    status=$?
  fi
  if printf '%s\n' "$package_path" | grep -q '^package:'; then
    return 1
  fi
  if [ "$status" -ne 0 ] && ! android_acceptance_adb_device_ready "$adb" "$serial"; then
    return 2
  fi
  return 0
}
