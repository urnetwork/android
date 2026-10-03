// test-insufficient-balance-driver.go is the Android half of MAIN's
// insufficient-balance case (tests/TEST-MAIN.md, tests/runner/RUN-MAIN.md).
// tests/runner/balance runs `test-insufficient-balance-driver <verb> [arg]`;
// each verb prints exactly one JSON object on stdout, or exits nonzero with one
// redacted stderr line.
//
// It reuses MAIN's Android mechanisms instead of a parallel stack:
//   - the owned acceptance AVD, started, proven, prepared, animation-frozen,
//     uninstalled and stopped through test-main-lib.sh's functions;
//   - the APK pair MAIN cached in tests/__acceptance__/build/github when its
//     input fingerprint still matches, else the same Gradle build MAIN runs;
//   - a long-lived, host-commanded instrumentation session
//     (InsufficientBalanceSessionTest), the PhysicalLowbarSessionTest pattern:
//     the VPN service lives in the app process, so per-step instrumentation
//     would restart the app and end the tunnel;
//   - private files installed through adb push + run-as, and the shared
//     client-cleanup.mjs release of retained clients.
//
// Never the reserved PERF phones: only an emulator this driver started and
// whose instance id it proves before every mutation is touched.
//
// State persists between verbs in $URNETWORK_INSUFFICIENT_BALANCE_STATE.
// Credentials are read only from $URNETWORK_INSUFFICIENT_BALANCE_CREDENTIALS
// and reach the device only as a private file; they are never printed.
//
// Build-free: GOWORK=off go run test-insufficient-balance-driver.go (standard
// library only).
package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"syscall"
	"time"
)

const (
	packageName     = "com.bringyour.network"
	testPackageName = "com.bringyour.network.test"
	sessionClass    = "com.bringyour.network.acceptance.InsufficientBalanceSessionTest"
	runnerComponent = testPackageName + "/androidx.test.runner.AndroidJUnitRunner"

	devicePrivateDir  = "files/acceptance"
	deviceCommandPath = devicePrivateDir + "/insufficient-balance-command"
	deviceStatusPath  = devicePrivateDir + "/insufficient-balance-status"
	deviceClientsPath = devicePrivateDir + "/active-client-ids"

	defaultAvd  = "urnetwork-acceptance"
	stateName   = "driver-state.json"
	cacheTarget = "github"
	cacheFlavor = "Github"
)

// Fixed per-verb bounds, each well inside the runner's 10 minute call limit.
type timing struct {
	Ready      time.Duration // instrumentation start until logged in on the connect screen
	Connect    time.Duration // includes the VPN consent dialog
	Probe      time.Duration // egress and traffic: the device's 45 s probe plus transfer
	Ui         time.Duration // observe, press-disconnect, kill-switch
	Finish     time.Duration
	StatusPoll time.Duration
}

var defaultTiming = timing{
	Ready:      5 * time.Minute,
	Connect:    3 * time.Minute,
	Probe:      90 * time.Second,
	Ui:         45 * time.Second,
	Finish:     2 * time.Minute,
	StatusPoll: 500 * time.Millisecond,
}

// ---- process boundary ----

// commandRunner is the only way the driver reaches the host. Tests replace it.
type commandRunner interface {
	// Run runs one bounded command and returns its stdout.
	Run(ctx context.Context, env []string, name string, args ...string) (string, error)
	// Start leaves a long-lived process running past this verb, its output in
	// logPath, and returns its pid.
	Start(env []string, logPath string, name string, args ...string) (int, error)
	Alive(pid int) bool
	Kill(pid int)
}

type clock interface {
	Now() time.Time
	Sleep(ctx context.Context, d time.Duration) error
}

type realClock struct{}

func (realClock) Now() time.Time { return time.Now() }
func (realClock) Sleep(ctx context.Context, d time.Duration) error {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-t.C:
		return nil
	}
}

type execRunner struct {
	callTimeout time.Duration
}

func (self *execRunner) Run(ctx context.Context, env []string, name string, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, self.callTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, name, args...)
	cmd.Env = append(os.Environ(), env...)
	cmd.WaitDelay = 5 * time.Second
	var stdout, stderr bytes.Buffer
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	if err := cmd.Run(); err != nil {
		return stdout.String(), fmt.Errorf("%w: %s", err, lastLine(stderr.String()))
	}
	return stdout.String(), nil
}

// Start detaches the child into its own process group with no inherited
// stdout, so the runner's pipe closes when this verb exits.
func (self *execRunner) Start(env []string, logPath string, name string, args ...string) (int, error) {
	log, err := os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
	if err != nil {
		return 0, err
	}
	defer log.Close()
	cmd := exec.Command(name, args...)
	cmd.Env = append(os.Environ(), env...)
	cmd.Stdout, cmd.Stderr = log, log
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := cmd.Start(); err != nil {
		return 0, err
	}
	pid := cmd.Process.Pid
	cmd.Process.Release()
	return pid, nil
}

