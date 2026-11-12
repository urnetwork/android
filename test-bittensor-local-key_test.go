package main

import (
	"encoding/xml"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strings"
	"testing"
)

// The payout key's Android side (apple/BTEASY.md, the port brief) is Kotlin and
// resources that can't run here, so these tests read them, as Apple's
// test-bittensor-local-key_test.go reads its Swift. The flow's own rules are
// tested in the sdk (bittensor_local_key_flow_test.go). These check what a run of
// the sdk's tests can't see:
//   - the decisions stay in the sdk: the app makes no key and signs nothing itself;
//   - the store keeps the phrase on this device, sealed by a Keystore key, never
//     replaced, out of backups, and a logout leaves it;
//   - nothing in the key's Kotlin can log, persist or hand the words to anything
//     but the Key screen and an explicit Copy;
//   - the Key screen's window is secure, the words hide on pause, and the
//     two-word fields learn nothing;
//   - sign out and account deletion go through their warnings;
//   - the owner's disclaimer shows once on every screen of the easy flows;
//   - every word on these screens is in the catalogs, in every locale.
// Run: GOWORK=off go test -count=1 test-bittensor-local-key_test.go

const (
	bittensorKeySourceRoot = "app/app/src/main/java/com/bringyour/network"
	bittensorKeyWalletDir  = bittensorKeySourceRoot + "/ui/wallet"
)

// the key's own Kotlin, where a leak would be
var bittensorKeySourceFiles = []string{
	"AndroidBittensorLocalKeyStore.kt",
	"BittensorKeyFlow.kt",
	"BittensorKeyModel.kt",
	"BittensorKeyPlatform.kt",
	"BittensorKeyCard.kt",
	"BittensorKeyScreen.kt",
	"BittensorKeySignOut.kt",
}

func bittensorKeyRoot(t *testing.T) string {
	t.Helper()
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("cannot locate the Android source")
	}
	return filepath.Dir(source)
}

func bittensorKeyRead(t *testing.T, rel string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(bittensorKeyRoot(t), rel))
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func bittensorKeyWallet(t *testing.T, name string) string {
	t.Helper()
	return bittensorKeyRead(t, filepath.Join(bittensorKeyWalletDir, name))
}

// The Kotlin without its comment lines: a word in a comment is not code.
func bittensorKeyCode(source string) string {
	var code strings.Builder
	for _, line := range strings.Split(source, "\n") {
		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(trimmed, "//") || strings.HasPrefix(trimmed, "/*") || strings.HasPrefix(trimmed, "*") {
			continue
		}
		code.WriteString(line + "\n")
	}
	return code.String()
}

// The key's sources, by file name, without comments.
func bittensorKeySources(t *testing.T) map[string]string {
	t.Helper()
	sources := map[string]string{}
	for _, name := range bittensorKeySourceFiles {
		sources[name] = bittensorKeyCode(bittensorKeyWallet(t, name))
	}
	if len(sources) != len(bittensorKeySourceFiles) {
		t.Fatalf("CONTROL FAILED: read %d files", len(sources))
	}
	return sources
}

// The text from start to the matching "}" of the block it opens. A start that ends
// in "(" is a function's: its body opens after the parameters, whose default
// lambdas are skipped.
func bittensorKeyBlock(t *testing.T, source string, start string) string {
	t.Helper()
	at := strings.Index(source, start)
	if at < 0 {
		t.Fatalf("no %q", start)
	}
	from := at
	if strings.HasSuffix(start, "(") {
		parens := 0
		for i := at + len(start) - 1; i < len(source); i++ {
			if source[i] == '(' {
				parens++
			} else if source[i] == ')' {
				parens--
				if parens == 0 {
					from = i
					break
				}
			}
		}
	}
	open := strings.Index(source[from:], "{")
	if open < 0 {
		t.Fatalf("%q opens no block", start)
	}
	open += from - at
	depth := 0
	for i := at + open; i < len(source); i++ {
		switch source[i] {
		case '{':
			depth++
		case '}':
			depth--
			if depth == 0 {
				return source[at : i+1]
			}
		}
	}
	t.Fatalf("%q does not close", start)
	return ""
}

// The text between two markers.
func bittensorKeyBetween(t *testing.T, source string, start string, end string) string {
	t.Helper()
	from := strings.Index(source, start)
	if from < 0 {
		t.Fatalf("no %q", start)
	}
	to := strings.Index(source[from+len(start):], end)
	if to < 0 {
		t.Fatalf("no %q after %q", end, start)
	}
	return source[from : from+len(start)+to]
}

func bittensorKeyContainsAll(t *testing.T, what string, source string, lines ...string) {
	t.Helper()
	for _, line := range lines {
		if !strings.Contains(source, line) {
			t.Errorf("%s lost %q", what, line)
		}
	}
}

