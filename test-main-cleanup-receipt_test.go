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
