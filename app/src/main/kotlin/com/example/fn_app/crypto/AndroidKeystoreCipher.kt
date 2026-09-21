package com.example.fn_app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.fnassistantdog.core.auth.CredentialCipher
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 复刻原 App 已验证的凭据加密方案（dex 取证：AndroidKeyStore + AES/GCM/NoPadding + KeyGenParameterSpec）。
 * 密钥不可导出、不落盘；密文格式 = 12 字节 IV 前缀 + GCM 密文（含 16 字节认证标签）。
 */
class AndroidKeystoreCipher : CredentialCipher {
    private val random = SecureRandom()

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val body = cipher.doFinal(plain)
        return iv + body
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw IllegalArgumentException("凭据密文长度异常：${blob.size}")
        val iv = blob.copyOfRange(0, IV_BYTES)
        val body = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(body)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fn_dog_credential_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
