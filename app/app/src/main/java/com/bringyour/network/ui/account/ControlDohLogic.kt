package com.bringyour.network.ui.account

import androidx.annotation.StringRes
import com.bringyour.network.R

/**
 * The plain shapes and decisions of the bootstrap DNS-over-HTTPS servers
 * (Account > Extenders, and the login screen's network settings): how the
 * multiline field maps to the network space's list, what shows under it, and
 * the messages of the sdk's error ids.
 *
 * The sdk owns the rules: the url check, the presets and the write all live in
 * it (control_doh.go, control_doh_ui.go), so nothing here validates a url.
 * What lives here is what the screens do with the sdk's answers, kept off the
 * gomobile classes so it runs in a JVM test.
 */

// The sdk's error ids (Sdk.ControlDohError*), as literals so this file stays
// off the native class's initializer. Each id is the key of its message.
const val CONTROL_DOH_ERROR_URL_INVALID = "control_doh_error_url_invalid"
const val CONTROL_DOH_ERROR_HTTPS_REQUIRED = "control_doh_error_https_required"
const val CONTROL_DOH_ERROR_IP_REQUIRED = "control_doh_error_ip_required"
const val CONTROL_DOH_ERROR_TOO_MANY = "control_doh_error_too_many"

// The country of the "Use China resolvers" preset (Sdk.regionalControlDohUrls)
const val CONTROL_DOH_PRESET_CHINA = "cn"

/** The field's text for a list of servers: one url per line. */
fun controlDohText(urls: List<String>): String = urls.joinToString("\n")

/**
 * The servers of the multiline field, in the order typed. A line ends at a
 * \n or a \r, so text pasted with Windows line endings reads the same, and
 * surrounding whitespace and blank lines are not servers. A comma does not
 * separate servers, since a url may carry one. The sdk drops repeats and
 * normalizes each url.
 */
fun controlDohLinesFromText(text: String): List<String> =
    text.split('\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * The error id of the first line that does not validate, empty when every
 * line does. `validate` is `Sdk.validateControlDohUrl`, which answers an id
 * or empty.
 */
fun controlDohValidationErrorId(lines: List<String>, validate: (String) -> String): String =
    lines.firstNotNullOfOrNull { line -> validate(line).ifEmpty { null } } ?: ""

/**
 * The error shown under the field. What the last save answered stands until
 * the text changes (too many servers is only known then); otherwise the
 * check of each line as it is typed.
 */
fun controlDohFormErrorId(validationErrorId: String, saveErrorId: String): String =
    saveErrorId.ifEmpty { validationErrorId }

/**
 * The localized message for one of the sdk's error ids. Anything this build
 * does not know reads as an invalid url.
 */
@StringRes
fun controlDohErrorResId(errorId: String): Int = when (errorId) {
    CONTROL_DOH_ERROR_URL_INVALID -> R.string.control_doh_error_url_invalid
    CONTROL_DOH_ERROR_HTTPS_REQUIRED -> R.string.control_doh_error_https_required
    CONTROL_DOH_ERROR_IP_REQUIRED -> R.string.control_doh_error_ip_required
    CONTROL_DOH_ERROR_TOO_MANY -> R.string.control_doh_error_too_many
    else -> R.string.control_doh_error_url_invalid
}