func (self *execRunner) Alive(pid int) bool {
	return 0 < pid && syscall.Kill(pid, 0) == nil
}

func (self *execRunner) Kill(pid int) {
	if 0 < pid {
		syscall.Kill(pid, syscall.SIGTERM)
	}
}

// ---- configuration ----

type config struct {
	root        string // workspace root
	here        string // android repo
	stateDir    string
	credentials string
	adb         string
	emulator    string
	avd         string
	headless    bool
}

func configFromEnv(getenv func(string) string, here string) (config, error) {
	c := config{
		root:        getenv("URNETWORK_ROOT"),
		here:        here,
		stateDir:    getenv("URNETWORK_INSUFFICIENT_BALANCE_STATE"),
		credentials: getenv("URNETWORK_INSUFFICIENT_BALANCE_CREDENTIALS"),
		avd:         getenv("UR_ACCEPT_ANDROID_AVD"),
		headless:    getenv("UR_ACCEPT_ANDROID_WINDOW") != "1",
	}
	if c.root == "" {
		c.root = filepath.Dir(here)
	}
	if c.avd == "" {
		c.avd = defaultAvd
	}
	if !filepath.IsAbs(c.root) || !filepath.IsAbs(c.stateDir) || !filepath.IsAbs(c.credentials) {
		return c, errors.New("URNETWORK_ROOT, URNETWORK_INSUFFICIENT_BALANCE_STATE and URNETWORK_INSUFFICIENT_BALANCE_CREDENTIALS must be absolute")
	}
	sdkRoot := getenv("ANDROID_SDK_ROOT")
	if sdkRoot == "" {
		sdkRoot = getenv("ANDROID_HOME")
	}
	if sdkRoot == "" {
		sdkRoot = filepath.Join(getenv("HOME"), "Library", "Android", "sdk")
	}
	c.adb = filepath.Join(sdkRoot, "platform-tools", "adb")
	c.emulator = filepath.Join(sdkRoot, "emulator", "emulator")
	return c, nil
}

// ---- credentials ----

