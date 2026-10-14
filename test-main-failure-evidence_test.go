// Exercise the real shell source oracle; Kotlin behavior remains covered by its JVM tests.
package main

import (
	"context"
	"flag"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

var failureOraclePath = flag.String("failure-oracle", "test-main.test.sh", "actual shell oracle to exercise")
var failureSourceRoot = flag.String("failure-source-root", ".", "Android source root for the actual Kotlin inputs")

const failureInstrumentationPath = "app/app/src/androidTest/java/com/bringyour/network/acceptance/PhysicalLowbarSessionTest.kt"
const failureEvidencePath = "app/app/src/acceptanceShared/java/com/bringyour/network/acceptance/PhysicalFailureEvidence.kt"

// Read the same bounded source block from either the original or corrected shell parent.
func readPhysicalFailureOracle(t *testing.T) (string, string, string) {
	t.Helper()
	read := func(path string) string {
		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		return string(data)
	}
	oracle := read(*failureOraclePath)
	startMarker := "physical_failure_source=\"$(sed -n \\\n"
	endMarker := "grep -Fq 'main-startup-goroutines.txt'"
	if strings.Count(oracle, startMarker) != 1 || strings.Count(oracle, endMarker) != 1 {
		t.Fatal("actual physical failure oracle boundary changed")
	}
	start := strings.Index(oracle, startMarker)
	end := strings.Index(oracle, endMarker)
	if end <= start {
		t.Fatal("actual physical failure oracle boundaries are reversed")
	}
	return oracle[start:end],
		read(filepath.Join(*failureSourceRoot, failureInstrumentationPath)),
		read(filepath.Join(*failureSourceRoot, failureEvidencePath))
}

// Execute Bash with the original strict options and only the two actual source inputs.
func runPhysicalFailureOracle(t *testing.T, oracle, instrumentation, evidence string) (int, string) {
	t.Helper()
	root := t.TempDir()
	for _, source := range []struct {
		path string
		data string
	}{
		{path: failureInstrumentationPath, data: instrumentation},
		{path: failureEvidencePath, data: evidence},
	} {
		path := filepath.Join(root, source.path)
		if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, []byte(source.data), 0600); err != nil {
			t.Fatal(err)
		}
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	script := "set -euo pipefail\nhere=$1\nfail() { printf 'FAIL: %s\\n' \"$*\" >&2; exit 1; }\n" + oracle
	command := exec.CommandContext(ctx, "/bin/bash", "--noprofile", "--norc", "-c", script, "failure-oracle", root)
	command.Env = []string{"PATH=/usr/bin:/bin", "LC_ALL=C"}
	output, err := command.CombinedOutput()
	if ctx.Err() != nil {
		t.Fatalf("shell watchdog expired: %v", ctx.Err())
	}
	if err == nil {
		return 0, string(output)
	}
	if command.ProcessState == nil {
		t.Fatalf("shell did not execute: %v", err)
	}
	return command.ProcessState.ExitCode(), string(output)
}

// The old ambient-state oracle rejects this same actual corrected instrumentation.
func TestPhysicalFailureOracleAcceptsRetainedSnapshot(t *testing.T) {
	oracle, instrumentation, evidence := readPhysicalFailureOracle(t)
	for _, required := range []string{
		"failure: PhysicalFailureSnapshot",
		"JSONObject(failure.statusFields())",
		"status(id, \"error\", application, startElapsedMs, extra, failure.phase)",
	} {
		if !strings.Contains(instrumentation, required) {
			t.Fatalf("positive source does not contain retained-snapshot publication: %q", required)
		}
	}
	if !strings.Contains(evidence, "loginUi = error.evidence?.copy()") {
		t.Fatal("positive source does not contain finite password evidence")
	}
	exitCode, output := runPhysicalFailureOracle(t, oracle, instrumentation, evidence)
	if exitCode != 0 || output != "" {
		t.Fatalf("actual shell oracle rejected the retained-snapshot source: exit=%d output=%q", exitCode, output)
	}
}

// Each source mutation starts from a positively accepted copy and must reach an explicit refusal.
func TestPhysicalFailureOracleRejectsBrokenOwnershipAndEvidence(t *testing.T) {
	oracle, instrumentation, evidence := readPhysicalFailureOracle(t)
	exitCode, output := runPhysicalFailureOracle(t, oracle, instrumentation, evidence)
	if exitCode != 0 || output != "" {
		t.Fatalf("positive control failed: exit=%d output=%q", exitCode, output)
	}
	captureLine := "            runCatching { captureFailure(error) }.onFailure(error::addSuppressed)\n"
	ownedEnd := captureLine + "            throw error\n        } finally {\n" +
		"            closeOwnedDeviceScope(application, device, connectVc, peerVc, sampler, stopped, startUnixMs, summary)"
	for _, test := range []struct {
		name     string
		evidence bool
		before   string
		after    string
		want     string
	}{
		{name: "missing-capture", before: captureLine, after: "", want: "physical owned failure is not captured and rethrown before cleanup"},
		{name: "capture-after-cleanup", before: ownedEnd,
			after: "            throw error\n        } finally {\n" +
				"            closeOwnedDeviceScope(application, device, connectVc, peerVc, sampler, stopped, startUnixMs, summary)\n" + captureLine,
			want: "physical owned failure is not captured and rethrown before cleanup"},
		{name: "lost-exact-rethrow", before: captureLine + "            throw error\n",
			after: captureLine + "            throw AssertionError()\n",
			want:  "physical owned failure is not captured and rethrown before cleanup"},
		{name: "ambient-state-publication", before: "        val extra = JSONObject(failure.statusFields())",
			after: "        val startupState = application.loginStartupState.value\n        val extra = JSONObject(failure.statusFields())",
			want:  "physical failure evidence rereads ambient state or exposes free-form error data"},
		{name: "post-cleanup-phase", before: "startElapsedMs, extra, failure.phase)", after: "startElapsedMs, extra, phase)",
			want: "physical failure publication lost its retained snapshot"},
		{name: "lost-snapshot-type", before: "        failure: PhysicalFailureSnapshot,", after: "        failure: Throwable,",
			want: "physical failure publication lost its retained snapshot"},
		{name: "lost-outer-capture", before: "failureStatus(activeCommandId, application, startElapsedMs, captureFailure(error))",
			after: "failureStatus(activeCommandId, application, startElapsedMs, error)",
			want:  "physical outer owner lost original failure or separate cleanup errors"},
		{name: "lost-cleanup-cause", before: "sessionFailure.addSuppressed(finalError)", after: "finalError.addSuppressed(sessionFailure)",
			want: "physical outer owner lost original failure or separate cleanup errors"},
		{name: "lost-error-identity", evidence: true, before: "retainedError === error", after: "retainedError == error",
			want: "physical failure evidence lost typed first-error ownership"},
		{name: "untyped-password-failure", evidence: true, before: "is PasswordLoginFailureException ->", after: "is IllegalStateException ->",
			want: "physical failure evidence lost typed first-error ownership"},
		{name: "uncopied-ui-evidence", evidence: true, before: "loginUi = error.evidence?.copy()", after: "loginUi = error.evidence",
			want: "physical password failure lost finite pre-cleanup evidence"},
		{name: "lost-finite-stage", evidence: true,
			before: "is PasswordLoginFailureException -> {\n                stage = error.stage.wireValue",
			after:  "is PasswordLoginFailureException -> {\n                stage = \"startup\"",
			want:   "physical password failure lost finite pre-cleanup evidence"},
		{name: "lost-ui-field", evidence: true, before: "\"discoveryErrorVisible\" to it.errorVisible", after: "\"unclassified\" to it.errorVisible",
			want: "physical failure snapshot lost finite publication fields"},
		{name: "lost-failure-time", evidence: true, before: "put(\"failureTimeUnixMs\", timeUnixMs)", after: "put(\"observedTimeUnixMs\", timeUnixMs)",
			want: "physical failure snapshot lost finite publication fields"},
		{name: "free-form-message", evidence: true, before: "errorType = error.javaClass.simpleName", after: "errorType = error.message.orEmpty()",
			want: "physical failure evidence rereads ambient state or exposes free-form error data"},
		{name: "free-form-cause", evidence: true, before: "errorType = error.javaClass.simpleName", after: "errorType = error.cause.toString()",
			want: "physical failure evidence rereads ambient state or exposes free-form error data"},
	} {
		currentInstrumentation, currentEvidence := instrumentation, evidence
		source := &currentInstrumentation
		if test.evidence {
			source = &currentEvidence
		}
		if strings.Count(*source, test.before) != 1 {
			t.Fatalf("%s: actual source mutation boundary is not unique", test.name)
		}
		*source = strings.Replace(*source, test.before, test.after, 1)
		exitCode, output := runPhysicalFailureOracle(t, oracle, currentInstrumentation, currentEvidence)
		if exitCode != 1 || output != "FAIL: "+test.want+"\n" {
			t.Errorf("%s: exit=%d output=%q, want explicit refusal %q", test.name, exitCode, output, test.want)
		}
	}
}
