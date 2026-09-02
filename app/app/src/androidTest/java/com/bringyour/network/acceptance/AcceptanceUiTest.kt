package com.bringyour.network.acceptance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AcceptanceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun actionWaitsForEnablingRecomposition() {
        val enabled = mutableStateOf(false)
        var clickCount = 0
        compose.setContent {
            Button(
                onClick = { clickCount += 1 },
                enabled = enabled.value,
                modifier = Modifier.testTag("submit"),
            ) {
                Text("Submit")
            }
        }

        // Compose exposes the tagged node before it becomes enabled. The old
        // acceptance helper clicked that node immediately; the action returns
        // normally but the disabled control intentionally drops the click.
        compose.onNodeWithTag("submit", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(0, clickCount) }

        val disabledObserved = CountDownLatch(1)
        val enabledPublished = CountDownLatch(1)
        val publisher = thread(name = "acceptance-enable-publisher") {
            if (disabledObserved.await(1, TimeUnit.SECONDS)) {
                enabled.value = true
                enabledPublished.countDown()
            }
        }
        try {
            compose.waitForEnabledTag(
                tag = "submit",
                timeoutMillis = 1_000,
                onDisabledObserved = { disabledObserved.countDown() },
            )
        } finally {
            disabledObserved.countDown()
            publisher.join(1_000)
        }
        assertFalse("state publisher did not terminate", publisher.isAlive)
        assertTrue("wait returned before the enabling state was published", enabledPublished.await(0, TimeUnit.SECONDS))
        compose.onNodeWithTag("submit", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun semanticSubmitDoesNotDependOnStaleScreenCoordinates() {
        val yOffset = mutableStateOf(0.dp)
        var clickCount = 0
        compose.setContent {
            Button(
                onClick = { clickCount += 1 },
                modifier = Modifier
                    .offset(y = yOffset.value)
                    .testTag("moving-submit"),
            ) {
                Text("Submit")
            }
        }

        val staleCenter = compose
            .onNodeWithTag("moving-submit", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
        compose.runOnIdle { yOffset.value = 200.dp }
        compose.waitForIdle()

        // A coordinate tap can outlive the layout snapshot it was based on.
        // It returns normally even though the moving submit action never ran.
        compose.onRoot(useUnmergedTree = true).performTouchInput { click(staleCenter) }
        compose.runOnIdle { assertEquals(0, clickCount) }

        compose.performEnabledSemanticsClick("moving-submit", 1_000)
        compose.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun semanticSubmitRequiresAcknowledgementWithoutRetrying() {
        var invocationCount = 0
        compose.setContent {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        onClick {
                            invocationCount += 1
                            false
                        }
                    }
                    .testTag("refusing-submit"),
            )
        }

        val failure = runCatching {
            compose.performEnabledSemanticsClick("refusing-submit", 1_000)
        }.exceptionOrNull()
        assertTrue("unacknowledged action was accepted", failure is AssertionError)
        compose.runOnIdle { assertEquals(1, invocationCount) }
    }
}
