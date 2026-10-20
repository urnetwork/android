package com.bringyour.network

/** One application's immutable api-alt choice, consumed before native space construction. */
internal class NetworkSpaceApiAltStartup {
    private var constructionStarted = false
    private var apiAltEnabled = true

    /** The dedicated hosted experiment runner binds this before Application.onCreate. */
    fun disableForHostedH1() {
        check(!constructionStarted) { "network space construction already started" }
        apiAltEnabled = false
    }

    /** No late choice can change an already constructed manager or restored space. */
    fun <T> construct(create: (Boolean) -> T): T {
        constructionStarted = true
        return create(apiAltEnabled)
    }
}
