package main

// Deterministic tests for the Android insufficient-balance driver. A fake host
// stands in for adb, the test-main-lib.sh functions, Gradle and node, and a
// fake session answers commands the way InsufficientBalanceSessionTest does;
// the clock only moves when the driver sleeps. Run:
//   GOWORK=off go test -count=1 test-insufficient-balance-driver.go test-insufficient-balance-driver_test.go

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"
)

type fakeClock struct{ now time.Time }

func (self *fakeClock) Now() time.Time { return self.now }
func (self *fakeClock) Sleep(ctx context.Context, d time.Duration) error {
	self.now = self.now.Add(d)
	return nil
}

// fakeHost records every call and emulates the device's private files.
type fakeHost struct {
	t        *testing.T
	libPath  string
	calls    []string
	libErrs  map[string]error
	staged   map[string]string
	files    map[string]string
	pushed   map[string]string
	alive    map[int]bool
	killed   []int
	nextPid  int
	sdkLevel string
	devices  string
	// answer returns the session's status for one command line
	answer func(command string) string
	// ready is the status the session writes once it started
	ready string
	// cleanupCredentials is what client-cleanup.mjs read from its credentials file
	cleanupCredentials []string
}

func newFakeHost(t *testing.T, libPath string) *fakeHost {
	return &fakeHost{
		t:        t,
		libPath:  libPath,
		libErrs:  map[string]error{},
		staged:   map[string]string{},
		files:    map[string]string{},
		pushed:   map[string]string{},
		alive:    map[int]bool{},
		nextPid:  100,
		sdkLevel: "34\n",
		devices:  "List of devices attached\nemulator-5554\tdevice\n",
		ready:    "command=0\nstate=ready\n",
	}
}

func (self *fakeHost) Run(ctx context.Context, env []string, name string, args ...string) (string, error) {
	switch {
	case name == "bash" && len(args) == 2 && args[1] == "type -P timeout":
		return "/usr/local/bin/timeout\n", nil
	case name == "bash" && 5 <= len(args) && args[3] == self.libPath:
		function := args[4]
		self.calls = append(self.calls, "lib "+function)
		if err := self.libErrs[function]; err != nil {
			return "", err
		}
		if function == "android_acceptance_input_fingerprint" {
			return strings.Repeat("a", 64) + "\n", nil
		}
		return "", nil
	case name == "bash":
		self.calls = append(self.calls, "gradle")
		return "", errors.New("unexpected build")
	case name == "node":
		self.calls = append(self.calls, "node "+filepath.Base(args[0]))
		for _, value := range env {
			if path, ok := strings.CutPrefix(value, "UR_ACCEPT_CREDENTIALS_FILE="); ok {
				b, _ := os.ReadFile(path)
				self.cleanupCredentials = append(self.cleanupCredentials, string(b))
			}
		}
		return "", nil
	case strings.HasSuffix(name, "/adb"):
		return self.adb(args)
	}
	self.t.Fatalf("unexpected command %s %v", name, args)
	return "", nil
}

func (self *fakeHost) adb(args []string) (string, error) {
	if args[0] == "devices" {
		return self.devices, nil
	}
	if args[0] != "-s" {
		self.t.Fatalf("adb without a serial: %v", args)
	}
	args = args[2:]
	self.calls = append(self.calls, "adb "+strings.Join(args, " "))
	switch {
	case args[0] == "push":
		b, err := os.ReadFile(args[1])
		if err != nil {
			return "", err
		}
		self.staged[args[2]] = string(b)
		self.pushed[filepath.Base(args[1])] = string(b)
	case len(args) == 6 && args[1] == "run-as" && args[3] == "cp":
		self.files[args[5]] = self.staged[args[4]]
	case len(args) == 7 && args[1] == "run-as" && args[3] == "mv":
		content := self.files[args[5]]
		delete(self.files, args[5])
		self.files[args[6]] = content
		if args[6] == deviceCommandPath && self.answer != nil {
			self.files[deviceStatusPath] = self.answer(strings.TrimSpace(content))
		}
	case args[0] == "exec-out" && args[3] == "cat":
		status, ok := self.files[args[4]]
		if !ok {
			return "", errors.New("no such file")
		}
		return status, nil
	case args[0] == "exec-out" && args[3] == "sh":
		return self.files[deviceClientsPath], nil
	case args[0] == "shell" && args[1] == "getprop":
		return self.sdkLevel, nil
	}
	return "", nil
}