// readCredentials accepts the runner's private file: exactly one `email:` and
// one `password:`, mode without group/world access. Values are never printed.
func readCredentials(path string) (email, password string, err error) {
	info, err := os.Stat(path)
	if err != nil {
		return "", "", fmt.Errorf("insufficient-balance credentials: %w", err)
	}
	if info.Mode().Perm()&0077 != 0 {
		return "", "", errors.New("insufficient-balance credentials must not be group/world readable")
	}
	b, err := os.ReadFile(path)
	if err != nil {
		return "", "", err
	}
	values := map[string]string{}
	s := bufio.NewScanner(bytes.NewReader(b))
	for s.Scan() {
		line := strings.TrimSpace(s.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, ok := strings.Cut(line, ":")
		key = strings.TrimSpace(key)
		if !ok || (key != "email" && key != "password") || values[key] != "" {
			return "", "", errors.New("insufficient-balance credentials: only one email and one password key are allowed")
		}
		value = strings.TrimSpace(value)
		if 2 <= len(value) && (value[0] == '"' || value[0] == '\'') && value[len(value)-1] == value[0] {
			value = value[1 : len(value)-1]
		}
		values[key] = value
	}
	if values["email"] == "" || values["password"] == "" {
		return "", "", errors.New("insufficient-balance credentials: email and password are required")
	}
	if strings.ContainsAny(values["email"]+values["password"], "\r\n") {
		return "", "", errors.New("insufficient-balance credentials: values must be single-line")
	}
	return values["email"], values["password"], nil
}

// The two-line form MainAcceptanceTest, the session and client-cleanup.mjs read.
func twoLineCredentials(email, password string) []byte {
	return []byte(email + "\n" + password + "\n")
}

// ---- persisted state ----

type driverState struct {
	Serial        string `json:"serial"`
	EmulatorPid   int    `json:"emulator_pid"`
	OwnerToken    string `json:"owner_token"`
	BuildId       string `json:"build_id"`
	SessionPid    int    `json:"session_pid"`
	NextCommandId int    `json:"next_command_id"`
	Installed     bool   `json:"installed"`
	Animations    bool   `json:"animations"`
}

// ---- device status ----

// parseStatus reads the session's `key=value` status record.
func parseStatus(text string) (map[string]string, error) {
	fields := map[string]string{}
	for _, line := range strings.Split(strings.ReplaceAll(text, "\r", ""), "\n") {
		if line == "" {
			continue
		}
		key, value, ok := strings.Cut(line, "=")
		if !ok || !statusKeyPattern.MatchString(key) {
			return nil, errors.New("malformed session status")
		}
		if _, seen := fields[key]; seen {
			return nil, errors.New("duplicate session status field")
		}
		fields[key] = value
	}
	if fields["command"] == "" || fields["state"] == "" {
		return nil, errors.New("incomplete session status")
	}
	return fields, nil
}

var statusKeyPattern = regexp.MustCompile(`^[a-z_]{1,32}$`)

// observation is the runner's `observe` object.
type observation struct {
	ConnectRequested bool `json:"connect_requested"`
	Connected        bool `json:"connected"`
	Alert            bool `json:"insufficient_balance_alert"`
	DisconnectButton bool `json:"disconnect_visible"`
	UpgradeButton    bool `json:"upgrade_visible"`
	Notifications    int  `json:"insufficient_balance_notifications"`
}

func observationFromStatus(fields map[string]string) (observation, error) {
	var o observation
	flags := []struct {
		key    string
		target *bool
	}{
		{"connect_requested", &o.ConnectRequested},
		{"connected", &o.Connected},
		{"alert", &o.Alert},
		{"disconnect_visible", &o.DisconnectButton},
		{"upgrade_visible", &o.UpgradeButton},
	}
	for _, flag := range flags {
		switch fields[flag.key] {
		case "true":
			*flag.target = true
		case "false":
		default:
			return o, fmt.Errorf("session observation has no valid %s", flag.key)
		}
	}
	n, err := strconv.Atoi(fields["notifications"])
	if err != nil || n < 0 {
		return o, errors.New("session observation has no valid notifications count")
	}
	o.Notifications = n
	return o, nil
}

type egressResult struct {
	Ip    string `json:"ip,omitempty"`
	Error string `json:"error,omitempty"`
}

var ipPattern = regexp.MustCompile(`^[0-9A-Fa-f:.]{2,45}$`)

// egressFromStatus maps one second-UID probe. A probe that cannot leave is
// reported as an error, which the runner treats as held; only an address is
// ever compared against the direct path.
func egressFromStatus(fields map[string]string) (egressResult, error) {
	if ip, ok := fields["ip"]; ok {
		if !ipPattern.MatchString(ip) {
			return egressResult{}, errors.New("session egress probe returned an invalid address")
		}
		return egressResult{Ip: ip}, nil
	}
	if message, ok := fields["egress_error"]; ok {
		if message == "" {
			message = "egress probe failed"
		}
		return egressResult{Error: bounded(message, 200)}, nil
	}
	return egressResult{}, errors.New("session egress status has neither ip nor egress_error")
}

// ---- adb devices ----

// freeConsolePort picks the first even console port in [5554, 5584] that no
// attached emulator uses, as MAIN does.
func freeConsolePort(adbDevices string) (int, error) {
	used := map[string]bool{}
	for _, line := range strings.Split(adbDevices, "\n") {
		fields := strings.Fields(line)
		if 0 < len(fields) && strings.HasPrefix(fields[0], "emulator-") {
			used[strings.TrimPrefix(fields[0], "emulator-")] = true
		}
	}
	for port := 5554; port <= 5584; port += 2 {
		if !used[strconv.Itoa(port)] {
			return port, nil
		}
	}
	return 0, errors.New("no free Android emulator console port")
}

var clientIdPattern = regexp.MustCompile(`^[A-Za-z0-9._-]+$`)

// retainedClientIds validates the session's ledger before any id is released.
func retainedClientIds(text string) ([]string, error) {
	set := map[string]bool{}
	for _, line := range strings.Split(strings.ReplaceAll(text, "\r", ""), "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if !clientIdPattern.MatchString(line) {
			return nil, errors.New("invalid retained client id from Android")
		}
		set[line] = true
	}
	clientIds := make([]string, 0, len(set))
	for clientId := range set {
		clientIds = append(clientIds, clientId)
	}
	sort.Strings(clientIds)
	return clientIds, nil
}

// ---- driver ----

type driver struct {
	config config
	runner commandRunner
	clock  clock
	timing timing
	now    func() time.Time
	// values that must never reach output
	secrets []string
}

func (self *driver) statePath() string { return filepath.Join(self.config.stateDir, stateName) }
func (self *driver) libPath() string   { return filepath.Join(self.config.here, "test-main-lib.sh") }

func (self *driver) loadState() (*driverState, error) {
	b, err := os.ReadFile(self.statePath())
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var state driverState
	if err := json.Unmarshal(b, &state); err != nil {
		return nil, errors.New("driver state is corrupt")
	}
	return &state, nil
}

func (self *driver) saveState(state *driverState) error {
	b, err := json.Marshal(state)
	if err != nil {
		return err
	}
	temporary := self.statePath() + ".tmp"
	if err := os.WriteFile(temporary, b, 0600); err != nil {
		return err
	}
	return os.Rename(temporary, self.statePath())
}

