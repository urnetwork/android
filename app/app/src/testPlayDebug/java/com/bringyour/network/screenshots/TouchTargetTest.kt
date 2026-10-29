package com.bringyour.network.screenshots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.components.ProductUpdatesOptOutRow
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.connect.ConnectOptions
import com.bringyour.network.ui.connect.WindowType
import com.bringyour.network.ui.stats.BlockActionRow
import com.bringyour.network.ui.stats.BlockActionUi
import com.bringyour.network.ui.theme.URNetworkTheme
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The app's small controls, each among the neighbors it has in the app, under
 * the Accessibility Scanner's touch target check (checkTouchTargets): the
 * switch, the terms checkbox, the info icon and the search field with its
 * clear button (Touch_Targets, apple/DUO.md). The screens with screenshots
 * run the same check on every capture.
 */
@Config(qualifiers = WindowSizes.COMPACT)
class TouchTargetTest : ScreenshotTest() {

    /**
     * The check fails a control it should: a 20 dp tall button right above a
     * 48 dp one. Compose grows the small one to 48 dp, but the one below
     * already covers the bottom of that, all the way across.
     */
    @Test
    fun theCheckFailsASmallTarget() {
        show {
            Column {
                Box(Modifier.size(width = 48.dp, height = 20.dp).clickable {})
                Box(Modifier.size(48.dp).clickable {})
            }
        }
        assertThrows(AccessibilityViewCheckException::class.java) {
            compose.checkTouchTargets()
        }
    }

    /** The drawer's switches: Fixed IP, strong anonymization and post-quantum encryption, stacked. */
    @Test
    fun switchesStackedInTheDrawer() {
        show {
            ConnectOptions(
                fixedIpSize = false,
                toggleFixedIpSize = {},
                selectedWindowType = WindowType.QUALITY,
                setSelectedWindowType = {},
                allowDirect = false,
                toggleAllowDirect = {},
                postQuantumEncryption = true,
                togglePostQuantumEncryption = {},
            )
        }
        compose.checkTouchTargets()
    }

    /** Sign up's terms checkbox, above the product updates switch as LoginCreateNetwork lays them out. */
    @Test
    fun termsCheckboxAboveTheProductUpdatesSwitch() {
        show {
            Column {
                Row {
                    TermsCheckbox(checked = false, onCheckChanged = {})
                }
                Spacer(modifier = Modifier.height(12.dp))
                ProductUpdatesOptOutRow(checked = true, onCheckedChanged = {})
            }
        }
        compose.checkTouchTargets()
    }

    /** An info icon beside its label, with Settings' closest spacing, in a row with a switch. */
    @Test
    fun infoIconBesideItsLabel() {
        show {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(id = R.string.kill_switch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    InfoIconWithOverlay(
                        contentDescription = stringResource(id = R.string.show_kill_switch_exception)
                    ) {}
                }
                URSwitch(checked = true, toggle = {})
            }
        }
        compose.checkTouchTargets()
    }

    /** The search field with text in it, so its clear button shows, above a list row. */
    @Test
    fun searchClearButton() {
        show {
            Column {
                URSearchInput(
                    value = TextFieldValue("Berlin"),
                    onValueChange = {},
                    keyboardController = null,
                    onClear = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.best_available_provider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {}
                        .padding(vertical = 14.dp),
                    color = Color.White,
                )
            }
        }
        compose.checkTouchTargets()
    }

    /** Sign up's terms in a wide window, where the text and its links fit on one line. */
    @Test
    @Config(qualifiers = WindowSizes.EXPANDED)
    fun termsOnOneLineAboveTheProductUpdatesSwitch() {
        show {
            Column(modifier = Modifier.width(640.dp)) {
                Row {
                    TermsCheckbox(checked = false, onCheckChanged = {})
                }
                Spacer(modifier = Modifier.height(12.dp))
                ProductUpdatesOptOutRow(checked = true, onCheckedChanged = {})
            }
        }
        compose.checkTouchTargets()
    }

    /** The location picker dialog: its Close button right above the search field. */
    @Test
    fun searchFieldUnderTheLocationPickersClose() {
        show {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {}) {
                        Text(stringResource(id = R.string.close))
                    }
                }
                URSearchInput(
                    value = TextFieldValue("Berlin"),
                    onValueChange = {},
                    keyboardController = null,
                    onClear = {},
                )
            }
        }
        compose.checkTouchTargets()
    }

    /** A sheet's drag handle, 32 by 48 dp and clickable, right above the search field. */
    @Test
    fun searchFieldUnderASheetsDragHandle() {
        show {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 32.dp, height = 48.dp).clickable {})
                URSearchInput(
                    value = TextFieldValue("Berlin"),
                    onValueChange = {},
                    keyboardController = null,
                    onClear = {},
                )
            }
        }
        compose.checkTouchTargets()
    }

    /**
     * A block action decided by a safety rule, with its info icon and "Route
     * locally", above the next action in the list, which is clickable too.
     */
    @Test
    fun safetyRuleInTheBlockActionsList() {
        val action = BlockActionUi(
            id = "a",
            timeMillis = 0L,
            hosts = listOf("www.example.com"),
            ips = listOf(),
            matchedHosts = listOf(),
            matchedIps = listOf(),
            hostBaseNames = listOf("example.com"),
            block = true,
            local = false,
            hasBlockOverride = false,
            hasRouteOverride = false,
            overrideId = null,
            byteCount = 2048L,
            safetyRule = true,
            routeLocalOverridable = true,
        )
        show {
            Column {
                BlockActionRow(action = action, onClick = {}, onRouteLocal = {})
                BlockActionRow(action = action.copy(id = "b"), onClick = {}, onRouteLocal = {})
            }
        }
        compose.checkTouchTargets()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            URNetworkTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }
}
