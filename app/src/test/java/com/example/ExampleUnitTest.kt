package com.example

import com.example.nearby.domain.matcher.ActivityMatcher
import com.example.nearby.domain.model.AvailabilityStatus
import com.example.nearby.domain.model.UserProfile
import com.example.nearby.domain.security.IdGenerator
import com.example.nearby.network.protocol.PacketValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testUserIdFormat() {
    val userId = IdGenerator.generateUserId()
    assertTrue("User ID must match NV-XXXX-XXXX format: $userId", userId.matches(Regex("^NV-[0-9A-F]{4}-[0-9A-F]{4}$")))
  }

  @Test
  fun testActivityMatcherDeterministicScore() {
    val profile = UserProfile(
      userId = "NV-1111-2222",
      nickname = "Tester",
      activities = listOf("Programming", "Study Buddy", "Gaming"),
      interests = listOf("Kotlin", "Android", "Chess"),
      languages = listOf("English", "Spanish"),
      availability = AvailabilityStatus.AVAILABLE
    )

    // Match with identical primary activity, 2 shared interests, 1 shared language, compatible availability
    val breakdown = ActivityMatcher.calculateMatch(
      myProfile = profile,
      peerPrimaryActivity = "Programming",
      peerActivities = listOf("Programming", "Board Games"),
      peerInterests = listOf("Kotlin", "Android", "Music"),
      peerLanguages = listOf("English", "French"),
      peerAvailability = AvailabilityStatus.AVAILABLE
    )

    // Same primary: 30, Shared interests (Kotlin, Android = 20), Shared language (English = 10), Availability = 20 -> 80
    assertEquals(80, breakdown.totalScore)
    assertTrue(breakdown.availabilityMatch)
    assertEquals(listOf("Kotlin", "Android"), breakdown.sharedInterests)
    assertEquals(listOf("English"), breakdown.sharedLanguages)
  }

  @Test
  fun testPacketValidatorFileNameSanitization() {
    val maliciousPath1 = "../../../secret_file.txt"
    val safe1 = PacketValidator.sanitizeFileName(maliciousPath1)
    assertEquals("secret_file.txt", safe1)
    assertFalse(safe1.contains("/"))
    assertFalse(safe1.contains(".."))

    val dangerousChars = "bad;rm -rf;file*name.pdf"
    val safe2 = PacketValidator.sanitizeFileName(dangerousChars)
    assertFalse(safe2.contains(";"))
    assertFalse(safe2.contains("*"))
    assertFalse(safe2.contains(" "))
  }

  @Test
  fun testStringSanitization() {
    val raw = "  Hello \u0000 World! \r\n  "
    val sanitized = PacketValidator.sanitizeString(raw, 50)
    assertEquals("Hello  World!", sanitized)
  }

  @Test
  fun testEcdhKeyExchangeAndSafetyNumberSymmetry() {
    // Generate Alice and Bob ECDH secp256r1 keypairs
    val aliceKeyPair = com.example.nearby.domain.security.CryptoManager.generateEcdhKeyPair()
    val bobKeyPair = com.example.nearby.domain.security.CryptoManager.generateEcdhKeyPair()

    val alicePubBase64 = com.example.nearby.domain.security.CryptoManager.publicKeyToBase64(aliceKeyPair)
    val bobPubBase64 = com.example.nearby.domain.security.CryptoManager.publicKeyToBase64(bobKeyPair)

    // Alice derives session key and safety number using Bob's public key
    val (aliceKey, aliceSafetyNumber) = com.example.nearby.domain.security.CryptoManager.deriveSessionKeyAndSafetyNumber(
      aliceKeyPair,
      bobPubBase64
    )

    // Bob derives session key and safety number using Alice's public key
    val (bobKey, bobSafetyNumber) = com.example.nearby.domain.security.CryptoManager.deriveSessionKeyAndSafetyNumber(
      bobKeyPair,
      alicePubBase64
    )

    // Verify derived AES-256 keys are identical
    org.junit.Assert.assertArrayEquals(aliceKey.encoded, bobKey.encoded)

    // Verify 6-digit Safety Numbers match exactly (anti-MITM SAS)
    assertEquals(aliceSafetyNumber, bobSafetyNumber)
    assertTrue("Safety number must be 6 digits formatted with a space (e.g. 123 456): $aliceSafetyNumber",
      aliceSafetyNumber.matches(Regex("^\\d{3} \\d{3}$")))
  }

  @Test
  fun testAesGcmEncryptionDecryptionRoundtrip() {
    val keyPair1 = com.example.nearby.domain.security.CryptoManager.generateEcdhKeyPair()
    val keyPair2 = com.example.nearby.domain.security.CryptoManager.generateEcdhKeyPair()
    val pub2 = com.example.nearby.domain.security.CryptoManager.publicKeyToBase64(keyPair2)
    val key = com.example.nearby.domain.security.CryptoManager.deriveSharedKey(keyPair1, pub2)

    val secretMessage = "Confidential P2P message between nearby peers!"
    val ciphertext = com.example.nearby.domain.security.CryptoManager.encryptAesGcm(secretMessage, key)

    val decrypted = com.example.nearby.domain.security.CryptoManager.decryptAesGcm(ciphertext, key)
    assertEquals(secretMessage, decrypted)
  }

  @Test
  fun testPacketValidatorReplayProtectionAndTimestampWindow() {
    val validPacket = com.example.nearby.network.protocol.NetworkPacket(
      packetId = "pkt_${System.currentTimeMillis()}_1",
      packetType = com.example.nearby.network.protocol.PacketType.MESSAGE,
      senderId = "NV-1234-5678",
      timestamp = System.currentTimeMillis()
    )
    assertTrue("Initial valid packet must pass validation", PacketValidator.validatePacket(validPacket))

    // Replaying identical packetId must be rejected
    assertFalse("Replay with identical packetId must be rejected", PacketValidator.validatePacket(validPacket))

    // Packet with timestamp too old (> 5 mins) must be rejected
    val stalePacket = com.example.nearby.network.protocol.NetworkPacket(
      packetId = "pkt_stale_${System.currentTimeMillis()}",
      packetType = com.example.nearby.network.protocol.PacketType.MESSAGE,
      senderId = "NV-1234-5678",
      timestamp = System.currentTimeMillis() - 400_000L // 6.6 minutes ago
    )
    assertFalse("Stale packet outside timestamp freshness window must be rejected", PacketValidator.validatePacket(stalePacket))
  }
}
