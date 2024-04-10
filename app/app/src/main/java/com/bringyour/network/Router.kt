package com.bringyour.network

import android.os.ParcelFileDescriptor
import android.util.Log
import com.bringyour.client.BringYourDevice
import com.bringyour.client.ReceivePacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class Router(byDevice : BringYourDevice) {
    // takes a parcel file descriptor

    // RemoteUserNat

//    val byClient = byClient
//    val byDevice = byDevice


    val clientIpv4: String? = "10.10.11.11"
    val clientIpv4PrefixLength = 32
    val dnsIpv4s = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    val clientIpv6: String? = null
    val clientIpv6PrefixLength = 64
    val dnsIpv6s = emptyList<String>()


    val pfds = LinkedTransferQueue<ParcelFileDescriptor>()

    @Volatile
    var active = true
//    var pfd: ParcelFileDescriptor? = null


    init {
        thread {
            val writeLock = ReentrantLock()
            val writeCondition = writeLock.newCondition()

//            var pfd : ParcelFileDescriptor? = null
//            var fis : FileInputStream? = null
            var fos : FileOutputStream? = null


            try {
                // FIXME ReceivePacket
                val receivePacket = ReceivePacket {
//                    Log.i("Router", String.format("write(%d)", it.size))
                    writeLock.lock()
                    try {
                        while (active) {
                            while (active && fos == null) {
                                Log.i("Router", String.format("write(%d) waiting for pfd", it.size))
                                writeCondition.await()
                            }
                            if (!active) {
                                break
                            }

                            try {
//                                    Log.d("Router", String.format("write(%d)", it.size))
                                fos!!.write(it)
                                break
                            } catch (_: IOException) {
                                try {
                                    fos!!.close()
                                } catch (_: IOException) {
                                }
                                fos = null

                                // retry
                            }
                        }
                    } finally {
                        writeLock.unlock()
                    }
                }

                Log.i("Router", "init")
                val localSendPacketSub = byDevice.addReceivePacket(receivePacket)
                try {

                    var nextPfd: ParcelFileDescriptor? = null
                    while (active) {
                        if (nextPfd == null) {
                            nextPfd = pfds.poll(1, TimeUnit.SECONDS)
                            if (nextPfd == null) {
                                continue
                            }
                        }
                        while (pfds.peek() != null) {
                            nextPfd = pfds.poll()
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

                            // (priority=Thread.MAX_PRIORITY)
                            thread {
                                // check for a new pfd only when there is an error on this one
                                val buffer = ByteArray(2048)
                                while (active) {
                                    try {
                                        val n = fis.read(buffer)
//                                Log.d("Router", String.format("read(%d)", n))
                                        // localReceive makes a copy
                                        if (0 < n) {
                                            byDevice.sendPacket(buffer, n)
                                        }
                                    } catch (_: IOException) {
                                        try {
                                            fis.close()
                                        } catch (_: IOException) {
                                        }
                                    }
                                }
                            }

                            while (active && nextPfd == null) {
                                nextPfd = pfds.poll(1, TimeUnit.SECONDS)
                            }

                        } finally {
                            try {
                                pfd.close()
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
        pfds.add(pfd)
    }

    fun close() {
        pfds.clear()
        active = false
    }
}
