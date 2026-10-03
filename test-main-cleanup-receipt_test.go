package main

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// These Go controls execute only named production shell functions. Runner
// startup, ADB, device/network operations and account credentials are excluded.
func cleanupReceiptFixture(t *testing.T, body string) string {
	t.Helper()
	source, err := os.ReadFile("test-main.sh")
	if err != nil {
		t.Fatal(err)
	}
	var script strings.Builder
	script.WriteString("set -uo pipefail\numask 077\nout=$1\np2p_cleanup_failed=0\n")
	for _, name := range []string{
		"record_p2p_cleanup_failure", "p2p_cleanup_operation", "record_p2p_failure",
		"finish_physical_session", "retain_physical_cleanup_ownership",
		"clear_physical_cleanup_ownership", "cleanup_physical_sessions",
	} {
		start := strings.Index(string(source), "\n"+name+"() {\n")
		if start < 0 {
			// The pristine implementation has no diagnostic recorder. Run the
			// existing operation first, then RED on the absent required receipt.
			if name == "record_p2p_cleanup_failure" || name == "p2p_cleanup_operation" {
				continue
			}
			t.Fatalf("production function %s is absent", name)
		}
		function := string(source)[start+1:]
		end := strings.Index(function, "\n}\n")
		if end < 0 {
			t.Fatalf("production function %s is unterminated", name)
		}
		script.WriteString(function[:end+3])
		script.WriteByte('\n')
	}
	script.WriteString(`
adb=fake_adb
run_dir=$out
# Ledger-only controls start after both guest finalizers completed. The
# transport-loss matrix below replaces these with real finalization evidence.
p2p_client_quiesced=1
p2p_provider_quiesced=1
authorize_selected_device() { return 0; }
pull_android_acceptance_active_clients() { return 0; }
pull_android_acceptance_private_client_id() { return 0; }
release_active_clients() { return 0; }
fake_adb() { return 0; }
timeout() { shift; "$@"; }
android_acceptance_session_running() { return 1; }
android_acceptance_verify_p2p_instrumentation() { return 0; }
android_acceptance_wait_for_session_exit() { return 0; }
android_acceptance_record_session_exit() { return 0; }
send_physical_command() { return 0; }
wait_physical_status() { return 0; }
observe_p2p_owned_guest() { return 0; }
wait() { return 0; }
`)
	script.WriteString(body)
	dir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	command := exec.CommandContext(ctx, "bash", "--noprofile", "--norc", "-c", script.String(), "fixture", dir)
	command.WaitDelay = time.Second
	output, err := command.CombinedOutput()
	if err != nil {
		t.Fatalf("fixture failed: %v; output=%s", err, output)
	}
	return dir
}

type cleanupFailureReceipt struct {
	SchemaVersion int    `json:"schemaVersion"`
	Role          string `json:"role"`
	Operation     string `json:"operation"`
	ExitCode      int    `json:"exitCode"`
}

func cleanupReceipts(t *testing.T, dir string) []cleanupFailureReceipt {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(dir, "p2p-cleanup-failures.ndjson"))
	if err != nil {
		t.Fatalf("missing operation-specific cleanup receipt: %v", err)
	}
	if bytes.Contains(data, []byte("credential-canary")) || bytes.Contains(data, []byte("private-client-canary")) || bytes.Contains(data, []byte(dir)) {
		t.Fatal("cleanup receipt leaked raw value or path")
	}
	var receipts []cleanupFailureReceipt
	for _, line := range bytes.Split(bytes.TrimSpace(data), []byte("\n")) {
		decoder := json.NewDecoder(bytes.NewReader(line))
		decoder.DisallowUnknownFields()
		var value cleanupFailureReceipt
		if err := decoder.Decode(&value); err != nil {
			t.Fatalf("invalid bounded receipt: %v", err)
		}
		if value.SchemaVersion != 1 || value.ExitCode < 1 || value.ExitCode > 255 {
			t.Fatalf("invalid failure receipt: %+v", value)
		}
		receipts = append(receipts, value)
	}
	return receipts
}