func (self *fakeHost) Start(env []string, logPath string, name string, args ...string) (int, error) {
	self.nextPid++
	pid := self.nextPid
	self.alive[pid] = true
	if strings.HasSuffix(name, "/adb") {
		self.calls = append(self.calls, "start session")
		self.files[deviceStatusPath] = self.ready
	} else {
		self.calls = append(self.calls, "start "+args[4])
	}
	return pid, nil
}

func (self *fakeHost) Alive(pid int) bool { return self.alive[pid] }
func (self *fakeHost) Kill(pid int) {
	self.killed = append(self.killed, pid)
	self.alive[pid] = false
}

type fixture struct {
	t      *testing.T
	root   string
	state  string
	host   *fakeHost
	driver *driver
	clock  *fakeClock
	// the runner's per-case credentials file, passed as `setup <file>`
	credentials string
}

const (
	testPassword  = "correct horse battery staple"
	decoyPassword = "decoy env password"
)

// writeCredentials writes a runner-style private credentials file.
func writeCredentials(t *testing.T, path, email, password string) string {
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("email: "+email+"\npassword: \""+password+"\"\n"), 0600); err != nil {
		t.Fatal(err)
	}
	return path
}

func newFixture(t *testing.T) *fixture {
	root := t.TempDir()
	here := filepath.Join(root, "android")
	state := filepath.Join(root, "artifacts", "android")
	for _, dir := range []string{here, state, filepath.Join(root, "sdk", "build", "android"), filepath.Join(here, "tests", "__acceptance__", "build", "github")} {
		if err := os.MkdirAll(dir, 0700); err != nil {
			t.Fatal(err)
		}
	}
	for _, name := range []string{"URnetworkSdk.aar", "URnetworkSdk-sources.jar"} {
		os.WriteFile(filepath.Join(root, "sdk", "build", "android", name), []byte("x"), 0600)
	}
	os.WriteFile(filepath.Join(here, "tests", "__acceptance__", "build", "github", "build-id"), []byte("20261002-101010-github\n"), 0600)
	credentials := writeCredentials(t, filepath.Join(root, "runner", "case-1-credentials"), "ib@example.invalid", testPassword)
	// the retired env var points at a different, valid account; it must be ignored
	decoy := writeCredentials(t, filepath.Join(root, "vault", "tests-insufficient-balance.yml"), "decoy@example.invalid", decoyPassword)
	env := map[string]string{
		"URNETWORK_ROOT":                             root,
		"URNETWORK_INSUFFICIENT_BALANCE_STATE":       state,
		"URNETWORK_INSUFFICIENT_BALANCE_CREDENTIALS": decoy,
		"ANDROID_SDK_ROOT":                           "/sdk",
	}
	c, err := configFromEnv(func(k string) string { return env[k] }, here)
	if err != nil {
		t.Fatal(err)
	}
	host := newFakeHost(t, filepath.Join(here, "test-main-lib.sh"))
	clock := &fakeClock{now: time.Date(2026, 10, 2, 12, 0, 0, 0, time.UTC)}
	d := &driver{config: c, runner: host, clock: clock, timing: defaultTiming, now: clock.Now}
	return &fixture{t: t, root: root, state: state, host: host, driver: d, clock: clock, credentials: credentials}
}

