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
        MockApp("com.example.notebook", "Example Notebook"),
        MockApp("org.example.reader", "Reader Browser"),
        MockApp("com.example.music", "Music"),
        MockApp("org.example.messenger", "Messenger"),
        MockApp("com.example.search.mobile", "Search"),
    )

    private val labels = apps.associate { it.packageName to it.label }

    private val rules = listOf(
        MockRule("rule-1", "com.example.notebook"),
        MockRule("rule-2", "org.example.reader"),
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
        val result = AppSplitFilter.filterApps(apps, "NOTEBOOK", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.example.notebook"), result.map { it.packageName })

        val mixedCase = AppSplitFilter.filterApps(apps, "ReAdEr", MockApp::label, MockApp::packageName)
        assertEquals(listOf("org.example.reader"), mixedCase.map { it.packageName })
    }

    @Test
    fun filterByPackageNameCaseInsensitive() {
        val result = AppSplitFilter.filterApps(apps, "org.example.mess", MockApp::label, MockApp::packageName)
        assertEquals(listOf("org.example.messenger"), result.map { it.packageName })

        val search = AppSplitFilter.filterApps(apps, "search", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.example.search.mobile"), search.map { it.packageName })
    }

    @Test
    fun queryWithWhitespaceIsTrimmed() {
        val result = AppSplitFilter.filterApps(apps, "  music  ", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.example.music"), result.map { it.packageName })

        val ruleResult = AppSplitFilter.filterRules(rules, labels, "  notebook  ", MockRule::appId)
        assertEquals(listOf("rule-1"), ruleResult.map { it.id })
    }

    @Test
    fun filterRulesMatchesLabelOrPackageId() {
        // Matches label "Reader Browser"
        val byLabel = AppSplitFilter.filterRules(rules, labels, "browser", MockRule::appId)
        assertEquals(listOf("rule-2"), byLabel.map { it.id })

        // Matches package id "com.example.notebook"
        val byPackage = AppSplitFilter.filterRules(rules, labels, "example.notebook", MockRule::appId)
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
        // "org." matches both the reader and the messenger
        val result = AppSplitFilter.filterApps(apps, "org.", MockApp::label, MockApp::packageName)
        assertEquals(listOf("org.example.reader", "org.example.messenger"), result.map { it.packageName })
    }

    @Test
    fun partialMatchFindsSubstrings() {
        val result = AppSplitFilter.filterApps(apps, "oo", MockApp::label, MockApp::packageName)
        assertEquals(listOf("com.example.notebook"), result.map { it.packageName }) // "Example Notebook" has "oo"
    }
}
