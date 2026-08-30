package com.bringyour.network.ui.login

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the instant-account server-error acceptance boundary. */
@RunWith(AndroidJUnit4::class)
class CreateNetworkInstantContentTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * A refusal must surface immediately. Without the tagged message, the
     * end-to-end test waits for a seedphrase until timeout and hides the HTTP
     * 429 (or any future root-cause response) already rendered on screen.
     */
    @Test
    fun serverErrorIsExposedToAcceptanceTest() {
        val refusal = "429 deterministic seedphrase signup refusal"
        compose.setContent { CreateNetworkInstantError(refusal) }

        compose.onNodeWithTag(ACCEPTANCE_INSTANT_ERROR_TAG, useUnmergedTree = true)
            .assertTextEquals(refusal)
    }
}
