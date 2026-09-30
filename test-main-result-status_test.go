package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// Exercise the production result tail and EXIT verifier, not runner startup.
// Every device/account operation is excluded; uninstall is a local status stub.
func resultStatusFunction(t *testing.T, source, name string) string {
	t.Helper()
	marker := "\n" + name + "() {\n"
	if strings.Count(source, marker) != 1 {
		t.Fatalf("expected exactly one production function %s", name)
	}
	function := source[strings.Index(source, marker)+1:]
	end := strings.Index(function, "\n}\n")
	if end < 0 {
		t.Fatalf("unterminated production function %s", name)
	}
	return function[:end+3] + "\n"
}

type resultStatusCell struct {
	flavor                    string
	auth, p2p, quote, cleanup int
}

type resultStatusRun struct {
	dir, output string
	exitCode    int
}

func resultStatusFixture(t *testing.T, cells []resultStatusCell, includeP2P, forceExitZero bool) resultStatusRun {
	t.Helper()
	data, err := os.ReadFile("test-main.sh")
	if err != nil {
		t.Fatal(err)
	}
	source := string(data)
	data, err = os.ReadFile("test-main-lib.sh")
	if err != nil {
		t.Fatal(err)
	}
	const startMarker = "    if ! uninstall_acceptance_packages \"$serial\" \"$out/post-acceptance-cleanup\"; then\n"
	const endMarker = "    if [ \"$fixture_missing\" -eq 1 ] || [ \"$client_cleanup_failed\" -eq 1 ]; then\n"
	if strings.Count(source, startMarker) != 1 || strings.Count(source, endMarker) != 1 {
		t.Fatal("production result-tail boundaries changed")
	}
	start, end := strings.Index(source, startMarker), strings.Index(source, endMarker)
	if end <= start || strings.Count(source, "\noverall=0\n") != 1 || !strings.HasSuffix(source, "\nexit \"$overall\"\n") {
		t.Fatal("production aggregate initialization/exit contract changed")
	}
	var script strings.Builder
	script.WriteString("set -euo pipefail\numask 077\nartifacts=$1\n")
	for _, name := range []string{"record_device_cases", "record_acceptance_cleanup_failure", "record_p2p_failure", "cleanup"} {
		script.WriteString(resultStatusFunction(t, source, name))
	}
	script.WriteString(resultStatusFunction(t, string(data), "android_acceptance_verify_device_flavor_results"))
	script.WriteString(`
provider_session_pid=; provider_session_out=; client_session_pid=; client_session_out=
private_staging=; private_staging_serial=; peer_serial=; peer_emulator_pid=; emulator_pid=
started_emulator=0; keep_emulator=0; credentials=
device_records=$artifacts/no-device-records
device_cleanup_records=$artifacts/no-device-cleanup-records
device_plan=$artifacts/plan.tsv
device_results=$artifacts/cases.tsv
result_matrix=$artifacts/matrix.tsv
run_dir=$artifacts/run
execution_mode=canonical; smoke_only=0
mkdir -p "$run_dir"
: >"$device_plan"
: >"$device_results"
record_acceptance_result() {
  printf '%s\t%s\t%s\t%s\n' "$2" "$3" "$4" "$5" >>"$artifacts/phases.tsv"
}
uninstall_acceptance_packages() {
  mkdir -p "$2"
  if [ "$cleanup_status" -ne 0 ]; then
    printf 'status=package-removal-unverified\n' >"$2/cleanup-status.txt"
  fi
  return "$cleanup_status"
}
remove_android_acceptance_run_dir() {
  [ "$1" = "$run_dir" ] || return 1
  printf 'joined\n' >"$artifacts/cleanup-joined"
}
# Any unexpected route to a device/account/process owner fails the fixture.
adb() { exit 90; }
timeout() { exit 91; }
authorize_selected_device() { exit 92; }
release_active_clients() { exit 93; }
android_acceptance_stop_emulator_child() { exit 94; }
trap cleanup EXIT
overall=0
summarize_cell() {
  local target="$1" test_status="$2" p2p_status="$3" usdc_status="$4" cleanup_status="$5"
  local out="$artifacts/$target" serial=emulator-fixture device_id=device-fixture
  local build_id=fixture input_fingerprint=fixture main_test_scope=fixture
  mkdir -p "$out/peer-to-peer"
  printf '%s\t%s\t%s\n' "$device_id" "$serial" "$target" >>"$device_plan"
  if [ "$p2p_status" -ne 0 ]; then
    printf 'java.lang.AssertionError: fixture\n' >"$out/peer-to-peer/client-instrumentation.log"
    record_p2p_failure "$out/peer-to-peer" client workflow-failed
    cp "$out/peer-to-peer/p2p-first-failure.json" "$out/first-cause.saved"
  fi
`)
	script.WriteString(source[start:end])
	script.WriteString("}\n")
	p2p := 0
	if includeP2P {
		p2p = 1
	}
	fmt.Fprintf(&script, "run_peer_to_peer=%d\n", p2p)
	for _, cell := range cells {
		if cell.flavor != "github" && cell.flavor != "play" {
			t.Fatal("fixture flavor is not allowlisted")
		}
		// Explicit conditional invocation also pins the questioned errexit context.
		fmt.Fprintf(&script, "if summarize_cell %s %d %d %d %d; then :; else exit 95; fi\n", cell.flavor, cell.auth, cell.p2p, cell.quote, cell.cleanup)
	}
	script.WriteString("printf '%s\\n' \"$overall\" >\"$artifacts/overall-before-exit\"\n")
	if forceExitZero {
		// Deliberately challenge the independent EXIT matrix fail-safe.
		script.WriteString("overall=0\n")
	}
	script.WriteString("exit \"$overall\"\n")
	dir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	command := exec.CommandContext(ctx, "bash", "--noprofile", "--norc", "-c", script.String(), "fixture", dir)
	command.WaitDelay = time.Second
	output, err := command.CombinedOutput()
	if ctx.Err() != nil {
		t.Fatalf("fixture did not join within its deadline: %v", ctx.Err())
	}
	code := 0
	if err != nil {
		exit, ok := err.(*exec.ExitError)
		if !ok {
			t.Fatalf("fixture did not execute: %v", err)
		}
		code = exit.ExitCode()
	}
	return resultStatusRun{dir, string(output), code}
}

