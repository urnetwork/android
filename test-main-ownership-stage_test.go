package main

import (
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"
)

func ownershipStageFunction(t *testing.T, source, name string) string {
	t.Helper()
	marker := "\n" + name + "() {\n"
	if strings.Count(source, marker) != 1 {
		t.Fatalf("expected exactly one function %s", name)
	}
	function := source[strings.Index(source, marker)+1:]
	end := strings.Index(function, "\n}\n")
	if end < 0 {
		t.Fatalf("unterminated function %s", name)
	}
	return function[:end+3] + "\n"
}

// Real guard/observer definitions; all process, ADB, clock and guest operations
// are fixed local stubs. No runner startup, device, SDK, route or hosted call.
func ownershipStageFixture(t *testing.T, scenario, body string) string {
	t.Helper()
	library, err := os.ReadFile("test-main-lib.sh")
	if err != nil {
		t.Fatal(err)
	}
	runner, err := os.ReadFile("test-main.sh")
	if err != nil {
		t.Fatal(err)
	}
	var script strings.Builder
	script.WriteString("set -uo pipefail\numask 077\nout=$1\nfixture_root=$1\nscenario=$2\nguest_helper=$3\nunset URNETWORK_ANDROID_P2P_RECOVERY_OBSERVATION\n")
	for _, name := range []string{"android_acceptance_runner_owns_emulator", "android_acceptance_adb_device_ready"} {
		script.WriteString(ownershipStageFunction(t, string(library), name))
	}
	for _, name := range []string{"observe_p2p_owned_guest", "authorize_selected_device", "runner_owns_peer_emulator", "record_p2p_failure", "record_p2p_cleanup_failure", "p2p_cleanup_operation", "finish_physical_session"} {
		script.WriteString(ownershipStageFunction(t, string(runner), name))
	}
	script.WriteString(`
adb=fake_adb
here=/fixture-helper
kill_calls=0
kill() {
  case "$1:$2" in -0:4242|-0:4243|-0:4244) ;; *) return 97 ;; esac
  kill_calls=$((kill_calls + 1))
  if [ "$((kill_calls % 2))" -eq 1 ]; then printf 'process-before\n' >>"$fixture_root/calls"
  else printf 'process-after\n' >>"$fixture_root/calls"; fi
  if [ "$scenario:$kill_calls" = process-after-other:2 ]; then return 7; fi
  [ "$scenario:$kill_calls" != process-before:1 ] && [ "$scenario:$kill_calls" != process-after:2 ]
}
timeout() {
  if [ "$1" = -k ]; then [ "$2:$3" = 1:10 ] || return 96; shift 2; fi
  case "$1" in 10|15) ;; *) return 96 ;; esac
  shift
  "$@"
}
fake_adb() {
  local selected="$2"
  case "$1:$selected" in -s:emulator-5554|-s:emulator-5556) ;; *) return 95 ;; esac
  shift 2
  case "$*" in
    get-state)
      printf 'get-state\n' >>"$fixture_root/calls"
      if [ "$scenario" = ready-exit ]; then printf 'private-error-canary\n' >&2; return 255; fi
      if [ "$scenario" = ready-state ]; then printf 'private-state-canary\n'; else printf 'device\r\n'; fi ;;
    'emu avd id')
      printf 'instance-id\n' >>"$fixture_root/calls"
      case "$scenario" in
        id-read) printf 'private-error-canary\n' >&2; return 255 ;;
        id-empty) printf '\nOK\n' ;;
        id-mismatch) printf 'private-owner-canary\nOK\n' ;;
        *)
          if [ "$selected" = emulator-5556 ]; then printf '%s\r\nOK\r\n' "${actual_provider_token:-provider-owner}"
          else printf '%s\r\nOK\r\n' "${actual_client_token:-fixture-owner}"; fi ;;
      esac ;;
    'emu avd name')
      printf 'avd-name\n' >>"$fixture_root/calls"
      case "$scenario" in
        avd-read) printf 'private-error-canary\n' >&2; return 255 ;;
        avd-empty) printf '\nOK\n' ;;
        avd-mismatch) printf 'private-avd-canary\nOK\n' ;;
        *) printf '%s\r\nOK\r\n' "${fixture_guest_avd:-fixture-avd}" ;;
      esac ;;
    shell*)
      [ "$#" -eq 2 ] || return 94
      printf 'guest-read\n' >>"$fixture_root/calls"
      if [[ "$scenario" = recovery-* ]]; then
        # Pin the one permitted command, not merely a matching substring.
        expected_guest_command='
    printf "boot_id="; cat /proc/sys/kernel/random/boot_id || exit $?
    printf "uptime="; cat /proc/uptime || exit $?
    printf "adbd_pid="; pidof adbd || exit $?
    printf "os_log_begin\n"
  '
        [ "$2" = "$expected_guest_command" ] || return 93
        printf 'boot_id=00000000-0000-0000-0000-000000000001\nuptime=123.45 345.67\nadbd_pid=531\nos_log_begin\n'
        if [ "$scenario" = recovery-read-failed ]; then printf 'private-error-canary\n' >&2; return 255; fi
        return 0
      fi
      printf 'synthetic safe guest data\n'
      return 0 ;;
    *) return 93 ;;
  esac
}
node() {
  if [ "$1" = -p ]; then printf '2026-01-01T00:00:00.000Z\n'; return 0; fi
  [ "$1" = /fixture-helper/test-main-guest-observation.mjs ] || return 92
  if [ "$scenario" = recovery-capture-failed ]; then cat >/dev/null; return 9; fi
  if [[ "$scenario" = recovery-* ]]; then command node "$guest_helper" "$2"; return "$?"; fi
  cat >/dev/null
}
p2p_observation_out="$out/guest-observation"
p2p_observation_avd=fixture-avd
p2p_observation_client_serial=emulator-5554
p2p_observation_client_pid=4242
p2p_observation_client_token=fixture-owner
p2p_observation_provider_serial=emulator-5556
p2p_observation_provider_pid=4243
p2p_observation_provider_token=provider-owner
execution_mode=diagnostic
diagnostic_owned_avd=1
reserved_device_serials=(physical-reserved-fixture)
canonical_solana_serial=''
diagnostic_device=emulator-5554
started_emulator_serial=emulator-5554
emulator_pid=4242
emulator_owner_token=fixture-owner
avd_name=fixture-avd
peer_serial=emulator-5556
peer_emulator_pid=4243
peer_emulator_owner_token=provider-owner
p2p_cleanup_failed=0
android_acceptance_session_running() { return 1; }
android_acceptance_verify_p2p_instrumentation() { [ "$scenario" != recovery-child-lost ]; }
android_acceptance_wait_for_session_exit() { printf 'natural-exit\n' >>"$fixture_root/calls"; return 0; }
android_acceptance_record_session_exit() { printf 'joined:%s\n' "$4" >>"$fixture_root/calls"; return 0; }
send_physical_command() { printf 'finish-command\n' >>"$fixture_root/calls"; return 0; }
wait_physical_status() { printf 'finish-ack\n' >>"$fixture_root/calls"; return 0; }
wait() {
  printf 'wait\n' >>"$fixture_root/calls"
  [ "$scenario" != recovery-child-lost ] || return 255
  return 0
}
`)
	script.WriteString(body)
	dir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	helper, err := filepath.Abs("test-main-guest-observation.mjs")
	if err != nil {
		t.Fatal(err)
	}
	command := exec.CommandContext(ctx, "bash", "--noprofile", "--norc", "-c", script.String(), "fixture", dir, scenario, helper)
	command.WaitDelay = time.Second
	output, err := command.CombinedOutput()
	if err != nil {
		t.Fatalf("fixture failed: %v; output=%s", err, output)
	}
	if strings.Contains(string(output), "canary") {
		t.Fatal("private guard output escaped to console")
	}
	return dir
}