func TestP2PCleanupSecondaryFailurePreservesFirstCauseAndPrecedesRelease(t *testing.T) {
	dir := cleanupReceiptFixture(t, `
record_p2p_failure "$out" client artifact-collection || exit 80
cp "$out/p2p-first-failure.json" "$out/first-cause.saved"
printf 'private-client-canary\n' >"$out/active-client-id-fixture"
pull_android_acceptance_active_clients() { [ "$2" != client-fixture ] || return 7; }
release_active_clients() {
  if [ -s "$out/p2p-cleanup-failures.ndjson" ]; then printf 'present\n'; else printf 'absent\n'; fi >"$out/receipt-before-release"
  rm -f "$out/active-client-id-fixture"
}
fake_adb() { printf 'unexpected clear\n' >"$out/unexpected-clear"; return 0; }
result=0
cleanup_physical_sessions "$out" client-fixture provider-fixture || result=$?
[ "$result:$p2p_cleanup_failed" = 1:1 ] || exit 81
`)
	receipts := cleanupReceipts(t, dir)
	if len(receipts) != 1 || receipts[0].Role != "client" || receipts[0].Operation != "retain-active-ledger" || receipts[0].ExitCode != 7 {
		t.Fatalf("wrong retained-ledger failure: %+v", receipts)
	}
	first, err := os.ReadFile(filepath.Join(dir, "p2p-first-failure.json"))
	if err != nil {
		t.Fatal(err)
	}
	saved, err := os.ReadFile(filepath.Join(dir, "first-cause.saved"))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(first, saved) {
		t.Fatal("secondary cleanup overwrote the first artifact failure")
	}
	before, _ := os.ReadFile(filepath.Join(dir, "receipt-before-release"))
	if string(before) != "present\n" {
		t.Fatal("cleanup failure was not retained before API aliases were removed")
	}
	if _, err := os.Stat(filepath.Join(dir, "unexpected-clear")); !os.IsNotExist(err) {
		t.Fatal("failed retention permitted device marker deletion")
	}
}

func TestP2PCleanupFailureOperationsStayDistinct(t *testing.T) {
	for _, test := range []struct {
		operation, command, setup string
		status                    int
	}{
		{"retain-authorization", `retain_physical_cleanup_ownership client-fixture client "$out" 1`, `authorize_selected_device() { return 3; }`, 3},
		{"retain-active-ledger", `retain_physical_cleanup_ownership client-fixture client "$out" 1`, `pull_android_acceptance_active_clients() { return 7; }`, 7},
		{"retain-reauthorization", `retain_physical_cleanup_ownership client-fixture client "$out" 1`, `checks=0; authorize_selected_device() { checks=$((checks+1)); [ "$checks" = 1 ] || return 3; }`, 3},
		{"retain-client-id", `retain_physical_cleanup_ownership client-fixture client "$out" 1`, `pull_android_acceptance_private_client_id() { return 8; }`, 8},
		{"release-clients", `cleanup_physical_sessions "$out" client-fixture provider-fixture`, `release_active_clients() { return 9; }`, 9},
		{"clear-authorization", `clear_physical_cleanup_ownership client-fixture "$out" client`, `authorize_selected_device() { return 3; }`, 3},
		{"clear-markers", `clear_physical_cleanup_ownership client-fixture "$out" client`, `fake_adb() { return 10; }`, 10},
		{"finish-authorization", `finish_physical_session client-fixture client 424242 "$out"`, `authorize_selected_device() { return 3; }`, 3},
		{"finish-command", `finish_physical_session client-fixture client 424242 "$out"`, `send_physical_command() { return 11; }`, 11},
		{"finish-ack", `finish_physical_session client-fixture client 424242 "$out"`, `wait_physical_status() { return 12; }`, 12},
		{"finish-child-exit", `finish_physical_session client-fixture client 424242 "$out"`, `wait() { return 255; }`, 255},
		{"finish-receipt", `finish_physical_session client-fixture client 424242 "$out"`, `android_acceptance_record_session_exit() { return 13; }`, 13},
	} {
		t.Run(test.operation, func(t *testing.T) {
			dir := cleanupReceiptFixture(t, test.setup+"\nresult=0\n"+test.command+" || result=$?\n[ \"$result\" -ne 0 ] || exit 80\n")
			receipts := cleanupReceipts(t, dir)
			if len(receipts) != 1 || receipts[0].Operation != test.operation || receipts[0].ExitCode != test.status {
				t.Fatalf("wrong operation receipt: %+v", receipts)
			}
		})
	}
}