func resultStatusRead(t *testing.T, run resultStatusRun, name string) []byte {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(run.dir, name))
	if err != nil {
		t.Fatalf("missing fixture evidence %s: %v; output=%s", name, err, run.output)
	}
	return data
}

func TestAndroidResultLabelNamesOnlyThePassingInstrumentation(t *testing.T) {
	run := resultStatusFixture(t, []resultStatusCell{{flavor: "github", p2p: 1}}, true, false)
	if run.exitCode != 1 {
		t.Fatalf("P2P failure exit=%d; output=%s", run.exitCode, run.output)
	}
	if strings.Contains(run.output, "[android acceptance] github accepted on ") ||
		!strings.Contains(run.output, "[android acceptance] github auth/data-plane instrumentation passed on ") {
		t.Fatalf("auth-only success has a misleading whole-flavor label:\n%s", run.output)
	}
}

func TestAndroidResultFailuresPersistAcrossLaterPassingCellsAndExit(t *testing.T) {
	for _, test := range []struct {
		name                  string
		cells                 []resultStatusCell
		includeP2P, forceZero bool
		wantExit              int
	}{
		{"auth-pass-p2p-fail", []resultStatusCell{{flavor: "github", p2p: 1}}, true, false, 1},
		{"later-flavor-pass", []resultStatusCell{{flavor: "github", p2p: 1}, {flavor: "play"}}, true, false, 1},
		{"auth-fail-p2p-pass", []resultStatusCell{{flavor: "github", auth: 7}}, true, false, 1},
		{"quote-fail", []resultStatusCell{{flavor: "github", quote: 1}}, true, false, 1},
		{"all-pass", []resultStatusCell{{flavor: "github"}}, true, false, 0},
		{"partial-profile-pass", []resultStatusCell{{flavor: "github"}}, false, false, 0},
		{"exit-matrix-independent-failsafe", []resultStatusCell{{flavor: "github", p2p: 1}}, true, true, 1},
	} {
		t.Run(test.name, func(t *testing.T) {
			run := resultStatusFixture(t, test.cells, test.includeP2P, test.forceZero)
			if run.exitCode != test.wantExit {
				t.Fatalf("exit=%d, want %d; output=%s", run.exitCode, test.wantExit, run.output)
			}
			if got := string(resultStatusRead(t, run, "overall-before-exit")); got != fmt.Sprintf("%d\n", test.wantExit) {
				t.Fatalf("aggregate before EXIT=%q, want %d", got, test.wantExit)
			}
			if string(resultStatusRead(t, run, "cleanup-joined")) != "joined\n" {
				t.Fatal("EXIT cleanup was not joined")
			}
			rows := strings.Split(strings.TrimSpace(string(resultStatusRead(t, run, "cases.tsv"))), "\n")
			wantRows := 6 * len(test.cells)
			if test.includeP2P {
				wantRows += len(test.cells)
			}
			if len(rows) != wantRows {
				t.Fatalf("case count=%d, want %d", len(rows), wantRows)
			}
			for _, row := range rows {
				fields := strings.Split(row, "\t")
				if len(fields) != 6 {
					t.Fatalf("invalid case row %q", row)
				}
				var cell resultStatusCell
				for _, candidate := range test.cells {
					if candidate.flavor == fields[2] {
						cell = candidate
					}
				}
				status := cell.auth
				switch fields[3] {
				case "peer-to-peer":
					status = cell.p2p
				case "usdc-quote":
					status = cell.quote
				}
				want := "PASS"
				if status != 0 {
					want = "FAIL"
				}
				if fields[4] != want {
					t.Fatalf("case %s/%s=%s, want %s", fields[2], fields[3], fields[4], want)
				}
			}
			want := "PASS"
			if test.wantExit != 0 {
				want = "FAIL"
			}
			for _, row := range strings.Split(strings.TrimSpace(string(resultStatusRead(t, run, "matrix.tsv"))), "\n") {
				fields := strings.Split(row, "\t")
				if len(fields) != 4 || fields[2] != want {
					t.Fatalf("wrong final matrix row %q", row)
				}
			}
		})
	}
}

