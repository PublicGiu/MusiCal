package com.example.musicplayer.data.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class LastFmConfigTest {

    @Test
    fun testMd5KnownValues() {
        // MD5 standard di stringa vuota
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", LastFmConfig.md5(""))

        // MD5 standard di "test"
        assertEquals("098f6bcd4621d373cade4e832627b4f6", LastFmConfig.md5("test"))

        // MD5 standard di stringa alfanumerica
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", LastFmConfig.md5("hello world"))
    }

    @Test
    fun testMd5LengthAndFormat() {
        val hash = LastFmConfig.md5("some_random_string_12345")
        assertEquals(32, hash.length)
        // Deve contenere solo caratteri esadecimali minuscoli
        assert(hash.matches(Regex("^[0-9a-f]{32}$")))
    }

    @Test
    fun testGenerateApiSigExcludesFormatAndCallback() {
        val paramsWithExcluded = mapOf(
            "method" to "track.scrobble",
            "api_key" to LastFmConfig.API_KEY,
            "format" to "json",
            "callback" to "myCallback"
        )

        val paramsWithoutExcluded = mapOf(
            "method" to "track.scrobble",
            "api_key" to LastFmConfig.API_KEY
        )

        val sigWithExcluded = LastFmConfig.generateApiSig(paramsWithExcluded)
        val sigWithoutExcluded = LastFmConfig.generateApiSig(paramsWithoutExcluded)

        // Entrambe le chiamate devono generare la medesima firma escludendo 'format' e 'callback'
        assertEquals(sigWithoutExcluded, sigWithExcluded)
        assertEquals(32, sigWithExcluded.length)
    }

    @Test
    fun testGenerateApiSigOrdersAlphabetically() {
        val paramsUnordered1 = mapOf(
            "z_param" to "valZ",
            "a_param" to "valA",
            "m_param" to "valM"
        )

        val paramsUnordered2 = mapOf(
            "a_param" to "valA",
            "m_param" to "valM",
            "z_param" to "valZ"
        )

        val sig1 = LastFmConfig.generateApiSig(paramsUnordered1)
        val sig2 = LastFmConfig.generateApiSig(paramsUnordered2)

        assertEquals("L'ordinamento delle chiavi deve produrre lo stesso identico hash", sig1, sig2)
    }

    @Test
    fun testIsConfiguredConsistency() {
        val expected = LastFmConfig.API_KEY.isNotBlank() && LastFmConfig.SHARED_SECRET.isNotBlank()
        assertEquals(expected, LastFmConfig.isConfigured)
        if (LastFmConfig.isConfigured) {
            assertEquals(32, LastFmConfig.API_KEY.length)
            assertEquals(32, LastFmConfig.SHARED_SECRET.length)
        }
    }

    @Test
    fun testCustomCredentialsOverrideAndReset() {
        try {
            LastFmConfig.setCustomCredentials("0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210")
            assertEquals("0123456789abcdef0123456789abcdef", LastFmConfig.API_KEY)
            assertEquals("fedcba9876543210fedcba9876543210", LastFmConfig.SHARED_SECRET)
            assert(LastFmConfig.hasCustomKeys)
            assert(LastFmConfig.isConfigured)
        } finally {
            LastFmConfig.setCustomCredentials(null, null)
            assertFalse(LastFmConfig.hasCustomKeys)
        }
    }
}