func ownershipStageRead(t *testing.T, dir, name string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(dir, name))
	if err != nil {
		t.Fatalf("missing %s: %v", name, err)
	}
	return strings.TrimSpace(string(data))
}

func ownershipStageCalls(t *testing.T, dir string) []string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(dir, "calls"))
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		t.Fatal(err)
	}
	return strings.Split(strings.TrimSpace(string(data)), "\n")
}

func TestOwnershipStagePreservesEveryGuardAndCommandOrder(t *testing.T) {
	before := []string{"process-before"}
	ready := append(append([]string{}, before...), "get-state")
	id := append(append([]string{}, ready...), "instance-id")
	avd := append(append([]string{}, id...), "avd-name")
	after := append(append([]string{}, avd...), "process-after")
	for _, tc := range []struct {
		name, args, status, stage string
		calls                     []string
	}{
		{"bad-serial", "physical fixture-avd 4242 fixture-owner", "2", "serial-validation", nil},
		{"bad-avd", "emulator-5554 '' 4242 fixture-owner", "2", "avd-validation", nil},
		{"bad-pid", "emulator-5554 fixture-avd 0 fixture-owner", "2", "pid-validation", nil},
		{"bad-token", "emulator-5554 fixture-avd 4242 'private/token-canary'", "2", "token-validation", nil},
		{"long-token", "emulator-5554 fixture-avd 4242 " + strings.Repeat("x", 93), "2", "token-length", nil},
		{"process-before", "", "1", "process-before", before},
		{"ready-exit", "", "1", "device-ready", ready},
		{"ready-state", "", "1", "device-ready", ready},
		{"id-read", "", "1", "instance-id-read", id},
		{"id-empty", "", "1", "instance-id-empty", id},
		{"id-mismatch", "", "3", "instance-id-match", id},
		{"avd-read", "", "1", "avd-name-read", avd},
		{"avd-empty", "", "1", "avd-name-empty", avd},
		{"avd-mismatch", "", "3", "avd-name-match", avd},
		{"process-after", "", "1", "process-after", after},
		{"process-after-other", "", "7", "process-after", after},
		{"success", "", "0", "verified", after},
	} {
		t.Run(tc.name, func(t *testing.T) {
			args := tc.args
			if args == "" {
				args = "emulator-5554 fixture-avd 4242 fixture-owner"
			}
			dir := ownershipStageFixture(t, tc.name, `
android_acceptance_emulator_ownership_stage=stale-private-canary
status=0
android_acceptance_runner_owns_emulator fake_adb `+args+` || status=$?
printf '%s\n' "$status" >"$out/result-status"
printf '%s\n' "${android_acceptance_emulator_ownership_stage:-missing}" >"$out/result-stage"
`)
			if got := ownershipStageRead(t, dir, "result-status"); got != tc.status {
				t.Fatalf("guard status %s, want %s", got, tc.status)
			}
			if got := ownershipStageCalls(t, dir); !reflect.DeepEqual(got, tc.calls) {
				t.Fatalf("command order/count %v, want %v", got, tc.calls)
			}
			if got := ownershipStageRead(t, dir, "result-stage"); got != tc.stage {
				t.Fatalf("lost exact guard stage: %q, want %q", got, tc.stage)
			}
		})
	}
}

