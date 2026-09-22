package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSplitFilterTest {

    private data class MockApp(
        val packageName: String,
        val label: String,
    )

    private data class MockRule(
        val id: String,
        val appId: String,
    )

    private val apps = listOf(
        MockApp("com.android.chrome", "Google Chrome"),
        MockApp("org.mozilla.firefox", "Firefox Browser"),
        MockApp("com.spotify.music", "Spotify"),
        MockApp("org.signal.messenger", "Signal"),
        MockApp("com.duckduckgo.mobile.android", "DuckDuckGo"),
    )

    private val labels = apps.associate { it.packageName to it.label }

    private val rules = listOf(
        MockRule("rule-1", "com.android.chrome"),
        MockRule("rule-2", "org.mozilla.firefox"),
        MockRule("rule-3", "com.example.uninstalled"),
    )

    @Test
    fun emptyOrBlankQueryReturnsAllRulesAndApps() {
        val emptyFilteredRules = AppSplitFilter.filterRules(rules, labels, "", MockRule::appId)
        val blankFilteredRules = AppSplitFilter.filterRules(rules, labels, "   ", MockRule::appId)
        assertEquals(rules, emptyFilteredRules)
        assertEquals(rules, blankFilteredRules)

        val emptyFilteredApps = AppSplitFilter.filterApps(apps, "", MockApp::label, MockApp::packageName)
        val blankFilteredApps = AppSplitFilter.filterApps(apps, "   ", MockApp::label, MockApp::packageName)
        assertEquals(apps, emptyFilteredApps)
        assertEquals(apps, blankFilteredApps)
    }

    @Test
    fun filterByAppLabelCaseInsensitive() {
        val result = AppSplitFilter.filterApps(apps, "CHROME", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.android.chrome"), result.map { it.packageName })

        val mixedCase = AppSplitFilter.filterApps(apps, "FiReFoX", MockApp::label, MockApp::packageName)
        assertEquals(listOf("org.mozilla.firefox"), mixedCase.map { it.packageName })
    }

    @Test
    fun filterByPackageNameCaseInsensitive() {
        val result = AppSplitFilter.filterApps(apps, "org.signal", MockApp::label, MockApp::packageName)
        assertEquals(listOf("org.signal.messenger"), result.map { it.packageName })

        val duck = AppSplitFilter.filterApps(apps, "duckduckgo", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.duckduckgo.mobile.android"), duck.map { it.packageName })
    }

    @Test
    fun queryWithWhitespaceIsTrimmed() {
        val result = AppSplitFilter.filterApps(apps, "  spotify  ", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.spotify.music"), result.map { it.packageName })

        val ruleResult = AppSplitFilter.filterRules(rules, labels, "  chrome  ", MockRule::appId)
        assertEquals(listOf("rule-1"), ruleResult.map { it.id })
    }

    @Test
    fun filterRulesMatchesLabelOrPackageId() {
        // Matches label "Firefox Browser"
        val byLabel = AppSplitFilter.filterRules(rules, labels, "browser", MockRule::appId)
        assertEquals(listOf("rule-2"), byLabel.map { it.id })

        // Matches package id "com.android.chrome"
        val byPackage = AppSplitFilter.filterRules(rules, labels, "android.chrome", MockRule::appId)
        assertEquals(listOf("rule-1"), byPackage.map { it.id })
    }

    @Test
    fun ruleWithoutInstalledAppMatchesPackageId() {
        // com.example.uninstalled is not in `labels` map, but matches package ID query
        val uninstalled = AppSplitFilter.filterRules(rules, labels, "uninstalled", MockRule::appId)
        assertEquals(listOf("rule-3"), uninstalled.map { it.id })
    }

    @Test
    fun noMatchReturnsEmptyList() {
        val noApp = AppSplitFilter.filterApps(apps, "nonexistentapp", MockApp::label, MockApp::packageName)
        assertTrue(noApp.isEmpty())

        val noRule = AppSplitFilter.filterRules(rules, labels, "nonexistentapp", MockRule::appId)
        assertTrue(noRule.isEmpty())
    }

    @Test
    fun multipleMatchesPreserveOriginalOrder() {
        // "org." matches both Firefox and Signal
        val result = AppSplitFilter.filterApps(apps, "org.", MockApp::label, MockApp::packageName)
        assertEquals(listOf("org.mozilla.firefox", "org.signal.messenger"), result.map { it.packageName })
    }

    @Test
    fun partialMatchFindsSubstrings() {
        val result = AppSplitFilter.filterApps(apps, "oo", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.android.chrome"), result.map { it.packageName }) // "Google Chrome" has "oo"
    }
}
