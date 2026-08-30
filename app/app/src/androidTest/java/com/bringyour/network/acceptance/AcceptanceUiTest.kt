package com.bringyour.network.acceptance

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
}
