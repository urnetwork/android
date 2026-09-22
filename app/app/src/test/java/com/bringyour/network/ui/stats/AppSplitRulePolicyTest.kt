package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSplitRulePolicyTest {

    private data class RuleEntry(
        val appId: String,
        val mode: AppSplitMode,
    )

    @Test
    fun resolveModeFromLowLevelFlags() {
        assertEquals(AppSplitMode.EXCLUDED, AppSplitRulePolicy.resolveMode(local = true, pin = false))
        assertEquals(AppSplitMode.PINNED, AppSplitRulePolicy.resolveMode(local = false, pin = true))
        assertEquals(AppSplitMode.INCLUDED, AppSplitRulePolicy.resolveMode(local = false, pin = false))

        // Local routing takes precedence over pinning
        assertEquals(AppSplitMode.EXCLUDED, AppSplitRulePolicy.resolveMode(local = true, pin = true))
    }

    @Test
    fun partitioningSeparatesModesCorrectly() {
        val rules = listOf(
            RuleEntry("com.included.app", AppSplitMode.INCLUDED),
            RuleEntry("com.excluded.app", AppSplitMode.EXCLUDED),
            RuleEntry("com.pinned.app", AppSplitMode.PINNED),
        )

        val partition = AppSplitRulePolicy.partition(
            rules = rules,
            appIdSelector = RuleEntry::appId,
            modeSelector = RuleEntry::mode,
        )

        assertEquals(listOf("com.included.app"), partition.includedAppIds)
        assertEquals(listOf("com.excluded.app"), partition.excludedAppIds)
        assertEquals(listOf("com.pinned.app"), partition.pinnedAppIds)

        // PINNED apps must never leak into includedAppIds or excludedAppIds
        assertFalse(partition.includedAppIds.contains("com.pinned.app"))
        assertFalse(partition.excludedAppIds.contains("com.pinned.app"))
    }

    @Test
    fun emptyRulesResultInStandardMode() {
        val partition = AppSplitRulePolicy.partition(
            rules = emptyList<RuleEntry>(),
            appIdSelector = RuleEntry::appId,
            modeSelector = RuleEntry::mode,
        )

        assertFalse(partition.isIncludeMode)
        assertFalse(partition.isExcludeMode)
    }

    @Test
    fun onlyExcludedAppsResultInDenylistMode() {
        val rules = listOf(
            RuleEntry("com.excluded.one", AppSplitMode.EXCLUDED),
            RuleEntry("com.excluded.two", AppSplitMode.EXCLUDED),
        )

        val partition = AppSplitRulePolicy.partition(
            rules = rules,
            appIdSelector = RuleEntry::appId,
            modeSelector = RuleEntry::mode,
        )

        assertFalse(partition.isIncludeMode)
        assertTrue(partition.isExcludeMode)
        assertTrue(partition.isRuleActive(AppSplitMode.EXCLUDED))
    }

    @Test
    fun inclusionTakesPrecedenceOverExclusion() {
        // Having even one included app forces the tunnel into allowlist mode
        val rules = listOf(
            RuleEntry("com.included.app", AppSplitMode.INCLUDED),
            RuleEntry("com.excluded.app", AppSplitMode.EXCLUDED),
        )

        val partition = AppSplitRulePolicy.partition(
            rules = rules,
            appIdSelector = RuleEntry::appId,
            modeSelector = RuleEntry::mode,
        )

        assertTrue(partition.isIncludeMode)
        assertFalse(partition.isExcludeMode)

        // Under allowlist mode, exclude rules are inactive/muted because allowlist takes precedence
        assertFalse(partition.isRuleActive(AppSplitMode.EXCLUDED))
        assertTrue(partition.isRuleActive(AppSplitMode.INCLUDED))
        assertTrue(partition.isRuleActive(AppSplitMode.PINNED))
    }

    @Test
    fun pinnedAppsDoNotTriggerAllowlistOrDenylistMode() {
        val rules = listOf(
            RuleEntry("com.pinned.app", AppSplitMode.PINNED),
        )

        val partition = AppSplitRulePolicy.partition(
            rules = rules,
            appIdSelector = RuleEntry::appId,
            modeSelector = RuleEntry::mode,
        )

        // Pinning is exit placement, not tunnel membership
        assertFalse(partition.isIncludeMode)
        assertFalse(partition.isExcludeMode)
        assertTrue(partition.isRuleActive(AppSplitMode.PINNED))
    }
}