func TestAndroidResultCleanupKeepsTheOriginalP2PFailure(t *testing.T) {
	run := resultStatusFixture(t, []resultStatusCell{{flavor: "github", p2p: 1, cleanup: 1}}, true, false)
	if run.exitCode != 1 {
		t.Fatalf("cleanup failure exit=%d; output=%s", run.exitCode, run.output)
	}
	first := resultStatusRead(t, run, "github/peer-to-peer/p2p-first-failure.json")
	if !bytes.Equal(first, resultStatusRead(t, run, "github/first-cause.saved")) {
		t.Fatal("package cleanup replaced the original P2P first cause")
	}
	var receipt struct {
		Phase                 string `json:"phase"`
		Reason                string `json:"reason"`
		PrecedingTestExitCode int    `json:"precedingTestExitCode"`
	}
	if err := json.Unmarshal(resultStatusRead(t, run, "github/cleanup-failure.json"), &receipt); err != nil {
		t.Fatal(err)
	}
	if receipt.Phase != "post-acceptance-cleanup" || receipt.Reason != "package-removal-unverified" || receipt.PrecedingTestExitCode != 0 {
		t.Fatalf("wrong independent cleanup receipt: %+v", receipt)
	}
	if strings.Contains(run.output, "instrumentation passed on") || strings.Contains(run.output, "github accepted on") {
		t.Fatal("cleanup failure reported instrumentation success")
	}
}
