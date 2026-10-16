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
 * when it runs. From the moment a sign out starts for a device
 * ([signOutStarted]: the app's own, the server's or the account deletion's),
 * no connect is admitted on it, from a tap, a waiting start, the balance
 * recovery or Always-on, and its connects still queued never run, while its
 * disconnects still do: the network space's local state outlives the sign
 * out, and a late connect's writes would reconnect the next sign-in. The
 * fence ends with that device, so the next sign-in's device admits its
 * connects at once. Until the last command made on a device has run, [pending]
 * says what the newest of them asks for, so a surface shows the tap's state
 * from the tap and a report from before it cannot flip the view back; the
 * listeners hear each change on the main thread, and a surface reads the
 * sdk's status again once nothing is pending.
 *
 * A command still running past [stallMillis] is logged ([onStalled]), and
 * until it returns no command shows as pending, so the surfaces go back to
 * the status the sdk reports instead of holding the tap's state for as long
 * as the sdk does. The command is never dropped or reordered: it keeps
 * running, and the commands after it run after it.
 *
 * [run] and [afterCommands] are called on the main thread; [pending] may be
 * read and [signOutStarted] called from any thread (the widgets render off
 * the main thread, the server's sign out reports on the sdk's). Commands run
 * on the queue's thread, one at a time.
 */
internal class ConnectCommands(
    /** Runs work off the main thread, one at a time, in order: a single thread executor in the app. */
    private val execute: (Runnable) -> Unit,
    /** Runs work on the main thread: a command's finish. */
    private val postMain: (Runnable) -> Unit,
    /** A command threw: logged by the app. */
    private val onFailure: (Throwable) -> Unit = {},
    /** Runs work on the main thread after a delay (milliseconds): the stall watchdog. */
    private val postMainDelayed: (Runnable, Long) -> Unit = { _, _ -> },
    /** A command has run for [stallMillis] and still runs: logged by the app. */
    private val onStalled: (connect: Boolean, millis: Long) -> Unit = { _, _ -> },
    private val stallMillis: Long = CONNECT_COMMAND_STALL_MILLIS,
) {
    private class Pending(val device: Any, val connect: Boolean)

    private val stateLock = Any()
    // guarded by stateLock: the commands made and not yet finished, oldest first
    private val pendings = mutableListOf<Pending>()
    // guarded by stateLock: the devices a sign out started for, by identity
    // (a gomobile proxy's equals is not identity), weak so they go with
    // their devices
    private val signedOutDevices = mutableListOf<java.lang.ref.WeakReference<Any>>()
    // guarded by stateLock: the running command is past its bound, so none
    // shows as pending
    private var stalled = false
    // main thread
    private val listeners = mutableListOf<() -> Unit>()
    // main thread: the command the queue runs now, as its start reached main
    private var running: Pending? = null

    /**
     * What the newest command not yet finished on `device` asks for: true for
     * a connect, false for a disconnect, null when none is pending.
     */
    fun pending(device: Any?): Boolean? {
        device ?: return null
        return synchronized(stateLock) {
            if (stalled) null else pendings.lastOrNull { it.device === device }?.connect
        }
    }

    /** Calls `listener` on the main thread each time a command is made or finishes. */
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * A sign out started for `device` (its identity): no connect is admitted
     * on it from here, and its connects already queued are skipped.
     */
    fun signOutStarted(device: Any) {
        synchronized(stateLock) {
            signedOutDevices.removeAll { it.get() == null }
            if (signedOutDevices.none { it.get() === device }) {
                signedOutDevices.add(java.lang.ref.WeakReference(device))
            }
        }
    }

    private fun signedOut(device: Any): Boolean = synchronized(stateLock) {
        signedOutDevices.any { it.get() === device }
    }

    /**
     * Queues `command`, a connect (`connect`) or a disconnect on `device` (the
     * sdk device's identity, see ConnectDevice.identity), and shows it as
     * pending until it has run. The command runs on the queue's thread, after
     * the commands made before it, unless it is a connect of a device a sign
     * out started for by then. True when it is queued; a connect of a device
     * signed out is refused.
     */
    fun run(device: Any, connect: Boolean, command: () -> Unit): Boolean {
        if (connect && signedOut(device)) {
            return false
        }
        val pending = Pending(device, connect)
        synchronized(stateLock) {
            pendings.add(pending)
        }
        changed()
        execute(Runnable {
            postMain(Runnable { started(pending) })
            try {
                // a disconnect always runs
                if (!connect || !signedOut(device)) {
                    command()
                }
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                postMain(Runnable { finished(pending) })
            }
        })
        return true
    }

    // main thread: `pending` started to run; it is watched until it returns
    private fun started(pending: Pending) {
        running = pending
        postMainDelayed(Runnable {
            if (running === pending) {
                synchronized(stateLock) {
                    stalled = true
                }
                onStalled(pending.connect, stallMillis)
                changed()
            }
        }, stallMillis)
    }

    // main thread: `pending` returned
    private fun finished(pending: Pending) {
        if (running === pending) {
            running = null
        }
        synchronized(stateLock) {
            pendings.remove(pending)
            if (running == null) {
                stalled = false
            }
        }
        changed()
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
        fun create(
            onFailure: (Throwable) -> Unit,
            onStalled: (connect: Boolean, millis: Long) -> Unit,
        ): ConnectCommands {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "connect-commands")
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            return ConnectCommands(
                execute = { executor.execute(it) },
                postMain = { mainHandler.post(it) },
                onFailure = onFailure,
                postMainDelayed = { runnable, millis -> mainHandler.postDelayed(runnable, millis) },
                onStalled = onStalled,
            )
        }
    }
}

/**
 * How long a connect command may run before it is logged and the surfaces
 * stop showing it as pending: well past a local state commit and a multi
 * client rebuild, which take milliseconds to a second under storage pressure.
 */
internal const val CONNECT_COMMAND_STALL_MILLIS = 10_000L

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
