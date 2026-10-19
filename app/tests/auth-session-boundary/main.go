// Compiles exact application boundary bodies against inert JVM substitutes.
// It never configures Gradle, opens application storage, starts Android, or fetches tools.
package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// A compile failure remains distinct from a behavioral regression failure.
type runReceipt struct {
	Scope       string            `json:"scope"`
	Inputs      map[string]string `json:"inputs_sha256"`
	Compiler    []string          `json:"compiler_command"`
	Tests       []string          `json:"test_command,omitempty"`
	CompileExit int               `json:"compile_exit"`
	TestExit    *int              `json:"test_exit,omitempty"`
}

// Required fixtures and source boundaries fail closed when their shape changes.
func mustRead(path string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		panic(err)
	}
	return string(data)
}

// Markers include indentation, so nested blocks cannot end an extraction.
func block(source, marker, end string) string {
	if strings.Count(source, marker) != 1 {
		panic("nonunique or absent source marker: " + marker)
	}
	start := strings.Index(source, marker)
	finish := strings.Index(source[start:], end)
	if finish < 0 {
		panic("missing source boundary after " + marker)
	}
	return source[start : start+finish+len(end)]
}

// Only an already cached binary jar at the selected version can be used.
func cachedJar(cache, group, artifact, version string) string {
	paths, err := filepath.Glob(filepath.Join(cache, group, artifact, version, "*", artifact+"-"+version+".jar"))
	if err != nil || len(paths) != 1 {
		panic(fmt.Sprintf("need exactly one cached %s:%s:%s jar; found %d", group, artifact, version, len(paths)))
	}
	return paths[0]
}

// Child output is retained verbatim; no shell or build configuration is evaluated.
func run(directory, logPath string, args []string) int {
	command := exec.Command(args[0], args[1:]...)
	command.Dir = directory
	output, err := command.CombinedOutput()
	if writeErr := os.WriteFile(logPath, output, 0600); writeErr != nil {
		panic(writeErr)
	}
	fmt.Print(string(output))
	if err == nil {
		return 0
	}
	if exit, ok := err.(*exec.ExitError); ok {
		return exit.ExitCode()
	}
	panic(err)
}

