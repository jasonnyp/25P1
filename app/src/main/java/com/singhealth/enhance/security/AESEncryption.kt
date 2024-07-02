package com.singhealth.enhance.security

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AESEncryption {
    fun encrypt(stringToEncrypt: String): String {
        try {
            val secretKey = getSecretKey()
            val salt = getSalt()
            val initVectors = getInitVectors()

            val ivParameterSpec = IvParameterSpec(Base64.decode(initVectors, Base64.DEFAULT))

            val factory = SecretKeyFactory.getInstance("pbkdf2withhmacsha1")
            val spec = PBEKeySpec(
                secretKey.toCharArray(),
                Base64.decode(salt, Base64.DEFAULT),
                10000,
                256
            )
            val tmp = factory.generateSecret(spec)
            val secret = SecretKeySpec(tmp.encoded, "aes")

            val cipher = Cipher.getInstance("aes/cbc/pkcs7padding")
            cipher.init(Cipher.ENCRYPT_MODE, secret, ivParameterSpec)
            return Base64.encodeToString(
                cipher.doFinal(stringToEncrypt.toByteArray(charset("UTF-8"))),
                Base64.DEFAULT
            ).replace("/", "*")
        } catch (e: Exception) {
            Log.d("Encryption error", e.message.toString())
        }
        return ""
    }

    fun decrypt(stringToDecrypt: String): String {
        try {
            val secretKey = getSecretKey()
            val salt = getSalt()
            val initVectors = getInitVectors()

            val ivParameterSpec = IvParameterSpec(Base64.decode(initVectors, Base64.DEFAULT))

            val factory = SecretKeyFactory.getInstance("pbkdf2withhmacsha1")
            val spec = PBEKeySpec(
                secretKey.toCharArray(),
                Base64.decode(salt, Base64.DEFAULT),
                10000,
                256
            )
            val tmp = factory.generateSecret(spec)
            val secret = SecretKeySpec(tmp.encoded, "aes")

            val cipher = Cipher.getInstance("aes/cbc/pkcs7padding")
            cipher.init(Cipher.DECRYPT_MODE, secret, ivParameterSpec)
            return String(
                cipher.doFinal(
                    Base64.decode(
                        stringToDecrypt.replace("*", "/"),
                        Base64.DEFAULT
                    )
                )
            )
        } catch (e: Exception) {
            Log.d("Decryption error", e.message.toString())
        }
        return ""
    }

    // TODO: Replace the following hardcoded keys with a more secure/obscured version
    private fun getSecretKey(): String {
        return "tk5utui+dph8lilbxya5xvsmedcoul6vhhdiesmb6sq="
    }

    private fun getSalt(): String {
        return "qwlgnhnhmtjtqwz2bghpv3u="
    }

    private fun getInitVectors(): String {
        return "bvqznfnhrkq1njc4uufawa=="
    }
}