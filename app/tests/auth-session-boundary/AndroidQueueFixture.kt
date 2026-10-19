@file:Suppress("UNUSED_PARAMETER")

package android.os

/** Explicit queue drains force the native handoff ordering; no scheduler or sleeps. */
class Handler(looper: Looper) {
    companion object { val queue = ArrayDeque<() -> Unit>() }
    fun post(action: Runnable): Boolean { queue.addLast { action.run() }; return true }
    fun postDelayed(action: Runnable, millis: Long): Boolean = true
}

class Looper {
    companion object {
        private val main = Looper()
        var isMain = true
        fun getMainLooper(): Looper = main
        fun myLooper(): Looper? = if (isMain) main else null
    }
}