func (self *driver) requireState() (*driverState, error) {
	state, err := self.loadState()
	if err == nil && (state == nil || state.Serial == "" || state.SessionPid == 0) {
		err = errors.New("setup has not completed")
	}
	return state, err
}

// lib runs one test-main-lib.sh function with MAIN's GNU timeout wrapper.
func (self *driver) lib(ctx context.Context, env []string, function string, args ...string) (string, error) {
	const prelude = `set -u
lib="$1"; shift
source "$lib"
ur_timeout="$(type -P timeout)" || { echo "GNU timeout is required" >&2; exit 127; }
timeout() { run_android_acceptance_timeout "$ur_timeout" "$@"; }
"$@"`
	out, err := self.runner.Run(ctx, env, "bash", append([]string{"-c", prelude, "bash", self.libPath(), function}, args...)...)
	if err != nil {
		return out, fmt.Errorf("%s: %w", function, err)
	}
	return out, nil
}

func (self *driver) timeoutExecutable(ctx context.Context) (string, error) {
	out, err := self.runner.Run(ctx, nil, "bash", "-c", "type -P timeout")
	path := strings.TrimSpace(out)
	if err != nil || !filepath.IsAbs(path) {
		return "", errors.New("GNU timeout is required (brew install coreutils)")
	}
	return path, nil
}

// authorize proves the emulator is still the one this driver started before
// any adb mutation, as MAIN's authorize_selected_device does.
func (self *driver) authorize(ctx context.Context, state *driverState) error {
	_, err := self.lib(ctx, nil, "android_acceptance_runner_owns_emulator",
		self.config.adb, state.Serial, self.config.avd, strconv.Itoa(state.EmulatorPid), state.OwnerToken)
	if err != nil {
		return fmt.Errorf("the acceptance emulator lost runner ownership: %w", err)
	}
	return nil
}

func (self *driver) adb(ctx context.Context, state *driverState, args ...string) (string, error) {
	return self.runner.Run(ctx, nil, self.config.adb, append([]string{"-s", state.Serial}, args...)...)
}

// installPrivateFile copies a host file into the app's private acceptance
// directory through a staging path that is always removed, then publishes it
// with a rename so the session never reads a partial file.
func (self *driver) installPrivateFile(ctx context.Context, state *driverState, source, destination string) error {
	staging := "/data/local/tmp/urnetwork-insufficient-balance-" + filepath.Base(destination)
	defer self.adb(context.WithoutCancel(ctx), state, "shell", "rm", "-f", staging)
	steps := [][]string{
		{"push", source, staging},
		{"shell", "run-as", packageName, "mkdir", "-p", devicePrivateDir},
		{"shell", "run-as", packageName, "cp", staging, destination + ".tmp"},
		{"shell", "run-as", packageName, "chmod", "600", destination + ".tmp"},
		{"shell", "run-as", packageName, "mv", "-f", destination + ".tmp", destination},
	}
	for _, step := range steps {
		if _, err := self.adb(ctx, state, step...); err != nil {
			return fmt.Errorf("install %s: %w", filepath.Base(destination), err)
		}
	}
	return nil
}

func (self *driver) readStatus(ctx context.Context, state *driverState) (map[string]string, error) {
	out, err := self.adb(ctx, state, "exec-out", "run-as", packageName, "cat", deviceStatusPath)
	if err != nil {
		return nil, nil // not written yet
	}
	return parseStatus(out)
}

// waitStatus polls until the session reports commandId complete or failed.
func (self *driver) waitStatus(ctx context.Context, state *driverState, commandId string, readyState string, limit time.Duration) (map[string]string, error) {
	deadline := self.clock.Now().Add(limit)
	for {
		fields, err := self.readStatus(ctx, state)
		if err != nil {
			return nil, err
		}
		if fields != nil && fields["command"] == commandId {
			switch fields["state"] {
			case readyState:
				return fields, nil
			case "failed":
				message := fields["error"]
				if message == "" {
					message = "no detail"
				}
				return nil, fmt.Errorf("session command failed: %s", bounded(message, 200))
			}
		}
		if !self.runner.Alive(state.SessionPid) {
			return nil, errors.New("the insufficient-balance instrumentation session exited; see session.log in the driver state")
		}
		if !self.clock.Now().Before(deadline) {
			return nil, fmt.Errorf("session did not answer within %s", limit)
		}
		if err := self.clock.Sleep(ctx, self.timing.StatusPoll); err != nil {
			return nil, err
		}
	}
}

