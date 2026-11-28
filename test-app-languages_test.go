package main

import (
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strings"
	"testing"
)

// The app's languages (support inbox 182, 270, 7485 and 8621).
//
// The localizations store generates one res/values*/strings.xml per language
// the app ships, Turkish and Persian included, and res/xml/locales_config.xml
// with the same languages. The manifest names that file as android:localeConfig,
// so Android 13 and later list exactly those languages in the system's per-app
// language settings, and Settings > Language offers them in the app. The
// location list names a country in the app's language with the lookup the
// connection region screens use, the server's English name the fallback.
//
// No Gradle runs here: this reads the sources and resources.
// Run: GOWORK=off go test -count=1 test-app-languages_test.go

const appLanguagesMain = "app/app/src/main"

func appLanguagesRead(t *testing.T, rel string) string {
	t.Helper()
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("cannot locate the Android source")
	}
	data, err := os.ReadFile(filepath.Join(filepath.Dir(source), rel))
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

// The BCP-47 tag of each res/values* directory that holds a strings.xml
// ("values-es-rMX" is es-MX, "values" is en), sorted.
func appLanguagesCatalogTags(t *testing.T) []string {
	t.Helper()
	_, source, _, _ := runtime.Caller(0)
	matches, err := filepath.Glob(filepath.Join(filepath.Dir(source), appLanguagesMain, "res", "values*", "strings.xml"))
	if err != nil {
		t.Fatal(err)
	}
	tags := []string{}
	for _, match := range matches {
		dir := filepath.Base(filepath.Dir(match))
		if dir == "values" {
			tags = append(tags, "en")
			continue
		}
		language, region, hasRegion := strings.Cut(strings.TrimPrefix(dir, "values-"), "-r")
		if hasRegion {
			tags = append(tags, language+"-"+region)
		} else {
			tags = append(tags, language)
		}
	}
	sort.Strings(tags)
	return tags
}

func TestAppLanguagesTurkishAndPersianShip(t *testing.T) {
	for _, dir := range []string{"values-tr", "values-fa"} {
		catalog := appLanguagesRead(t, filepath.Join(appLanguagesMain, "res", dir, "strings.xml"))
		if !regexp.MustCompile(`<string name="connect">[^<]+</string>`).MatchString(catalog) {
			t.Errorf("%s/strings.xml has no translated connect string", dir)
		}
	}
}

func TestAppLanguagesLocaleConfigListsEveryCatalog(t *testing.T) {
	config := appLanguagesRead(t, filepath.Join(appLanguagesMain, "res", "xml", "locales_config.xml"))
	listed := []string{}
	for _, m := range regexp.MustCompile(`<locale android:name="([^"]+)" />`).FindAllStringSubmatch(config, -1) {
		listed = append(listed, m[1])
	}
	sort.Strings(listed)
	want := appLanguagesCatalogTags(t)
	if strings.Join(listed, " ") != strings.Join(want, " ") {
		t.Fatalf("locales_config.xml lists %v; the catalogs are %v", listed, want)
	}
}

func TestAppLanguagesManifestNamesTheLocaleConfig(t *testing.T) {
	manifest := appLanguagesRead(t, filepath.Join(appLanguagesMain, "AndroidManifest.xml"))
	application := regexp.MustCompile(`(?s)<application\b[^>]*>`).FindString(manifest)
	if !strings.Contains(application, `android:localeConfig="@xml/locales_config"`) {
		t.Fatalf("the <application> element does not name @xml/locales_config: %s", application)
	}
}

func TestAppLanguagesSettingsOffersTheShippedLanguages(t *testing.T) {
	settings := appLanguagesRead(t, filepath.Join(appLanguagesMain, "java/com/bringyour/network/ui/settings/SettingsScreen.kt"))
	if !strings.Contains(settings, "AppLanguageSettingsRow()") {
		t.Error("Settings does not show the Language row")
	}
	picker := appLanguagesRead(t, filepath.Join(appLanguagesMain, "java/com/bringyour/network/ui/settings/AppLanguage.kt"))
	for _, want := range []string{
		// the shipped languages, from the manifest's locale config
		"LocaleConfig(context).supportedLocales",
		// the system's per-app language: the same setting as the system's
		"localeManager.applicationLocales =",
		"LocaleList.getEmptyLocaleList()",
		// shown on Android 13 and later only
		"Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU",
		"R.string.app_language",
		"R.string.app_language_system_default",
	} {
		if !strings.Contains(picker, want) {
			t.Errorf("AppLanguage.kt does not contain %q", want)
		}
	}
	base := appLanguagesRead(t, filepath.Join(appLanguagesMain, "res", "values", "strings.xml"))
	for _, name := range []string{"app_language", "app_language_system_default"} {
		if !strings.Contains(base, `<string name="`+name+`">`) {
			t.Errorf("values/strings.xml has no %s", name)
		}
	}
}

func TestAppLanguagesLocationListNamesCountriesInTheAppLanguage(t *testing.T) {
	list := appLanguagesRead(t, filepath.Join(appLanguagesMain, "java/com/bringyour/network/ui/connect/LocationsList.kt"))
	if strings.Contains(list, "location = location.name") {
		t.Error("a location row shows the server's name instead of locationDisplayName")
	}
	if n := strings.Count(list, "location = locationDisplayName(location)"); n != 6 {
		t.Errorf("%d location rows use locationDisplayName; the list has 6 (favorites, best matches, countries, regions, cities, devices)", n)
	}
	button := appLanguagesRead(t, filepath.Join(appLanguagesMain, "java/com/bringyour/network/ui/connect/ConnectActions.kt"))
	if !strings.Contains(button, "else -> locationDisplayName(selectedLocation)") {
		t.Error("the connect screen's location button does not name the location as the list does")
	}
	name := appLanguagesRead(t, filepath.Join(appLanguagesMain, "java/com/bringyour/network/ui/connect/LocationDisplayName.kt"))
	// the connection region screens' lookup, so both name a country alike
	if !strings.Contains(name, "countryName = ::platformCountryName") {
		t.Error("locationDisplayName does not use the connection region screens' country lookup")
	}
	region := appLanguagesRead(t, filepath.Join(appLanguagesMain, "java/com/bringyour/network/ui/connection_region/ConnectionRegionUi.kt"))
	if !strings.Contains(region, "platformCountryName(countryCode).ifEmpty { countryCode.uppercase() }") {
		t.Error("the connection region screens do not use platformCountryName")
	}
}