// run executes one verb and requires exactly one JSON object on stdout or one
// stderr line.
// Each verb is a separate process, so nothing learned by an earlier verb is kept
// in memory.
func (self *fixture) run(args ...string) (map[string]any, string, int) {
	self.driver.secrets = nil
	var stdout, stderr bytes.Buffer
	code := mainCode(context.Background(), self.driver, args, &stdout, &stderr)
	if code == 0 {
		if stderr.Len() != 0 {
			self.t.Fatalf("%v wrote stderr on success: %q", args, stderr.String())
		}
		d := json.NewDecoder(&stdout)
		var out map[string]any
		if err := d.Decode(&out); err != nil {
			self.t.Fatalf("%v stdout is not one JSON object: %v", args, err)
		}
		if d.More() {
			self.t.Fatalf("%v wrote more than one JSON object", args)
		}
		return out, "", code
	}
	if stdout.Len() != 0 {
		self.t.Fatalf("%v wrote stdout on failure: %q", args, stdout.String())
	}
	if strings.Count(strings.TrimRight(stderr.String(), "\n"), "\n") != 0 {
		self.t.Fatalf("%v stderr is not one line: %q", args, stderr.String())
	}
	return nil, stderr.String(), code
}

func (self *fixture) setup() {
	out, errLine, code := self.run("setup", self.credentials)
	if code != 0 {
		self.t.Fatalf("setup failed: %s", errLine)
	}
	if !reflect.DeepEqual(out, map[string]any{"kill_switch_supported": true}) {
		self.t.Fatalf("setup output %v", out)
	}
	self.host.calls = nil
}

func indexOf(calls []string, prefix string) int {
	for i, call := range calls {
		if strings.HasPrefix(call, prefix) {
			return i
		}
	}
	return -1
}

func TestSetupReusesMainsCacheAndOwnsTheAvd(t *testing.T) {
	f := newFixture(t)
	_, errLine, code := f.run("setup", f.credentials)
	if code != 0 {
		t.Fatalf("setup failed: %s", errLine)
	}
	calls := f.host.calls
	if indexOf(calls, "gradle") != -1 {
		t.Fatalf("setup rebuilt although MAIN's cache is current: %v", calls)
	}
	order := []string{
		"lib android_acceptance_cache_is_current",
		"lib android_acceptance_no_running_avd",
		"start run_android_acceptance_shared_avd_emulator",
		"lib android_acceptance_wait_for_runner_owned_emulator",
		"lib android_acceptance_prepare_owned_emulator",
		"lib android_acceptance_disable_animations",
		"lib android_acceptance_install_cell_apks",
		"adb shell pm grant " + packageName + " android.permission.POST_NOTIFICATIONS",
		"adb push",
		"start session",
	}
	last := -1
	for _, want := range order {
		i := indexOf(calls[last+1:], want)
		if i < 0 {
			t.Fatalf("missing or out of order %q in %v", want, calls)
		}
		last += i + 1
	}
	// a busy emulator-5554 moves the owned AVD to the next console port
	var state driverState
	b, _ := os.ReadFile(filepath.Join(f.state, stateName))
	json.Unmarshal(b, &state)
	if state.Serial != "emulator-5556" || state.BuildId != "20261002-101010-github" || state.SessionPid == 0 || state.EmulatorPid == 0 {
		t.Fatalf("state %+v", state)
	}
	// credentials reach the device only as the private two-line file
	if f.host.pushed["credentials"] != "ib@example.invalid\n"+testPassword+"\n" {
		t.Fatalf("pushed credentials %q", f.host.pushed["credentials"])
	}
	if f.host.files[devicePrivateDir+"/credentials"] == "" {
		t.Fatal("credentials were not published in the app's private directory")
	}
	if _, err := os.Stat(filepath.Join(f.state, "credentials")); !os.IsNotExist(err) {
		t.Fatal("host copy of the credentials was left in the state directory")
	}
	for _, call := range calls {
		if strings.Contains(call, testPassword) {
			t.Fatalf("a command line carried the password: %q", call)
		}
	}
}

func TestSetupSkipsNotificationGrantBeforeAndroid13(t *testing.T) {
	f := newFixture(t)
	f.host.sdkLevel = "31\n"
	if _, errLine, code := f.run("setup", f.credentials); code != 0 {
		t.Fatalf("setup failed: %s", errLine)
	}
	if indexOf(f.host.calls, "adb shell getprop") < 0 {
		t.Fatal("did not read the API level")
	}
	if indexOf(f.host.calls, "adb shell pm grant") != -1 {
		t.Fatal("granted a permission that does not exist before API 33")
	}
}

