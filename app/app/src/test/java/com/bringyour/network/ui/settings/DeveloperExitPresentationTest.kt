package com.bringyour.network.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The developer screen's exit readout shows the security rules generation of
 * each exit's provider: the number once the provider's diagnostics arrive,
 * "unknown" for a provider that reports its policy without one, and nothing
 * before its first diagnostics. The sdk carried no generation before, so no
 * readout could show which exits run older rules.
 */
class DeveloperExitPresentationTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val DEVELOPER_SCREEN =
            "src/main/java/com/bringyour/network/ui/settings/DeveloperScreen.kt"

        private fun stringValue(locale: String, name: String): String? {
            val dir = if (locale == "en") "values" else "values-$locale"
            val xml = moduleFile("src/main/res/$dir/strings.xml").readText()
            return Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)
        }
    }

    @Test
    fun noLineBeforeTheProvidersFirstDiagnostics() {
        for (generation in listOf(0L, 2L)) {
            assertNull(
                "generation $generation without diagnostics",
                exitPolicyGenerationLine(
                    providerDiagnosticsAvailable = false,
                    providerSecurityPolicyGeneration = generation,
                ),
            )
        }
    }

    @Test
    fun theReportedGenerationIsShown() {
        for (generation in listOf(1L, 2L, Long.MAX_VALUE)) {
            assertEquals(
                ExitPolicyGenerationLine.Known(generation),
                exitPolicyGenerationLine(
                    providerDiagnosticsAvailable = true,
                    providerSecurityPolicyGeneration = generation,
                ),
            )
        }
    }

    @Test
    fun aProviderWithoutAGenerationReadsUnknown() {
        // 0 is connect's unknown; a negative value never comes from the sdk
        // and reads the same
        for (generation in listOf(0L, -1L)) {
            assertEquals(
                ExitPolicyGenerationLine.Unknown,
                exitPolicyGenerationLine(
                    providerDiagnosticsAvailable = true,
                    providerSecurityPolicyGeneration = generation,
                ),
            )
        }
    }

    @Test
    fun theExitRowShowsThePolicyLine() {
        val screen = moduleFile(DEVELOPER_SCREEN).readText()
        assertTrue(
            "the exit row does not read the provider's generation",
            screen.contains("providerSecurityPolicyGeneration = exit.providerSecurityPolicyGeneration"),
        )
        assertTrue(
            "the exit row does not read whether the provider's diagnostics arrived",
            screen.contains("providerDiagnosticsAvailable = exit.providerDiagnosticsAvailable"),
        )
        assertTrue(screen.contains("R.string.dev_exit_policy_generation, policyGenerationLine.generation"))
        assertTrue(screen.contains("R.string.dev_exit_policy_generation_unknown"))
    }

    @Test
    fun thePolicyLinesAreLocalized() {
        assertEquals("policy generation %1\$d", stringValue("en", "dev_exit_policy_generation"))
        assertEquals("policy generation unknown", stringValue("en", "dev_exit_policy_generation_unknown"))
        for (locale in listOf("ar", "de", "es", "ja", "ru", "zh")) {
            val known = stringValue(locale, "dev_exit_policy_generation")
            assertNotNull("$locale translation missing", known)
            assertTrue("$locale drops the generation: $known", known!!.contains("%1\$d"))
            assertNotNull(
                "$locale translation missing",
                stringValue(locale, "dev_exit_policy_generation_unknown"),
            )
        }
    }
}
