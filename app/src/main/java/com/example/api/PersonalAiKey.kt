package com.example.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only encrypted credential, excluded from Android backup and shift exports. */
object PersonalAiKey {
    private const val ALIAS = "personal_gemini_v1"
    private fun file(context: Context): AtomicFile {
        val uid = AuthManager.getFirebaseAuthSafely()?.currentUser?.uid
        val name = if (uid == null) "personal-gemini.enc" else {
            val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            "personal-gemini-" + digest.joinToString("") { "%02x".format(it) } + ".enc"
        }
        return AtomicFile(File(context.noBackupFilesDir, name))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(context: Context): String {
        val target = file(context)
        if (!target.baseFile.exists()) return ""
        return try {
            val bytes = target.readFully()
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("לא ניתן לקרוא את המפתח האישי. יש להזין אותו מחדש בהגדרות") }
    }
    fun save(context: Context, value: String) {
        val cleaned = value.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 512 && cleaned.none { it.isWhitespace() }) { "יש להזין מפתח אישי תקין" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(cleaned.toByteArray(Charsets.UTF_8))
        val target = file(context)
        val stream = target.startWrite()
        try { stream.write(encrypted); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
    }
    fun remove(context: Context) { file(context).delete() }
}
