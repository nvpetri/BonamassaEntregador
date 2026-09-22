package br.com.bonamassa.driver.connected

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import br.com.bonamassa.driver.client.SavedCodec
import br.com.bonamassa.driver.client.SavedState
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A single encrypted transaction contains the session and original pending write. */
class SecureStore(context: Context) {
    companion object {
        // AtomicFile requires one lock across readers and writers, including other instances.
        private val fileLock = Any()
    }
    private val file = AtomicFile(File(context.noBackupFilesDir, "driver-api-v1.bin"))
    private val alias = "bonamassa.driver.api.v1"
    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    fun read(): SavedState? = synchronized(fileLock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
        val bytes = file.readFully()
        require(bytes.size > 29 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        SavedCodec.decode(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
    }
    fun write(state: SavedState) = synchronized(fileLock) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(cipher.iv.size == 12)
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(SavedCodec.encode(state).toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
