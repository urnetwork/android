package com.bringyour.network.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bringyour.network.ui.components.PaneSplit
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The connect screen keeps its state across what a fold does to it
 * (apple/DUO.md items 2 and 10): a recreated activity restores the drawer's
 * position, the location list's scroll and the location dialog, and a switch
 * between the phone drawer and the panes moves the connect graphic with its
 * state instead of drawing it again.
 */
@RunWith(AndroidJUnit4::class)
class ConnectStateRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theOpenDrawerStaysOpenAcrossRecreation() {
        val restoration = StateRestorationTester(compose)
        lateinit var drawer: ConnectDrawerState
        lateinit var scope: CoroutineScope

        restoration.setContent {
            drawer = rememberConnectDrawerState()
            scope = rememberCoroutineScope()
            URNetworkTheme {
                ConnectActionsSheetScaffold(
                    drawerState = drawer,
                    sheetContent = { _, _, _ ->
                        Box(modifier = Modifier.fillMaxWidth().height(600.dp))
                    },
                    mainContent = {},
                )
            }
        }

        compose.runOnIdle { scope.launch { drawer.expand() } }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(ConnectDrawerValue.Expanded, drawer.currentValue) }

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle { assertEquals(ConnectDrawerValue.Expanded, drawer.targetValue) }
    }

    @Test
    fun theLocationDialogAndListPositionSurviveRecreation() {
        val restoration = StateRestorationTester(compose)
        lateinit var position: LocationListPosition
        var dialogOpen: Boolean? = null
        var setDialogOpen: (Boolean) -> Unit = {}

        restoration.setContent {
            position = rememberLocationListPosition()
            var open by rememberSaveable { mutableStateOf(false) }
            dialogOpen = open
            setDialogOpen = { open = it }
        }

        compose.runOnIdle {
            position.index = 12
            position.offset = 34
            setDialogOpen(true)
        }

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            assertEquals(12, position.index)
            assertEquals(34, position.offset)
            assertEquals(true, dialogOpen)
        }
    }

    @Test
    fun theConnectGraphicKeepsItsStateFromTheDrawerToThePanes() {
        var panes by mutableStateOf(false)

        compose.setContent {
            val movableGraphic = remember {
                movableContentOf { content: @Composable () -> Unit -> content() }
            }
            // stands in for the connect button: state that a redraw would reset
            val graphic: @Composable () -> Unit = {
                movableGraphic {
                    var taps by remember { mutableIntStateOf(0) }
                    Text(
                        "$taps",
                        modifier = Modifier
                            .size(256.dp)
                            .testTag(GRAPHIC_TAG)
                            .clickable { taps++ }
                    )
                }
            }
            URNetworkTheme {
                if (panes) {
                    ConnectPanes(
                        sideBySide = true,
                        splitFor = { width, _ -> PaneSplit(width / density * 0.4f, width / density * 0.4f) },
                        statsPane = {},
                        connectPane = {
                            ConnectGraphicLayout(
                                minSize = 128.dp,
                                maxSize = 280.dp,
                                topSpacing = 0.dp,
                                gap = 32.dp,
                                graphic = graphic,
                                below = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ConnectActionsSheetScaffold(
                        drawerState = rememberConnectDrawerState(),
                        sheetContent = { _, _, _ -> },
                        mainContent = {
                            ConnectMainContent(graphic = graphic, status = {})
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag(GRAPHIC_TAG).performClick()
        compose.onNodeWithTag(GRAPHIC_TAG).performClick()
        compose.onNodeWithTag(GRAPHIC_TAG).assertTextEquals("2")

        compose.runOnIdle { panes = true }

        compose.onNodeWithTag(GRAPHIC_TAG).assertTextEquals("2")
    }

    private companion object {
        const val GRAPHIC_TAG = "connect.graphic"
    }
}