func (self *driver) command(ctx context.Context, verb, argument string, limit time.Duration) (map[string]string, error) {
	state, err := self.requireState()
	if err != nil {
		return nil, err
	}
	return self.commandWithState(ctx, state, verb, argument, limit)
}

func (self *driver) commandWithState(ctx context.Context, state *driverState, verb, argument string, limit time.Duration) (map[string]string, error) {
	state.NextCommandId++
	commandId := strconv.Itoa(state.NextCommandId)
	if err := self.saveState(state); err != nil {
		return nil, err
	}
	if err := self.authorize(ctx, state); err != nil {
		return nil, err
	}
	local := filepath.Join(self.config.stateDir, "command")
	if err := os.WriteFile(local, []byte(commandId+"|"+verb+"|"+argument+"\n"), 0600); err != nil {
		return nil, err
	}
	if err := self.installPrivateFile(ctx, state, local, deviceCommandPath); err != nil {
		return nil, err
	}
	return self.waitStatus(ctx, state, commandId, "complete", limit)
}

// ---- verbs ----

type apkPair struct {
	app     string
	test    string
	buildId string
}

var buildIdPattern = regexp.MustCompile(`^[A-Za-z0-9._-]+$`)

// apks reuses MAIN's github APK cache when its input fingerprint matches this
// tree, else builds the pair with MAIN's Gradle tasks and refreshes the cache.
func (self *driver) apks(ctx context.Context) (apkPair, error) {
	sdkDir := filepath.Join(self.config.root, "sdk", "build", "android")
	sdkAar := filepath.Join(sdkDir, "URnetworkSdk.aar")
	sdkSources := filepath.Join(sdkDir, "URnetworkSdk-sources.jar")
	for _, path := range []string{sdkAar, sdkSources} {
		if _, err := os.Stat(path); err != nil {
			return apkPair{}, errors.New("the Android SDK artifact is missing; MAIN's android/test-main.sh builds it")
		}
	}
	cacheDir := filepath.Join(self.config.here, "tests", "__acceptance__", "build", cacheTarget)
	out, err := self.lib(ctx, nil, "android_acceptance_input_fingerprint", self.config.here, sdkAar, sdkSources, cacheTarget, cacheFlavor)
	if err != nil {
		return apkPair{}, err
	}
	fingerprint := strings.TrimSpace(out)
	pair := apkPair{app: filepath.Join(cacheDir, "app.apk"), test: filepath.Join(cacheDir, "test.apk")}
	if _, err := self.lib(ctx, nil, "android_acceptance_cache_is_current", cacheDir, fingerprint); err == nil {
		b, err := os.ReadFile(filepath.Join(cacheDir, "build-id"))
		pair.buildId = strings.TrimSpace(string(b))
		if err != nil || !buildIdPattern.MatchString(pair.buildId) {
			return apkPair{}, errors.New("cached APK build-id is invalid")
		}
		return pair, nil
	}

	pair.buildId = self.now().UTC().Format("20060102-150405") + "-insufficient-balance-" + cacheTarget
	appDir := filepath.Join(self.config.here, "app")
	if _, err := self.runner.Run(ctx, []string{"BRINGYOUR_HOME=" + self.config.root}, "bash", "-c",
		`cd "$1" && ./gradlew ":app:assemble${2}Debug" ":app:assemble${2}DebugAndroidTest" "-PurnetworkAcceptanceBuildId=$3"`,
		"bash", appDir, cacheFlavor, pair.buildId); err != nil {
		return apkPair{}, fmt.Errorf("build the Android APK pair: %w", err)
	}
	app, err := firstApk(filepath.Join(appDir, "app", "build", "outputs", "apk", cacheTarget, "debug"), true)
	if err != nil {
		return apkPair{}, err
	}
	test, err := firstApk(filepath.Join(appDir, "app", "build", "outputs", "apk", "androidTest", cacheTarget, "debug"), false)
	if err != nil {
		return apkPair{}, err
	}
	if _, err := self.lib(ctx, nil, "android_acceptance_cache_apks", app, test, cacheDir); err != nil {
		return apkPair{}, err
	}
	if _, err := self.lib(ctx, nil, "android_acceptance_write_cache_metadata", cacheDir, pair.buildId, fingerprint); err != nil {
		return apkPair{}, err
	}
	return pair, nil
}

// firstApk mirrors MAIN's locate_apks: the universal APK when there is one.
func firstApk(dir string, preferUniversal bool) (string, error) {
	matches, _ := filepath.Glob(filepath.Join(dir, "*.apk"))
	sort.Strings(matches)
	if preferUniversal {
		for _, match := range matches {
			if strings.Contains(filepath.Base(match), "universal") {
				return match, nil
			}
		}
	}
	if len(matches) == 0 {
		return "", fmt.Errorf("no APK in %s", dir)
	}
	return matches[0], nil
}

