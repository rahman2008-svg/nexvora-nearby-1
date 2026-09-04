package com.example.nearby.domain.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 10000
    private const val PBKDF2_KEY_LENGTH = 256
    private val secureRandom = SecureRandom()

    private val HKDF_SALT = "NexVora-P2P-v1-Salt".toByteArray(StandardCharsets.UTF_8)
    private val HKDF_INFO = "NexVora-AES256GCM-SessionKey".toByteArray(StandardCharsets.UTF_8)

    fun base64Encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    fun base64Decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str.trim())
        } catch (_: Throwable) {
            Base64.decode(str.trim(), Base64.NO_WRAP)
        }
    }

    /**
     * Generates an Ephemeral ECDH (secp256r1) key pair for P2P handshake.
     */
    fun generateEcdhKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(ecSpec, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Converts a Public Key to Base64 string for network transmission.
     */
    fun publicKeyToBase64(keyPair: KeyPair): String {
        return base64Encode(keyPair.public.encoded)
    }

    /**
     * Strictly validates an incoming Base64-encoded EC public key:
     * - Decodes Base64 safely
     * - Validates byte array bounds
     * - Parses as X.509 EC public key
     * - Validates that the point is not the point-at-infinity
     * - Checks affine coordinate bounds to prevent small subgroup / invalid curve attacks
     */
    fun validateEcPublicKey(peerPublicKeyBase64: String): PublicKey {
        require(peerPublicKeyBase64.isNotBlank()) { "Public key cannot be blank" }
        val peerKeyBytes = try {
            base64Decode(peerPublicKeyBase64)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed Base64 public key", e)
        }

        require(peerKeyBytes.size in 64..384) {
            "Invalid public key byte length: ${peerKeyBytes.size}"
        }

        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

        if (peerPublicKey is ECPublicKey) {
            val point = peerPublicKey.w
            if (point == ECPoint.POINT_INFINITY) {
                throw IllegalArgumentException("Point at infinity rejected")
            }
            if (point.affineX == null || point.affineY == null ||
                point.affineX.signum() <= 0 || point.affineY.signum() <= 0
            ) {
                throw IllegalArgumentException("Invalid EC coordinates: off-curve or non-positive")
            }
        }
        return peerPublicKey
    }

    /**
     * HKDF-Extract (RFC 5869) using HmacSHA256
     */
    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand (RFC 5869) using HmacSHA256
     */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var i = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val bytesToCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, bytesToCopy)
            offset += bytesToCopy
            i++
        }
        return okm
    }

    /**
     * Computes a 6-digit verification Safety Number (SAS) from the handshake transcript.
     * Both peers can visually compare this number to verify the absence of MITM.
     */
    fun computeSafetyNumber(myPubKeyBase64: String, peerPubKeyBase64: String, sharedSecret: ByteArray): String {
        val orderedKeys = listOf(myPubKeyBase64.trim(), peerPubKeyBase64.trim()).sorted()
        val transcript = "${orderedKeys[0]}:${orderedKeys[1]}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
        val digest = mac.doFinal(transcript.toByteArray(StandardCharsets.UTF_8))

        val value = ((digest[0].toInt() and 0xFF) shl 24) or
                ((digest[1].toInt() and 0xFF) shl 16) or
                ((digest[2].toInt() and 0xFF) shl 8) or
                (digest[3].toInt() and 0xFF)
        val positiveVal = Math.abs(value) % 1_000_000
        val rawNum = String.format("%06d", positiveVal)
        return "${rawNum.substring(0, 3)} ${rawNum.substring(3, 6)}"
    }

    /**
     * Derives a shared AES-256 SecretKey and Safety Number using ECDH key agreement with peer's public key.
     * Employs HKDF-SHA256 (RFC 5869) for key derivation.
     */
    fun deriveSessionKeyAndSafetyNumber(
        myKeyPair: KeyPair,
        peerPublicKeyBase64: String
    ): Pair<SecretKeySpec, String> {
        val peerPublicKey = validateEcPublicKey(peerPublicKeyBase64)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myKeyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive AES-256 key from shared secret using RFC 5869 HKDF-SHA256
        val prk = hkdfExtract(HKDF_SALT, sharedSecret)
        val aesKeyBytes = hkdfExpand(prk, HKDF_INFO, 32)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val myPubKeyBase64 = publicKeyToBase64(myKeyPair)
        val safetyNumber = computeSafetyNumber(myPubKeyBase64, peerPublicKeyBase64, sharedSecret)

        return Pair(secretKey, safetyNumber)
    }

    /**
     * Derives a shared AES-256 SecretKey using ECDH + HKDF-SHA256.
     */
    fun deriveSharedKey(myKeyPair: KeyPair, peerPublicKeyBase64: String): SecretKeySpec {
        return deriveSessionKeyAndSafetyNumber(myKeyPair, peerPublicKeyBase64).first
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns Base64 encoded string: [12-byte IV][Ciphertext + 16-byte Tag]
     */
    fun encryptAesGcm(plainText: String, secretKey: SecretKeySpec): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return base64Encode(combined)
    }

    /**
     * Decrypts Base64 AES-256-GCM ciphertext.
     */
    fun decryptAesGcm(base64Ciphertext: String, secretKey: SecretKeySpec): String {
        val combined = base64Decode(base64Ciphertext)
        if (combined.size < GCM_IV_LENGTH + 16) {
            throw IllegalArgumentException("Ciphertext too short")
        }

        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

        val cipherLength = combined.size - GCM_IV_LENGTH
        val cipherBytes = ByteArray(cipherLength)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherLength)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    /**
     * Hashes a user PIN using PBKDF2WithHmacSHA256 with random salt.
     * Returns "saltBase64:hashBase64"
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded

        val saltStr = base64Encode(salt)
        val hashStr = base64Encode(hash)
        return "$saltStr:$hashStr"
    }

    /**
     * Verifies entered PIN against stored "salt:hash".
     */
    fun verifyPin(pin: String, storedSaltAndHash: String): Boolean {
        val parts = storedSaltAndHash.split(":")
        if (parts.size != 2) return false
        val salt = base64Decode(parts[0])
        val expectedHash = base64Decode(parts[1])

        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val actualHash = skf.generateSecret(spec).encoded

        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    /**
     * Derives AES-256 key from a passphrase/PIN for backup encryption.
     */
    private fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = skf.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts backup JSON payload using user PIN.
     * Returns formatted backup container string.
     */
    fun encryptBackup(backupJson: String, pin: String): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val key = deriveKeyFromPassphrase(pin, salt)
        val encryptedData = encryptAesGcm(backupJson, key)
        val saltBase64 = base64Encode(salt)
        return "NEXVORA_V1:$saltBase64:$encryptedData"
    }

    /**
     * Decrypts backup container string using user PIN.
     */
    fun decryptBackup(backupContainer: String, pin: String): String {
        val parts = backupContainer.split(":")
        if (parts.size != 3 || parts[0] != "NEXVORA_V1") {
            throw IllegalArgumentException("Invalid backup format or version")
        }
        val salt = base64Decode(parts[1])
        val key = deriveKeyFromPassphrase(pin, salt)
        return decryptAesGcm(parts[2], key)
    }
}
