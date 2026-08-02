package com.bringyour.network

/**
 * Publishes replaceable state without allowing an older, delayed dispatch to
 * overwrite a newer one. Listener callbacks are serialized per listener, but
 * never run while the collection lock is held.
 */
internal class SequencedValueListeners<T>(initialValue: T) {
    private val lock = Any()
    private val listeners = mutableListOf<Entry<T>>()
    private var generation = 0L
    private var value = initialValue

    fun add(listener: (T) -> Unit): () -> Unit {
        val entry = Entry(listener)
        val initial = synchronized(lock) {
            listeners.add(entry)
            VersionedValue(generation, value)
        }
        entry.deliver(initial)
        return {
            entry.close()
            synchronized(lock) {
                listeners.remove(entry)
            }
        }
    }

    /**
     * Records the new value now and returns work that may be dispatched later.
     * If a later update dispatches first, each listener drops this stale work.
     */
    fun prepareUpdate(nextValue: T): () -> Unit {
        val update = synchronized(lock) {
            generation += 1
            value = nextValue
            PendingUpdate(
                VersionedValue(generation, nextValue),
                listeners.toList(),
            )
        }
        return {
            update.listeners.forEach { it.deliver(update.value) }
        }
    }

    private data class VersionedValue<T>(
        val generation: Long,
        val value: T,
    )

    private data class PendingUpdate<T>(
        val value: VersionedValue<T>,
        val listeners: List<Entry<T>>,
    )

    private class Entry<T>(
        private val listener: (T) -> Unit,
    ) {
        private var active = true
        private var deliveredGeneration = -1L

        fun deliver(update: VersionedValue<T>) {
            synchronized(this) {
                if (!active || update.generation <= deliveredGeneration) {
                    return
                }
                deliveredGeneration = update.generation
                listener(update.value)
            }
        }

        fun close() {
            synchronized(this) {
                active = false
            }
        }
    }
}
