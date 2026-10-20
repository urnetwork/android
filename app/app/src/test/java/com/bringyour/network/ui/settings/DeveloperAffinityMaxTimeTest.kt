package com.bringyour.network.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure presentation plus the same source-wiring checks as the other settings editors. */
class DeveloperAffinityMaxTimeTest {
    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/ui/settings/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing $start", from >= 0)
        val to = source.indexOf(end, from)
        assertTrue("missing $end after $start", to > from)
        return source.substring(from, to)
    }

    @Test
    fun affinityUsesTheDurationEditorAndSignedMillisecondSetter() {
        val screen = source("DeveloperScreen.kt")
        val label = screen.indexOf("R.string.dev_affinity_max_time)")
        assertTrue(label >= 0)
        val start = screen.lastIndexOf("    DeveloperDurationSetting(", label)
        assertTrue(start >= 0)
        val row = between(screen.substring(start), "    DeveloperDurationSetting(", "\n    )")
        assertTrue(row.contains("R.string.dev_affinity_max_time_detail"))
        assertTrue(row.contains("millis = reliability?.affinityMaxTimeMillis ?: 0L"))
        assertTrue(row.contains("presets = DeveloperViewModel.AFFINITY_MAX_TIME_PRESETS"))
        assertTrue(row.contains("onSelect = developerViewModel.setAffinityMaxTimeMillis"))
        assertFalse(row.contains("checked ="))
        assertFalse(screen.contains("freshFlowAffinity"))
        val viewModel = source("DeveloperViewModel.kt")
        val setter = between(viewModel, "    val setAffinityMaxTimeMillis: (Long) -> Unit = { millis ->", "\n    }")
        assertTrue(setter.contains("update { s -> s.affinityMaxTimeMillis = millis }"))
        assertFalse(viewModel.contains("setFreshFlowAffinity"))
        assertEquals(2, Regex("R[.]string[.]dev_group_follow_compat_detail").findAll(screen).count())
        assertFalse(screen.contains("R.string.dev_group_follow_detail)"))
        assertFalse(screen.contains("R.string.dev_group_follow_window_detail)"))
    }

    @Test
    fun presetsUseMillisecondsAndIncludeOffAndTheConnectDefault() {
        assertEquals(
            listOf(0L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L),
            DeveloperViewModel.AFFINITY_MAX_TIME_PRESETS,
        )
        val editor = between(source("DeveloperScreen.kt"), "private fun DeveloperDurationSetting(", "\n/**")
        assertTrue(editor.contains("val index = presets.indexOf(millis)"))
        assertTrue(editor.contains("onSelect(presets[(index + 1) % presets.size])"))
        assertTrue(editor.contains("formatDurationMillis(millis)"))
    }

    @Test
    fun disabledAndPositiveValuesArePresentedAsDurations() {
        for (value in listOf(Long.MIN_VALUE, -1L, 0L)) {
            assertEquals("Off", formatDurationMillis(value))
        }
        for ((value, expected) in listOf(
            1L to "1ms", 999L to "999ms", 1_000L to "1s",
            1_500L to "1.5s", 15_000L to "15s", 30_000L to "30s",
            60_000L to "1m", 120_000L to "2m", 300_000L to "5m",
        )) {
            assertEquals(expected, formatDurationMillis(value))
        }
    }

    @Test
    fun updatesRequireEffectiveSettingsAndReadBackTheAppliedValue() {
        val viewModel = source("DeveloperViewModel.kt")
        val update = between(viewModel, "    private fun update(mutate: (ReliabilitySettings) -> Unit) {", "\n    }")
        val gate = update.indexOf("val current = reliability ?: return")
        val write = update.indexOf("deviceManager.device?.reliabilitySettings = current")
        val refresh = update.indexOf("refresh()")
        assertTrue(gate >= 0 && gate < write && write < refresh)
        assertTrue(update.contains("mutate(current)"))
        val read = between(viewModel, "    fun refresh() {", "\n    private fun update(")
        assertTrue(read.contains("reliability = null"))
        assertTrue(read.contains("reliability = device.reliabilitySettings"))
    }

    @Test
    fun resetRestoresSdkDefaultsInsteadOfWritingAnOffOrLocalDefaultValue() {
        val reset = between(source("DeveloperViewModel.kt"), "    val resetReliability: () -> Unit = {", "\n    }")
        val call = reset.indexOf("deviceManager.device?.resetReliabilitySettings()")
        val refresh = reset.indexOf("refresh()")
        assertTrue(call >= 0 && call < refresh)
        assertFalse(reset.contains("affinityMaxTimeMillis ="))
        assertFalse(reset.contains("ReliabilitySettings("))
    }
}
