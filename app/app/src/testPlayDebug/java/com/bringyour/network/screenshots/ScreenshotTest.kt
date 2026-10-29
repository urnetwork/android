package com.bringyour.network.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A screenshot test of one screen at the four window sizes (WindowSizes),
 * drawn on the JVM by Robolectric's native graphics and checked by Roborazzi
 * against src/testPlayDebug/screenshots. `./gradlew :app:testPlayDebugUnitTest`
 * verifies them; `-Pscreenshots=record` rewrites them (README in that folder).
 *
 * The application is the framework's, not MainApplication, which would start
 * the sdk. The sdk's own classes (the gomobile packages) are instrumented,
 * which makes them inert on the JVM: loading the native library does nothing
 * and every native call returns null, zero or false, so the view models built
 * on them see an sdk with no data instead of failing to link. ShadowSdk
 * answers the calls whose null a screen would not survive.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [36],
    application = Application::class,
    instrumentedPackages = ["go.", "com.bringyour.sdk."],
    shadows = [ShadowSdk::class],
)
abstract class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    internal lateinit var viewModels: ScreenshotViewModels

    @Before
    fun createViewModels() {
        viewModels = ScreenshotViewModels(ApplicationProvider.getApplicationContext())
    }

    @After
    fun clearViewModels() {
        viewModels.viewModelStore.clear()
    }
}