// Source hashes bind this standalone boundary result; it is not a native app gate.
func main() {
	repo := flag.String("repo", ".", "Android repository root")
	managerPath := flag.String("manager", "", "DeviceManager preimage or candidate")
	continuation := flag.Bool("continuation", false, "also run broader request-origin regressions")
	adjacent := flag.Bool("adjacent", false, "also run current LoginStartupStateTest (candidate API)")
	activityPath := flag.String("activity", "", "LoginActivity preimage or candidate")
	instantPath := flag.String("instant", "", "CreateNetworkInstantViewModel preimage or candidate")
	sourcePath := flag.String("source", "", "MainApplication preimage or candidate; defaults to current source")
	outputDir := flag.String("output", "", "required output directory for generated source, logs and receipt")
	flag.Parse()
	if *outputDir == "" {
		panic("-output is required")
	}
	root, err := filepath.Abs(*repo)
	if err != nil {
		panic(err)
	}
	output, err := filepath.Abs(*outputDir)
	if err != nil {
		panic(err)
	}
	if err := os.MkdirAll(output, 0700); err != nil {
		panic(err)
	}
	base := filepath.Join(root, "app/app/src/main/java/com/bringyour/network")
	fixtures := filepath.Join(root, "app/tests/auth-session-boundary")
	if *sourcePath == "" {
		*sourcePath = filepath.Join(base, "MainApplication.kt")
	}
	application := mustRead(*sourcePath)
	if *managerPath == "" {
		*managerPath = filepath.Join(base, "DeviceManager.kt")
	}
	device := mustRead(*managerPath)
	methodMarkers := []string{
		"    private fun updateActiveNetworkSpace(networkSpace: NetworkSpace) {",
		"    fun login(byJwt: String, newNetwork: Boolean = false): Boolean {",
		"    internal fun authenticateNetworkSession(",
		"    private fun dispatchLoginAction(action: () -> Unit) {",
		"    fun stop() {",
		"    private fun startClientSession(byClientJwt: String): ClientSessionStartResult {",

		"    fun logout() {",
		"    private fun logoutInternal() {",
		"    private fun DeviceInitFailure.asLoginStartupFailure(): LoginStartupFailure = when (this) {",
	}
	var methods []string
	for _, marker := range methodMarkers {
		methods = append(methods, block(application, marker, "\n    }\n"))
	}
	owned := strings.Contains(application, "    fun beginLoginRequest(): LoginRequest?")
	if owned {
		for _, marker := range []string{
			"    private fun captureLoginRequest(",
			"    internal fun authenticateWithPassword(",
			"    fun dispatchLoginResult(request:",
			"    internal fun dispatchLoginContinuation(request:",
			"    private fun applySelectedNetworkSpace(",
			"    private fun login(request:",
			"    internal fun persistNetworkSession(",
			"    internal fun authenticateLoginClient(request:",
		} {
			methods = append(methods, block(application, marker, "\n    }\n"))
		}
		for _, marker := range []string{
			"    fun beginLoginRequest(): LoginRequest?",
			"    fun ownsLoginRequest(request:",
			"    fun isCurrentLoginRequest(request:",
			"    private fun isPendingLoginRequest(request:",
		} {
			methods = append(methods, block(application, marker, "\n\n"))
		}
	} else {
		methods = append(methods, block(application, "    internal fun authenticateLoginClient(callback: (LoginClientCompletion) -> Unit) {", "\n    }\n"))
		methods = append(methods, block(application, "    private fun logoutStaleLocalState(localState: LocalState) {", "\n    }\n"))
		// Compatibility adapters only supply the new call shape. They delegate
		// into the unchanged preimage policy, without adding ownership checks.
		methods = append(methods, `
    fun authenticateWithPassword(user: String, password: String, completion: (LoginRequest, PasswordLoginCompletion) -> Unit): Long? = passwordLoginCoordinator.authenticate(user, password) { completion(activeLoginRequest!!, it) }
    fun beginLoginRequest(): LoginRequest? = captureLoginRequest(loginStartupTracker.begin(LoginStartupStage.NETWORK_SESSION_PERSISTENCE))
    private fun captureLoginRequest(id: Long?): LoginRequest? = id?.let { LoginRequest(it, networkSpaceManagerProvider.current!!, api!!, asyncLocalState!!.localState!!).also { activeLoginRequest = it } }
    fun ownsLoginRequest(request: LoginRequest) = true
    fun isCurrentLoginRequest(request: LoginRequest) = true
    fun dispatchLoginResult(request: LoginRequest, action: () -> Unit) = dispatchLoginAction(action)
    fun dispatchLoginContinuation(request: LoginRequest, action: () -> Unit) = dispatchLoginAction(action)
    fun authenticateNetworkSession(request: LoginRequest, jwt: String, newNetwork: Boolean, completion: (LoginClientCompletion) -> Unit) = authenticateNetworkSession(jwt, newNetwork, completion)
    fun persistNetworkSession(request: LoginRequest, jwt: String, newNetwork: Boolean, completion: (Boolean) -> Unit) = dispatchLoginAction { completion(login(jwt, newNetwork)) }
    fun authenticateLoginClient(request: LoginRequest, completion: (LoginClientCompletion) -> Unit) = authenticateLoginClient(completion)
    private fun applySelectedNetworkSpace(space: NetworkSpace): Boolean { updateActiveNetworkSpace(space); return true }
`)
	}

	types := []string{
		block(device, "enum class DeviceInitFailure {", "\n}\n"),
		block(device, "sealed class DeviceInitResult {", "\n}\n"),
		block(device, "internal sealed class DeviceCreationResult<out T> {", "\n}\n"),
		block(device, "internal sealed class DeviceConfigurationResult<out T> {", "\n}\n"),
		block(device, "internal fun <T, K> createDeviceWithStoredKey(", "\n}\n"),
		block(device, "internal fun <T> configureCreatedDevice(", "\n}\n"),
		block(device, "internal fun enableDeviceAutoSave(", "\n}\n"),
	}
	commandsPath := filepath.Join(base, "ui/connect/ConnectCommands.kt")
	commands := mustRead(commandsPath)
	types = append(types, block(commands, "internal class ConnectCommands(", "\n}\n"),
		"internal const val CONNECT_COMMAND_STALL_MILLIS = 10_000L")
	initPrefix := block(application, "        deviceManager.onLogoutStarted = { signedOutDevice ->", "\n        resetTunnelRecoveryState()")
	declaration := block(device, "    var onAuthLogout:", "\n")
	if *activityPath == "" {
		*activityPath = filepath.Join(root, "app/app/src/google/java/com/bringyour/network/LoginActivity.kt")
	}
	activity := mustRead(*activityPath)
	activityMethods := block(activity, "    fun finishAuthenticatedLoginAfterWelcome(", "\n    }\n") + block(activity, "    fun finishAuthenticatedLoginNow(", "\n    }\n")
	if !strings.Contains(activityMethods, "request: LoginRequest") {
		activityMethods += `
    fun finishAuthenticatedLoginAfterWelcome(request: LoginRequest) = finishAuthenticatedLoginAfterWelcome()
    fun finishAuthenticatedLoginNow(request: LoginRequest) = finishAuthenticatedLoginNow()
`
	}
	if *instantPath == "" {
		*instantPath = filepath.Join(base, "ui/login/CreateNetworkInstantViewModel.kt")
	}
	instant := block(mustRead(*instantPath), "    fun createNetwork(", "\n    }\n")
	instantAdapter := `fun createOwned(request: LoginRequest, complete: (LoginRequest, String, Boolean, (Boolean) -> Unit) -> Unit) = createNetwork(request, true, appLogin = complete)`
	if !strings.Contains(instant, "        request: LoginRequest,") {
		instantAdapter = `fun createOwned(request: LoginRequest, complete: (LoginRequest, String, Boolean, (Boolean) -> Unit) -> Unit) = createNetwork(true, appLogin = { jwt, newNetwork, callback -> complete(request, jwt, newNetwork, callback) })`
	}
	requestPath := filepath.Join(base, "LoginRequest.kt")
	requestSource := mustRead(requestPath)
	requestSource = requestSource[strings.Index(requestSource, "class LoginRequest internal constructor("):]
	requestSource = "internal " + requestSource
	generated := mustRead(filepath.Join(fixtures, "BoundaryFixture.kt"))
	for marker, body := range map[string]string{
		"// @production_activity_methods@":        activityMethods,
		"// @production_instant_create@":          instant + instantAdapter,
		"// @production_login_request@":           requestSource,
		"// @production_methods@":                 strings.Join(methods, "\n"),
		"// @production_types@":                   strings.Join(types, "\n"),
		"// @production_application_init_prefix@": initPrefix,
		"// @production_callback_declaration@":    declaration,
		"// @production_device_init@":             block(device, "    fun initDevice(", "\n    }\n"),
	} {
		if strings.Count(generated, marker) != 1 {
			panic("nonunique fixture marker: " + marker)
		}
		generated = strings.Replace(generated, marker, body, 1)
	}
	generatedPath := filepath.Join(output, "MainApplicationBoundary.kt")
	if err := os.WriteFile(generatedPath, []byte(generated), 0600); err != nil {
		panic(err)
	}
	userHome, err := os.UserHomeDir()
	if err != nil {
		panic(err)
	}
	cache := filepath.Join(userHome, ".gradle/caches/modules-2/files-2.1")
	compiler := cachedJar(cache, "org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.21")
	stdlib := cachedJar(cache, "org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21")
	script := cachedJar(cache, "org.jetbrains.kotlin", "kotlin-script-runtime", "2.3.21")
	reflect := cachedJar(cache, "org.jetbrains.kotlin", "kotlin-reflect", "2.3.21")
	coroutines := cachedJar(cache, "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
	annotations := cachedJar(cache, "org.jetbrains", "annotations", "13.0")
	junit := cachedJar(cache, "junit", "junit", "4.13.2")
	hamcrest := cachedJar(cache, "org.hamcrest", "hamcrest-core", "1.3")
	java, err := exec.LookPath("java")
	if err != nil {
		panic(err)
	}
	compilerJars := []string{compiler, stdlib, script, reflect, coroutines, annotations}
	jsonJar := cachedJar(cache, "org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.11.0")
	coreJar := cachedJar(cache, "org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.11.0")
	coroutineTests := cachedJar(cache, "org.jetbrains.kotlinx", "kotlinx-coroutines-test-jvm", "1.9.0")
	runtimeJars := []string{coroutineTests, stdlib, annotations, junit, hamcrest, coroutines, jsonJar, coreJar}
	compileSources := []string{
		generatedPath,
		filepath.Join(root, "app/tests/stored-session-startup/AnalyticsFixture.kt"),
		filepath.Join(fixtures, "AuthSessionBoundaryTest.kt"),
		filepath.Join(fixtures, "AndroidQueueFixture.kt"),
		filepath.Join(fixtures, "VerifySendFixture.kt"),
		filepath.Join(base, "LoginStartupState.kt"),
	}
	if *continuation {
		compileSources = append(compileSources, filepath.Join(fixtures, "AuthOriginBoundaryTest.kt"))
	}
	if *adjacent {
		compileSources = append(compileSources,
			filepath.Join(root, "app/app/src/test/java/com/bringyour/network/LoginStartupStateTest.kt"),
			filepath.Join(root, "app/app/src/test/java/com/bringyour/network/ConnectSignOutFenceWiringTest.kt"),
			filepath.Join(root, "app/app/src/test/java/com/bringyour/network/QuickConnectSignOutWiringTest.kt"),
			filepath.Join(root, "app/app/src/test/java/com/bringyour/network/ui/login/SignInNoticeWiringTest.kt"),
		)
	}
	classes := filepath.Join(output, "classes")
	classpath := strings.Join(runtimeJars, string(os.PathListSeparator))
	compileCommand := []string{
		java, "-cp", strings.Join(compilerJars, string(os.PathListSeparator)),
		"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
		"-jvm-target", "21", "-classpath", classpath, "-d", classes,
	}
	compileCommand = append(compileCommand, compileSources...)
	receipt := runReceipt{
		Scope:    "Exact selected Kotlin owning methods and application init prefix with inert framework/SDK substitutes; not Android, gomobile, real SDK storage, UI, or wire qualification",
		Inputs:   map[string]string{},
		Compiler: compileCommand,
	}
	inputs := append([]string{*activityPath, *instantPath, requestPath, *sourcePath, *managerPath, commandsPath, filepath.Join(base, "LoginStartupState.kt"), filepath.Join(fixtures, "BoundaryFixture.kt"), filepath.Join(fixtures, "main.go")}, compileSources...)
	inputs = append(inputs, compilerJars...)
	inputs = append(inputs, runtimeJars...)
	for _, path := range inputs {
		sum := sha256.Sum256([]byte(mustRead(path)))
		receipt.Inputs[path] = hex.EncodeToString(sum[:])
	}
	receipt.CompileExit = run(root, filepath.Join(output, "compile.log"), compileCommand)
	exitCode := 2
	if receipt.CompileExit == 0 {
		receipt.Tests = []string{
			java, "-cp", classes + string(os.PathListSeparator) + classpath, "org.junit.runner.JUnitCore",
			"com.bringyour.network.AuthSessionBoundaryTest",
		}
		if *continuation {
			receipt.Tests = append(receipt.Tests, "com.bringyour.network.AuthOriginBoundaryTest")
		}
		if *adjacent {
			receipt.Tests = append(receipt.Tests,
				"com.bringyour.network.LoginStartupStateTest",
				"com.bringyour.network.ConnectSignOutFenceWiringTest",
				"com.bringyour.network.QuickConnectSignOutWiringTest",
				"com.bringyour.network.ui.login.SignInNoticeWiringTest",
			)
		}
		exitCode = run(root, filepath.Join(output, "tests.log"), receipt.Tests)
		receipt.TestExit = &exitCode
	}
	encoded, err := json.MarshalIndent(receipt, "", "  ")
	if err != nil {
		panic(err)
	}
	if err := os.WriteFile(filepath.Join(output, "receipt.json"), append(encoded, '\n'), 0600); err != nil {
		panic(err)
	}
	os.Exit(exitCode)
}
