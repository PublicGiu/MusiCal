package com.example.musicplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceFilterTest {

    @Test
    fun testStandardMusicTrack_isAccepted() {
        val accepted = isIntelligentMusicTrack(
            relativePath = "Music/Rock/",
            displayName = "Bohemian Rhapsody.mp3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationMs = 354_000L // ~5m 54s
        )
        assertTrue("Un brano musicale standard con tag validi deve essere accettato", accepted)
    }

    @Test
    fun testWhatsAppAudioAndVoiceNotes_areExcluded() {
        assertFalse(
            "WhatsApp Audio deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "WhatsApp/Media/WhatsApp Audio/",
                displayName = "AUD-20231102-WA0004.m4a",
                title = "AUD-20231102-WA0004",
                artist = "Sconosciuto",
                album = "Sconosciuto",
                durationMs = 45_000L
            )
        )

        assertFalse(
            "WhatsApp Voice Notes deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202345/",
                displayName = "PTT-20231103-WA0012.opus",
                title = "PTT-20231103-WA0012",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 28_000L
            )
        )
    }

    @Test
    fun testTelegramAndRecordings_areExcluded() {
        assertFalse(
            "Telegram Audio deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "Telegram/Telegram Audio/",
                displayName = "audio_msg_123.ogg",
                title = "audio_msg_123",
                artist = "Sconosciuto",
                album = "Sconosciuto",
                durationMs = 60_000L
            )
        )

        assertFalse(
            "Registrazioni vocali del telefono devono essere escluse",
            isIntelligentMusicTrack(
                relativePath = "Recordings/CallRecordings/",
                displayName = "Call_Mama_2023.m4a",
                title = "Call_Mama",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 180_000L
            )
        )

        assertFalse(
            "Sound Recorder MIUI deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "MIUI/sound_recorder/",
                displayName = "Record_001.mp3",
                title = "Record_001",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 90_000L
            )
        )
    }

    @Test
    fun testHiddenFilesAndSpecialExtensions_areExcluded() {
        assertFalse(
            "File nascosto con punto iniziale deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "Music/",
                displayName = ".hidden_audio.mp3",
                title = ".hidden_audio",
                artist = "Queen",
                album = "Greatest Hits",
                durationMs = 120_000L
            )
        )

        assertFalse(
            "File .amr (memo vocale) deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "memo.amr",
                title = "memo",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 40_000L
            )
        )
    }

    @Test
    fun testShortDuration_threshold15Seconds() {
        // Meno di 15s senza metadati musicali validi -> ESCLUSO
        assertFalse(
            "Effetto sonoro o memo brevissimo senza tag deve essere escluso",
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "notification_sound.mp3",
                title = "Notification",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 8_000L // 8 secondi
            )
        )

        // Meno di 15s con metadati completi autentici (es. intro musicale, interludio) -> PRESERVATO
        assertTrue(
            "Brano musicale autentico ma breve con album e artista validi deve essere preservato",
            isIntelligentMusicTrack(
                relativePath = "Music/Pink Floyd/",
                displayName = "01 - Speak to Me Intro.mp3",
                title = "Speak to Me Intro",
                artist = "Pink Floyd",
                album = "The Dark Side of the Moon",
                durationMs = 12_000L // 12 secondi
            )
        )
    }

    @Test
    fun testLongDuration_threshold20Minutes() {
        // Più di 20 minuti senza album o con album generico/sconosciuto -> ESCLUSO
        assertFalse(
            "Registrazione vocale prolungata senza tag deve essere esclusa",
            isIntelligentMusicTrack(
                relativePath = "Download/",
                displayName = "Lezione_Universita_Registrazione.mp3",
                title = "Lezione Universita",
                artist = "Artista sconosciuto",
                album = "Album sconosciuto",
                durationMs = 1_800_000L // 30 minuti
            )
        )

        // Più di 20 minuti ma con album e artista autentici (es. sinfonia, brano prog rock) -> PRESERVATO
        assertTrue(
            "Sinfonia o brano lungo oltre 20 minuti con metadati autentici deve essere preservato",
            isIntelligentMusicTrack(
                relativePath = "Music/Classical/",
                displayName = "Beethoven Symphony No 9 Complete.flac",
                title = "Symphony No. 9 in D minor",
                artist = "Ludwig van Beethoven",
                album = "Beethoven: The 9 Symphonies",
                durationMs = 3_900_000L // 65 minuti
            )
        )
    }
}