func TestSetupFailureIsOneRedactedLineAndTeardownStillStopsTheAvd(t *testing.T) {
	f := newFixture(t)
	f.host.libErrs["android_acceptance_prepare_owned_emulator"] = fmt.Errorf("exit status 1: echoed %s", testPassword)
	_, errLine, code := f.run("setup", f.credentials)
	if code == 0 {
		t.Fatal("setup passed on an unready AVD")
	}
	if strings.Contains(errLine, testPassword) || !strings.Contains(errLine, "[redacted]") {
		t.Fatalf("stderr not redacted: %q", errLine)
	}
	f.host.calls = nil
	if _, errLine, code := f.run("teardown"); code != 0 {
		t.Fatalf("teardown failed: %s", errLine)
	}
	if indexOf(f.host.calls, "lib android_acceptance_stop_emulator_child") < 0 {
		t.Fatalf("teardown left the AVD running: %v", f.host.calls)
	}
	if indexOf(f.host.calls, "lib android_acceptance_uninstall_package") != -1 {
		t.Fatalf("teardown uninstalled packages that were never installed: %v", f.host.calls)
	}
	if _, err := os.Stat(filepath.Join(f.state, stateName)); !os.IsNotExist(err) {
		t.Fatal("teardown kept the driver state")
	}
}

func TestSetupRefusesAnAvdAlreadyRunning(t *testing.T) {
	f := newFixture(t)
	f.host.libErrs["android_acceptance_no_running_avd"] = errors.New("exit status 1")
	if _, _, code := f.run("setup", f.credentials); code == 0 {
		t.Fatal("setup adopted an AVD it did not start")
	}
	if indexOf(f.host.calls, "start ") != -1 {
		t.Fatalf("started an emulator anyway: %v", f.host.calls)
	}
}

func TestObserveMapsTheSessionStatus(t *testing.T) {
	f := newFixture(t)
	f.setup()
	var commands []string
	f.host.answer = func(command string) string {
		commands = append(commands, command)
		id := strings.Split(command, "|")[0]
		return "command=" + id + "\nstate=complete\nconnect_requested=true\nconnected=true\nalert=true\n" +
			"disconnect_visible=true\nupgrade_visible=true\nnotifications=1\n"
	}
	out, errLine, code := f.run("observe")
	if code != 0 {
		t.Fatalf("observe failed: %s", errLine)
	}
	want := map[string]any{
		"connect_requested":                  true,
		"connected":                          true,
		"insufficient_balance_alert":         true,
		"disconnect_visible":                 true,
		"upgrade_visible":                    true,
		"insufficient_balance_notifications": float64(1),
	}
	if !reflect.DeepEqual(out, want) {
		t.Fatalf("observe %v", out)
	}
	if !reflect.DeepEqual(commands, []string{"1|observe|"}) {
		t.Fatalf("commands %v", commands)
	}
	// every command is authorized against the owned emulator first
	if indexOf(f.host.calls, "lib android_acceptance_runner_owns_emulator") > indexOf(f.host.calls, "adb push") {
		t.Fatalf("command written before ownership was proven: %v", f.host.calls)
	}
	f.run("observe")
	if commands[1] != "2|observe|" {
		t.Fatalf("command ids do not advance: %v", commands)
	}
}

