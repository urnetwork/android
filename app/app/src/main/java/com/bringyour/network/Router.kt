package com.bringyour.network

import android.os.ParcelFileDescriptor
import android.util.Log
import com.bringyour.client.BringYourDevice
import com.bringyour.client.ReceivePacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class Router(val byDevice: BringYourDevice, val reconnect: () -> Unit = {}) {
    companion object {
//        val WRITE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5L)
    }

    val clientIpv4: String? = "169.254.2.1"
    val clientIpv4PrefixLength = 32
    // see:
    // https://security.googleblog.com/2022/07/dns-over-http3-in-android.html#fn2
    // only Google DNS and CloudFlare DNS will auto-enable DoT/DoH on Android
    // *important* removing the Google public server will disable DoT/DoH
    val dnsIpv4s = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    val clientIpv6: String? = null
    val clientIpv6PrefixLength = 64
    val dnsIpv6s = emptyList<String>()

    val pfds = LinkedTransferQueue<ParcelFileDescriptor>()

    @Volatile
    var active = true


    init {
        thread {
            val writeLock = ReentrantLock()
            val writeCondition = writeLock.newCondition()
            var fos : FileOutputStream? = null

            try {
                val receivePacket = ReceivePacket {
//                    val endTime = System.currentTimeMillis() + WRITE_TIMEOUT_MILLIS
                    writeLock.withLock {

                        if (active && fos != null) {
//                            while (active && fos == null && System.currentTimeMillis() < endTime) {
//                                Log.i("Router", String.format("write(%d) waiting for pfd", it.size))
//                                val timeout = endTime - System.currentTimeMillis()
//                                if (0 < timeout) {
//                                    writeCondition.await(timeout, TimeUnit.MILLISECONDS)
//                                }
//                            }
//                            if (!active) {
//                                break
//                            }
//                            if (endTime <= System.currentTimeMillis()) {
//                                Log.i("Router", "Receive packet dropped.")
//                                break
//                            }

                            try {

//                                Log.i("Router", "write(${it.size})")
                                fos!!.write(it)

//                                break
                            } catch (_: IOException) {
                                try {
                                    fos!!.close()
                                } catch (_: IOException) {
                                }
                                fos = null

                                // retry
                            }
                        }
                    }
                }

                Log.i("Router", "init")
                val localSendPacketSub = byDevice.addReceivePacket(receivePacket)
                try {

                    var nextPfd: ParcelFileDescriptor? = null
                    try {
                        while (active) {
                            if (nextPfd == null) {
                                nextPfd = pfds.poll(1, TimeUnit.SECONDS) ?: continue
                            }
                            // drain the queue
                            while (true) {
                                val p = pfds.poll() ?: break
                                try {
                                    nextPfd?.close()
                                } catch (e: IOException) {
                                    // ignore
                                }
                                nextPfd = p
                            }

                            val pfd: ParcelFileDescriptor = nextPfd!!
                            nextPfd = null

                            try {
                                val fis = FileInputStream(pfd.fileDescriptor)

                                writeLock.lock()
                                try {
                                    fos = FileOutputStream(pfd.fileDescriptor)
                                    writeCondition.signalAll()
                                } finally {
                                    writeLock.unlock()
                                }

                                val readThread = thread {
                                    val buffer = ByteArray(2048)
                                    while (active) {
                                        try {
                                            val n = fis.read(buffer)

                                            if (0 < n) {
                                                // note sendPacket makes a copy of the buffer
                                                val success = byDevice.sendPacket(buffer, n)
                                                if (!success) {
                                                    Log.i("Router", "Send packet dropped.")
                                                }
                                            }
                                        } catch (_: IOException) {
                                            try {
                                                fis.close()
                                            } catch (_: IOException) {
                                            }
                                            break
                                        }
                                    }
                                }

                                while (active && nextPfd == null && readThread.isAlive) {
                                    nextPfd = pfds.poll(1, TimeUnit.SECONDS)
                                }

                                if (active && nextPfd == null) {
                                    // the reader terminated without a replacement pfd
                                    // request a reconnect
                                    reconnect()
                                }

                            } finally {
                                try {
                                    pfd.close()
                                } catch (_: IOException) {
                                }
                            }
                        }
                    } finally {
                        if (nextPfd != null) {
                            try {
                                nextPfd.close()
                            } catch (_: IOException) {
                            }
                        }
                    }

                    writeLock.lock()
                    try {
                        writeCondition.signalAll()
                    } finally {
                        writeLock.unlock()
                    }

                } finally {
                    localSendPacketSub.close()
                }
            } finally {
                Log.i("Router", "final")
            }
        }
    }

    fun activateLocalInterface(pfd : ParcelFileDescriptor) {
        while (true) {
            val drainPfd = pfds.poll() ?: break
            drainPfd.close()
        }
        pfds.add(pfd)
    }

    fun close() {
        active = false
        while (true) {
            val drainPfd = pfds.poll() ?: break
            drainPfd.close()
        }
    }
}