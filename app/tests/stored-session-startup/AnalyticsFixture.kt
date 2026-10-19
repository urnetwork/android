@file:Suppress("UNUSED_PARAMETER")

package com.bringyour.network.analytics

/** Inert product-event installation; no event is queued or sent by the boundary tests. */
internal object ClientEvents {
    fun install(application: Any, queue: () -> Any?) {}
}