func TestEgressReportsHeldAsErrorAndAddressAsIp(t *testing.T) {
	f := newFixture(t)
	f.setup()
	answer := ""
	f.host.answer = func(command string) string {
		return "command=" + strings.Split(command, "|")[0] + "\nstate=complete\n" + answer
	}

	answer = "ip=198.51.100.7\n"
	if out, _, _ := f.run("egress"); !reflect.DeepEqual(out, map[string]any{"ip": "198.51.100.7"}) {
		t.Fatalf("egress %v", out)
	}
	if out, _, _ := f.run("direct-egress"); !reflect.DeepEqual(out, map[string]any{"ip": "198.51.100.7"}) {
		t.Fatalf("direct-egress %v", out)
	}

	// held in the tunnel: the probe fails, which the runner counts as held
	answer = "egress_error=egress probe failed: SocketTimeoutException:timeout\n"
	out, _, code := f.run("egress")
	if code != 0 || out["ip"] != nil || !strings.Contains(out["error"].(string), "SocketTimeoutException") {
		t.Fatalf("held egress %v", out)
	}
	// but the direct path must be measured
	if _, errLine, code := f.run("direct-egress"); code == 0 || !strings.Contains(errLine, "no direct-path address") {
		t.Fatalf("direct-egress without an address: %q", errLine)
	}

	answer = "ip=not an address\n"
	if _, _, code := f.run("egress"); code == 0 {
		t.Fatal("accepted an invalid address")
	}
}

func TestActionsAndKillSwitch(t *testing.T) {
	f := newFixture(t)
	f.setup()
	var commands []string
	f.host.answer = func(command string) string {
		commands = append(commands, command)
		return "command=" + strings.Split(command, "|")[0] + "\nstate=complete\n"
	}
	for _, args := range [][]string{{"connect"}, {"traffic"}, {"kill-switch", "on"}, {"press-disconnect"}, {"kill-switch", "off"}} {
		out, errLine, code := f.run(args...)
		if code != 0 || len(out) != 0 {
			t.Fatalf("%v: %v %s", args, out, errLine)
		}
	}
	want := []string{"1|connect|", "2|traffic|", "3|kill-switch|on", "4|press-disconnect|", "5|kill-switch|off"}
	if !reflect.DeepEqual(commands, want) {
		t.Fatalf("commands %v", commands)
	}
	if _, _, code := f.run("kill-switch", "maybe"); code == 0 {
		t.Fatal("accepted a kill-switch value other than on/off")
	}
	if _, _, code := f.run("kill-switch"); code == 0 {
		t.Fatal("accepted kill-switch without a value")
	}
}

func TestFailedCommandAndDeadSessionFail(t *testing.T) {
	f := newFixture(t)
	f.setup()
	f.host.answer = func(command string) string {
		return "command=" + strings.Split(command, "|")[0] + "\nstate=failed\nerror=Timed out waiting for UI tag acceptance.disconnect after 5s\n"
	}
	_, errLine, code := f.run("press-disconnect")
	if code == 0 || !strings.Contains(errLine, "acceptance.disconnect") {
		t.Fatalf("failed command: %q", errLine)
	}

	f.host.answer = nil
	for pid := range f.host.alive {
		f.host.alive[pid] = false
	}
	start := f.clock.now
	_, errLine, code = f.run("observe")
	if code == 0 || !strings.Contains(errLine, "session exited") {
		t.Fatalf("dead session: %q", errLine)
	}
	if f.clock.now != start {
		t.Fatal("waited on a session that had already exited")
	}
}

func TestSilentSessionTimesOutOnTheFixedBound(t *testing.T) {
	f := newFixture(t)
	f.setup()
	f.host.answer = func(command string) string { return "command=0\nstate=ready\n" }
	start := f.clock.now
	_, errLine, code := f.run("observe")
	if code == 0 || !strings.Contains(errLine, "did not answer within 45s") {
		t.Fatalf("silent session: %q", errLine)
	}
	if elapsed := f.clock.now.Sub(start); elapsed < 45*time.Second || 46*time.Second < elapsed {
		t.Fatalf("waited %s", elapsed)
	}
}

func TestVerbsBeforeSetupFail(t *testing.T) {
	f := newFixture(t)
	if _, errLine, code := f.run("observe"); code == 0 || !strings.Contains(errLine, "setup has not completed") {
		t.Fatalf("observe before setup: %q", errLine)
	}
	if out, _, code := f.run("teardown"); code != 0 || len(out) != 0 {
		t.Fatal("teardown without setup must be a no-op")
	}
	if _, _, code := f.run("upgrade"); code == 0 {
		t.Fatal("accepted an unknown verb")
	}
}

