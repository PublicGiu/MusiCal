package com.example.musicplayer.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GithubUpdateCheckerTest {

    private lateinit var checker: GithubUpdateChecker

    @Before
    fun setUp() {
        checker = GithubUpdateChecker()
    }

    @Test
    fun testSanitizeVersionString() {
        assertEquals("0.1.5", checker.sanitizeVersionString("v0.1.5"))
        assertEquals("0.1.5", checker.sanitizeVersionString("V0.1.5"))
        assertEquals("0.1.5", checker.sanitizeVersionString(" 0.1.5 "))
        assertEquals("1.0.0", checker.sanitizeVersionString("v1.0.0"))
    }

    @Test
    fun testIsRemoteNewer_patchUpdate() {
        assertTrue(checker.isRemoteNewer("0.1.5", "0.1.4"))
        assertTrue(checker.isRemoteNewer("v0.1.5", "0.1.4"))
        assertTrue(checker.isRemoteNewer("0.1.10", "0.1.9"))
        assertFalse(checker.isRemoteNewer("0.1.4", "0.1.5"))
    }

    @Test
    fun testIsRemoteNewer_minorUpdate() {
        assertTrue(checker.isRemoteNewer("0.2.0", "0.1.5"))
        assertTrue(checker.isRemoteNewer("v0.2.0", "v0.1.4"))
        assertFalse(checker.isRemoteNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun testIsRemoteNewer_majorUpdate() {
        assertTrue(checker.isRemoteNewer("1.0.0", "0.1.5"))
        assertTrue(checker.isRemoteNewer("2.0.0", "1.9.9"))
        assertFalse(checker.isRemoteNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun testIsRemoteNewer_sameVersion() {
        assertFalse(checker.isRemoteNewer("0.1.5", "0.1.5"))
        assertFalse(checker.isRemoteNewer("v0.1.5", "0.1.5"))
        assertFalse(checker.isRemoteNewer("0.1.5", "v0.1.5"))
    }

    @Test
    fun testIsRemoteNewer_withPreReleaseTag() {
        assertTrue(checker.isRemoteNewer("0.1.6-beta1", "0.1.5"))
        assertFalse(checker.isRemoteNewer("0.1.5-beta1", "0.1.5"))
    }

    @Test
    fun testIsRemoteNewer_invalidOrBlankVersions() {
        assertFalse(checker.isRemoteNewer("", "0.1.5"))
        assertFalse(checker.isRemoteNewer("0.1.5", ""))
        assertFalse(checker.isRemoteNewer(" ", " "))
    }
}
