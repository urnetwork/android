package com.bringyour.network

import com.bringyour.client.AsyncLocalState
import com.bringyour.client.Client
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AsyncLocalStateManager @Inject constructor() {

    private var asyncLocalState: AsyncLocalState? = null

    val init: (java.io.File) -> AsyncLocalState? = { filesDir ->
        asyncLocalState = Client.newAsyncLocalState(filesDir.absolutePath)
        asyncLocalState
    }

    val getAsyncLocalState: () -> AsyncLocalState? = {
        asyncLocalState
    }

}