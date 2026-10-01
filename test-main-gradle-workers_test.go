package main

import (
	"bytes"
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"
)

// Run both production Gradle argument vectors, not a retyped approximation.
// timeout is a local argv/exit stub: no build, device, account or network access.
func TestAcceptanceGradleWorkerArgumentsUnderNounset(t *testing.T) {
	data, err := os.ReadFile("test-main.sh")
	if err != nil {
		t.Fatal(err)
	}
	source := string(data)
	setupStart := strings.Index(source, "\ngradle_worker_args=()\n")
	if setupStart < 0 {
		t.Fatal("production worker setup boundary changed")
	}
	setup := source[setupStart+1:]
	setupEnd := strings.Index(setup, "\nfi\n")
	if setupEnd < 0 {
		t.Fatal("unterminated worker setup")
	}
	setup = setup[:setupEnd+4]
	var sdk string
	for _, line := range strings.Split(source, "\n") {
		if strings.Contains(line, "timeout 3600 ./gradlew :app:buildSdkAcceptance ") {
			if sdk != "" {
				t.Fatal("ambiguous SDK argument vector")
			}
			sdk = strings.TrimSpace(line)
		}
	}
	appStart := strings.Index(source, "      BRINGYOUR_HOME=\"$root\" timeout 3600 ./gradlew \\\n")
	if sdk == "" || appStart < 0 {
		t.Fatal("production Gradle command boundary changed")
	}
	app := source[appStart:]
	appEnd := strings.Index(app, "\n    ) 2>&1 | tee")
	if appEnd < 0 {
		t.Fatal("unterminated application argument vector")
	}
	app = app[:appEnd]
	library, err := filepath.Abs("test-main-lib.sh")
	if err != nil {
		t.Fatal(err)
	}
	paths := []string{"/bin/bash"}
	if path, err := exec.LookPath("bash"); err == nil && path != paths[0] {
		paths = append(paths, path)
	}
	for _, shell := range paths {
		for _, command := range []struct {
			name, source string
			want         []string
		}{{"sdk", sdk, []string{"3600", "./gradlew", ":app:buildSdkAcceptance"}},
			{"app", app, []string{"3600", "./gradlew", ":app:assembleGithubDebug", ":app:assembleGithubDebugAndroidTest", "-PurnetworkAcceptanceBuildId=fixture-build"}}} {
			for _, workers := range []string{"unset", "", "0", "invalid", "7", "0007"} {
				t.Run(shell+"/"+command.name+"/"+workers, func(t *testing.T) {
					for _, mode := range []string{"normal", "child-failure", "unrelated-unset"} {
						ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
						defer cancel()
						script := "set -euo pipefail\nsource \"$1\"\nroot=/fixture\nflavor=Github\nbuild_id=fixture-build\n" +
							"if [ \"$2\" = unset ]; then unset GOMAXPROCS; else GOMAXPROCS=$2; fi\n" + setup +
							"timeout() { printf '%s\\0' \"$@\"; if [ \"$fixture_mode\" = child-failure ]; then return 37; fi; }\n" +
							"fixture_mode=$3\n" + command.source + "\n" +
							"if [ \"$fixture_mode\" = unrelated-unset ]; then printf '%s' \"$must_remain_unset\"; fi\n"
						cmd := exec.CommandContext(ctx, shell, "-c", script, "fixture", library, workers, mode)
						var stdout, stderr bytes.Buffer
						cmd.Stdout, cmd.Stderr = &stdout, &stderr
						err := cmd.Run()
						want := append([]string{}, command.want...)
						if workers == "7" || workers == "0007" {
							want = append(want, "--max-workers", "7")
						}
						got := strings.Split(strings.TrimSuffix(stdout.String(), "\x00"), "\x00")
						if !reflect.DeepEqual(got, want) {
							t.Fatalf("%s: argv=%q want=%q exit=%v stderr=%s", mode, got, want, err, stderr.String())
						}
						switch mode {
						case "normal":
							if err != nil || stderr.Len() != 0 {
								t.Fatalf("optional worker arguments failed: %v %s", err, stderr.String())
							}
						case "child-failure":
							if cmd.ProcessState.ExitCode() != 37 {
								t.Fatalf("child failure changed: %v %s", err, stderr.String())
							}
						case "unrelated-unset":
							if err == nil || !strings.Contains(stderr.String(), "must_remain_unset") {
								t.Fatalf("unrelated nounset failure suppressed: %v %s", err, stderr.String())
							}
						}
					}
				})
			}
		}
	}
}
