package com.bringyour.network.acceptance

/** Serial mailbox owner: a typed command timeout leaves only finish admissible.
 * Fatal errors, interruption, failed publication and a failed finish still unwind.
 * The enclosing owner keeps its original lifetime and rethrows failure after join.
 */
internal class PhysicalCommandOwner {
    var failure: PhysicalWaitTimeout? = null
        private set

    fun run(
        finish: Boolean,
        command: () -> Boolean,
        failed: (PhysicalWaitTimeout) -> Unit,
        canceled: () -> Boolean = { Thread.currentThread().isInterrupted },
    ): Boolean {
        if (canceled()) throw InterruptedException("physical command owner canceled")
        failure?.let { error ->
            if (!finish) {
                failed(error)
                return false
            }
        }
        try {
            return command()
        } catch (error: PhysicalWaitTimeout) {
            if (finish || canceled()) throw error
            failure = error
            failed(error)
            return false
        }
    }
}