func (self *driver) setup(ctx context.Context) (any, error) {
	if err := os.MkdirAll(self.config.stateDir, 0700); err != nil {
		return nil, err
	}
	if state, err := self.loadState(); err != nil || state != nil {
		return nil, errors.New("a previous setup was not torn down")
	}
	email, password, err := readCredentials(self.config.credentials)
	if err != nil {
		return nil, err
	}
	self.secrets = append(self.secrets, email, password)
	timeoutExecutable, err := self.timeoutExecutable(ctx)
	if err != nil {
		return nil, err
	}
	pair, err := self.apks(ctx)
	if err != nil {
		return nil, err
	}

	// an AVD already running belongs to someone else; never adopt it
	if _, err := self.lib(ctx, nil, "android_acceptance_no_running_avd", self.config.adb, self.config.avd); err != nil {
		return nil, fmt.Errorf("AVD %s is already running outside this driver or its state is unknown: %w", self.config.avd, err)
	}
	devices, err := self.runner.Run(ctx, nil, self.config.adb, "devices")
	if err != nil {
		return nil, err
	}
	port, err := freeConsolePort(devices)
	if err != nil {
		return nil, err
	}
	state := &driverState{
		Serial:     "emulator-" + strconv.Itoa(port),
		OwnerToken: fmt.Sprintf("insufficient-balance-%d-%d", self.now().Unix(), os.Getpid()),
		BuildId:    pair.buildId,
	}
	emulatorArgs := []string{"-avd", self.config.avd, "-read-only", "-gpu", "host", "-port", strconv.Itoa(port),
		"-no-snapshot", "-no-boot-anim", "-netdelay", "none", "-netspeed", "full"}
	if self.config.headless {
		emulatorArgs = append(emulatorArgs, "-no-window")
	}
	emulatorLog := filepath.Join(self.config.stateDir, "emulator.log")
	// the lib function execs the emulator, so this pid is the emulator's
	state.EmulatorPid, err = self.runner.Start(nil, emulatorLog, "bash",
		append([]string{"-c", `source "$1"; shift; "$@"`, "bash", self.libPath(),
			"run_android_acceptance_shared_avd_emulator", self.config.emulator, emulatorLog, state.OwnerToken}, emulatorArgs...)...)
	if err != nil {
		return nil, fmt.Errorf("start the acceptance AVD: %w", err)
	}
	// saved before anything can fail, so teardown always stops it
	if err := self.saveState(state); err != nil {
		return nil, err
	}
	pid := strconv.Itoa(state.EmulatorPid)
	if _, err := self.lib(ctx, nil, "android_acceptance_wait_for_runner_owned_emulator",
		self.config.adb, state.Serial, self.config.avd, pid, state.OwnerToken, "120"); err != nil {
		return nil, fmt.Errorf("the acceptance AVD did not prove runner ownership: %w", err)
	}
	if _, err := self.lib(ctx, []string{"ANDROID_ACCEPTANCE_EMULATOR_OWNER_TOKEN=" + state.OwnerToken},
		"android_acceptance_prepare_owned_emulator", self.config.adb, state.Serial, self.config.avd, pid,
		filepath.Join(self.config.stateDir, "device"),
		filepath.Join(self.config.stateDir, "readiness.txt"),
		filepath.Join(self.config.stateDir, "interactive.txt")); err != nil {
		return nil, fmt.Errorf("the acceptance AVD is not ready: %w", err)
	}
	if err := self.authorize(ctx, state); err != nil {
		return nil, err
	}
	state.Animations = true
	if err := self.saveState(state); err != nil {
		return nil, err
	}
	if _, err := self.lib(ctx, nil, "android_acceptance_disable_animations", self.config.adb, state.Serial,
		filepath.Join(self.config.stateDir, "animation-scales"),
		filepath.Join(self.config.stateDir, "animations.txt")); err != nil {
		return nil, err
	}

	state.Installed = true
	if err := self.saveState(state); err != nil {
		return nil, err
	}
	if _, err := self.lib(ctx, nil, "android_acceptance_install_cell_apks", "full", timeoutExecutable,
		self.config.adb, state.Serial, pair.app, pair.test,
		filepath.Join(self.config.stateDir, "install-app.log"),
		filepath.Join(self.config.stateDir, "install-test.log")); err != nil {
		return nil, err
	}
	// a user who allows notifications; Android 13+ drops them otherwise
	sdkLevel, err := self.adb(ctx, state, "shell", "getprop", "ro.build.version.sdk")
	if err != nil {
		return nil, err
	}
	if level, _ := strconv.Atoi(strings.TrimSpace(sdkLevel)); 33 <= level {
		if _, err := self.adb(ctx, state, "shell", "pm", "grant", packageName, "android.permission.POST_NOTIFICATIONS"); err != nil {
			return nil, err
		}
	}

	credentials := filepath.Join(self.config.stateDir, "credentials")
	if err := os.WriteFile(credentials, twoLineCredentials(email, password), 0600); err != nil {
		return nil, err
	}
	err = self.installPrivateFile(ctx, state, credentials, devicePrivateDir+"/credentials")
	os.Remove(credentials)
	if err != nil {
		return nil, err
	}

	state.SessionPid, err = self.runner.Start(nil, filepath.Join(self.config.stateDir, "session.log"),
		self.config.adb, "-s", state.Serial, "shell", "am", "instrument", "-w", "-r",
		"-e", "class", sessionClass,
		"-e", "acceptanceBuildId", state.BuildId,
		runnerComponent)
	if err != nil {
		return nil, fmt.Errorf("start the insufficient-balance session: %w", err)
	}
	if err := self.saveState(state); err != nil {
		return nil, err
	}
	if _, err := self.waitStatus(ctx, state, "0", "ready", self.timing.Ready); err != nil {
		return nil, fmt.Errorf("sign in: %w", err)
	}
	// the session deletes its copy too; the kill switch is the Settings toggle
	return map[string]bool{"kill_switch_supported": true}, nil
}