func TestTeardownFinishesReleasesUninstallsAndStops(t *testing.T) {
	f := newFixture(t)
	f.setup()
	var commands []string
	f.host.answer = func(command string) string {
		commands = append(commands, command)
		return "command=" + strings.Split(command, "|")[0] + "\nstate=complete\n"
	}
	f.host.files[deviceClientsPath] = "client-b\nclient-a\nclient-b\n"
	out, errLine, code := f.run("teardown")
	if code != 0 || len(out) != 0 {
		t.Fatalf("teardown: %v %s", out, errLine)
	}
	if !reflect.DeepEqual(commands, []string{"1|finish|"}) {
		t.Fatalf("commands %v", commands)
	}
	order := []string{
		"node client-cleanup.mjs",
		"lib android_acceptance_restore_animations",
		"lib android_acceptance_uninstall_package",
		"lib android_acceptance_uninstall_package",
		"adb emu kill",
		"lib android_acceptance_stop_emulator_child",
	}
	last := -1
	for _, want := range order {
		i := indexOf(f.host.calls[last+1:], want)
		if i < 0 {
			t.Fatalf("missing or out of order %q in %v", want, f.host.calls)
		}
		last += i + 1
	}
	markers, _ := filepath.Glob(filepath.Join(f.state, "cleanup-clients", "active-client-id-*"))
	if len(markers) != 2 {
		t.Fatalf("client markers %v", markers)
	}
	if _, err := os.Stat(filepath.Join(f.state, "cleanup-clients", "credentials")); !os.IsNotExist(err) {
		t.Fatal("cleanup credentials were left behind")
	}
	if len(f.host.killed) != 1 {
		t.Fatalf("instrumentation host process not stopped: %v", f.host.killed)
	}
}

func TestTeardownWithoutOwnershipNeverTouchesTheDevice(t *testing.T) {
	f := newFixture(t)
	f.setup()
	f.host.libErrs["android_acceptance_runner_owns_emulator"] = errors.New("exit status 3")
	_, _, code := f.run("teardown")
	if code != 0 {
		t.Fatal("teardown failed although only host cleanup was possible")
	}
	for _, call := range f.host.calls {
		if strings.HasPrefix(call, "adb ") || strings.Contains(call, "uninstall") || strings.Contains(call, "restore_animations") {
			t.Fatalf("touched a device this driver no longer owns: %v", f.host.calls)
		}
	}
	if indexOf(f.host.calls, "lib android_acceptance_stop_emulator_child") < 0 {
		t.Fatal("did not stop its own emulator process")
	}
}

func TestReadCredentials(t *testing.T) {
	dir := t.TempDir()
	write := func(text string, mode os.FileMode) string {
		path := filepath.Join(dir, fmt.Sprintf("c%d", len(text)))
		os.WriteFile(path, []byte(text), mode)
		os.Chmod(path, mode)
		return path
	}
	email, password, err := readCredentials(write("# account\nemail: a@b.invalid\npassword: 'p w'\n", 0600))
	if err != nil || email != "a@b.invalid" || password != "p w" {
		t.Fatalf("%q %q %v", email, password, err)
	}
	for _, bad := range []string{"email: a\n", "email: a\npassword: b\ntoken: c\n", "email: a\nemail: b\npassword: c\n"} {
		if _, _, err := readCredentials(write(bad, 0600)); err == nil {
			t.Fatalf("accepted %q", bad)
		}
	}
	if _, _, err := readCredentials(write("email: a\npassword: group-readable\n", 0640)); err == nil {
		t.Fatal("accepted a group-readable credentials file")
	}
}