func TestP2PCleanupSuccessDoesNotInventFailure(t *testing.T) {
	dir := cleanupReceiptFixture(t, `
finish_physical_session client-fixture client 424242 "$out" || exit 80
cleanup_physical_sessions "$out" client-fixture provider-fixture || exit 81
[ "$p2p_cleanup_failed" = 0 ] || exit 82
`)
	for _, name := range []string{"p2p-first-failure.json", "p2p-cleanup-failures.ndjson", "p2p-cleanup-failures.truncated"} {
		if _, err := os.Stat(filepath.Join(dir, name)); !os.IsNotExist(err) {
			t.Fatalf("success invented failure artifact %s", name)
		}
	}
}

// A disconnected host shell is not a stopped guest. Use the production grace
// loop and exact emulator ownership guard, with only virtual time/processes and
// fixed ADB replies. No emulator, device, account or network is contacted.
func TestP2PCleanupRequiresGuestQuiescenceAfterTransportLoss(t *testing.T) {
	var definitions strings.Builder
	for sourcePath, names := range map[string][]string{
		"test-main-lib.sh": {
			"android_acceptance_session_running", "android_acceptance_verify_p2p_instrumentation",
			"android_acceptance_wait_for_session_exit", "android_acceptance_record_session_exit",
			"android_acceptance_runner_owns_emulator", "android_acceptance_adb_device_ready",
		},
		"test-main.sh": {"authorize_selected_device", "runner_owns_peer_emulator"},
	} {
		source, err := os.ReadFile(sourcePath)
		if err != nil {
			t.Fatal(err)
		}
		for _, name := range names {
			start := strings.Index(string(source), "\n"+name+"() {\n")
			if start < 0 {
				t.Fatalf("missing production function %s", name)
			}
			function := string(source)[start+1:]
			end := strings.Index(function, "\n}\n")
			if end < 0 {
				t.Fatalf("unterminated production function %s", name)
			}
			definitions.WriteString(function[:end+3] + "\n")
		}
	}
	for _, mode := range []string{
		"recovered", "unavailable", "token-mismatch", "avd-mismatch", "owner-exited",
		"stop-failed", "finish-acked-stream-lost", "finish-ack-failed", "normal", "live-hung",
	} {
		t.Run(mode, func(t *testing.T) {
			dir := cleanupReceiptFixture(t, definitions.String()+"\nmode="+mode+"\n"+`
ticks=0
host_alive=0
app_alive=1
forced=0
released=0
cleared=0
joined=0
child_code=255
p2p_client_quiesced=0
# The independent provider has already positively completed and joined.
p2p_provider_quiesced=1
reserved_device_serials=(reserved-phone)
execution_mode=canonical
canonical_solana_serial=''
started_emulator_serial=emulator-5554
emulator_pid=4242
emulator_owner_token=client-owner
avd_name=fixture-avd
peer_serial=emulator-5556
peer_emulator_pid=4243
peer_emulator_owner_token=provider-owner
success_transcript() {
  printf '%s\n' \
    'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
    'INSTRUMENTATION_STATUS: test=physicalLowbarSession' \
    'INSTRUMENTATION_STATUS_CODE: 1' \
    'INSTRUMENTATION_STATUS: class=com.bringyour.network.acceptance.PhysicalLowbarSessionTest' \
    'INSTRUMENTATION_STATUS: test=physicalLowbarSession' \
    'INSTRUMENTATION_STATUS_CODE: 0' 'OK (1 test)' 'INSTRUMENTATION_CODE: -1'
}
success_transcript >"$out/provider-instrumentation.log"
success_transcript | head -3 >"$out/client-instrumentation.log"
case "$mode" in normal|live-hung) host_alive=1; child_code=0 ;; esac
if [ "$mode" != normal ]; then
  record_p2p_failure "$out" client artifact-collection || exit 80
  cp "$out/p2p-first-failure.json" "$out/first-cause.saved"
fi
kill() {
  case "$1:$2" in
    -0:5151) [ "$host_alive" = 1 ] ;;
    -0:4242) [ "$mode" != owner-exited ] || [ "$ticks" = 0 ] ;;
    -0:4243) return 0 ;;
    *) printf 'unexpected process action\n' >&2; return 90 ;;
  esac
}
sleep() {
  [ "$1" = 0.2 ] || return 91
  ticks=$((ticks+1))
  if [ "$mode:$ticks" = normal:3 ]; then
    host_alive=0; app_alive=0
    success_transcript >"$out/client-instrumentation.log"
  fi
}
timeout() {
  case "$1" in 15|30) ;; *) return 92 ;; esac
  shift
  "$@"
}
fake_adb() {
  [ "$1" = -s ] || return 93
  local selected="$2"
  case "$selected" in emulator-5554|emulator-5556) ;; *) return 94 ;; esac
  shift 2
  printf '%s\t%s\n' "$selected" "$*" >>"$out/adb-calls"
  case "$*" in
    get-state)
      if [ "$selected" = emulator-5554 ]; then
        case "$mode" in
          unavailable) return 255 ;;
          recovered|token-mismatch|avd-mismatch|owner-exited|stop-failed)
            [ "$ticks" -gt 0 ] || return 255 ;;
        esac
      fi
      printf 'device\n' ;;
    'emu avd id')
      if [ "$selected" = emulator-5556 ]; then printf 'provider-owner\nOK\n'
      elif [ "$mode" = token-mismatch ]; then printf 'replacement-owner\nOK\n'
      else printf 'client-owner\nOK\n'; fi ;;
    'emu avd name')
      if [ "$selected:$mode" = emulator-5554:avd-mismatch ]; then printf 'replacement-avd\nOK\n'
      else printf 'fixture-avd\nOK\n'; fi ;;
    'shell am force-stop com.bringyour.network')
      [ "$selected:$ticks" = emulator-5554:150 ] || return 95
      forced=$((forced+1))
      [ "$mode" != stop-failed ] || return 9
      app_alive=0; host_alive=0 ;;
    'shell run-as com.bringyour.network rm -f files/acceptance/physical-active-client-id files/acceptance/active-client-ids')
      [ "$app_alive" = 0 ] || printf 'unsafe-clear\n' >>"$out/unsafe"
      cleared=$((cleared+1)) ;;
    *) printf 'unexpected ADB command\n' >&2; return 96 ;;
  esac
}
send_physical_command() {
  [ "$1:$2" = emulator-5554:client-finish'|finish|' ] || return 97
}
wait_physical_status() {
  [ "$1:$2:$3:$4:$5" = emulator-5554:client-finish:complete:none:120 ] || return 98
  [ "$mode" != finish-ack-failed ]
}
collect_physical_artifacts() {
  [ "$ticks" = 150 ] || return 99
  printf 'before-force-stop\n' >>"$out/events"
}
wait() {
  [ "$1:$host_alive" = 5151:0 ] || return 100
  joined=$((joined+1))
  return "$child_code"
}
release_active_clients() {
  [ "$app_alive" = 0 ] || printf 'unsafe-release\n' >>"$out/unsafe"
  released=$((released+1))
}
finish_result=0
finish_physical_session emulator-5554 client 5151 "$out" || finish_result=$?
cleanup_result=0
cleanup_physical_sessions "$out" emulator-5554 emulator-5556 || cleanup_result=$?
printf '%s\n' "$ticks:$forced:$released:$cleared:$joined:$finish_result:$cleanup_result:$p2p_cleanup_failed" >"$out/state"
`)
			state, err := os.ReadFile(filepath.Join(dir, "state"))
			if err != nil {
				t.Fatal(err)
			}
			want := "150:1:1:2:1:1:0:0\n"
			switch mode {
			case "normal":
				want = "3:0:1:2:1:0:0:0\n"
			case "unavailable", "token-mismatch", "avd-mismatch", "owner-exited":
				want = "150:0:0:0:1:1:1:1\n"
			case "stop-failed":
				want = "150:1:0:0:1:1:1:1\n"
			}
			if string(state) != want {
				t.Errorf("ticks:forced:released:cleared:joined:finish:cleanup:unsafe = %q, want %q", state, want)
			}
			if unsafe, err := os.ReadFile(filepath.Join(dir, "unsafe")); !os.IsNotExist(err) {
				t.Errorf("guest still owned a live session at destructive cleanup: %s, %v", unsafe, err)
			}
			var receipt struct{ WaitExitCode int }
			exit, err := os.ReadFile(filepath.Join(dir, "client-instrumentation-exit.json"))
			if err != nil || json.Unmarshal(exit, &receipt) != nil {
				t.Fatalf("missing exact child receipt: %s, %v", exit, err)
			}
			wantChild := 255
			if mode == "normal" || mode == "live-hung" {
				wantChild = 0
			}
			if receipt.WaitExitCode != wantChild {
				t.Errorf("cleanup repaired child status %d, want %d", receipt.WaitExitCode, wantChild)
			}
			if mode != "normal" {
				first, _ := os.ReadFile(filepath.Join(dir, "p2p-first-failure.json"))
				saved, _ := os.ReadFile(filepath.Join(dir, "first-cause.saved"))
				if !bytes.Equal(first, saved) {
					t.Error("cleanup replaced the original first failure")
				}
				transcript, _ := os.ReadFile(filepath.Join(dir, "client-instrumentation.log"))
				if strings.Contains(string(transcript), "OK (1 test)") {
					t.Error("cleanup invented the missing JUnit verdict")
				}
			}
		})
	}
}

