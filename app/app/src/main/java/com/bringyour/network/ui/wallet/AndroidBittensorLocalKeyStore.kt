package com.bringyour.network.ui.wallet

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.bringyour.sdk.BittensorLocalKeyStore
import com.bringyour.sdk.Sdk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * The sdk's secret store for the payout key (sdk BittensorLocalKeyStore,
 * apple/BTEASY.md, Storage), on Android: one file per network id in the
 * app's no-backup directory, with the address in the clear and the phrase
 * sealed by an AES-256-GCM key that lives in the Android Keystore and never
 * leaves it, one Keystore key per network id. The Keystore key asks for no
 * user authentication: the sdk gates the reveal, not the store. Nothing here
 * is backed up or moved to another device: the no-backup directory is outside
 * Auto Backup and device transfer, and a Keystore key can't be exported.
 *
 * A logout leaves the file, the Keystore key and the notes alone (the sdk's
 * local state, which a logout clears, lives elsewhere), so the key comes back
 * when its network signs in again. Another network on the device asks for
 * another file and finds none. Only this app's user id can read the
 * directory or use the Keystore key.
 *
 * The non-secret notes the sdk keeps by network id (addresses and numbers,
 * never a phrase) are preferences of their own, which the backup rules leave
 * out (xml/backup_rules, xml/data_extraction_rules).
 *
 * This file holds no decisions: the sdk reads back what it writes before it
 * signs, and checks a deletion by reading again. It never logs: a failure
 * says only that the store can't answer. Every method can be called on any
 * thread; one lock orders them.
 */
