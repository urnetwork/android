package com.bringyour.network.ui.connect

import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ContractStatus
import com.bringyour.sdk.Sub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A connect or disconnect of a surface without a presented controller (the
 * Quick Settings tile, the shortcuts, the widget button, the notification,
 * the balance recovery, Always-on) on the connect command queue (owner
 * report 2026-10-16). Each touches the sdk only when the queue runs it, reads
 * the location with its command, and the surfaces decide on what the newest
 * command asks for. The queue runs only when the test runs it.
 */
class DeviceConnectCommandTest {

    private class HeldQueue {
        private val queued = ArrayDeque<Runnable>()

        fun execute(runnable: Runnable) {
            queued.addLast(runnable)
        }

        fun runAll() {
            while (queued.isNotEmpty()) {
                queued.removeFirst().run()
            }
        }
    }

    private class FakeSub : Sub {
        override fun close() {}
    }

    /**
     * The sdk device: what it was asked, in order. Its saved location is
     * none, and each read of it is logged (a gomobile ConnectLocation needs
     * the native library, which these JVM tests do not load).
     */
    private class FakeDevice : ConnectDevice {
        val log = mutableListOf<String>()
        override val identity: Any get() = this
        override var connectEnabled = false
        override val tunnelStarted = false
        override val contractStatus: ContractStatus? = null
        override val connectLocation: ConnectLocation?
            get() {
                log.add("read saved location")
                return null
            }

        override fun addTunnelChangeListener(listener: (tunnelStarted: Boolean) -> Unit): Sub = FakeSub()
        override fun addContractStatusChangeListener(listener: () -> Unit): Sub = FakeSub()

        override fun openConnectViewController(): ConnectController {
            log.add("open")
            return object : ConnectController {
                override val connectionStatus: String? = null
                override val grid: ConnectGrid? = null
                override val selectedLocation: ConnectLocation? = null
                override fun addGridListener(listener: () -> Unit): Sub = FakeSub()
                override fun addConnectionStatusListener(listener: () -> Unit): Sub = FakeSub()
                override fun addSelectedLocationListener(listener: () -> Unit): Sub = FakeSub()
                override fun start() {}
                override fun stop() {}
                override fun connect(location: ConnectLocation) {
                    log.add("connect")
                    connectEnabled = true
                }
                override fun connectBestAvailable() {
                    log.add("connectBestAvailable")
                    connectEnabled = true
                }
                override fun disconnect() {
                    log.add("disconnect")
                    connectEnabled = false
                }
            }
        }

        override fun closeViewController(controller: ConnectController) {
            log.add("close")
        }
    }

    @Test
    fun aCommandTouchesTheDeviceOnlyOnTheQueue() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = FakeDevice()

        assertTrue(commands.runOnDevice(device, connect = true))
        assertEquals(emptyList<String>(), device.log)

        // the saved location is read with the command, so it is the one the
        // commands before it left, not the one when it was made; none is the
        // best available provider
        queue.runAll()
        assertEquals(listOf("open", "read saved location", "connectBestAvailable", "close"), device.log)
    }

    @Test
    fun aRecoveryStartReadsItsOwnTarget() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = FakeDevice()
        commands.runOnDevice(device, connect = true, location = {
            device.log.add("read target")
            null
        })
        assertEquals(emptyList<String>(), device.log)

        queue.runAll()
        assertEquals(listOf("open", "read target", "connectBestAvailable", "close"), device.log)
    }

    @Test
    fun aDisconnectReadsNoLocation() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = FakeDevice()
        commands.runOnDevice(device, connect = false)
        queue.runAll()
        assertEquals(listOf("open", "disconnect", "close"), device.log)
    }

    @Test
    fun theRequestedConnectionIsTheNewestCommand() {
        // a tile tap after a queued disconnect is a connect, not a connection
        // already made, though the device still reports connectEnabled
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = FakeDevice()
        device.connectEnabled = true
        assertTrue(commands.connectRequested(device))

        commands.runOnDevice(device, connect = false)
        assertTrue(device.connectEnabled)
        assertFalse(commands.connectRequested(device))
        commands.runOnDevice(device, connect = true)
        assertTrue(commands.connectRequested(device))

        queue.runAll()
        assertEquals(
            listOf("open", "disconnect", "close", "open", "read saved location", "connectBestAvailable", "close"),
            device.log,
        )
        assertTrue(commands.connectRequested(device))
    }
}
