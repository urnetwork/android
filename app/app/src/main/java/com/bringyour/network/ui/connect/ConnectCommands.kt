package com.bringyour.network.ui.connect

/**
 * The serial queue every connect and disconnect runs on, off the main thread,
 * in the order they were made (owner report 2026-10-16: a tap shows its state
 * at once).
 *
 * The sdk's device runs in the app's process (DeviceLocal), and a connect or
 * a disconnect does all of its work on the calling thread: it commits the
 * connect and default locations to the local state, each a write, an fsync, a
 * rename and a directory fsync under the account's auth locks, it rebuilds the
 * multi client under the device's lock, and it runs the device's listeners
 * inside the call, whose local state mirrors (DeviceManager) write the same
 * records again. On the main thread a tap froze the screen for as long, an
 * ANR risk under storage pressure, and the controller's status reached the
 * screen only after it. The Apple app runs its commands on a serial queue
 * for the same reason (ConnectViewModel.runConnectCommand).
 *
 * A command runs against the device it was made on, never the device current
 * when it runs. Until the last command made on a device has run, [pending]
 * says what the newest of them asks for, so a surface shows the tap's state
 * from the tap and a report from before it cannot flip the view back; the
 * listeners hear each change on the main thread, and a surface reads the
 * sdk's status again once nothing is pending.
 *
 * [run] and [afterCommands] are called on the main thread; [pending] may be
 * read from any thread (the widgets render off the main thread). Commands
 * run on the queue's thread, one at a time.
 */
internal class ConnectCommands(
    /** Runs work off the main thread, one at a time, in order: a single thread executor in the app. */
    private val execute: (Runnable) -> Unit,
    /** Runs work on the main thread: a command's finish. */
    private val postMain: (Runnable) -> Unit,
    /** A command threw: logged by the app. */
    private val onFailure: (Throwable) -> Unit = {},
) {
    private class Pending(val device: Any, val connect: Boolean)

    private val stateLock = Any()
    // guarded by stateLock: the commands made and not yet finished, oldest first
    private val pendings = mutableListOf<Pending>()
    // main thread
    private val listeners = mutableListOf<() -> Unit>()

    /**
     * What the newest command not yet finished on `device` asks for: true for
     * a connect, false for a disconnect, null when none is pending.
     */
    fun pending(device: Any?): Boolean? {
        device ?: return null
        return synchronized(stateLock) {
            pendings.lastOrNull { it.device === device }?.connect
        }
    }

    /** Calls `listener` on the main thread each time a command is made or finishes. */
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * Queues `command`, a connect (`connect`) or a disconnect on `device` (the
     * sdk device's identity, see ConnectDevice.identity), and shows it as
     * pending until it has run. The command runs on the queue's thread, after
     * the commands made before it. True when it is queued.
     */
    fun run(device: Any, connect: Boolean, command: () -> Unit): Boolean {
        val pending = Pending(device, connect)
        synchronized(stateLock) {
            pendings.add(pending)
        }
        changed()
        execute(Runnable {
            try {
                command()
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                postMain(Runnable {
                    synchronized(stateLock) {
                        pendings.remove(pending)
                    }
                    changed()
                })
            }
        })
        return true
    }

    /**
     * Runs `work` on the queue's thread after the commands made so far: a
     * presented controller's close, so a command made against it still finds
     * it open (a closed controller refuses a disconnect).
     */
    fun afterCommands(work: () -> Unit) {
        execute(Runnable {
            try {
                work()
            } catch (e: Exception) {
                onFailure(e)
            }
        })
    }

    private fun changed() {
        listeners.toList().forEach { it() }
    }

    companion object {
        /** The app's queue: one thread, named for the logs; finishes post to the main looper. */
        fun create(onFailure: (Throwable) -> Unit): ConnectCommands {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "connect-commands")
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            return ConnectCommands(
                execute = { executor.execute(it) },
                postMain = { mainHandler.post(it) },
                onFailure = onFailure,
            )
        }
    }
}

/**
 * Queues a connect (`connect`) or a disconnect of a surface without a
 * presented controller (the quick connect surfaces, the notification, the
 * balance recovery, Always-on) on `device`, through a controller opened for
 * it on the queue and closed after. A connect goes to what `location` gives,
 * read with the command on the queue, so it sees what the commands before it
 * left (the saved connection's location by default); null is the best
 * available provider. True when it is queued.
 */
internal fun ConnectCommands.runOnDevice(
    device: ConnectDevice,
    connect: Boolean,
    location: (ConnectDevice) -> com.bringyour.sdk.ConnectLocation? = { it.connectLocation },
): Boolean = run(device.identity, connect) {
    val vc = device.openConnectViewController()
    try {
        if (connect) {
            location(device)?.let(vc::connect) ?: vc.connectBestAvailable()
        } else {
            vc.disconnect()
        }
    } finally {
        device.closeViewController(vc)
    }
}

/**
 * Whether the user asked for a connection on `device`: the newest command
 * not yet run, else the device's `connectEnabled`. A surface decides on it,
 * so a connect tapped after a queued disconnect is not taken for one already
 * made.
 */
internal fun ConnectCommands.connectRequested(device: ConnectDevice): Boolean =
    pending(device.identity) ?: device.connectEnabled
