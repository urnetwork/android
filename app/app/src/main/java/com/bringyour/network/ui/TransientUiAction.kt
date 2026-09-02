package com.bringyour.network.ui

internal const val POST_LOGIN_WELCOME_ENTER_TAG = "acceptance.welcome.enter"
internal const val POST_LOGIN_INTRO_CLOSE_TAG = "acceptance.intro.close"

internal enum class PostLoginUiAction {
    WelcomeEnter,
    IntroClose,
    Close,
    CloseOverlay,
}

internal fun nextPostLoginUiAction(
    welcomeEnterPresent: Boolean,
    introClosePresent: Boolean,
    closePresent: Boolean,
    closeOverlayPresent: Boolean,
): PostLoginUiAction? = when {
    welcomeEnterPresent -> PostLoginUiAction.WelcomeEnter
    introClosePresent -> PostLoginUiAction.IntroClose
    closePresent -> PostLoginUiAction.Close
    closeOverlayPresent -> PostLoginUiAction.CloseOverlay
    else -> null
}

/**
 * Performs an action on UI state that can disappear asynchronously. A node
 * may be removed after the presence query but before an input event resolves
 * it; that absence is reported to the caller instead of escaping as a test
 * assertion. An assertion while the surface is still present is a real driver
 * failure and must remain visible to the test.
 */
internal fun performTransientUiActionIfPresent(
    isPresent: () -> Boolean,
    action: () -> Unit,
): Boolean {
    if (!isPresent()) return false
    return try {
        action()
        true
    } catch (error: AssertionError) {
        if (isPresent()) throw error
        false
    }
}
