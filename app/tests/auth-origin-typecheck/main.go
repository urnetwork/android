// Offline changed-source compilation. Never invokes Gradle or reads project properties.
package main

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
)

func require(err error) {
	if err != nil {
		panic(err)
	}
}
func hash(path string) string {
	data, err := os.ReadFile(path)
	require(err)
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}
func cached(root, group, artifact, version string) string {
	p, e := filepath.Glob(filepath.Join(root, group, artifact, version, "*", artifact+"-"+version+".jar"))
	require(e)
	if len(p) != 1 {
		panic(artifact)
	}
	return p[0]
}
func extract(archive, dir string) []string {
	z, e := zip.OpenReader(archive)
	require(e)
	defer z.Close()
	out := []string{}
	for _, f := range z.File {
		if f.Name != "classes.jar" && !(strings.HasPrefix(f.Name, "libs/") && strings.HasSuffix(f.Name, ".jar")) {
			continue
		}
		r, e := f.Open()
		require(e)
		data, e := io.ReadAll(r)
		require(e)
		r.Close()
		p := filepath.Join(dir, filepath.Base(f.Name))
		require(os.MkdirAll(dir, 0700))
		require(os.WriteFile(p, data, 0600))
		out = append(out, p)
	}
	return out
}
func main() {
	repo := flag.String("repo", ".", "Android repository root")
	output := flag.String("output", "", "required disposable output")
	sdk := flag.String("sdk", "", "real offline SDK AAR")
	sourceRoot := flag.String("source-root", "", "optional captured preimage overlay; missing files use current source")
	narrow := flag.Bool("narrow", false, "compile actual DeviceManager/LoginRequest and native SDK contract only")
	flavor := flag.String("flavor", "google", "source flavor")
	flag.Parse()
	if *output == "" || *sdk == "" {
		panic("output and sdk required")
	}
	root, e := filepath.Abs(*repo)
	require(e)
	out, e := filepath.Abs(*output)
	require(e)
	require(os.MkdirAll(out, 0700))
	home, e := os.UserHomeDir()
	require(e)
	cache := filepath.Join(home, ".gradle/caches/modules-2/files-2.1")
	variant := map[string]string{"google": "playDebug", "ungoogle": "githubDebug", "solana_dapp": "solana_dappDebug", "ethos_dapp": "ethos_dappDebug"}[*flavor]
	if variant == "" {
		panic("flavor")
	}
	compiler := []string{cached(cache, "org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.21"), cached(cache, "org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"), cached(cache, "org.jetbrains.kotlin", "kotlin-script-runtime", "2.3.21"), cached(cache, "org.jetbrains.kotlin", "kotlin-reflect", "2.3.21"), cached(cache, "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2"), cached(cache, "org.jetbrains", "annotations", "13.0")}
	plugin := cached(cache, "org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", "2.3.21")
	cp := extract(*sdk, filepath.Join(out, "sdk"))
	inputs := map[string]string{*sdk: hash(*sdk), plugin: hash(plugin)}
	for _, p := range compiler {
		inputs[p] = hash(p)
	}
	platform := filepath.Join(home, "Library/Android/sdk/platforms/android-36/android.jar")
	inputs[platform] = hash(platform)
	cp = append(cp, platform)
	// Read dependency binaries already cached by the user's previous build, never project config.
	var deps []string
	require(filepath.WalkDir(cache, func(p string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		n := d.Name()
		if (strings.HasSuffix(n, ".jar") || strings.HasSuffix(n, ".aar")) && !strings.Contains(n, "-sources") && !strings.Contains(n, "-javadoc") {
			deps = append(deps, p)
		}
		return nil
	}))
	sort.Strings(deps)
	for i, p := range deps {
		inputs[p] = hash(p)
		if strings.HasSuffix(p, ".aar") {
			cp = append(cp, extract(p, filepath.Join(out, "aar", fmt.Sprint(i)))...)
		} else {
			cp = append(cp, p)
		}
	}
	oldClasses := filepath.Join(root, "app/app/build/tmp/kotlin-classes", variant)
	cp = append(cp, oldClasses, filepath.Join(root, "app/app/build/intermediates/javac", variant, "compile"+strings.ToUpper(variant[:1])+variant[1:]+"JavaWithJavac/classes"))
	rjars, e := filepath.Glob(filepath.Join(root, "app/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar", variant, "*", "R.jar"))
	require(e)
	cp = append(cp, rjars...)
	// Hash cached surrounding app class files as well; these are an explicit qualification limit.
	for _, dir := range cp {
		info, e := os.Stat(dir)
		require(e)
		if !info.IsDir() {
			continue
		}
		require(filepath.WalkDir(dir, func(p string, d os.DirEntry, e error) error {
			if e != nil {
				return e
			}
			if !d.IsDir() {
				inputs[p] = hash(p)
			}
			return nil
		}))
	}
	for _, p := range rjars {
		inputs[p] = hash(p)
	}
	base := filepath.Join(root, "app/app/src/main/java/com/bringyour/network")
	files := []string{"MainApplication.kt", "DeviceManager.kt", "LoginStartupState.kt", "LoginRequest.kt", "ui/LoginNavHost.kt", "ui/login/LoginUtils.kt", "ui/login/LoginPassword.kt", "ui/login/LoginCreateNetwork.kt", "ui/login/LoginVerify.kt", "ui/login/CreateNetworkInstant.kt", "ui/login/CreateNetworkInstantViewModel.kt", "ui/login/AuthCodeLoginSheet.kt", "ui/login/SeedphraseLoginSheet.kt", "ui/login/BittensorLogin.kt", "ui/login/SwitchAccountScreen.kt"}
	var sources []string
	for _, f := range files {
		sources = append(sources, filepath.Join(base, f))
	}
	for _, f := range []string{"LoginActivity.kt", "ui/login/LoginViewModel.kt", "ui/login/LoginInitial.kt"} {
		sources = append(sources, filepath.Join(root, "app/app/src", *flavor, "java/com/bringyour/network", f))
	}
	supportPath := filepath.Join(root, "app/tests/auth-origin-typecheck/support.txt")
	supportData, e := os.ReadFile(supportPath)
	require(e)
	inputs[supportPath] = hash(supportPath)
	seen := map[string]bool{}
	for _, p := range sources {
		seen[p] = true
	}
	for _, p := range strings.Fields(string(supportData)) {
		p = filepath.Join(root, p)
		if !seen[p] {
			sources = append(sources, p)
			seen[p] = true
		}
	}
	if *narrow {
		appPath := filepath.Join(base, "MainApplication.kt")
		data, e := os.ReadFile(appPath)
		require(e)
		text := string(data)
		start := strings.Index(text, "        const val IOS_MEMORY_AUDIT_PROFILE")
		end := strings.Index(text[start:], "        // Stable platform capability id")
		if start < 0 || end < 0 {
			panic("memory constants extraction")
		}
		memoryPath := filepath.Join(out, "MainApplicationMemoryBoundary.kt")
		require(os.WriteFile(memoryPath, []byte("package com.bringyour.network\nimport kotlin.math.min\nclass MainApplication { companion object {\n"+text[start:start+end]+"\n} }\n"), 0600))
		inputs[appPath] = hash(appPath)
		sources = []string{memoryPath, filepath.Join(base, "DeviceManager.kt"), filepath.Join(base, "LoginRequest.kt"), filepath.Join(root, "app/tests/auth-origin-typecheck/SdkIdentityContract.kt")}
	}
	if *sourceRoot != "" {
		for i, p := range sources {
			rel, e := filepath.Rel(root, p)
			require(e)
			candidate := filepath.Join(*sourceRoot, rel)
			if _, e := os.Stat(candidate); e == nil {
				sources[i] = candidate
			}
		}
	}
	for _, p := range sources {
		inputs[p] = hash(p)
	}
	java, e := exec.LookPath("java")
	require(e)
	args := []string{"-Xmx4g", "-cp", strings.Join(compiler, string(os.PathListSeparator)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21", "-Xplugin=" + plugin, "-Xfriend-paths=" + oldClasses, "-classpath", strings.Join(cp, string(os.PathListSeparator)), "-d", filepath.Join(out, "classes")}
	args = append(args, sources...)
	cmd := exec.Command(java, args...)
	cmd.Dir = root
	log, runErr := cmd.CombinedOutput()
	require(os.WriteFile(filepath.Join(out, "compile.log"), log, 0600))
	fmt.Print(string(log))
	code := 0
	if runErr != nil {
		if ex, ok := runErr.(*exec.ExitError); ok {
			code = ex.ExitCode()
		} else {
			panic(runErr)
		}
	}
	receipt := map[string]any{"scope": "Raw Kotlin changed-source compile against real offline ARM64 SDK binding plus cached Android/dependency binaries and previously compiled surrounding app; no Gradle, full app build, install or native Android execution", "flavor": *flavor, "narrow": *narrow, "source_root": *sourceRoot, "exit_code": code, "command": append([]string{java}, args...), "inputs_sha256": inputs}
	data, e := json.MarshalIndent(receipt, "", "  ")
	require(e)
	require(os.WriteFile(filepath.Join(out, "receipt.json"), append(data, '\n'), 0600))
	os.Exit(code)
}