func (self *driver) egress(ctx context.Context) (egressResult, error) {
	fields, err := self.command(ctx, "egress", "", self.timing.Probe)
	if err != nil {
		return egressResult{}, err
	}
	return egressFromStatus(fields)
}

func (self *driver) directEgress(ctx context.Context) (any, error) {
	e, err := self.egress(ctx)
	if err != nil {
		return nil, err
	}
	if e.Ip == "" {
		return nil, fmt.Errorf("no direct-path address: %s", e.Error)
	}
	return e, nil
}

func (self *driver) observe(ctx context.Context) (any, error) {
	fields, err := self.command(ctx, "observe", "", self.timing.Ui)
	if err != nil {
		return nil, err
	}
	return observationFromStatus(fields)
}

func (self *driver) simple(ctx context.Context, verb, argument string, limit time.Duration) (any, error) {
	if _, err := self.command(ctx, verb, argument, limit); err != nil {
		return nil, err
	}
	return struct{}{}, nil
}

// teardown removes everything setup created, on every path, and is
// idempotent. Each step runs even when an earlier one failed.
func (self *driver) teardown(ctx context.Context) (any, error) {
	state, err := self.loadState()
	if err != nil {
		return nil, err
	}
	if state == nil {
		return struct{}{}, nil
	}
	var errs []error
	owned := state.Serial != "" && self.authorize(ctx, state) == nil
	if owned && state.SessionPid != 0 && self.runner.Alive(state.SessionPid) {
		// disconnect, restore the kill switch setting and log out in the app
		if _, err := self.commandWithState(ctx, state, "finish", "", self.timing.Finish); err != nil {
			errs = append(errs, fmt.Errorf("finish session: %w", err))
		}
	}
	if owned && state.Installed {
		errs = append(errs, self.releaseClients(ctx, state))
	}
	if state.SessionPid != 0 {
		self.runner.Kill(state.SessionPid)
	}
	if owned && state.Animations {
		if _, err := self.lib(ctx, nil, "android_acceptance_restore_animations", self.config.adb, state.Serial,
			filepath.Join(self.config.stateDir, "animation-scales")); err != nil {
			errs = append(errs, err)
		}
	}
	if owned && state.Installed {
		timeoutExecutable, err := self.timeoutExecutable(ctx)
		if err != nil {
			errs = append(errs, err)
		} else {
			for _, name := range []string{packageName, testPackageName} {
				if _, err := self.lib(ctx, nil, "android_acceptance_uninstall_package", timeoutExecutable,
					self.config.adb, state.Serial, name,
					filepath.Join(self.config.stateDir, "uninstall-"+name+".log")); err != nil {
					errs = append(errs, err)
				}
			}
		}
	}
	if state.EmulatorPid != 0 {
		graceful := "0"
		if owned {
			self.adb(ctx, state, "emu", "kill")
			graceful = "150"
		}
		if _, err := self.lib(ctx, nil, "android_acceptance_stop_emulator_child", strconv.Itoa(state.EmulatorPid), graceful, "50"); err != nil {
			errs = append(errs, fmt.Errorf("stop the acceptance AVD: %w", err))
		}
	}
	os.Remove(filepath.Join(self.config.stateDir, "credentials"))
	if err := errors.Join(errs...); err != nil {
		return nil, err
	}
	if err := os.Remove(self.statePath()); err != nil && !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	return struct{}{}, nil
}

