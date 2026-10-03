package com.bringyour.network.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The add sign-in method sheet, its Google button and the sign-in screen's
 * error toasts were English literals ("Sign-in method added successfully",
 * "Wallet sign-in method added", ...), so every locale showed them in English.
 * They are store keys now.
 *
 * Reads the module's sources and generated string resources; no device.
 */
class AddAuthStringsLocalizedTest {

    private val res = File("src/main/res")

    private val sources = listOf(
        "src/main/java/com/bringyour/network/ui/settings/AddAuthMethodSheet.kt",
        "src/google/java/com/bringyour/network/ui/settings/GoogleAddAuthButton.kt",
        "src/ethos_dapp/java/com/bringyour/network/ui/settings/GoogleAddAuthButton.kt",
        "src/solana_dapp/java/com/bringyour/network/ui/settings/GoogleAddAuthButton.kt",
        "src/google/java/com/bringyour/network/ui/login/LoginInitial.kt",
        "src/ethos_dapp/java/com/bringyour/network/ui/login/LoginInitial.kt",
        "src/ungoogle/java/com/bringyour/network/ui/login/LoginInitial.kt",
        "src/solana_dapp/java/com/bringyour/network/ui/login/LoginInitial.kt",
    )

    // user-visible text handed over as a literal; product names may stay literal
    private val literalText = Regex(
        """(Toast\.makeText\([^,]+,\s*|Text\(\s*|(?:label|placeholder)\s*=\s*|addError\s*=\s*|onError\()"([^"]+)""""
    )
    private val productNames = setOf("Google")

    private val keys = listOf(
        "sign_in_method_added_successfully",
        "wallet_sign_in_method_added",
        "google_sign_in_method_added",
        "add_a_sign_in_method",
        "link_another_way_to_sign_in_to",
        "wallet",
        "site_app_email",
        "your_email_com",
        "enter_a_password",
        "add_sign_in_method_2",
        "sign_in_with_google_to_add_it",
        "sign_in_with_google",
        "connect_solana_wallet_to_add_sign_in_method",
        "error_connecting_to_wallet",
        "no_compatible_wallet_app_found",
        "could_not_get_google_id_token",
        "error_signing_in_with_google",
        "error_logging_in_please_try_again",
        "no_ethos_wallet_found",
    )

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `add sign-in method text is not an english literal`() {
        val literals = sources.flatMap { path ->
            val file = File(path)
            assertTrue("missing $path", file.exists())
            literalText.findAll(file.readText())
                .map { it.groupValues[2] }
                .filter { it !in productNames }
                .map { "${file.path}: \"$it\"" }
                .toList()
        }
        assertTrue("english literals: $literals", literals.isEmpty())
    }

    @Test
    fun `add sign-in method strings are in every locale`() {
        val locales = res.listFiles { file -> file.name == "values" || file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.size > 1)

        val missing = mutableListOf<String>()
        for (locale in locales) {
            val values = strings(locale)
            for (key in keys) {
                if (values[key].isNullOrEmpty()) {
                    missing.add("${locale.name}/$key")
                }
            }
        }
        assertTrue("missing: $missing", missing.isEmpty())
    }
}
