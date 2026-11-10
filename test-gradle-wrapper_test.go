package main

import (
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"testing"
)

// The Gradle wrapper downloads the distribution that distributionUrl names and
// runs it. With distributionSha256Sum set, the wrapper hashes each download and
// refuses a zip whose SHA-256 differs, so a tampered or substituted distribution
// never runs; without it, whatever zip that URL serves runs unchecked. No Gradle
// runs here, so this test reads the wrapper's properties file and checks:
//   - the distribution comes from services.gradle.org, over https;
//   - distributionSha256Sum is set exactly once, as 64 lowercase hex digits (the
//     wrapper compares lowercase hex, so any other form fails every download).
// The checksum comes from https://gradle.org/release-checksums/, and a version
// bump changes both lines together (README, "Updating Gradle").
// Run: GOWORK=off go test -count=1 test-gradle-wrapper_test.go

const gradleWrapperProperties = "app/gradle/wrapper/gradle-wrapper.properties"

var (
	gradleWrapperDistributionUrl = regexp.MustCompile(`^https://services\.gradle\.org/distributions/gradle-[0-9][0-9A-Za-z.+-]*-(bin|all)\.zip$`)
	gradleWrapperSha256          = regexp.MustCompile(`^[0-9a-f]{64}$`)
)

// Every value of each key, in file order. The wrapper's file is plain key=value
// lines with ':' escaped as "\:". A line in any other form fails the test, so the
// test never reads the file differently from the wrapper.
func gradleWrapperRead(t *testing.T) map[string][]string {
	t.Helper()
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("cannot locate the Android source")
	}
	data, err := os.ReadFile(filepath.Join(filepath.Dir(source), gradleWrapperProperties))
	if err != nil {
		t.Fatal(err)
	}
	values := map[string][]string{}
	for i, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(strings.TrimSuffix(line, "\r"))
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		key, value, ok := strings.Cut(line, "=")
		if !ok || strings.HasSuffix(line, `\`) {
			t.Fatalf("%s:%d: not a key=value line: %q", gradleWrapperProperties, i+1, line)
		}
		key = strings.TrimSpace(key)
		value = strings.ReplaceAll(strings.TrimSpace(value), `\:`, ":")
		values[key] = append(values[key], value)
	}
	return values
}

func TestGradleWrapperPinsDistributionChecksum(t *testing.T) {
	values := gradleWrapperRead(t)

	urls := values["distributionUrl"]
	if len(urls) != 1 || !gradleWrapperDistributionUrl.MatchString(urls[0]) {
		t.Fatalf("distributionUrl = %q, want one https://services.gradle.org/distributions/gradle-<version>-<bin|all>.zip", urls)
	}

	sums := values["distributionSha256Sum"]
	if len(sums) != 1 || !gradleWrapperSha256.MatchString(sums[0]) {
		t.Fatalf("distributionSha256Sum = %q, want one SHA-256 (64 lowercase hex digits) for %s, from https://gradle.org/release-checksums/", sums, urls[0])
	}
}