func TestParseStatusAndObservation(t *testing.T) {
	for _, bad := range []string{"", "state=complete\n", "command=1\n", "command=1\nstate=complete\nBad=x\n", "command=1\nstate=complete\nalert\n", "command=1\ncommand=2\nstate=complete\n"} {
		if _, err := parseStatus(bad); err == nil {
			t.Fatalf("accepted status %q", bad)
		}
	}
	fields, err := parseStatus("command=3\r\nstate=complete\r\nerror=a=b\r\n")
	if err != nil || fields["error"] != "a=b" {
		t.Fatalf("%v %v", fields, err)
	}
	complete := map[string]string{"connect_requested": "true", "connected": "false", "alert": "true", "disconnect_visible": "true", "upgrade_visible": "true", "notifications": "2"}
	o, err := observationFromStatus(complete)
	if err != nil || o != (observation{ConnectRequested: true, Alert: true, DisconnectButton: true, UpgradeButton: true, Notifications: 2}) {
		t.Fatalf("%+v %v", o, err)
	}
	for key, value := range map[string]string{"alert": "yes", "connected": "", "notifications": "-1"} {
		broken := map[string]string{}
		for k, v := range complete {
			broken[k] = v
		}
		broken[key] = value
		if _, err := observationFromStatus(broken); err == nil {
			t.Fatalf("accepted %s=%q", key, value)
		}
	}
}

func TestFreeConsolePortAndRetainedClients(t *testing.T) {
	if port, err := freeConsolePort("List of devices attached\n"); err != nil || port != 5554 {
		t.Fatalf("%d %v", port, err)
	}
	var b strings.Builder
	for port := 5554; port <= 5584; port += 2 {
		fmt.Fprintf(&b, "emulator-%d\tdevice\n", port)
	}
	if _, err := freeConsolePort(b.String()); err == nil {
		t.Fatal("found a port when all are used")
	}
	clientIds, err := retainedClientIds("b\r\na\n\nb\n")
	if err != nil || !reflect.DeepEqual(clientIds, []string{"a", "b"}) {
		t.Fatalf("%v %v", clientIds, err)
	}
	if _, err := retainedClientIds("a\nb/../c\n"); err == nil {
		t.Fatal("accepted an unsafe client id")
	}
}

func TestSetupReadsCredentialsOnlyFromItsArgument(t *testing.T) {
	f := newFixture(t)
	f.setup()
	if f.host.pushed["credentials"] != "ib@example.invalid\n"+testPassword+"\n" {
		t.Fatalf("pushed credentials %q", f.host.pushed["credentials"])
	}
	for _, content := range f.host.pushed {
		if strings.Contains(content, "decoy") {
			t.Fatal("setup read the retired URNETWORK_INSUFFICIENT_BALANCE_CREDENTIALS")
		}
	}
}

func TestSetupWithoutAnArgumentIgnoresTheRetiredEnvVar(t *testing.T) {
	for _, value := range []string{"decoy", "not a path", "/nonexistent/credentials"} {
		f := newFixture(t)
		if value != "decoy" {
			env := map[string]string{
				"URNETWORK_ROOT":                             f.root,
				"URNETWORK_INSUFFICIENT_BALANCE_STATE":       f.state,
				"URNETWORK_INSUFFICIENT_BALANCE_CREDENTIALS": value,
				"ANDROID_SDK_ROOT":                           "/sdk",
			}
			c, err := configFromEnv(func(k string) string { return env[k] }, f.driver.config.here)
			if err != nil {
				t.Fatalf("env var %q broke the configuration: %v", value, err)
			}
			f.driver.config = c
		}
		_, errLine, code := f.run("setup")
		if code == 0 || !strings.Contains(errLine, "credentials file") {
			t.Fatalf("env var %q: setup without an argument: %q", value, errLine)
		}
		if len(f.host.calls) != 0 || len(f.host.pushed) != 0 {
			t.Fatalf("env var %q: setup without an argument reached the host: %v", value, f.host.calls)
		}
		// with the argument, the env var does not matter
		f.setup()
		if f.host.pushed["credentials"] != "ib@example.invalid\n"+testPassword+"\n" {
			t.Fatalf("env var %q: pushed credentials %q", value, f.host.pushed["credentials"])
		}
	}
}

