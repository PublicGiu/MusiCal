package com.example.musicplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRepositoryIntelligentScanTest {

    @Test
    fun `filters out MediaStore IS_RECORDING and system non-music flags even in Music folder`() {
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Music/",
                displayName = "Lezione università.m4a",
                title = "Lezione università",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 180_000L,
                isSystemNonMusic = true
            )
        )
    }

    @Test
    fun `filters out recording folders even when nested inside Music directory`() {
        val blockedPaths = listOf(
            "Music/Recordings/" to "Nota 1.m4a",
            "Music/Registrazioni/" to "Appunto.m4a",
            "Music/Sound_Recorder/" to "Prova.mp3",
            "Music/Voice Recorder/" to "Test.m4a",
            "Music/WhatsApp Audio/" to "Brano inoltrato.opus",
            "Registrazioni vocali/" to "Sessione 1.m4a",
            "Recordings/Call recordings/" to "Chiamata Ufficio.m4a",
            "Sounds/CallRecord/" to "Numero.mp3",
            "MIUI/sound_recorder/call_rec/" to "Chiamata.mp3",
            "Record/" to "Audio.m4a"
        )
        blockedPaths.forEach { (path, fileName) ->
            assertFalse(
                "Expected path '$path' ($fileName) to be excluded",
                isIntelligentMusicTrack(
                    relativePath = path,
                    displayName = fileName,
                    title = fileName.substringBeforeLast('.'),
                    artist = "Artista sconosciuto",
                    album = "Album sconosciuto",
                    durationMs = 95_000L
                )
            )
        }
    }

    @Test
    fun `filters out OEM recorder filenames and pure timestamps across folders`() {
        val recordingFiles = listOf(
            "Voce 001.m4a" to "Voce 001",
            "Voice 012.m4a" to "Voice 012",
            "Registrazione vocale 003.m4a" to "Registrazione vocale 003",
            "Nuova registrazione 1.m4a" to "Nuova registrazione 1",
            "New recording 2.m4a" to "New recording 2",
            "20261119_213000.m4a" to "20261119_213000",
            "2026-11-19 21-30-00.m4a" to "2026-11-19 21-30-00",
            "@19112026_143022.mp3" to "@19112026_143022",
            "Mercoledì alle 14-30.m4a" to "Mercoledì alle 14-30",
            "Tuesday at 09-15.m4a" to "Tuesday at 09-15",
            "audio_2026-11-19_14-30-00.ogg" to "audio_2026-11-19_14-30-00",
            "PTT-20261119-WA0001.opus" to "PTT-20261119-WA0001",
            "Call_20261119_1800.mp3" to "Call_20261119_1800",
            "prova.awb" to "Prova"
        )
        recordingFiles.forEach { (fileName, title) ->
            assertFalse(
                "Expected recording file '$fileName' to be excluded",
                isIntelligentMusicTrack(
                    relativePath = "Download/",
                    displayName = fileName,
                    title = title,
                    artist = "Artista sconosciuto",
                    album = "Download",
                    durationMs = 120_000L
                )
            )
        }
    }

    @Test
    fun `filters out files tagged with recorder metadata`() {
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "Appunti.m4a",
                title = "Appunti",
                artist = "Voice Recorder",
                album = "Recordings",
                durationMs = 150_000L
            )
        )
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "Intervista.m4a",
                title = "Intervista",
                artist = "Artista sconosciuto",
                album = "Registrazioni",
                durationMs = 150_000L
            )
        )
    }

    @Test
    fun `preserves authentic music including downloads and edge song titles`() {
        assertTrue(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "Lazza - Cenere.mp3",
                title = "Cenere",
                artist = "Lazza",
                album = "Sirio",
                durationMs = 208_000L
            )
        )
        assertTrue(
            isIntelligentMusicTrack(
                relativePath = "Music/Pink Floyd/",
                displayName = "01 The Voice.flac",
                title = "The Voice",
                artist = "Artista",
                album = "Columbia Records Anthology",
                durationMs = 245_000L
            )
        )
        assertTrue(
            isIntelligentMusicTrack(
                relativePath = "Music/Smashing Pumpkins/",
                displayName = "1979.mp3",
                title = "1979",
                artist = "The Smashing Pumpkins",
                album = "Mellon Collie",
                durationMs = 266_000L
            )
        )
        assertTrue(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "Call Me Maybe.mp3",
                title = "Call Me Maybe",
                artist = "Carly Rae Jepsen",
                album = "Kiss",
                durationMs = 193_000L
            )
        )
    }

    @Test
    fun `preserves music folders whose artist or album name matches generic chat or system words`() {
        assertTrue(
            isIntelligentMusicTrack(
                relativePath = "Music/Signal/",
                displayName = "01 Intro.mp3",
                title = "Intro",
                artist = "Signal",
                album = "Signal",
                durationMs = 185_000L
            )
        )
        assertTrue(
            isIntelligentMusicTrack(
                relativePath = "Music/The Alarms/",
                displayName = "Sixty Eight Guns.flac",
                title = "Sixty Eight Guns",
                artist = "The Alarms",
                album = "Declaration",
                durationMs = 198_000L
            )
        )
    }

    @Test
    fun `filters out zero duration corrupt files app data folders and short untagged voice clips`() {
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Music/",
                displayName = "Corrupt.mp3",
                title = "Corrupt",
                artist = "Artist",
                album = "Album",
                durationMs = 0L
            )
        )
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Android/data/com.somegame/files/audio/",
                displayName = "bgm_loop.mp3",
                title = "bgm_loop",
                artist = "Game Studio",
                album = "OST",
                durationMs = 95_000L
            )
        )
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "Standard 001.m4a",
                title = "Standard 001",
                artist = "Artista sconosciuto",
                album = "Download",
                durationMs = 70_000L
            )
        )
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "msg_99123.opus",
                title = "msg_99123",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 22_000L
            )
        )
        assertFalse(
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "click.mp3",
                title = "click",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 30_000L,
                fileSizeBytes = 4_096L
            )
        )
    }
}