func TestOwnershipStageObservationIsFiniteAndDoesNotReadAfterFailedGuard(t *testing.T) {
	for _, tc := range []struct {
		scenario, stage, status string
		calls                   int
	}{
		{"ready-exit", "device-ready", "1", 2},
		{"id-mismatch", "instance-id-match", "3", 3},
		{"avd-mismatch", "avd-name-match", "3", 4},
		{"process-after-other", "unreported", "7", 5},
		{"success", "verified", "0", 6},
	} {
		t.Run(tc.scenario, func(t *testing.T) {
			dir := ownershipStageFixture(t, tc.scenario, `
status=0
observe_p2p_owned_guest emulator-5554 after-break artifact-read || status=$?
printf '%s\n' "$status" >"$out/observer-status"
# Repeated invocation preserves the first observation, with no additional calls.
observe_p2p_owned_guest emulator-5554 after-break artifact-read || true
`)
			prefix := "guest-observation/client/after-break/"
			if got := ownershipStageRead(t, dir, prefix+"ownership-status.txt"); got != tc.status {
				t.Fatalf("guard status %s, want %s", got, tc.status)
			}
			calls := ownershipStageCalls(t, dir)
			if len(calls) != tc.calls {
				t.Fatalf("calls %v, want exactly %d", calls, tc.calls)
			}
			if tc.status != "0" && strings.Contains(strings.Join(calls, ","), "guest-read") {
				t.Fatal("failed guard reached guest")
			}
			if got := ownershipStageRead(t, dir, prefix+"ownership-stage.txt"); got != tc.stage {
				t.Fatalf("stage %q, want %q", got, tc.stage)
			}
			info, err := os.Stat(filepath.Join(dir, prefix+"ownership-stage.txt"))
			if err != nil || info.Mode().Perm() != 0600 {
				t.Fatal("stage receipt is not private")
			}
		})
	}
}

