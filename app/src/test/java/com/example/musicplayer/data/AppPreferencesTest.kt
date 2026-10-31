package com.example.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class AppPreferencesTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockPrefs = Mockito.mock(SharedPreferences::class.java)
        mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences(eq("music_player_settings"), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
    }

    @Test
    fun testThemeModeEnumLabels() {
        assertEquals("Segui il sistema", ThemeMode.SYSTEM.label)
        assertEquals("Tema chiaro", ThemeMode.LIGHT.label)
        assertEquals("Tema scuro", ThemeMode.DARK.label)
    }

    @Test
    fun testDefaultValuesWhenPrefsAreEmpty() {
        `when`(mockPrefs.getBoolean(eq("autoplay_enabled"), eq(true))).thenReturn(true)
        `when`(mockPrefs.getBoolean(eq("dynamic_color"), eq(false))).thenReturn(false)
        `when`(mockPrefs.getString(eq("theme_mode"), eq(ThemeMode.SYSTEM.name))).thenReturn(ThemeMode.SYSTEM.name)

        val appPrefs = AppPreferences(mockContext)

        assertTrue("L'autoplay deve essere abilitato di default", appPrefs.isAutoplayEnabled())
        assertFalse("Il colore dinamico deve essere disabilitato di default per preservare l'identità cromatica dell'icona", appPrefs.isDynamicColorEnabled())
        assertEquals("Il tema deve essere SYSTEM di default", ThemeMode.SYSTEM, appPrefs.getThemeMode())
    }

    @Test
    fun testThemeModeFallbackOnInvalidString() {
        `when`(mockPrefs.getString(eq("theme_mode"), anyString())).thenReturn("INVALID_UNKNOWN_THEME")

        val appPrefs = AppPreferences(mockContext)
        assertEquals("Un valore sconosciuto deve ricadere sul tema SYSTEM di default", ThemeMode.SYSTEM, appPrefs.getThemeMode())
    }

    @Test
    fun testSettersInvokeEditor() {
        val appPrefs = AppPreferences(mockContext)

        appPrefs.setAutoplayEnabled(false)
        Mockito.verify(mockEditor).putBoolean("autoplay_enabled", false)

        appPrefs.setThemeMode(ThemeMode.DARK)
        Mockito.verify(mockEditor).putString("theme_mode", ThemeMode.DARK.name)

        appPrefs.setDynamicColorEnabled(false)
        Mockito.verify(mockEditor).putBoolean("dynamic_color", false)
    }
}