func TestP2PCleanupReceiptBoundedAndRejectsRawMetadata(t *testing.T) {
	dir := cleanupReceiptFixture(t, `
if declare -F record_p2p_cleanup_failure >/dev/null; then
  for attempt in $(seq 1 40); do record_p2p_cleanup_failure "$out" client finish-authorization 3 || exit 80; done
  for bad in credential-canary 'private-client-canary/route' 'provider\nclient'; do
    if record_p2p_cleanup_failure "$out" "$bad" finish-authorization 3; then exit 81; fi
    if record_p2p_cleanup_failure "$out" client "$bad" 3; then exit 82; fi
  done
  for bad in 0 256 -1 1.5 credential-canary; do
    if record_p2p_cleanup_failure "$out" client finish-authorization "$bad"; then exit 83; fi
  done
fi
`)
	receipts := cleanupReceipts(t, dir)
	if len(receipts) != 32 {
		t.Fatalf("receipt cap = %d; want 32", len(receipts))
	}
	truncated, err := os.ReadFile(filepath.Join(dir, "p2p-cleanup-failures.truncated"))
	if err != nil || string(truncated) != "true\n" {
		t.Fatalf("missing bounded truncation marker: %v", err)
	}
	stat, err := os.Stat(filepath.Join(dir, "p2p-cleanup-failures.ndjson"))
	if err != nil || stat.Mode().Perm() != 0600 {
		t.Fatalf("private receipt mode = %v, %v", stat, err)
	}
}