class AndroidBittensorLocalKeyStore(context: Context) : BittensorLocalKeyStore {

    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY)
    private val notes: SharedPreferences =
        context.applicationContext.getSharedPreferences(NOTES, Context.MODE_PRIVATE)
    private val stateLock = Any()

    /** The item as its file holds it: the address, and the sealed phrase with its nonce. */
    private class Item(val address: String, val iv: ByteArray, val sealed: ByteArray)

    override fun addKey(networkId: String?, mnemonic: String?, address: String?): Boolean {
        if (networkId.isNullOrEmpty() || mnemonic.isNullOrEmpty() || address.isNullOrEmpty()) {
            return false
        }
        synchronized(stateLock) {
            return try {
                val target = itemFile(networkId)
                // an add never replaces an item
                if (target.exists()) {
                    return false
                }
                directory.mkdirs()
                // a new Keystore key for the network: one left by an add that
                // never finished seals nothing that is kept
                val key = newKey(keyAlias(networkId))
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                cipher.updateAAD(associatedData(networkId, address))
                val plain = mnemonic.toByteArray(Charsets.UTF_8)
                val sealed = try {
                    cipher.doFinal(plain)
                } finally {
                    plain.fill(0)
                }
                val partial = File(directory, fileName(networkId) + "." + UUID.randomUUID() + PARTIAL_SUFFIX)
                try {
                    FileOutputStream(partial).use { out ->
                        out.write(encode(Item(address, cipher.iv, sealed)))
                        out.fd.sync()
                    }
                    // a move with no replace option refuses an item already there
                    Files.move(partial.toPath(), target.toPath())
                    true
                } catch (e: FileAlreadyExistsException) {
                    false
                } finally {
                    partial.delete()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun keyPhrase(networkId: String?): String {
        if (networkId.isNullOrEmpty()) {
            return Sdk.BittensorLocalKeyStoreUnavailable
        }
        synchronized(stateLock) {
            return try {
                val target = itemFile(networkId)
                if (!target.exists()) {
                    return ""
                }
                val item = decode(target.readBytes()) ?: return Sdk.BittensorLocalKeyStoreUnavailable
                val key = existingKey(keyAlias(networkId)) ?: return Sdk.BittensorLocalKeyStoreUnavailable
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, item.iv))
                // a file renamed to another network, or an address changed in
                // place, does not open
                cipher.updateAAD(associatedData(networkId, item.address))
                val plain = cipher.doFinal(item.sealed)
                try {
                    String(plain, Charsets.UTF_8)
                } finally {
                    plain.fill(0)
                }
            } catch (e: Exception) {
                Sdk.BittensorLocalKeyStoreUnavailable
            }
        }
    }

    override fun keyAddress(networkId: String?): String {
        if (networkId.isNullOrEmpty()) {
            return Sdk.BittensorLocalKeyStoreUnavailable
        }
        synchronized(stateLock) {
            return try {
                val target = itemFile(networkId)
                if (!target.exists()) {
                    return ""
                }
                // the address only: the sealed phrase is not opened
                decode(target.readBytes())?.address ?: Sdk.BittensorLocalKeyStoreUnavailable
            } catch (e: Exception) {
                Sdk.BittensorLocalKeyStoreUnavailable
            }
        }
    }

    override fun deleteKey(networkId: String?): Boolean {
        if (networkId.isNullOrEmpty()) {
            return false
        }
        synchronized(stateLock) {
            return try {
                val target = itemFile(networkId)
                if (target.exists() && !target.delete()) {
                    return false
                }
                // without its Keystore key no copy of the sealed phrase opens
                runCatching {
                    val keyStore = keyStore()
                    if (keyStore.containsAlias(keyAlias(networkId))) {
                        keyStore.deleteEntry(keyAlias(networkId))
                    }
                }
                !target.exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun readNote(networkId: String?, name: String?): String =
        notes.getString(noteKey(networkId, name), null) ?: ""

    override fun writeNote(networkId: String?, name: String?, value: String?) {
        val editor = notes.edit()
        if (value.isNullOrEmpty()) {
            editor.remove(noteKey(networkId, name))
        } else {
            editor.putString(noteKey(networkId, name), value)
        }
        // on disk before the sdk reads it again: a saved phrase stays saved
        editor.commit()
    }

    // ---- outside the Earnings screen

    /**
     * The network has a key here whose phrase is not known to be saved: the
     * sign out and account deletion confirmations warn. The sdk decides, from
     * a flow made just to ask, with no Api.
     */
    fun needsBackup(networkId: String?): Boolean {
        if (networkId.isNullOrEmpty()) {
            return false
        }
        val flow = Sdk.newBittensorLocalKeyFlow(null, networkId, this) ?: return false
        return try {
            flow.needsBackup()
        } finally {
            flow.close()
        }
    }

    /** Removes the key and the notes after an account deletion. False if the key could not be shown gone. */
    fun forgetNetwork(networkId: String?): Boolean {
        if (networkId.isNullOrEmpty()) {
            return false
        }
        val flow = Sdk.newBittensorLocalKeyFlow(null, networkId, this) ?: return false
        return try {
            flow.forgetNetwork()
        } finally {
            flow.close()
        }
    }

    // ---- the file and the Keystore key

    // a hash of the network id names both, so no id can name a path
    private fun fileName(networkId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((NAME_CONTEXT + networkId).toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun itemFile(networkId: String): File = File(directory, fileName(networkId) + ITEM_SUFFIX)

    private fun keyAlias(networkId: String): String = KEY_ALIAS_PREFIX + fileName(networkId)

    private fun associatedData(networkId: String, address: String): ByteArray =
        (ASSOCIATED_DATA_CONTEXT + networkId + "\u0000" + address).toByteArray(Charsets.UTF_8)

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun newKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                // the sdk asks for device authentication before it shows a
                // stored phrase or deletes it; signing during setup asks none
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun existingKey(alias: String): SecretKey? =
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun encode(item: Item): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(FORMAT)
            out.writeUTF(item.address)
            out.writeInt(item.iv.size)
            out.write(item.iv)
            out.writeInt(item.sealed.size)
            out.write(item.sealed)
        }
        return bytes.toByteArray()
    }

    // null for anything this format did not write
    private fun decode(bytes: ByteArray): Item? {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readByte().toInt() != FORMAT) {
                    return null
                }
                val address = input.readUTF()
                val ivSize = input.readInt()
                if (ivSize !in 1..MAX_PART_BYTES) {
                    return null
                }
                val iv = ByteArray(ivSize)
                input.readFully(iv)
                val sealedSize = input.readInt()
                if (sealedSize !in 1..MAX_PART_BYTES) {
                    return null
                }
                val sealed = ByteArray(sealedSize)
                input.readFully(sealed)
                if (address.isEmpty() || input.available() != 0) {
                    return null
                }
                return Item(address, iv, sealed)
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun noteKey(networkId: String?, name: String?): String = (networkId ?: "") + "." + (name ?: "")

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val FORMAT = 1
        private const val MAX_PART_BYTES = 4096
        private const val DIRECTORY = "bittensor-local-key"
        private const val ITEM_SUFFIX = ".key"
        private const val PARTIAL_SUFFIX = ".partial"
        private const val NOTES = "bittensor_local_key_notes"
        private const val KEY_ALIAS_PREFIX = "network.ur.bittensor-local-key."
        private const val NAME_CONTEXT = "urnetwork bittensor local key\u0000"
        private const val ASSOCIATED_DATA_CONTEXT = "urnetwork bittensor local key v1\u0000"

        @Volatile
        private var instance: AndroidBittensorLocalKeyStore? = null

        /** The app's one store. */
        fun shared(context: Context): AndroidBittensorLocalKeyStore =
            instance ?: synchronized(this) {
                instance ?: AndroidBittensorLocalKeyStore(context.applicationContext).also { instance = it }
            }
    }
}