func TestSetupRejectsUnsafeCredentialArguments(t *testing.T) {
	f := newFixture(t)
	dir := filepath.Join(f.root, "runner")
	groupReadable := writeCredentials(t, filepath.Join(dir, "group"), "a@example.invalid", "p")
	os.Chmod(groupReadable, 0640)
	malformed := filepath.Join(dir, "malformed")
	os.WriteFile(malformed, []byte("email: a@example.invalid\n"), 0600)
	relative, err := filepath.Rel(mustGetwd(t), f.credentials)
	if err != nil {
		t.Fatal(err)
	}
	cases := map[string][]string{
		"relative path":  {"setup", relative},
		"bare name":      {"setup", "credentials"},
		"group readable": {"setup", groupReadable},
		"malformed":      {"setup", malformed},
		"missing":        {"setup", filepath.Join(dir, "missing")},
		"directory":      {"setup", dir},
		"extra argument": {"setup", f.credentials, f.credentials},
	}
	for name, args := range cases {
		_, errLine, code := f.run(args...)
		if code == 0 {
			t.Fatalf("%s: setup accepted %v", name, args)
		}
		if strings.Contains(errLine, testPassword) || strings.Contains(errLine, "a@example.invalid") {
			t.Fatalf("%s: stderr carried a credential: %q", name, errLine)
		}
		if indexOf(f.host.calls, "start ") != -1 || len(f.host.pushed) != 0 {
			t.Fatalf("%s: setup went ahead: %v", name, f.host.calls)
		}
		if _, err := os.Stat(filepath.Join(f.state, stateName)); !os.IsNotExist(err) {
			t.Fatalf("%s: setup left driver state", name)
		}
	}
}

func mustGetwd(t *testing.T) string {
	wd, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	return wd
}

// Verbs after setup are separate processes: they reach the credentials only
// through the path setup recorded in the state directory.
func TestLaterVerbsFindTheCredentialsThroughTheStateDir(t *testing.T) {
	f := newFixture(t)
	f.setup()
	b, err := os.ReadFile(filepath.Join(f.state, stateName))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(b), testPassword) {
		t.Fatal("driver state carries the password")
	}

	// redaction in a later verb
	f.host.answer = func(command string) string {
		return "command=" + strings.Split(command, "|")[0] + "\nstate=failed\nerror=signed in as ib@example.invalid with " + testPassword + "\n"
	}
	_, errLine, code := f.run("observe")
	if code == 0 || strings.Contains(errLine, testPassword) || strings.Contains(errLine, "ib@example.invalid") || !strings.Contains(errLine, "[redacted]") {
		t.Fatalf("observe stderr not redacted: %q", errLine)
	}

	// client release at teardown signs in as the argument's account
	f.host.answer = func(command string) string {
		return "command=" + strings.Split(command, "|")[0] + "\nstate=complete\n"
	}
	f.host.files[deviceClientsPath] = "client-a\n"
	if _, errLine, code := f.run("teardown"); code != 0 {
		t.Fatalf("teardown: %s", errLine)
	}
	if !reflect.DeepEqual(f.host.cleanupCredentials, []string{"ib@example.invalid\n" + testPassword + "\n"}) {
		t.Fatalf("client cleanup credentials %q", f.host.cleanupCredentials)
	}
}

// The runner's kill-switch case is a second setup with a new account and a new
// state directory after the first teardown.
func TestSecondCaseSignsInWithItsOwnAccount(t *testing.T) {
	f := newFixture(t)
	f.setup()
	f.host.answer = func(command string) string {
		return "command=" + strings.Split(command, "|")[0] + "\nstate=complete\n"
	}
	if _, errLine, code := f.run("teardown"); code != 0 {
		t.Fatalf("teardown: %s", errLine)
	}
	if _, err := os.Stat(filepath.Join(f.state, "credentials")); !os.IsNotExist(err) {
		t.Fatal("teardown left a credentials copy")
	}
	f.state = filepath.Join(f.root, "artifacts", "android", "kill-switch")
	f.driver.config.stateDir = f.state
	f.credentials = writeCredentials(t, filepath.Join(f.root, "runner", "case-2-credentials"), "ib2@example.invalid", "second password")
	f.host.pushed = map[string]string{}
	f.host.answer = nil
	f.setup()
	if f.host.pushed["credentials"] != "ib2@example.invalid\nsecond password\n" {
		t.Fatalf("second case pushed %q", f.host.pushed["credentials"])
	}
}
