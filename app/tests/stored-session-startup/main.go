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
	fixtures := filepath.Join(root, "app/tests/stored-session-startup")
	if *sourcePath == "" {
		*sourcePath = filepath.Join(base, "MainApplication.kt")
	}
	application := mustRead(*sourcePath)
	device := mustRead(filepath.Join(base, "DeviceManager.kt"))
	login := mustRead(filepath.Join(base, "LoginStartupState.kt"))
	methodMarkers := []string{
		"    private fun updateActiveNetworkSpace(networkSpace: NetworkSpace) {",
		"    private fun startClientSession(byClientJwt: String): ClientSessionStartResult {",
		"    private fun logoutStaleLocalState(localState: LocalState) {",
		"    fun logout() {",
		"    private fun logoutInternal() {",
		"    private fun DeviceInitFailure.asLoginStartupFailure(): LoginStartupFailure = when (this) {",
	}
	var methods []string
	for _, marker := range methodMarkers {
		methods = append(methods, block(application, marker, "\n    }\n"))
	}
	types := []string{
		block(device, "enum class DeviceInitFailure {", "\n}\n"),
		block(device, "sealed class DeviceInitResult {", "\n}\n"),
		block(login, "enum class LoginStartupStage(val wireValue: String) {", "\n}\n"),
		block(login, "enum class LoginStartupFailure(val wireValue: String) {", "\n}\n"),
		block(login, "internal sealed class ClientSessionStartResult {", "\n}\n"),
	}
	listeners := block(application, "        deviceManager.onLogoutStarted = { signedOutDevice ->", "\n        val deviceInitResult")
	listeners = strings.TrimSuffix(listeners, "\n        val deviceInitResult")
	generated := mustRead(filepath.Join(fixtures, "BoundaryFixture.kt"))
	for marker, body := range map[string]string{
		"// @production_methods@":               strings.Join(methods, "\n"),
		"// @production_types@":                 strings.Join(types, "\n"),
		"// @production_auth_logout_listeners@": listeners,
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
	runtimeJars := []string{stdlib, annotations, junit, hamcrest}
	compileSources := []string{
		generatedPath,
		filepath.Join(fixtures, "AnalyticsFixture.kt"),
		filepath.Join(fixtures, "StoredSessionStartupBoundaryTest.kt"),
		filepath.Join(root, "app/app/src/test/java/com/bringyour/network/ConnectSignOutFenceWiringTest.kt"),
		filepath.Join(root, "app/app/src/test/java/com/bringyour/network/QuickConnectSignOutWiringTest.kt"),
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
		Scope:    "Exact Kotlin method bodies with inert framework/SDK substitutes; not Android, gomobile, real SDK storage, UI, or wire qualification",
		Inputs:   map[string]string{},
		Compiler: compileCommand,
	}
	inputs := append([]string{*sourcePath, filepath.Join(base, "DeviceManager.kt"), filepath.Join(base, "LoginStartupState.kt"), filepath.Join(fixtures, "BoundaryFixture.kt"), filepath.Join(fixtures, "main.go")}, compileSources...)
	inputs = append(inputs, compilerJars...)
	inputs = append(inputs, junit, hamcrest)
	for _, path := range inputs {
		sum := sha256.Sum256([]byte(mustRead(path)))
		receipt.Inputs[path] = hex.EncodeToString(sum[:])
	}
	receipt.CompileExit = run(root, filepath.Join(output, "compile.log"), compileCommand)
	exitCode := 2
	if receipt.CompileExit == 0 {
		receipt.Tests = []string{
			java, "-cp", classes + string(os.PathListSeparator) + classpath, "org.junit.runner.JUnitCore",
			"com.bringyour.network.StoredSessionStartupBoundaryTest",
			"com.bringyour.network.ConnectSignOutFenceWiringTest",
			"com.bringyour.network.QuickConnectSignOutWiringTest",
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
