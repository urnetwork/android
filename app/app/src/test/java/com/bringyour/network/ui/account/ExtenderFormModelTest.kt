package com.bringyour.network.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The extenders form against a fake network space. Reported defect: after an
 * import on the import screen, the extenders screen kept showing the old dns
 * name and gossip url until it was recreated, although the import had saved
 * the new ones. Each screen has its own view model, and the form's never read
 * the space again.
 */
class ExtenderFormModelTest {

    /**
     * One network space, which both screens' view models read: the settings
     * through a controller, none while `controllerOpen` is false, and the
     * private extender from the space itself.
     */
    private class FakeSpace(
        var settings: ExtenderSettingsUi,
        var privateExtender: ExtenderPrivateUi = ExtenderPrivateUi(),
        var controllerOpen: Boolean = true,
    ) : ExtenderFormSource {
        override fun readSettings(): ExtenderSettingsUi? =
            if (controllerOpen) settings else null

        override fun readPrivateExtender(): ExtenderPrivateUi = privateExtender
    }

    // the derived defaults of a network.example space
    private val defaults = ExtenderSettingsUi(
        dnsName = "extender.network.example",
        dnsNameDefault = true,
        gossipUrl = "wss://gossip.network.example",
        gossipUrlDefault = true,
        hosts = listOf("192.0.2.1"),
        networkHost = "network.example",
    )

    // what an import with another operator's settings leaves in the space: its
    // dns name and gossip url replace the defaults, while the hosts and the
    // network host stay this space's
    private val imported = defaults.copy(
        dnsName = "extender.example",
        dnsNameDefault = false,
        gossipUrl = "wss://gossip.example",
        gossipUrlDefault = false,
    )

    private val savedPrivateExtender = ExtenderPrivateUi(ip = "192.0.2.7", secret = "secret")

    // a screen's view model as it opens: the controller reads the settings,
    // and the private extender is read from the space
    private fun openOn(space: FakeSpace) = ExtenderFormModel(space).apply {
        showSettings(space.readSettings())
        reloadPrivateExtender()
    }

    @Test
    fun theFormShowsAnImportMadeOnTheImportScreenWhenItShowsAgain() {
        val space = FakeSpace(settings = defaults)
        val form = openOn(space)
        val importScreen = openOn(space)

        // the import saves through the import screen's view model, which shows
        // what it saved (ExtendersViewModel.importShare)
        space.settings = imported
        importScreen.showSettings(space.readSettings())

        // the form's view model is another instance, and nothing told it
        assertEquals(defaults, form.settings)

        // back on the extenders screen
        form.reload()

        assertEquals(imported, form.settings)
        // the boxes fill with the imported values, and an override has no
        // default to offer as the placeholder
        assertEquals("extender.example", form.settings?.dnsNameField)
        assertEquals("wss://gossip.example", form.settings?.gossipUrlField)
        assertEquals("", form.settings?.dnsNamePlaceholder)
        assertEquals("", form.settings?.gossipUrlPlaceholder)
    }

    @Test
    fun aReloadReadsEveryField() {
        val space = FakeSpace(settings = defaults)
        val form = openOn(space)

        // not only what an import replaces: the hosts and the private extender
        // are read again too
        val changed = imported.copy(hosts = listOf("extender.example", "2001:db8::1"))
        space.settings = changed
        space.privateExtender = savedPrivateExtender
        form.reload()

        assertEquals(changed, form.settings)
        assertEquals(savedPrivateExtender, form.privateExtender)
    }

    @Test
    fun withNoControllerAReloadKeepsTheSettingsShown() {
        val space = FakeSpace(settings = defaults)
        val form = openOn(space)

        // signed out, or between devices, there is nothing to read the
        // settings through and the form is not editable: it keeps what it
        // showed rather than go blank
        space.controllerOpen = false
        space.settings = imported
        space.privateExtender = savedPrivateExtender
        form.reload()

        assertEquals(defaults, form.settings)
        // the private extender is a space value and is still read
        assertEquals(savedPrivateExtender, form.privateExtender)
    }

    @Test
    fun aFormThatNeverHadAControllerShowsNoSettings() {
        val space = FakeSpace(settings = defaults, controllerOpen = false)
        val form = ExtenderFormModel(space)

        form.reload()

        // the screen leaves its boxes empty until there are settings to show
        assertNull(form.settings)

        // and a reload with a controller open reads them
        space.controllerOpen = true
        form.reload()

        assertEquals(defaults, form.settings)
    }
}
