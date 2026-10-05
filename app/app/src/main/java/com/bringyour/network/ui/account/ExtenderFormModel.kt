package com.bringyour.network.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The network space as the extenders form reads it (EXTENDER.md K6). The view
 * model reads through the sdk; the tests read a fake.
 */
interface ExtenderFormSource {
    /** The three settings, through the sdk controller. Null while none is open. */
    fun readSettings(): ExtenderSettingsUi?

    /** The legacy private extender, a space value with no controller of its own. */
    fun readPrivateExtender(): ExtenderPrivateUi
}

/**
 * The values the extenders form shows, as last read from the network space
 * (K6).
 *
 * Every screen of the section gets its own [ExtendersViewModel], so an import
 * on the import screen writes the space through another instance than the
 * form's, and nothing tells the form's. The screen therefore reloads each time
 * it shows: every field is read again, the same way it is read when the screen
 * opens, so the form does not keep showing the dns name and gossip url that an
 * import with settings has replaced.
 *
 * State is compose snapshot state, read by the extenders screen. Mutate on the
 * main thread only.
 */
class ExtenderFormModel(
    private val source: ExtenderFormSource,
) {
    /** The effective settings, or null until a controller has read them. */
    var settings by mutableStateOf<ExtenderSettingsUi?>(null)
        private set

    var privateExtender by mutableStateOf(ExtenderPrivateUi())
        private set

    /** Shows the settings an sdk call answered with: a controller opening, a save, an import. */
    fun showSettings(next: ExtenderSettingsUi?) {
        settings = next
    }

    /**
     * Reads every field again. Without a controller there is nothing to read
     * the settings through, and the form is not editable then: the settings
     * shown stay until the next controller opens and reads them.
     */
    fun reload() {
        source.readSettings()?.let { settings = it }
        reloadPrivateExtender()
    }

    /** Reads the legacy private extender again, which needs no controller. */
    fun reloadPrivateExtender() {
        privateExtender = source.readPrivateExtender()
    }
}