// releaseClients removes the network clients the session's logins allocated,
// through MAIN's shared client-cleanup.mjs.
func (self *driver) releaseClients(ctx context.Context, state *driverState) error {
	out, err := self.adb(ctx, state, "exec-out", "run-as", packageName, "sh", "-c",
		"if [ -f "+deviceClientsPath+" ]; then cat "+deviceClientsPath+"; fi")
	if err != nil {
		return fmt.Errorf("read retained clients: %w", err)
	}
	clientIds, err := retainedClientIds(out)
	if err != nil || len(clientIds) == 0 {
		return err
	}
	email, password, err := readCredentials(self.config.credentials)
	if err != nil {
		return err
	}
	self.secrets = append(self.secrets, email, password)
	dir := filepath.Join(self.config.stateDir, "cleanup-clients")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	credentials := filepath.Join(dir, "credentials")
	if err := os.WriteFile(credentials, twoLineCredentials(email, password), 0600); err != nil {
		return err
	}
	defer os.Remove(credentials)
	markers := []string{}
	for i, clientId := range clientIds {
		marker := filepath.Join(dir, fmt.Sprintf("active-client-id-%d", i+1))
		if err := os.WriteFile(marker, []byte(clientId+"\n"), 0600); err != nil {
			return err
		}
		markers = append(markers, marker)
	}
	if _, err := self.runner.Run(ctx, []string{"UR_ACCEPT_CREDENTIALS_FILE=" + credentials}, "node",
		append([]string{filepath.Join(self.config.root, "build", "all", "acceptance", "client-cleanup.mjs")}, markers...)...); err != nil {
		return fmt.Errorf("release retained clients: %w", err)
	}
	return nil
}

func (self *driver) dispatch(ctx context.Context, args []string) (any, error) {
	if len(args) == 0 {
		return nil, errors.New("usage: test-insufficient-balance-driver <verb> [arg]")
	}
	verb := args[0]
	wantArgs := 1
	if verb == "kill-switch" {
		wantArgs = 2
	}
	if len(args) != wantArgs {
		return nil, fmt.Errorf("%s: wrong number of arguments", verb)
	}
	switch verb {
	case "setup":
		return self.setup(ctx)
	case "direct-egress":
		return self.directEgress(ctx)
	case "connect":
		return self.simple(ctx, "connect", "", self.timing.Connect)
	case "observe":
		return self.observe(ctx)
	case "egress":
		return self.egress(ctx)
	case "traffic":
		return self.simple(ctx, "traffic", "", self.timing.Probe)
	case "press-disconnect":
		return self.simple(ctx, "press-disconnect", "", self.timing.Ui)
	case "kill-switch":
		if args[1] != "on" && args[1] != "off" {
			return nil, errors.New("kill-switch takes on or off")
		}
		return self.simple(ctx, "kill-switch", args[1], self.timing.Ui)
	case "teardown":
		return self.teardown(ctx)
	}
	return nil, fmt.Errorf("unknown verb %s", verb)
}

// redact removes every known secret, then bounds the line.
func (self *driver) redact(message string) string {
	for _, secret := range self.secrets {
		if secret != "" {
			message = strings.ReplaceAll(message, secret, "[redacted]")
		}
	}
	return bounded(strings.Join(strings.Fields(message), " "), 300)
}

// mainCode prints exactly one JSON object on success, and one redacted stderr
// line with a nonzero code on failure.
func mainCode(ctx context.Context, d *driver, args []string, stdout, stderr io.Writer) int {
	out, err := d.dispatch(ctx, args)
	if err == nil {
		var b []byte
		b, err = json.Marshal(out)
		if err == nil {
			fmt.Fprintln(stdout, string(b))
			return 0
		}
	}
	verb := "driver"
	if 0 < len(args) {
		verb = args[0]
	}
	fmt.Fprintln(stderr, d.redact("android insufficient-balance "+verb+": "+err.Error()))
	return 1
}

func bounded(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}

func lastLine(s string) string {
	lines := strings.Split(strings.TrimSpace(s), "\n")
	return bounded(strings.TrimSpace(lines[len(lines)-1]), 200)
}

func main() {
	here := os.Getenv("URNETWORK_ANDROID_DRIVER_HOME")
	c, err := configFromEnv(os.Getenv, here)
	d := &driver{config: c, runner: &execRunner{callTimeout: 9 * time.Minute}, clock: realClock{}, timing: defaultTiming, now: time.Now}
	if err != nil || !filepath.IsAbs(here) {
		if err == nil {
			err = errors.New("run through android/test-insufficient-balance-driver")
		}
		fmt.Fprintln(os.Stderr, "android insufficient-balance driver: "+err.Error())
		os.Exit(1)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 9*time.Minute+30*time.Second)
	defer cancel()
	os.Exit(mainCode(ctx, d, os.Args[1:], os.Stdout, os.Stderr))
}
