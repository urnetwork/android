package com.bringyour.network.ui.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.FoldSafeAlertDialog
import com.bringyour.network.ui.theme.TextMuted
import java.util.Locale

/*
 * Settings > General > Language (Android 13 and later): the app's own
 * language, over the phone's.
 *
 * The choices are System default, then the languages the app ships, each named
 * in itself. Those are res/xml/locales_config.xml, which the localizations
 * store generates with the values-* catalogs, so a language is offered exactly
 * when the app has its strings. A choice is the system's per-app language
 * (LocaleManager): the same setting as the system's Apps > URnetwork >
 * Language, kept across restarts and applied to the whole app, its
 * notifications and widgets included. The system recreates the activity in
 * the new language. Android 12 and earlier have no per-app language, so the
 * row is not shown there.
 */

/** A choice of the language picker. [tag] "" is System default. */
data class AppLanguageOption(val tag: String, val name: String)

/**
 * The picker's choices: System default, then each language the app ships in
 * the locale config's order, named by [nameOf]. Pure, for tests.
 */
fun appLanguageOptions(tags: List<String>, systemDefault: String, nameOf: (String) -> String): List<AppLanguageOption> =
    listOf(AppLanguageOption(tag = "", name = systemDefault)) +
        tags.distinct().map { AppLanguageOption(tag = it, name = nameOf(it)) }

/**
 * The choice the app's applied language tags select: that exact language, else
 * the shipped language of the same base language ("fa-IR" is fa), else System
 * default. Pure, for tests.
 */
fun selectedAppLanguage(applied: String, tags: List<String>): String {
    val first = applied.substringBefore(',').trim()
    if (first.isEmpty()) {
        return ""
    }
    tags.firstOrNull { it.equals(first, ignoreCase = true) }?.let { return it }
    val language = first.substringBefore('-')
    return tags.firstOrNull { it.equals(language, ignoreCase = true) } ?: ""
}

/** A language's own name for itself, capitalized as that language writes a label. */
fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun shippedLanguageTags(context: Context): List<String> {
    val locales = LocaleConfig(context).supportedLocales ?: return emptyList()
    return (0 until locales.size()).map { locales[it].toLanguageTag() }
}

/** The Language row and its picker; nothing before Android 13. */
@Composable
fun AppLanguageSettingsRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }
    AppLanguageSettingsRowWithLocaleManager()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AppLanguageSettingsRowWithLocaleManager() {
    val context = LocalContext.current
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) } ?: return
    val tags = remember(context) { shippedLanguageTags(context) }
    if (tags.isEmpty()) {
        return
    }
    val systemDefault = stringResource(id = R.string.app_language_system_default)
    val options = remember(tags, systemDefault) { appLanguageOptions(tags, systemDefault, ::languageName) }
    // a change, here or in the system's settings, recreates the activity, so
    // the value read once per composition stays current
    val selected = remember(localeManager) {
        selectedAppLanguage(localeManager.applicationLocales.toLanguageTags(), tags)
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    val choose = { tag: String ->
        showPicker = false
        if (tag != selected) {
            localeManager.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(id = R.string.app_language),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            options.firstOrNull { it.tag == selected }?.name ?: systemDefault,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }

    if (showPicker) {
        FoldSafeAlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(id = R.string.app_language)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (option in options) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { choose(option.tag) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.tag == selected,
                                onClick = { choose(option.tag) }
                            )
                            Text(option.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }
}