func TestOwnershipStageObservationRejectsUnreportedOrUntrustedMetadata(t *testing.T) {
	for _, injected := range []string{"", "private-owner-token-and-url-canary", "verified"} {
		t.Run(injected, func(t *testing.T) {
			dir := ownershipStageFixture(t, "success", `
android_acceptance_emulator_ownership_stage=verified
android_acceptance_runner_owns_emulator() {
  `+func() string {
				if injected == "" {
					return ":"
				}
				return "android_acceptance_emulator_ownership_stage=" + injected
			}()+`
  return 1
}
observe_p2p_owned_guest emulator-5554 after-break artifact-read || true
`)
			if got := ownershipStageCalls(t, dir); got != nil {
				t.Fatalf("untrusted failed guard reached commands: %v", got)
			}
			got := ownershipStageRead(t, dir, "guest-observation/client/after-break/ownership-stage.txt")
			if got != "unreported" {
				t.Fatalf("untrusted/stale metadata retained: %q", got)
			}
		})
	}
}

const ownershipRecoverySetup = `
URNETWORK_ANDROID_P2P_RECOVERY_OBSERVATION=1
mkdir -p "$out/guest-observation/client/after-break" "$out/guest-observation/provider/after-break"
for recovery_role in client provider; do
  printf 'ownership-unavailable\n' >"$out/guest-observation/$recovery_role/after-break/observation-status.txt"
  printf '1\n' >"$out/guest-observation/$recovery_role/after-break/ownership-status.txt"
done
record_p2p_failure "$out" client artifact-collection || exit 80
cp "$out/p2p-first-failure.json" "$out/first-cause.saved"
`

func ownershipRecoveryFinish(target, role string) string {
	return `
status=0
finish_physical_session ` + target + ` ` + role + ` 5151 "$out" || status=$?
printf '%s\n' "$status" >"$out/finish-status"
`
}

func ownershipRecoveryPreserved(t *testing.T, dir, status string, guestReads int) {
	t.Helper()
	if got := ownershipStageRead(t, dir, "finish-status"); got != status {
		t.Fatalf("finish status %q, want original %q", got, status)
	}
	if ownershipStageRead(t, dir, "p2p-first-failure.json") != ownershipStageRead(t, dir, "first-cause.saved") {
		t.Fatal("recovery changed the immutable first failure")
	}
	if got := ownershipStageRead(t, dir, "guest-observation/client/after-break/observation-status.txt"); got != "ownership-unavailable" {
		t.Fatalf("recovery overwrote original observation: %s", got)
	}
	calls := ownershipStageCalls(t, dir)
	reads, waits := 0, 0
	for _, call := range calls {
		if call == "guest-read" {
			reads++
		}
		if call == "wait" {
			waits++
		}
	}
	if reads != guestReads || waits == 0 {
		t.Fatalf("guest reads=%d want %d; child joins=%d; calls=%v", reads, guestReads, waits, calls)
	}
}

