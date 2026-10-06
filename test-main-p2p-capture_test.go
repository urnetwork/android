package main

import (
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// Execute the production P2P orchestration, collectors and finalizers with
// offline ADB endpoints. Bulk logcat poisons a live transport deterministically;
// it is safe only after the original children have both been joined and the
// selected guest has a positive completion or owned-stop proof.
func TestP2PBulkLogcatFollowsQuiescenceWithoutLosingLiveEvidence(t *testing.T) {
	for _, mode := range []string{"normal", "live-read-failed", "full-read-failed", "missing-terminal", "stop-failed", "post-ownership-lost", "workflow-failed"} {
		t.Run(mode, func(t *testing.T) {
			source, err := os.ReadFile("test-main.sh")
			if err != nil {
				t.Fatal(err)
			}
			here, err := os.Getwd()
			if err != nil {
				t.Fatal(err)
			}
			var script strings.Builder
			script.WriteString("set -uo pipefail\numask 077\nhere=$1\nout=$2\ncapture_fixture=$2\nmode=$3\nsource \"$here/test-main-lib.sh\"\n")
			for _, name := range []string{
				"run_android_peer_to_peer", "collect_physical_adb_read", "collect_physical_artifacts_once", "collect_physical_artifacts",
				"collect_p2p_quiescent_logcat", "record_p2p_failure", "record_p2p_cleanup_failure", "p2p_cleanup_operation", "finish_physical_session",
			} {
				start := strings.Index(string(source), "\n"+name+"() {\n")
				if start < 0 {
					// The original caller still runs and deterministically exposes
					// its live bulk read; do not fail merely on an absent new helper.
					if name == "collect_p2p_quiescent_logcat" {
						continue
					}
					t.Fatalf("missing production function %s", name)
				}
				function := string(source)[start+1:]
				end := strings.Index(function, "\n}\n")
				if end < 0 {
					t.Fatalf("unterminated production function %s", name)
				}
				script.WriteString(function[:end+3])
			}
			// Production shell functions dynamically scope a local `out` too.
			// Fixture state must remain outside each collector's output directory.
			script.WriteString(strings.ReplaceAll(`
fail() { printf '%s\n' "$*" >&2; exit 80; }
run_dir=$out artifacts=$out adb=fake_adb serial=emulator-5554 peer_serial=emulator-5556
execution_mode=canonical diagnostic_owned_avd=0 credentials=unused repeat_count=1
peer_emulator_owner_token=fixture-owner peer_emulator_pid=$$ avd_name=fixture-avd
android_acceptance_timeout_executable=unused ticks=0
mkdir -p "$out/input-glog"
printf 'pre-finish app log\n' >"$out/input-glog/app.log"
touch "$out/app.apk" "$out/test.apk" "$out/live-client" "$out/live-provider"
boot_peer_emulator() { return 0; }
uninstall_acceptance_packages() { return 0; }
android_acceptance_install_cell_apks() { return 0; }
install_private_file_on() { return 0; }
android_acceptance_runner_owned_emulator_interactive() { return 0; }
selected_device_interactive() { return 0; }
android_acceptance_preflight_device() { return 0; }
observe_p2p_owned_guest() { return 0; }
pull_physical_client() { return 0; }
timeout() { [ "$1" = 30 ] || fail 'changed read/stop bound'; shift; "$@"; }
sleep() { [ "$1" = 0.2 ] || fail 'unexpected retry'; ticks=$((ticks+1)); }
authorize_selected_device() {
  case "$1" in emulator-5554|emulator-5556) ;; *) fail 'changed selected device';; esac
  printf 'authorize:%s\n' "$1" >>"$out/events"
  if [ "$mode:$1" = post-ownership-lost:emulator-5554 ] && [ -f "$out/joined-provider" ]; then return 3; fi
}
android_acceptance_session_running() {
  if [ "$1" = "${fixture_client_pid:-}" ]; then [ -f "$out/live-client" ];
  elif [ "$1" = "${fixture_provider_pid:-}" ]; then [ -f "$out/live-provider" ];
  else fail 'changed session PID'; fi
}
wait_physical_status() {
  local role=client iteration
  [ "$1" != "$peer_serial" ] || role=provider
  if [ "$2" = 0 ]; then
    printf -v "fixture_${role}_pid" '%s' "$6"
    for iteration in $(seq 1 100); do
      [ ! -f "$out/ready-$role" ] || return 0
      command sleep 0.01
    done
    fail 'offline instrumentation failed to start'
  fi
  [ "$mode:$2" != workflow-failed:client-probe-1 ]
}
send_physical_command() {
  case "$2" in
    client-finish*) rm -f "$out/live-client"; printf 'finish:client\n' >>"$out/events" ;;
    provider-finish*) rm -f "$out/live-provider"; printf 'finish:provider\n' >>"$out/events" ;;
  esac
}
wait() {
  local status=0 role=client
  [ "$1" != "$fixture_provider_pid" ] || role=provider
  command wait "$1" || status=$?
  printf 'joined:%s\n' "$role" >>"$out/events"
  touch "$out/joined-$role"
  return "$status"
}
cleanup_physical_sessions() {
  [ -f "$out/joined-client" ] && [ -f "$out/joined-provider" ] || fail 'cleanup preceded joins'
  printf 'cleanup\n' >>"$out/events"
  [ "$p2p_client_quiesced:$p2p_provider_quiesced" = 1:1 ]
}
fake_adb() {
  [ "$1" = -s ] || fail 'enumerated/restarted ADB'
  local role selected="$2" quiesced
  case "$selected" in emulator-5554) role=client ;; emulator-5556) role=provider ;; *) fail 'foreign serial' ;; esac
  shift 2
  case "$*" in
    'shell am instrument -w -r '*)
      printf '%s\n' 'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
        'INSTRUMENTATION_STATUS: test=physicalLowbarSession' 'INSTRUMENTATION_STATUS_CODE: 1'
      case "$mode:$role" in missing-terminal:client|stop-failed:client)
        touch "$out/ready-$role"; return 255 ;;
      esac
      printf '%s\n' 'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
        'INSTRUMENTATION_STATUS: test=physicalLowbarSession' 'INSTRUMENTATION_STATUS_CODE: 0' \
        'OK (1 test)' 'INSTRUMENTATION_CODE: -1'
      touch "$out/ready-$role" ;;
    'shell pm grant '*|'shell appops set '*) return 0 ;;
    'logcat -d -t 12000')
      printf 'bulk:%s\n' "$role" >>"$out/events"
      if [ ! -f "$out/joined-client" ] || [ ! -f "$out/joined-provider" ]; then
        printf 'live-bulk:%s\n' "$role" >>"$out/events"
        printf '%024576d' 0
        return 255
      fi
      if [ "$role" = client ]; then quiesced=$p2p_client_quiesced; else quiesced=$p2p_provider_quiesced; fi
      [ "$quiesced" = 1 ] || fail 'bulk read without guest quiescence'
      [ "$(tail -2 "$out/events" | head -1)" = "authorize:$selected" ] || fail 'bulk read omitted fresh ownership'
      if [ "$mode:$role" = full-read-failed:client ]; then printf '%0606208d' 0; return 255; fi
      # More than a small diagnostic tail: preserve the complete original read.
      printf '%0606208d' 0; printf '\nfull-log-end:%s\n' "$role" ;;
    'exec-out screencap -p')
      printf 'live-state:%s\n' "$role" >>"$out/events"
      [ ! -f "$out/joined-provider" ] || fail 'live evidence was collected after finish'
      if [ "$mode:$role" = live-read-failed:client ]; then printf 'partial'; return 255; fi
      printf '\211PNG\015\012\032\012pre-finish\n' ;;
    'shell dumpsys activity activities') printf 'pre-finish activity\n' ;;
    'shell ps -A') printf 'pre-finish processes\n' ;;
    'exec-out run-as com.bringyour.network cat files/acceptance/physical-status')
      printf '{"phase":"probe","state":"complete","commandId":"probe","extra":{}}\n' ;;
    'exec-out run-as com.bringyour.network cat files/acceptance/physical-startup-goroutines.txt') return 1 ;;
    'exec-out run-as com.bringyour.network tar -C files/logs -cf - .') command tar -C "$out/input-glog" -cf - . ;;
    'exec-out run-as com.bringyour.network cat files/acceptance/physical-memory.ndjson') printf '{"phase":"pre-finish"}\n' ;;
    'exec-out run-as com.bringyour.network cat files/acceptance/physical-diagnostics.ndjson') printf '{"phase":"pre-finish"}\n' ;;
    'shell am force-stop com.bringyour.network')
      [ "$role:$ticks" = client:150 ] || fail 'stop bypassed bounded grace'
      printf 'stop:client\n' >>"$out/events"
      [ "$mode" != stop-failed ] || return 7 ;;
    *) fail "unexpected fake command: $*" ;;
  esac
}
result=0
run_android_peer_to_peer "$out" "$out/app.apk" "$out/test.apk" client-build \
  "$out/app.apk" "$out/test.apk" provider-build device-001 || result=$?
printf '%s\n' "$result" >"$out/result"
`, "$out", "$capture_fixture"))
			dir := t.TempDir()
			ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
			defer cancel()
			command := exec.CommandContext(ctx, "bash", "--noprofile", "--norc", "-c", script.String(), "fixture", here, dir, mode)
			command.WaitDelay = time.Second
			output, err := command.CombinedOutput()
			if err != nil {
				t.Fatalf("offline fixture failed: %v: %s", err, output)
			}
			read := func(name string) string {
				t.Helper()
				value, err := os.ReadFile(filepath.Join(dir, name))
				if err != nil {
					t.Fatalf("missing %s: %v", name, err)
				}
				return string(value)
			}
			events := read("events")
			if strings.Contains(events, "live-bulk:") {
				t.Fatalf("full logcat poisoned a live instrumentation transport: %s", events)
			}
			wantResult := "1\n"
			if mode == "normal" {
				wantResult = "0\n"
			}
			if result := read("result"); result != wantResult {
				t.Fatalf("wrong cell result %q, want %q; events=%s; output=%s", result, wantResult, events, output)
			}
			for _, role := range []string{"client", "provider"} {
				before := role + "-before-teardown/"
				if value := read(before + "logcat-status.txt"); value != "deferred-until-quiescence\n" {
					t.Fatalf("missing explicit deferred-log provenance: %q", value)
				}
				if _, err := os.Stat(filepath.Join(dir, before, "logcat.txt")); !os.IsNotExist(err) {
					t.Fatal("live phase created a misleading full system-log artifact")
				}
				if !(mode == "live-read-failed" && role == "client") {
					for _, name := range []string{"foreground.png", "activity.txt", "processes.txt", "physical-memory.ndjson", "physical-diagnostics.ndjson", "glog/app.log"} {
						if !strings.Contains(read(before+name), "pre-finish") {
							t.Fatalf("lost original live-state evidence %s%s", before, name)
						}
					}
					if !strings.Contains(read(before+"status.json"), `"phase":"probe"`) {
						t.Fatal("post-finish status replaced live app state")
					}
				}
				after := role + "-after-quiescence/"
				if role == "client" && (mode == "stop-failed" || mode == "post-ownership-lost") {
					if strings.Contains(events, "bulk:client") {
						t.Fatal("unproven/foreign guest was read")
					}
					continue
				}
				log := read(after + "logcat.txt")
				if role == "client" && mode == "full-read-failed" {
					if len(log) != 606208 || read(after+"collection-failed-command.tsv") != "logcat\t255\n" || read(after+"collection-attempts.tsv") != "1\t255\n" {
						t.Fatal("post-quiescence failed read lost partial bytes or exact status")
					}
				} else if len(log) <= 606208 || !strings.HasSuffix(log, "full-log-end:"+role+"\n") {
					t.Fatal("post-quiescence log was truncated or lost")
				}
				if strings.Count(events, "bulk:"+role+"\n") != 1 {
					t.Fatal("bulk log was retried")
				}
			}
			if mode != "normal" {
				var first struct{ Role, Reason string }
				if err := json.Unmarshal([]byte(read("p2p-first-failure.json")), &first); err != nil {
					t.Fatal(err)
				}
				wantReason := "artifact-collection"
				if mode == "missing-terminal" || mode == "stop-failed" {
					wantReason = "natural-exit-timeout"
					if strings.Contains(read("client-instrumentation.log"), "OK (1 test)") || !strings.Contains(read("client-instrumentation-exit.json"), `"waitExitCode":255`) {
						t.Fatal("later capture repaired missing terminal/255")
					}
				} else if mode == "workflow-failed" {
					wantReason = "workflow-failed"
				}
				if first.Reason != wantReason {
					t.Fatalf("changed first failure %q, want %q", first.Reason, wantReason)
				}
			}
		})
	}
}