func TestP2PCleanupRecorderRefusesSymlink(t *testing.T) {
	dir := cleanupReceiptFixture(t, `
printf 'private-client-canary\n' >"$out/unrelated"
ln -s "$out/unrelated" "$out/p2p-cleanup-failures.ndjson"
if declare -F record_p2p_cleanup_failure >/dev/null; then
  if record_p2p_cleanup_failure "$out" client finish-authorization 3; then exit 80; fi
fi
`)
	data, err := os.ReadFile(filepath.Join(dir, "unrelated"))
	if err != nil || string(data) != "private-client-canary\n" {
		t.Fatalf("unrelated data modified: %v", err)
	}
}

func TestP2PCleanupDiagnosticWriteFailureKeepsGuardsAndJoins(t *testing.T) {
	for _, mode := range []string{"directory", "fifo", "symlink", "unwritable-parent", "truncation-symlink", "truncation-fifo"} {
		t.Run(mode, func(t *testing.T) {
			if mode == "unwritable-parent" && os.Geteuid() == 0 {
				t.Skip("root bypasses the permission failure fixture")
			}
			dir := cleanupReceiptFixture(t, `
mkdir "$out/state"
record_p2p_failure "$out" client artifact-collection || exit 80
cp "$out/p2p-first-failure.json" "$out/first-cause.saved"
printf 'private-client-canary\n' >"$out/unrelated"
mode=`+mode+`
case "$mode" in
  directory) mkdir "$out/p2p-cleanup-failures.ndjson" ;;
  fifo) mkfifo "$out/p2p-cleanup-failures.ndjson" ;;
  symlink) ln -s "$out/unrelated" "$out/p2p-cleanup-failures.ndjson" ;;
  unwritable-parent) chmod 500 "$out"; trap 'chmod 700 "$out"' EXIT ;;
  truncation-symlink|truncation-fifo)
    for attempt in $(seq 1 32); do record_p2p_cleanup_failure "$out" provider finish-command 7 || exit 81; done
    if [ "$mode" = truncation-symlink ]; then
      ln -s "$out/unrelated" "$out/p2p-cleanup-failures.truncated"
    else
      mkfifo "$out/p2p-cleanup-failures.truncated"
    fi
    ;;
esac
authorize_selected_device() { [ "$1" != client-fixture ] || return 3; }
send_physical_command() { printf 'unsafe\n' >"$out/state/unsafe-send"; return 0; }
wait() { printf 'joined\n' >"$out/state/joined"; return 0; }
release_active_clients() { printf 'released\n' >"$out/state/released"; return 0; }
fake_adb() { printf 'unsafe\n' >"$out/state/unsafe-clear"; return 0; }
result=0
finish_physical_session client-fixture client 424242 "$out" || result=$?
[ "$result:$p2p_cleanup_failed" = 1:1 ] || exit 82
result=0
cleanup_physical_sessions "$out" client-fixture provider-fixture || result=$?
[ "$result:$p2p_cleanup_failed" = 1:1 ] || exit 83
chmod 700 "$out"
`)
			for _, name := range []string{"joined", "released"} {
				if _, err := os.Stat(filepath.Join(dir, "state", name)); err != nil {
					t.Fatalf("diagnostic write failure prevented safe %s: %v", name, err)
				}
			}
			for _, name := range []string{"unsafe-send", "unsafe-clear"} {
				if _, err := os.Stat(filepath.Join(dir, "state", name)); !os.IsNotExist(err) {
					t.Fatalf("diagnostic failure bypassed ownership guard: %s", name)
				}
			}
			first, _ := os.ReadFile(filepath.Join(dir, "p2p-first-failure.json"))
			saved, _ := os.ReadFile(filepath.Join(dir, "first-cause.saved"))
			if !bytes.Equal(first, saved) {
				t.Fatal("diagnostic failure changed first cause")
			}
			unrelated, _ := os.ReadFile(filepath.Join(dir, "unrelated"))
			if string(unrelated) != "private-client-canary\n" {
				t.Fatal("diagnostic write followed an unrelated symlink")
			}
		})
	}
}