func TestOwnershipRecoveryUsesExistingFinishProofOnce(t *testing.T) {
	for _, role := range []string{"client", "provider"} {
		t.Run(role, func(t *testing.T) {
			target := "emulator-5554"
			if role == "provider" {
				target = "emulator-5556"
			}
			dir := ownershipStageFixture(t, "recovery-success", ownershipRecoverySetup+ownershipRecoveryFinish(target, role))
			ownershipRecoveryPreserved(t, dir, "0", 1)
			want := []string{"process-before", "get-state", "instance-id", "avd-name", "process-after", "guest-read", "finish-command", "finish-ack", "natural-exit", "wait", "joined:0"}
			if got := ownershipStageCalls(t, dir); !reflect.DeepEqual(got, want) {
				t.Fatalf("recovery added/reordered guard or finish calls: %v", got)
			}
			prefix := "guest-observation/" + role + "/after-recovery/"
			for name, want := range map[string]string{"ownership-status.txt": "0", "ownership-stage.txt": "verified", "ownership-source.txt": "finish-authorization", "observation-status.txt": "captured", "read-status.txt": "0", "capture-status.txt": "0", "trigger.txt": "finish-authorized", "started-at.txt": "2026-01-01T00:00:00.000Z", "ended-at.txt": "2026-01-01T00:00:00.000Z"} {
				if got := ownershipStageRead(t, dir, prefix+name); got != want {
					t.Fatalf("%s=%s, want %s", name, got, want)
				}
			}
			var capture struct {
				Identity  struct{ BootID, UptimeSeconds, AdbdPid string }
				Truncated bool
			}
			if err := json.Unmarshal([]byte(ownershipStageRead(t, dir, prefix+"capture.json")), &capture); err != nil || capture.Identity.BootID != "00000000-0000-0000-0000-000000000001" || capture.Identity.AdbdPid != "531" || capture.Identity.UptimeSeconds != "123.45" || capture.Truncated {
				t.Fatalf("existing safe helper did not retain bounded identity: %+v, %v", capture, err)
			}
			info, err := os.Stat(filepath.Join(dir, prefix+"guest.txt"))
			if err != nil || info.Mode().Perm() != 0600 {
				t.Fatal("guest identity not private")
			}
		})
	}
}

func TestOwnershipRecoveryDefaultOffAndCanonicalNeverRead(t *testing.T) {
	for _, setup := range []string{"unset URNETWORK_ANDROID_P2P_RECOVERY_OBSERVATION", "URNETWORK_ANDROID_P2P_RECOVERY_OBSERVATION=0", "URNETWORK_ANDROID_P2P_RECOVERY_OBSERVATION=true", "execution_mode=canonical", "diagnostic_owned_avd=0"} {
		t.Run(setup, func(t *testing.T) {
			dir := ownershipStageFixture(t, "recovery-success", ownershipRecoverySetup+setup+"\n"+ownershipRecoveryFinish("emulator-5554", "client"))
			ownershipRecoveryPreserved(t, dir, "0", 0)
			if _, err := os.Stat(filepath.Join(dir, "guest-observation/client/after-recovery")); !os.IsNotExist(err) {
				t.Fatal("disabled observation created an attempt")
			}
		})
	}
}