// Every Kotlin file of the app, by path relative to the repo.
func bittensorKeyAllKotlin(t *testing.T) map[string]string {
	t.Helper()
	files := map[string]string{}
	err := filepath.Walk(filepath.Join(bittensorKeyRoot(t), "app/app/src"), func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() && info.Name() == "build" {
			return filepath.SkipDir
		}
		if info.IsDir() || !strings.HasSuffix(path, ".kt") {
			return nil
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		rel, _ := filepath.Rel(bittensorKeyRoot(t), path)
		files[rel] = string(data)
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(files) < 300 {
		t.Fatalf("CONTROL FAILED: read %d Kotlin files", len(files))
	}
	return files
}

// The sdk decides. The app reads what to show from its flow, and makes no key and
// signs nothing of its own, so every platform has the same rules (the port brief).
func TestBittensorKeyDecisionsStayInTheSdk(t *testing.T) {
	for path, source := range bittensorKeyAllKotlin(t) {
		for _, forbidden := range []string{"bittensorLocalKeyGenerate", "bittensorLocalKeySign", "BittensorLocalKeyGenerate", "BittensorLocalKeySign"} {
			if strings.Contains(bittensorKeyCode(source), forbidden) {
				t.Errorf("%s calls %s: only the sdk's flow makes a key or signs with one", path, forbidden)
			}
		}
	}
	seam := bittensorKeySources(t)["BittensorKeyFlow.kt"]
	bittensorKeyContainsAll(t, "the seam", seam,
		"Sdk.newBittensorLocalKeyFlow(api, networkId, store)",
		"fundsDisclaimer = card.fundsDisclaimer,",
		"claimFundsDisclaimer = card.claimFundsDisclaimer,",
		"fundsDisclaimer = screen.fundsDisclaimer,",
		"flow.fundsDisclaimerAt(place)",
		"flow.checkWords(first, second)",
		"flow.submitWords(first, second, authenticated)",
	)
	// the wallets' own consent path stays the wallets'
	if strings.Contains(bittensorKeyCode(bittensorKeyWallet(t, "SnConsent.kt")), "BittensorLocalKey") {
		t.Error("the wallet consent flow reaches the key")
	}
	// the earnings view model owns one flow for the signed-in network
	model := bittensorKeyWallet(t, "EarningsViewModel.kt")
	bittensorKeyContainsAll(t, "the earnings view model", model,
		"store = AndroidBittensorLocalKeyStore.shared(context),",
		"keyModel.reset(device?.api, if (device == null) null else networkId)",
		"keyModel.consentRead(it)",
		"keyModel.visit()",
		"keyModel.close()",
	)
}

// The phrase is sealed with an AES-GCM key that never leaves the Android Keystore,
// in the app's no-backup directory, never replaced, and a deletion takes the
// Keystore key too.
func TestBittensorKeyStoreKeepsTheItemOnThisDevice(t *testing.T) {
	store := bittensorKeySources(t)["AndroidBittensorLocalKeyStore.kt"]
	bittensorKeyContainsAll(t, "the store", store,
		"File(context.applicationContext.noBackupFilesDir, DIRECTORY)",
		`private const val ANDROID_KEY_STORE = "AndroidKeyStore"`,
		`private const val TRANSFORMATION = "AES/GCM/NoPadding"`,
		".setBlockModes(KeyProperties.BLOCK_MODE_GCM)",
		".setRandomizedEncryptionRequired(true)",
		".setUserAuthenticationRequired(false)",
		"cipher.updateAAD(associatedData(networkId, address))",
		"cipher.updateAAD(associatedData(networkId, item.address))",
		"Files.move(partial.toPath(), target.toPath())",
		"keyStore.deleteEntry(keyAlias(networkId))",
		"Sdk.newBittensorLocalKeyFlow(null, networkId, this)",
	)
	// an add never replaces an item
	add := bittensorKeyBlock(t, store, "override fun addKey(")
	if exists, write := strings.Index(add, "if (target.exists()) {\n                    return false"), strings.Index(add, "FileOutputStream(partial)"); exists < 0 || write < exists {
		t.Error("an add can write over an item")
	}
	for _, forbidden := range []string{
		"REPLACE_EXISTING", "renameTo(", ".filesDir", "getExternal", "MODE_WORLD", "setUserAuthenticationRequired(true)",
		"Log.", "println(", "printStackTrace",
	} {
		if strings.Contains(store, forbidden) {
			t.Errorf("the store uses %s", forbidden)
		}
	}
	// the notes stay out of backups and device transfer, as every preference does
	for _, rel := range []string{"app/app/src/main/res/xml/backup_rules.xml", "app/app/src/main/res/xml/data_extraction_rules.xml"} {
		rules := bittensorKeyRead(t, rel)
		for _, line := range []string{`<exclude domain="sharedpref" path="."/>`, `<exclude domain="file" path="."/>`} {
			if !strings.Contains(rules, line) {
				t.Errorf("%s lost %s", rel, line)
			}
		}
	}
	manifest := bittensorKeyRead(t, "app/app/src/main/AndroidManifest.xml")
	bittensorKeyContainsAll(t, "the manifest", manifest,
		`android:dataExtractionRules="@xml/data_extraction_rules"`,
		`android:fullBackupContent="@xml/backup_rules"`,
	)
}

// The phrase's only ways out of the flow are the Key screen and an explicit Copy.
func TestBittensorKeySourcesHoldNoWayToLeakTheWords(t *testing.T) {
	sources := bittensorKeySources(t)
	for name, source := range sources {
		for _, forbidden := range []string{
			"Log.", "println(", "printStackTrace", "Timber", "ClientEvents", "Firebase", "Crashlytics",
			"rememberSaveable", "SelectionContainer", "SavedStateHandle", "putExtra(\"phrase",
		} {
			if strings.Contains(source, forbidden) {
				t.Errorf("%s uses %s", name, forbidden)
			}
		}
		if name != "BittensorKeyPlatform.kt" && (strings.Contains(source, "ClipboardManager") || strings.Contains(source, "setPrimaryClip")) {
			t.Errorf("%s reaches the clipboard with the phrase", name)
		}
		if name != "BittensorKeyFlow.kt" && name != "BittensorKeyModel.kt" && strings.Contains(source, "phraseToCopy") {
			t.Errorf("%s reads the phrase to copy", name)
		}
	}
	platform := sources["BittensorKeyPlatform.kt"]
	bittensorKeyContainsAll(t, "the clipboard", platform,
		"putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)",
		"Sdk.BittensorLocalKeyCopySeconds * 1000L",
		"clipboard.clearPrimaryClip()",
	)
	screen := sources["BittensorKeyScreen.kt"]
	bittensorKeyContainsAll(t, "the Key screen", screen,
		// the screen's window is secure: no screenshot, recording, cast or recents thumbnail shows it
		"securePolicy = SecureFlagPolicy.SecureOn,",
		// the words hide whenever the app leaves the foreground
		"LifecycleResumeEffect(model) {\n        onPauseOrDispose { model.leftForeground() }",
		"BittensorKeyWordGrid(words = words)",
	)
	// the words are in the composition only while they show
	main := bittensorKeyBlock(t, screen, "private fun BittensorKeyMainContent(")
	if revealed, grid := strings.Index(main, "if (screen.revealed) {"), strings.Index(main, "BittensorKeyWordGrid(words = words)"); revealed < 0 || grid < revealed {
		t.Error("the words are drawn while hidden")
	}
	// a stored key's reveal asks for device authentication first, through the model
	bittensorKeyContainsAll(t, "the model's reveal", bittensorKeyBlock(t, sources["BittensorKeyModel.kt"], "suspend fun tapToReveal("),
		"if (_screen.value.needsAuthentication) authenticator.authenticate() else false",
		"current.reveal(authenticated)",
	)
	// the device's owner authenticates with a biometric, or the device credential
	bittensorKeyContainsAll(t, "the authenticator", platform,
		"BiometricManager.Authenticators.DEVICE_CREDENTIAL",
		".setAllowedAuthenticators(authenticators)",
		"if (keyguard != null && !keyguard.isDeviceSecure) {",
	)
	// nothing outside the key's files and Earnings reaches the store, and a
	// logout never touches it: the key stays for the network's next sign-in
	for _, rel := range []string{
		bittensorKeySourceRoot + "/MainApplication.kt",
		bittensorKeySourceRoot + "/SignedOutNetworkState.kt",
		bittensorKeySourceRoot + "/DeviceManager.kt",
		bittensorKeySourceRoot + "/MainService.kt",
	} {
		if strings.Contains(bittensorKeyRead(t, rel), "BittensorLocalKey") || strings.Contains(bittensorKeyRead(t, rel), "BittensorKey") {
			t.Errorf("%s reaches the payout key: a logout must leave it", rel)
		}
	}
}

// The two-word fields: nothing the keyboard could learn the words from or suggest
// them with, no autofill, no saved state, and the confirmation uses them.
func TestBittensorKeyWordFieldsLearnNothing(t *testing.T) {
	screen := bittensorKeySources(t)["BittensorKeyScreen.kt"]
	field := bittensorKeyBlock(t, screen, "private fun BittensorKeyWordField(")
	bittensorKeyContainsAll(t, "the word field", field,
		"InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD",
		"InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS",
		"EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING",
		"importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS",
		"isSaveEnabled = false",
	)
	if strings.Contains(field, "TYPE_TEXT_FLAG_AUTO_CORRECT") || strings.Contains(field, "TYPE_TEXT_FLAG_AUTO_COMPLETE") {
		t.Error("the word field corrects or completes")
	}
	confirmation := bittensorKeyBlock(t, screen, "private fun BittensorKeyConfirmation(")
	for _, forbidden := range []string{"TextField(", "URTextInput(", "rememberSaveable"} {
		if strings.Contains(confirmation, forbidden) {
			t.Errorf("the two-word check uses %s", forbidden)
		}
	}
	if !strings.Contains(bittensorKeyBlock(t, screen, "private fun BittensorKeyWordEntry("), "BittensorKeyWordField(") {
		t.Error("the two-word check doesn't use the quiet field")
	}
}

// Keep and Delete hide the words before anything is asked, so none can be read
// behind the device's authentication or the two-word check, and only deleting a
// stored key asks for authentication, at its button.
func TestBittensorKeyWordsHideBeforeTheConfirmation(t *testing.T) {
	model := bittensorKeySources(t)["BittensorKeyModel.kt"]
	explain := bittensorKeyBlock(t, model, "fun explainDelete() {")
	if hide, shown := strings.Index(explain, "hide()"), strings.Index(explain, "_deleteExplained.value = true"); hide < 0 || shown < hide {
		t.Error("what deleting means shows before the words hide")
	}
	for _, name := range []string{"fun confirmDelete() {", "fun confirmKeep() {"} {
		if strings.Contains(bittensorKeyBlock(t, model, name), "authenticat") {
			t.Errorf("%s asks for device authentication", name)
		}
	}
	bittensorKeyContainsAll(t, "the check's button", bittensorKeyBlock(t, model, "suspend fun submitWords("),
		"if (_screen.value.confirmNeedsAuthentication) {",
		"if (!authenticated) {",
		"current.submitWords(first, second, authenticated)",
	)
	main := bittensorKeyBlock(t, bittensorKeySources(t)["BittensorKeyScreen.kt"], "private fun BittensorKeyMainContent(")
	bittensorKeyContainsAll(t, "the Key screen", main, "onClick = { model.explainDelete() },", "onClick = { model.confirmKeep() },")
}

// Keep and Delete as drawn: Keep beside a coral-outlined Delete, what deleting means
// behind Continue, then the two words, each wrong word named as it is typed and the
// button only for right ones, coral with dark text.
func TestBittensorKeyConfirmationsAsDrawn(t *testing.T) {
	screen := bittensorKeySources(t)["BittensorKeyScreen.kt"]
	bittensorKeyContainsAll(t, "what deleting means", bittensorKeyBlock(t, screen, "private fun BittensorKeyDeleteExplanation("),
		"R.string.bittensor_key_delete_save_now_title else R.string.bittensor_key_delete_title_2",
		"R.string.continue_txt",
		"onClick = { model.confirmDelete() },",
		"style = ButtonStyle.DESTRUCTIVE,",
		"if (screen.warnsTop200) {",
		"BittensorKeyWording()",
	)
	bittensorKeyContainsAll(t, "the two-word check", bittensorKeyBlock(t, screen, "private fun BittensorKeyConfirmation("),
		"model.checkWords(firstWord, secondWord)",
		"enabled = check.ready,",
		"if (screen.confirmNeedsAuthentication) {",
		"R.string.bittensor_key_keep_confirm_2",
		"R.string.bittensor_key_save_now_delete_confirm else R.string.bittensor_key_delete_confirm",
		"style = ButtonStyle.DESTRUCTIVE,",
		"onClick = { model.cancelConfirmation() },",
	)
	bittensorKeyContainsAll(t, "a word's entry", bittensorKeyBlock(t, screen, "private fun BittensorKeyWordEntry("),
		"if (state == Sdk.BittensorLocalKeyWordWrong) {",
		"R.string.bittensor_key_word_wrong, position",
	)
	main := bittensorKeyBlock(t, screen, "private fun BittensorKeyMainContent(")
	bittensorKeyContainsAll(t, "the Key screen", main, "style = ButtonStyle.WARNING,", "if (screen.offersKeep) {", "if (!screen.offersRetry) {")
	button := bittensorKeyRead(t, bittensorKeySourceRoot+"/ui/components/URButton.kt")
	if !strings.Contains(button, "ButtonStyle.DESTRUCTIVE -> ButtonDefaults.buttonColors(\n            containerColor = Red,\n            contentColor = Black,") {
		t.Error("the destructive button is not coral with dark text")
	}
}

// The payout card leads Earnings while it needs action, the backup banner above it
// while the phrase isn't saved; Back up first, from Sign out and Delete account,
// and the reminder's notification open the Key screen.
func TestBittensorKeyCardLeadsAndBackUpFirstOpensTheKey(t *testing.T) {
	earnings := bittensorKeyWallet(t, "EarningsScreen.kt")
	content := earnings[strings.Index(earnings, "fun EarningsScreenContent("):]
	banner := strings.Index(content, "if (keyCard.backupReminder) {\n                    BittensorKeyBackupBanner(onOpen = keyActions.openKeyScreen)")
	lead := strings.Index(content, "if (keyCard.showsKeyPath && consentTile != null) {\n                    payoutCard()")
	points := strings.Index(content, "PointsHeadline(")
	rest := strings.Index(content, "if (!keyCard.showsKeyPath && consentTile != null) {")
	if banner < 0 || lead < banner || points < lead || rest < points {
		t.Error("the banner and the payout card don't lead Earnings while the key needs action")
	}
	bittensorKeyContainsAll(t, "Earnings", earnings,
		"if (keyScreenRequested && keyReady && BittensorKeyScreenRequests.take()) {\n            keyModel.openKeyScreen()",
		"BittensorKeyScreenDialog(\n        model = keyModel,",
		"earningsViewModel.keyModel.closeScreen()",
		"keyModel.setGuest(isGuest)",
		// a key replaces the payout wallet only behind its warning
		"if (pendingReplacement != null) {\n        BittensorKeyReplaceDialog(",
	)
	nav := bittensorKeyRead(t, bittensorKeySourceRoot+"/ui/MainNavHost.kt")
	bittensorKeyContainsAll(t, "the nav host", nav,
		"if (route == com.bringyour.network.QuickConnectActivity.ROUTE_EARNINGS_KEY) {\n            // the payout key's reminder, tapped: Earnings with its Key screen\n            BittensorKeyScreenRequests.request()",
		"val pendingKeyScreenNavigation by BittensorKeyScreenRequests.navigation.collectAsState()",
		"isGuest = isGuestNetwork,",
	)
	if !strings.Contains(bittensorKeyBlock(t, nav, "LaunchedEffect(pendingKeyScreenNavigation) {"), "navController.navigate(Route.Earnings)") {
		t.Error("a request for the Key screen doesn't open Earnings")
	}
	switcher := bittensorKeyRead(t, bittensorKeySourceRoot+"/ui/components/AccountSwitcher.kt")
	bittensorKeyContainsAll(t, "Sign out", switcher,
		"if (BittensorKeySignOut.needsBackup(context)) {\n                            keySignOutPending = true",
		"BittensorKeySignOutDialog(",
		"BittensorKeyScreenRequests.request()",
	)
	signOut := bittensorKeySources(t)["BittensorKeySignOut.kt"]
	bittensorKeyContainsAll(t, "the sign out warning", bittensorKeyBlock(t, signOut, "fun BittensorKeySignOutDialog("),
		"R.string.bittensor_key_sign_out_title_2",
		"R.string.bittensor_key_sign_out_message_2",
		"R.string.bittensor_key_back_up_first",
		"R.string.sign_out",
	)
	settings := bittensorKeyRead(t, bittensorKeySourceRoot+"/ui/settings/SettingsScreen.kt")
	bittensorKeyContainsAll(t, "Delete account", settings,
		"val keyNeedsBackup = remember { BittensorKeySignOut.needsBackup(context, deletedNetworkId) }",
		"R.string.bittensor_key_delete_account_message_2",
		"R.string.bittensor_key_back_up_first",
	)
	// the deleted network's key goes with it, before the sign out
	if forget, logout := strings.Index(settings, "BittensorKeySignOut.forgetNetwork(context, deletedNetworkId)"), strings.Index(settings, "application?.logout(signedOutDevice)"); forget < 0 || logout < forget {
		t.Error("account deletion doesn't forget the key before it signs out")
	}
	// the sessions screen's sign out of this session adds the older warning
	sessions := bittensorKeyRead(t, bittensorKeySourceRoot+"/ui/account/SessionsScreen.kt")
	bittensorKeyContainsAll(t, "the sessions screen", sessions,
		"confirmation.row.current && BittensorKeySignOut.needsBackup(context)",
		"R.string.bittensor_key_sign_out_message",
	)
	// the one notification: one channel, one id, its tap to Earnings with the Key screen
	platform := bittensorKeySources(t)["BittensorKeyPlatform.kt"]
	bittensorKeyContainsAll(t, "the reminder", platform,
		`const val CHANNEL_ID = "bittensor_key_reminder"`,
		"manager.notify(NOTIFICATION_ID, notification)",
		".putExtra(QuickConnectActivity.EXTRA_ROUTE, QuickConnectActivity.ROUTE_EARNINGS_KEY)",
		"if (!manager.areNotificationsEnabled()) {",
		"flow.reminderScheduled(reminder.atMillis)",
		"flow.reminderDelivered()",
		"R.string.bittensor_key_reminder_title_2",
		"R.string.bittensor_key_reminder_body_2",
	)
	if strings.Contains(platform, "requestPermission") || strings.Contains(platform, "POST_NOTIFICATIONS") {
		t.Error("the reminder asks for the notification permission itself")
	}
	activity := bittensorKeyRead(t, bittensorKeySourceRoot+"/QuickConnectActivity.kt")
	if !strings.Contains(activity, `const val ROUTE_EARNINGS_KEY = "earnings_key"`) {
		t.Error("the app has no route for the Key screen")
	}
}

// Claims through the relay: the tile and the dialog word what the sdk's claim
// status decides, and the device's own claim stays as the fallback.
func TestBittensorKeyClaimsThroughTheRelay(t *testing.T) {
	relay := bittensorKeyCode(bittensorKeyWallet(t, "ClaimRelayDialog.kt"))
	for _, reason := range []string{
		"Sdk.SnClaimReasonUnderThreshold", "Sdk.SnClaimReasonQueued", "Sdk.SnClaimReasonCapReached", "Sdk.SnClaimReasonRefused",
		"Sdk.SnClaimReasonRelayNotRunning", "Sdk.SnClaimReasonRelayPaused",
		"Sdk.SnClaimNowReasonUnderFloor", "Sdk.SnClaimNowReasonRequested", "Sdk.SnClaimNowReasonLimit",
	} {
		if !strings.Contains(relay, reason+" ->") {
			t.Errorf("the claim screens don't word %s", reason)
		}
	}
	bittensorKeyContainsAll(t, "the claim screens", relay,
		"enabled = status.claimNowAvailable,",
		"if (status.selfClaimAvailable) {",
		"R.string.sn_claim_relay_queued_button",
	)
	model := bittensorKeyWallet(t, "EarningsViewModel.kt")
	bittensorKeyContainsAll(t, "the earnings view model", model,
		"val outcome = s.claimNow(status.coldkeySs58)",
		"refreshClaimStatus()",
	)
	sdk := bittensorKeyWallet(t, "SdkProtocolSource.kt")
	bittensorKeyContainsAll(t, "the sdk source", sdk,
		"device.snClaimStatus { result, _ ->",
		"device.snClaimNow(coldkeySs58) { result, err ->",
		"unavailable = result.error != null,",
	)
	// a network with no wallet on this device still sees what the relay claims for
	// it, while the sdk says the tile shows (ShowsTile)
	bittensorKeyContainsAll(t, "Earnings", bittensorKeyWallet(t, "EarningsScreen.kt"),
		"ledgerTile = snLedgerTileWithRelay(wallet != null, snLedgerTile(ledgerLoad, ledgerClaimConfigured), claimStatus),",
	)
	tile := bittensorKeyBlock(t, bittensorKeyWallet(t, "SnClaimRelay.kt"), "fun snLedgerTileWithRelay(")
	if !strings.Contains(tile, "if (claimStatus == null || !claimStatus.showsTile) {") || strings.Contains(tile, "pendingRao") {
		t.Error("the tile's rule is not the sdk's ShowsTile")
	}
	// the claimed state (4d) as the sdk names it: what URnetwork's claim paid
	// (ClaimAmountRao), the coldkey it landed on (ColdkeySs58), its transaction
	// (ClaimTxUrl), when Claim now is back (ClaimNowNextAtMillis), and each
	// account's (SnClaimRelayAccount)
	bittensorKeyContainsAll(t, "the sdk source", sdk,
		"showsTile = result.showsTile,",
		"showsClaimed = result.showsClaimed,",
		"claimTxHash = result.claimTxHash,",
		"claimTxUrl = result.claimTxUrl,",
		"claimAmountRao = result.claimAmountRao,",
		"accounts = snClaimRelayAccountsOf(result.accounts),",
		"claimTxHash = a.claimTxHash,",
		"claimTxUrl = a.claimTxUrl,",
		"claimAmountRao = a.claimAmountRao,",
	)
	bittensorKeyContainsAll(t, "the claim dialog", bittensorKeyBlock(t, relay, "fun ClaimRelayDialog("),
		"val claimed = status != null && !status.unavailable && status.showsClaimed",
		"val rows = snClaimRelayRows(status)",
		// each pool's row, named by its kind, Below the claim minimum under the relay's floor,
		// alone or beside other pools (owner, 2026-11-11), and the pools the relay could not
		// read (capture the flag, sn CAPTURETHEFLAG.md §10)
		"SnClaimRelayRows(\n                        rows = rows,\n                        formatAlpha = formatAlpha,\n                        shortSs58 = shortSs58,",
		"transferFloorRao = status.transferFloorRao,",
		"unavailablePools = status.unavailablePools,",
		"pools = snClaimRelayPools(status),",
		`txUrl = if (rows.any { it.showsClaimed && it.claimTxUrl.isNotEmpty() }) "" else status.claimTxUrl,`,
		"R.string.done",
		"SnClaimRelayAgainOn(status = status, centered = true)",
	)
	// the store's one claimed-state sentence on every app, with the amount
	// without its symbol, which the sentence names itself
	banner := bittensorKeyBlock(t, relay, "private fun SnClaimRelayClaimed(")
	bittensorKeyContainsAll(t, "the claimed banner", banner,
		"id = R.string.sn_claim_relay_claimed,",
		"EarningsFormat.alphaAmount(status.claimAmountRao),",
		"shortSs58(status.coldkeySs58),",
		"R.string.claim_confirmed",
		"SnClaimRelayTransactionLink(txUrl)",
	)
	if strings.Contains(banner, "formatAlpha(") || strings.Contains(banner, "EarningsFormat.alpha(") {
		t.Error("the claimed banner's amount names SN25α, and so does the sentence")
	}
	bittensorKeyContainsAll(t, "the number-only alpha", bittensorKeyBlock(t, bittensorKeyCode(bittensorKeyWallet(t, "EarningsModels.kt")), "fun alphaAmount("),
		`String.format(Locale.US, "%.4f", rao / RAO_PER_ALPHA)`,
	)
	bittensorKeyContainsAll(t, "the claim sheet's rows", bittensorKeyBlock(t, relay, "private fun SnClaimRelayRows("),
		"if (row.showsClaimed) row.claimAmountRao else row.claimableRao",
		"SnClaimRelayPill(stringResource(id = R.string.claim_confirmed), color = BlueMedium)",
		"SnClaimRelayTransactionLink(row.claimTxUrl)",
	)
	bittensorKeyContainsAll(t, "the tile's claimed state", bittensorKeyBlock(t, relay, "fun SnClaimRelaySection("),
		"if (!inDialog && status.showsClaimed) {",
		"txUrl = status.claimTxUrl,",
		"SnClaimRelayAgainOn(status = status, centered = false)",
	)
	bittensorKeyContainsAll(t, "when Claim now is back", bittensorKeyBlock(t, relay, "private fun SnClaimRelayAgainOn("),
		"if (0 < status.claimNowNextAtMillis) {",
		"R.string.sn_claim_relay_again_on",
	)
	rules := bittensorKeyCode(bittensorKeyWallet(t, "SnClaimRelay.kt"))
	bittensorKeyContainsAll(t, "the claim relay's rules", rules,
		// the tile speaks in the claimed state, where nothing is left to claim
		"(relay.showsClaimed || relay.action != Sdk.SnClaimActionNone || 0 < relay.claimableRao)",
		// a row reads Claimed by the rule the sdk's ShowsClaimed applies to the primary account
		"get() = claimTxHash.isNotEmpty() && claimableRao <= 0",
		"status.accounts.filter { 0 < it.claimableRao || it.showsClaimed }",
	)
	// the transaction's page is the sdk's (ClaimTxUrl), never one the screens make
	if strings.Contains(relay, "explorerTxUrl") || strings.Contains(relay, "EXPLORER_TX_URL") {
		t.Error("the claim relay's screens make their own explorer link")
	}
}

// After the one tap the wallet card and the points card show the key on this
// device, not a call to connect a second wallet.
func TestBittensorKeyWalletCardShowsTheKeyOnThisDevice(t *testing.T) {
	earnings := bittensorKeyWallet(t, "EarningsScreen.kt")
	section := bittensorKeyBlock(t, earnings, "internal fun WalletSection(")
	wallet, key, connect := strings.Index(section, "if (wallet != null) {"), strings.Index(section, "if (localKey != null) {"), strings.Index(section, "R.string.connect_bittensor_wallet")
	if wallet < 0 || key < wallet || connect < key {
		t.Error("the wallet card offers Connect Bittensor wallet beside the key on this device")
	}
	keyBranch := bittensorKeyBlock(t, section, "if (localKey != null) {")
	bittensorKeyContainsAll(t, "the key's wallet card", keyBranch,
		"shortSs58(localKey.address)",
		"connectSolanaOverflowItem(onConnectSolana)",
		"if (localKey.linked) R.string.wallet_connected_to_protocol else R.string.bittensor_key_card_not_linked",
	)
	if strings.Contains(keyBranch, "onConnectWallet") {
		t.Error("the key's wallet card still connects a wallet")
	}
	// the sdk says when the consent pays the key on this device (PaysKeyOnThisDevice)
	bittensorKeyContainsAll(t, "Earnings", earnings,
		"localKey = BittensorWalletCardLocalKey.of(keyCard),",
		"val hasColdkey = wallet != null || keyCard.paysKeyOnThisDevice",
		"hasColdkey = hasColdkey,",
	)
	bittensorKeyContainsAll(t, "the wallet card's key", bittensorKeySources(t)["BittensorKeyCard.kt"],
		"BittensorWalletCardLocalKey(card.storedAddress, card.paysKeyOnThisDevice)",
	)
	bittensorKeyContainsAll(t, "the seam", bittensorKeySources(t)["BittensorKeyFlow.kt"],
		"paysKeyOnThisDevice = card.paysKeyOnThisDevice,",
	)
	if strings.Contains(earnings, "Sdk.BittensorLocalKeyStateSaveLaterLinked") {
		t.Error("Earnings decides from the key's state what the sdk's card says")
	}
}

// The owner's disclaimer (BTEASY.md, Wording; 2026-11-03), in plain view on every
// screen of the easy flows and once a screen: the payout card, the Key screen and
// its delete and keep steps, the first claim tile, the claim dialog and Change
// wallet's offer of a key. The sdk names which text a screen shows.
func TestBittensorFundsDisclaimerShowsOnEveryEasyFlowScreen(t *testing.T) {
	sources := bittensorKeySources(t)
	card := sources["BittensorKeyCard.kt"]
	bittensorKeyContainsAll(t, "the disclaimer", card,
		"Sdk.BittensorFundsDisclaimerFull -> R.string.bittensor_funds_disclaimer",
		"Sdk.BittensorFundsDisclaimerFundsOnly -> R.string.bittensor_funds_disclaimer_funds_only",
	)
	// plain text, never behind a link, a dialog or a tooltip
	text := bittensorKeyBlock(t, card, "fun BittensorFundsDisclaimerText(")
	for _, hidden := range []string{"clickable", "Popup", "Dialog(", "Tooltip", "AnimatedVisibility", "maxLines"} {
		if strings.Contains(text, hidden) {
			t.Errorf("the disclaimer is behind %s", hidden)
		}
	}
	screen := sources["BittensorKeyScreen.kt"]
	for _, step := range []string{"private fun BittensorKeyMainContent(", "private fun BittensorKeyDeleteExplanation(", "private fun BittensorKeyConfirmation("} {
		if got := strings.Count(bittensorKeyBlock(t, screen, step), "BittensorFundsDisclaimerText(disclaimer = screen.fundsDisclaimer, boxed = true)"); got != 1 {
			t.Errorf("%s shows the disclaimer %d times", step, got)
		}
	}
	// what deleting means, and the check, replace the Key screen, so one disclaimer shows at a time
	if !strings.Contains(screen, "when {\n                    screen.confirming != Sdk.BittensorLocalKeyConfirmNone ->\n                        key(screen.confirming) {\n                            BittensorKeyConfirmation(model = model, screen = screen)\n                        }\n                    deleteExplained -> BittensorKeyDeleteExplanation(") {
		t.Error("the Key screen's steps no longer replace each other, so two disclaimers could show")
	}
	consent := bittensorKeyWallet(t, "SnConsentTile.kt")
	if !strings.Contains(bittensorKeyBlock(t, consent, "fun SnConsentBlock("), "BittensorFundsDisclaimerText(disclaimer = key.fundsDisclaimer)") {
		t.Error("the payout card shows no disclaimer")
	}
	// the claim tiles share Earnings with the payout card: the sdk gives them the
	// disclaimer only while the payout card has none, on the first tile shown
	earnings := bittensorKeyWallet(t, "EarningsScreen.kt")
	if got := strings.Count(bittensorKeyBlock(t, earnings, "private fun UnclaimedTile("), "BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer)"); got != 1 {
		t.Errorf("the Unclaimed tile shows the disclaimer %d times", got)
	}
	if got := strings.Count(bittensorKeyBlock(t, bittensorKeyWallet(t, "SnLedgerTile.kt"), "fun SnLedgerTileBlock("), "BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer)"); got != 1 {
		t.Errorf("the Settlement v2 tile shows the disclaimer %d times", got)
	}
	bittensorKeyContainsAll(t, "Earnings", earnings,
		"fundsDisclaimer = keyCard.claimFundsDisclaimer,\n                    )\n                }",
		"fundsDisclaimer = if (wallet != null) Sdk.BittensorFundsDisclaimerNone else keyCard.claimFundsDisclaimer,",
		"keyModel.fundsDisclaimerAt(Sdk.BittensorFundsDisclaimerPlaceClaimSheet)",
		"keyModel.fundsDisclaimerAt(Sdk.BittensorFundsDisclaimerPlaceChangeWallet)",
	)
	if !strings.Contains(bittensorKeyBlock(t, bittensorKeyWallet(t, "ClaimRelayDialog.kt"), "fun ClaimRelayDialog("), "BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer, boxed = true)") {
		t.Error("the claim dialog shows no disclaimer")
	}
	if !strings.Contains(bittensorKeyBlock(t, card, "fun BittensorKeyChangeWalletOffer("), "BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer, boxed = true)") {
		t.Error("Change wallet's offer of a key shows no disclaimer")
	}
}

// The catalogs as the localizations store generates them: the owner's words and
// the plan's word for word, and every string the key's screens look up in every
// locale the app ships.
func TestBittensorKeyStringsAreInTheCatalog(t *testing.T) {
	res := filepath.Join(bittensorKeyRoot(t), "app/app/src/main/res")
	catalogs := map[string]map[string]string{}
	entries, err := os.ReadDir(res)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if !entry.IsDir() || (entry.Name() != "values" && !strings.HasPrefix(entry.Name(), "values-")) {
			continue
		}
		data, err := os.ReadFile(filepath.Join(res, entry.Name(), "strings.xml"))
		if err != nil {
			continue
		}
		var parsed struct {
			Strings []struct {
				Name  string `xml:"name,attr"`
				Value string `xml:",chardata"`
			} `xml:"string"`
		}
		if err := xml.Unmarshal(data, &parsed); err != nil {
			t.Fatalf("%s: %v", entry.Name(), err)
		}
		catalog := map[string]string{}
		for _, s := range parsed.Strings {
			catalog[s.Name] = strings.ReplaceAll(s.Value, `\'`, "'")
		}
		catalogs[entry.Name()] = catalog
	}
	base := catalogs["values"]
	if base == nil || len(catalogs) < 19 {
		t.Fatalf("CONTROL FAILED: read %d catalogs", len(catalogs))
	}

	for id, english := range map[string]string{
		"bittensor_funds_disclaimer":            "This key is stored locally in the app secure storage. It is never shared with URnetwork. URnetwork does not touch and is not responsible for any user funds.",
		"bittensor_funds_disclaimer_funds_only": "URnetwork does not touch and is not responsible for any user funds.",
		"bittensor_key_wording_not_wallet":      "URnetwork is not a wallet. It created this key so you can receive SN25α. To transfer or use your SN25α, enter this recovery phrase into a Bittensor wallet such as Talisman or Nova, or into btcli.",
		"bittensor_key_wording_control":         "Anyone with this phrase controls your SN25α. URnetwork will never ask for it, and can't recover it. If you lose this device before saving it, your SN25α can't be reached.",
		"bittensor_key_confirm_top200":          "Deleting this key also gives up claiming a Top 200 spot with one tap. You could still claim from a Bittensor wallet holding this recovery phrase.",
		"bittensor_key_delete_action":           "I have saved the key, it's OK to delete it now.",
		"bittensor_key_set_up":                  "Set up payouts",
		"bittensor_key_save_first":              "Save the recovery phrase first",
		"bittensor_key_use_wallet":              "Use a wallet I already have",
		"bittensor_key_payouts_start":           "Payouts start next epoch",
		"bittensor_key_back_up":                 "Back up your key",
		"bittensor_key_no_longer_here":          "This app no longer has the key",
		"bittensor_key_reveal":                  "Tap to reveal",
	} {
		if base[id] != english {
			t.Errorf("%s reads %q", id, base[id])
		}
	}

	// every string the key's screens and the claim relay look up
	lookup := regexp.MustCompile(`R\.string\.([a-z0-9_]+)`)
	var looked strings.Builder
	for _, name := range append(append([]string{}, bittensorKeySourceFiles...), "ClaimRelayDialog.kt", "SnConsentTile.kt", "SnLedgerTile.kt", "EarningsScreen.kt") {
		looked.WriteString(bittensorKeyWallet(t, name))
	}
	ids := map[string]bool{}
	for _, match := range lookup.FindAllStringSubmatch(looked.String(), -1) {
		ids[match[1]] = true
	}
	ours := []string{}
	for id := range ids {
		if strings.HasPrefix(id, "bittensor_key_") || strings.HasPrefix(id, "sn_claim_relay_") || strings.HasPrefix(id, "bittensor_funds_") {
			ours = append(ours, id)
		}
	}
	sort.Strings(ours)
	if len(ours) < 90 {
		t.Fatalf("CONTROL FAILED: the screens look up %d of the key's strings", len(ours))
	}
	for id := range ids {
		if _, ok := base[id]; !ok {
			t.Errorf("values/strings.xml has no %s", id)
		}
	}
	for name, catalog := range catalogs {
		if name == "values" {
			continue
		}
		for _, id := range ours {
			if catalog[id] == "" {
				t.Errorf("%s has no %s", name, id)
			}
		}
	}
}

// Claim now's period is a count (the relay's 30 days today), so its two sentences are
// the store's plural forms, which the catalogs carry as <plurals>: the note looks each
// up with pluralStringResource and the period as its count, so every language reads the
// count in its own form, and nothing looks them up as one sentence, which the catalogs
// no longer have.
func TestBittensorKeyClaimNowPeriodTakesThePluralForm(t *testing.T) {
	ids := []string{"sn_claim_relay_claim_now_paid", "sn_claim_relay_claim_now_paid_each"}
	note := bittensorKeyBlock(t, bittensorKeyCode(bittensorKeyWallet(t, "ClaimRelayDialog.kt")), "fun SnClaimNowNote(")
	for _, id := range ids {
		if !strings.Contains(note, "pluralStringResource(id = R.plurals."+id+", count = status.claimNowPeriodDays.toInt(), status.claimNowPeriodDays)") {
			t.Errorf("the Claim now note doesn't look up %s by the period's count", id)
		}
		single := regexp.MustCompile(`R\.string\.` + id + `\b`)
		for path, source := range bittensorKeyAllKotlin(t) {
			if single.MatchString(bittensorKeyCode(source)) {
				t.Errorf("%s looks up %s as one sentence", path, id)
			}
		}
	}

	res := filepath.Join(bittensorKeyRoot(t), "app/app/src/main/res")
	entries, err := os.ReadDir(res)
	if err != nil {
		t.Fatal(err)
	}
	read := 0
	for _, entry := range entries {
		if !entry.IsDir() || (entry.Name() != "values" && !strings.HasPrefix(entry.Name(), "values-")) {
			continue
		}
		data, err := os.ReadFile(filepath.Join(res, entry.Name(), "strings.xml"))
		if err != nil {
			continue
		}
		var parsed struct {
			Plurals []struct {
				Name  string `xml:"name,attr"`
				Items []struct {
					Quantity string `xml:"quantity,attr"`
					Value    string `xml:",chardata"`
				} `xml:"item"`
			} `xml:"plurals"`
		}
		if err := xml.Unmarshal(data, &parsed); err != nil {
			t.Fatalf("%s: %v", entry.Name(), err)
		}
		read++
		for _, id := range ids {
			forms := map[string]string{}
			for _, plural := range parsed.Plurals {
				if plural.Name == id {
					for _, item := range plural.Items {
						forms[item.Quantity] = item.Value
					}
				}
			}
			// "other" is every language's form for a count it has no other form for,
			// and it always prints the count
			if !strings.Contains(forms["other"], "%1$d") {
				t.Errorf("%s: %s has no other form with the count: %q", entry.Name(), id, forms["other"])
			}
			if entry.Name() == "values" && (forms["one"] == "" || !strings.Contains(forms["one"], "once a day")) {
				t.Errorf("values: %s has no English one form: %q", id, forms["one"])
			}
		}
	}
	if read < 19 {
		t.Fatalf("CONTROL FAILED: read %d catalogs", read)
	}
}

// Claim now queued (4c) says what the relay does, as the owner decided on 2026-11-11
// (BTEASY.md, Claim now and the daily cap): taps go out ahead of the automatic claims,
// the oldest first, as each day's budget allows, and the coldkey's limit starts when this
// claim goes through, not at the tap. The tile's note and the dialog after the tap say it
// with the relay's period, a count in its plural form; the limit's date reads as the same
// limit. Before, the note said "within today's budget" beside a line saying the claim
// waits for the next day, and nothing said when the 30 days start.
func TestBittensorKeyClaimNowQueuedSaysWhatHappens(t *testing.T) {
	relay := bittensorKeyCode(bittensorKeyWallet(t, "ClaimRelayDialog.kt"))
	queued := bittensorKeyBlock(t, relay, "fun snClaimNowQueuedText(")
	bittensorKeyContainsAll(t, "the queued note", queued,
		"stringResource(id = R.string.sn_claim_relay_requested)",
		"if (status.claimNowPeriodDays <= 0) return queued",
		"pluralStringResource(id = R.plurals.sn_claim_relay_requested_period, count = status.claimNowPeriodDays.toInt(), status.claimNowPeriodDays)",
	)
	bittensorKeyContainsAll(t, "the tile's note", bittensorKeyBlock(t, relay, "fun SnClaimNowNote("),
		"Sdk.SnClaimNowReasonRequested -> snClaimNowQueuedText(status)",
		"stringResource(id = R.string.sn_claim_relay_again_on, snClaimNowDate(status.claimNowNextAtMillis))",
	)
	dialog := bittensorKeyBlock(t, relay, "fun ClaimRelayDialog(")
	if !regexp.MustCompile(`SnClaimNowOutcome\.Requested -> \{\s*Text\(\s*snClaimNowQueuedText\(status\),`).MatchString(dialog) {
		t.Error("the dialog's answer to the tap is not the queued note")
	}
	if regexp.MustCompile(`R\.string\.sn_claim_relay_requested_period\b`).MatchString(relay) {
		t.Error("the period is looked up as one sentence")
	}

	read := 0
	res := filepath.Join(bittensorKeyRoot(t), "app/app/src/main/res")
	entries, err := os.ReadDir(res)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if !entry.IsDir() || (entry.Name() != "values" && !strings.HasPrefix(entry.Name(), "values-")) {
			continue
		}
		data, err := os.ReadFile(filepath.Join(res, entry.Name(), "strings.xml"))
		if err != nil {
			continue
		}
		var parsed struct {
			Strings []struct {
				Name  string `xml:"name,attr"`
				Value string `xml:",chardata"`
			} `xml:"string"`
			Plurals []struct {
				Name  string `xml:"name,attr"`
				Items []struct {
					Quantity string `xml:"quantity,attr"`
					Value    string `xml:",chardata"`
				} `xml:"item"`
			} `xml:"plurals"`
		}
		if err := xml.Unmarshal(data, &parsed); err != nil {
			t.Fatalf("%s: %v", entry.Name(), err)
		}
		read++
		strs := map[string]string{}
		for _, s := range parsed.Strings {
			strs[s.Name] = strings.ReplaceAll(s.Value, `\'`, "'")
		}
		forms := map[string]string{}
		for _, plural := range parsed.Plurals {
			if plural.Name == "sn_claim_relay_requested_period" {
				for _, item := range plural.Items {
					forms[item.Quantity] = item.Value
				}
			}
		}
		if !strings.Contains(forms["other"], "%1$d") {
			t.Errorf("%s: the period has no other form with the count: %q", entry.Name(), forms["other"])
		}
		if entry.Name() != "values" {
			continue
		}
		for id, english := range map[string]string{
			"sn_claim_relay_requested": "Claim now is queued. It goes out ahead of automatic claims, in the order requests came in, as each day's budget allows.",
			"sn_claim_relay_cap":       "Today's claim budget is used up, so this waits. The budget renews each day at 00:00 UTC. Nothing is lost: this credit never expires.",
			"sn_claim_relay_again_on":  "Your next Claim now opens on %1$s.",
		} {
			if strs[id] != english {
				t.Errorf("%s reads %q", id, strs[id])
			}
		}
		if forms["other"] != "Your next Claim now opens %1$d days after this claim goes through." {
			t.Errorf("the period reads %q", forms["other"])
		}
	}
	if read < 19 {
		t.Fatalf("CONTROL FAILED: read %d catalogs", read)
	}
}