func TestOwnershipRecoveryRefusesUnavailableMismatchAndStaleProof(t *testing.T) {
	for _, tc := range []struct{ name, setup string }{
		{"unavailable", "scenario=ready-exit"},
		{"mismatch", "scenario=id-mismatch"},
		{"stale-proof", "android_acceptance_emulator_ownership_stage=verified; authorize_selected_device() { return 1; }"},
		{"unattested-success", "android_acceptance_emulator_ownership_stage=verified; authorize_selected_device() { return 0; }"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := ownershipStageFixture(t, "recovery-success", ownershipRecoverySetup+tc.setup+"\n"+ownershipRecoveryFinish("emulator-5554", "client"))
			status := "1"
			if tc.name == "unattested-success" {
				status = "0"
			}
			ownershipRecoveryPreserved(t, dir, status, 0)
			if _, err := os.Stat(filepath.Join(dir, "guest-observation/client/after-recovery")); !os.IsNotExist(err) {
				t.Fatal("failed/missing original proof admitted recovery")
			}
		})
	}
}

func TestOwnershipRecoveryRejectsReplacedTupleAndIneligiblePrior(t *testing.T) {
	for _, tc := range []struct{ name, setup string }{
		{"pid", "emulator_pid=4244"},
		{"token", "emulator_owner_token=new-owner; actual_client_token=new-owner"},
		{"avd", "avd_name=new-avd; fixture_guest_avd=new-avd"},
		{"serial", "p2p_observation_client_serial=emulator-5558"},
		{"role", "p2p_observation_client_serial=emulator-5556; p2p_observation_provider_serial=emulator-5554; p2p_observation_provider_pid=4242; p2p_observation_provider_token=fixture-owner"},
		{"prior-mismatch", "printf '3\\n' >\"$out/guest-observation/client/after-break/ownership-status.txt\""},
		{"prior-captured", "printf 'captured\\n' >\"$out/guest-observation/client/after-break/observation-status.txt\""},
		{"prior-missing", "mv \"$out/guest-observation/client/after-break\" \"$out/prior.saved\""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := ownershipStageFixture(t, "recovery-success", ownershipRecoverySetup+tc.setup+"\n"+ownershipRecoveryFinish("emulator-5554", "client"))
			if got := ownershipStageRead(t, dir, "finish-status"); got != "0" {
				t.Fatal("observation refusal disrupted finish")
			}
			for _, call := range ownershipStageCalls(t, dir) {
				if call == "guest-read" {
					t.Fatal("replaced tuple or ineligible prior evidence admitted guest read")
				}
			}
		})
	}
}

func TestOwnershipRecoveryFailureIsOneShotAndDoesNotInterruptFinish(t *testing.T) {
	for _, scenario := range []string{"recovery-success", "recovery-read-failed", "recovery-capture-failed", "recovery-child-lost"} {
		t.Run(scenario, func(t *testing.T) {
			dir := ownershipStageFixture(t, scenario, ownershipRecoverySetup+ownershipRecoveryFinish("emulator-5554", "client")+ownershipRecoveryFinish("emulator-5554", "client"))
			status := "0"
			if scenario == "recovery-child-lost" {
				status = "1"
			}
			ownershipRecoveryPreserved(t, dir, status, 1)
			want := "captured"
			if scenario == "recovery-read-failed" || scenario == "recovery-capture-failed" {
				want = "read-or-capture-failed"
			}
			prefix := "guest-observation/client/after-recovery/"
			if got := ownershipStageRead(t, dir, prefix+"observation-status.txt"); got != want {
				t.Fatalf("recovery status %s want %s", got, want)
			}
			if scenario == "recovery-read-failed" {
				if got := ownershipStageRead(t, dir, prefix+"read-status.txt"); got != "255" {
					t.Fatal("lost original diagnostic read status")
				}
				if strings.Contains(ownershipStageRead(t, dir, prefix+"guest.txt"), "canary") {
					t.Fatal("guest metadata includes raw failure text")
				}
			}
			calls := strings.Join(ownershipStageCalls(t, dir), ",")
			if strings.Count(calls, "finish-command") != 2 || strings.Count(calls, "joined:") != 2 || strings.Count(calls, "get-state") != 2 {
				t.Fatalf("one-shot read changed required finish, join or guard count: %s", calls)
			}
		})
	}
}
