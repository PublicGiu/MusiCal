# 🎵 MusiCal — Lettore Musicale Nativo per Android 16

![Versione](https://img.shields.io/badge/versione-0.2.0-8B5CF6)
![Android](https://img.shields.io/badge/Android-16%20(API%2036)-7C3AED)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-A855F7)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-C084FC)
![Media3](https://img.shields.io/badge/Audio-AndroidX%20Media3%20(ExoPlayer)-6D28D9)
![Test](https://img.shields.io/badge/test-295%20passed-success)

**MusiCal** è un lettore musicale locale moderno, veloce e privo di pubblicità o tracciamento, progettato nativamente ed esclusivamente per **Android 16 (API 36)**.

Realizzato interamente in **Kotlin** e **Jetpack Compose** con un'identità visiva **Obsidian & Electric Violet** (Edge-to-Edge nativo), combina un motore audio ad alte prestazioni basato su **AndroidX Media3 (ExoPlayer)**, animazioni fluide a zero-jank (60/120fps), gestione intelligente della coda di riproduzione, playlist con copertine personalizzabili, Smart Auto-Tagger con testi sincronizzati (Karaoke), gestione duplicati avanzata, supporto completo multilingua (Italiano & English) e integrazione nativa con **Last.fm** (inclusa sincronizzazione automatica offline ed avatar con GIF animate hardware).

---

## ✨ Cosa fa l'App — Funzionalità Principali

### 🎧 1. Esperienza di Ascolto e Player a Doppio Livello
- **Mini-Player Fluttuante a Pillola (`PlayerBottomBar`)**:
  - Design compatto con bordo luminescente viola neon, ombra morbida e titoli scorrevoli continui (`basicMarquee`).
  - **Seekbar a filo superiore con Floating Time Badge**: barra di avanzamento integrata nel profilo superiore della pillola che si espande dinamicamente al tocco mostrando un cursore luminoso e un badge rialzato sopra il dito per visualizzare il minutaggio esatto durante lo scrubbing.
  - **Gesti rapidi**: carosello interattivo a scorrimento (`HorizontalPager`) dell'intera pillola per passare al brano precedente o successivo con snap elastico, swipe verso l'alto o tocco per aprire il player a schermo intero.
  - Pulsante rapido **`+`** per aggiungere subito il brano in ascolto a una playlist o ai preferiti, e pulsante circolare **Play/Pausa** con animazione di morphing.
- **Player a Schermo Intero (`FullScreenPlayerSheet`)**:
  - Sfondo dinamico immersivo a gradiente generato estraendo i colori dominanti della copertina del brano tramite **AndroidX Palette**.
  - Scorrimento orizzontale (`HorizontalPager`) tra le copertine dei brani in coda.
  - **Effetto respiro e profondità**: lieve animazione dinamica della copertina (scala 1.0f in riproduzione ⇄ 0.92f in pausa).
  - **Intestazione contestuale dinamica**: visualizza l'origine della riproduzione (*"IN RIPRODUZIONE DALL'ALBUM"*, *"DALLA PLAYLIST"*, *"DALL'ARTISTA"*, *"DALLA LIBRERIA"*); toccandola si naviga istantaneamente alla schermata d'origine, mentre toccando il nome dell'artista si apre la scheda monografica dedicata.
  - **Ripristino persistente della sessione (Cold Start)**: all'avvio a freddo dell'app viene ripristinato fedelmente in stato di pausa l'ultimo brano ascoltato, il minutaggio esatto, la coda e il contesto attivo (zero avvii audio inaspettati).

### 📋 2. Coda Intelligente a Due Livelli, Sincronizzazione Ripeti/Shuffle & Autoplay
- **Separazione netta tra Coda Utente e Autoplay**:
  - **Coda Utente (Prioritaria)**: tutti i brani, album o playlist aggiunti tramite *"Aggiungi alla coda"* vengono inseriti subito dopo la traccia corrente (`currentIndex + 1`) con priorità assoluta. Se vuota, la sezione si nasconde automaticamente.
  - **Riproduzione Automatica (Rolling Window di 15 brani)**: mantiene costantemente pronti i 15 brani successivi, reintegrando automaticamente una nuova traccia ogni volta che un brano viene riprodotto o rimosso.
  - **Sincronizzazione 1:1 con Ripeti e Shuffle**: attivando la ripetizione (`REPEAT_MODE_ALL` o `REPEAT_MODE_ONE`) o lo Shuffle, la lista di Riproduzione Automatica nella coda riflette al 100% l'esatta sequenza di riproduzione dei brani successivi (incluso il riavvolgimento ciclico dell'album o della playlist).
- **Controllo totale in `QueueBottomSheet`**:
  - Riordinamento fluido tramite **Drag & Drop**, pulsante **`↑`** per portare istantaneamente qualsiasi traccia in cima alla sequenza dei successivi, pulsante **`X`** e gesti di swipe orizzontali per rimuovere o promuovere elementi.
  - Card del brano in riproduzione dotata di equalizzatore grafico animato disegnato su GPU Canvas (`AnimatedEqualizerBars`).
- **Swipe-to-Queue negli Album e nelle Playlist**:
  - Nelle viste di dettaglio di album e playlist, trascinando un brano verso sinistra (`SwipeToQueueTrackItem`) lo si accoda all'istante con animazione elastica e feedback visivo.

### 💜 3. Playlist Personalizzate, Drag & Drop e "Brani preferiti"
- **Playlist di sistema permanente "Brani preferiti" (Favorite songs)**:
  - Sempre presente, fissata automaticamente al primo posto (`indice 0`) con qualsiasi criterio di ordinamento, protetta contro l'eliminazione accidentale e dotata di copertina dedicata.
  - Voce dinamica nel menu contestuale a tre puntini (`⋮`) di qualsiasi brano: mostra *"Aggiungi ai brani preferiti"* con cuore vuoto oppure *"Aggiunto ai brani preferiti"* con cuore pieno / *"Rimuovi dai brani preferiti"*.
- **Playlist create dall'utente**:
  - Creazione, eliminazione, ridenominazione e persistenza atomica locale sicura (`playlists.json`).
  - **Riordinamento manuale dei brani**: maniglia Drag & Drop touch in `PlaylistDetailView` per riordinare liberamente la scaletta.
  - **Copertina personalizzata**: selezione tramite il **Photo Picker di Android 16** (alla creazione o in seguito dal menu `⋮` con *"Cambia copertina"* / *"Rimuovi copertina"*).

### 🎚️ 4. Equalizzatore Audio DSP, Sleep Timer con Fade-Out, Statistiche & Scheda Artista
- **Equalizzatore Audio ed Effetti Sonori (`EqualizerDialog`)**: equalizzatore hardware a 5 bande (-15 dB / +15 dB), preset musicali integrati (Flat, Rock, Pop, Jazz, Hip Hop, Elettronica, Classica, Bass Boost, Voce), potenziamento bassi (**Bass Boost**) e spazializzatore 3D (**Virtualizer**), con persistenza autonoma dei parametri in `AppPreferences`.
- **Sleep Timer con Dissolvenza Volume (`SleepTimerDialog`)**: spegnimento programmato con preset rapidi (15, 30, 45, 60 min), slider personalizzato fino a 120 min o opzione *"Al termine del brano"*, con dissolvenza progressiva del volume (fade-out morbido negli ultimi 15 secondi).
- **Dashboard Statistiche d'Ascolto (`PlaybackStatsDialog`)**: riepilogo del tempo totale d'ascolto, ascolti complessivi e brani unici esplorati, con classifiche interattive Top 10 Brani, Top 5 Artisti e Top 5 Album.
- **Scheda Monografica Artista (`ArtistDetailSheet`)**: schermata dedicata per ogni artista con conteggio brani e dischi, pulsanti di riproduzione/accodamento globale, carosello orizzontale degli album e discografia completa.

### 🗂️ 5. Libreria Locale Multi-Sorgente, Ordinamenti Reattivi e Ricerca Globale
- **Selezione Multi-Sorgente (`AudioSourceMode`)**:
  - **Scansione Intelligente** (predefinita): analizza lo storage escludendo automaticamente cartelle di note vocali (WhatsApp, Telegram), memo vocali (.amr, .3gp) e applicando una doppia soglia di durata (<15s e >20min).
  - **Cartella Music**: interrogazione circoscritta a `Music/` e relative sottocartelle.
  - **Tutto**: tutti i file audio indicizzati senza restrizioni.
  - **Cartelle a scelta**: selezione granulare di directory tramite Storage Access Framework (`OpenDocumentTree`).
- **Instant-On Library Cache**: avvio a zero latenza (<15ms) tramite `LibraryCacheRepository` e pattern *Stale-While-Revalidate*.
- **Navigazione fluida a 3 schede (`Brani`, `Album`, `Playlist`)**:
  - Swipe orizzontale tra schede principali con `HorizontalPager` e **Collapsing Header** compatto a zero jank.
  - Transizioni cinematografiche shared-axis a 60/120 FPS tra schede e viste di dettaglio.
  - **Pulsante fluttuante "Torna in alto"**: compare fluidamente dopo aver scorso la lista per risalire all'istante con un tocco.
- **4 Criteri di Ordinamento con direzione invertibile (`↑` / `↓`)**:
  1. *Ascoltati di recente*
  2. *Più ascoltati* (con badge contatore ascolti visibile)
  3. *Aggiunti di recente*
  4. *Alfabetico (`A-Z` / `Z-A`)*
- **Ricerca Globale in tempo reale**: barra unificata con filtro istantaneo simultaneo su brani, album e playlist.

### 🔍 6. Gestione Intelligente dei Brani Duplicati (Confronto, Pulizia & "Ignora Duplicati")
- **Rilevamento intelligente**: algoritmo di normalizzazione dei titoli (senza punteggiatura o suffissi rumore) e tolleranza di durata ($\le 5$s) per individuare file audio doppi all'interno dello stesso album.
- **Foglio di Confronto Dedicato (`AlbumDuplicatesSheet`)**:
  - Confronto fianco a fianco dei brani con badge *"Conserva"* / *"Elimina"*, metadati tecnici completi (percorso file, bitrate, frequenza di campionamento e dimensione).
  - Ascolto dell'anteprima audio in tempo reale.
  - Eliminazione fisica sicura dallo storage tramite `MediaStore.createDeleteRequest` (Android 16 Scoped Storage).
- **Funzione "Ignora file duplicati" (Ignore Duplicates)**:
  - Possibilità di contrassegnare come ignorati i file duplicati segnalati (per singolo gruppo o per l'intero album).
  - Memorizzazione persistente in `ignored_duplicate_keys` (su `AppPreferences`), consentendo di nascondere le segnalazioni senza eliminare i file.
  - Facile ripristino in qualsiasi momento dal menu a tre puntini dell'album (*"Mostra brani ignorati"*) o dalla schermata Impostazioni.

### 🏷️ 7. Smart Auto-Tagger, Metadati HD & Testi Sincronizzati (Karaoke)
- **Identificazione e Arricchimento ad Alta Precisione**:
  - Interrogazione combinata delle API pubbliche di **Deezer** e **LRCLIB** per il recupero di metadati canonici, anno, genere, numero traccia/disco e copertine HD a 1000x1000px.
  - Sanitizzazione avanzata del testo (`QuerySanitizer`) per rimuovere tag rumorosi da file audio o titoli (`[Official Video]`, bitrate, prefissi traccia).
  - **Risoluzione della frammentazione degli album**: scrittura rigorosa dell'artista specifico su `ARTIST` e dell'artista principale su `ALBUM_ARTIST`, con allineamento automatico della scaletta (`alignAlbumTracks`).
- **Scrittura Atomica su Scoped Storage**:
  - Modifica fisica dei frame ID3/Vorbis e inserimento copertina JPEG embedded tramite `jaudiotagger` Android (LGPL v2.1 privo di AWT) e autorizzazione di sistema `MediaStore.createWriteRequest`.
- **Interfaccia Interattiva (`MetadataEnricherSheet`)**:
  - Confronto visivo "Prima vs Dopo", selezione copertina da galleria (Photo Picker), Quick-Edit dei campi con **Cursor Scrubbing** orizzontale, ricerca manuale personalizzata e modalità batch per interi album.
  - **Rilevamento e Separazione Intelligente Album Misti**: banner contestuale con opzioni per separare file aggregati erroneamente o uniformarli in un unico album.
- **Visualizzazione Testi Sincronizzati (Karaoke)**:
  - Vista dedicata [`SyncedLyricsView`](app/src/main/java/com/example/musicplayer/ui/components/SyncedLyricsView.kt) integrata nel player a schermo intero con auto-scroll dinamico, evidenziazione della riga corrente con compensazione latenza (150ms lead time), tap-to-seek interattivo, salvataggio locale dei file `.lrc` in `filesDir/lyrics/` e gestione brani strumentali.

### 🔴 8. Integrazione Last.fm Avanzata (Scrobbling, Cache Offline, Avatar GIF & Chiavi Inline)
- **Autenticazione & Scrobbling automatico**:
  - Login diretto in-app (`auth.getMobileSession`) con firma crittografica MD5 conforme alle specifiche Last.fm.
  - Invio dello stato *"Now Playing"* all'avvio e scrobble al raggiungimento del **50% della durata** (o dopo **4 minuti** di ascolto).
- **Profilo Utente con Avatar Animati (GIF Hardware)**:
  - Visualizzazione dell'avatar utente Last.fm con supporto nativo sia per immagini statiche sia per **GIF animate** tramite decodifica hardware (`ImageDecoder` + `AnimatedImageDrawable`), fluide e a basso consumo.
- **Configurazione Chiavi API Inline**:
  - Possibilità di inserire o modificare `API Key` e `Shared Secret` direttamente dalla finestra del profilo Last.fm, anche a sessione già attiva, senza dover effettuare il logout.
- **Resilienza Offline (Room Database)**:
  - In assenza di connessione Internet, gli scrobble vengono salvati automaticamente in un database SQLite locale (`scrobble_cache.db`) e inviati in background non appena il dispositivo torna online (`ConnectivityManager.NetworkCallback`).

### 🌐 9. Supporto Multilingua Nativo (Italiano & English)
- **Supporto Per-App Language nativo per Android 16**:
  - Integrazione completa con `android.app.LocaleManager` e `res/xml/locales_config.xml`.
  - Scelta della lingua direttamente in **Impostazioni → Lingua dell'applicazione** (*"App language"*):
    - *Segui il sistema* (*"System default"*)
    - *Italiano*
    - *English*
  - Ricomposizione reattiva istantanea di tutta la UI (dialoghi, sheet modali, menu a discesa, snackbar, badge e messaggi informativi) senza dover riavviare l'applicazione.
  - Traduzione dinamica della playlist di sistema permanente (*"Brani preferiti"* $\leftrightarrow$ *"Favorite songs"*).

### 🔔 10. Verifica Aggiornamenti In-App & Note di Rilascio in Markdown Nativo
- **Verifica Automatica e Manuale**: controllo asincrono delle release ufficiali da GitHub (`PublicGiu/MusiCal`).
- **Dialog Note di Rilascio (`UpdateAvailableDialog`)**: anteprima del changelog formattato tramite un parser Markdown nativo in puro Kotlin (`MarkdownContent`), con supporto per titoli, grassetto/corsivo, elenchi puntati, pillole di codice e collegamenti ipertestuali, pulsante di download diretto dell'APK e opzione "Ignora questa versione".

---

## 🛠️ Stack Tecnologico e Architettura

| Livello | Tecnologie Utilizzate |
| :--- | :--- |
| **Sistema Operativo Target** | **Android 16** (`compileSdk = 36`, `minSdk = 36`, `targetSdk = 36`) |
| **Linguaggio & Toolchain** | Kotlin `2.1.0`, Java `21`, Android Gradle Plugin `8.8.0`, KSP `2.1.0-1.0.29`, Gradle `8.11.1` |
| **UI & Design System** | Jetpack Compose (BOM `2025.01.00`), Material 3, Edge-to-Edge nativo, AndroidX Palette |
| **Audio & Background** | AndroidX Media3 `1.5.1` (`ExoPlayer`, `MediaSessionService`, `MediaController`, `ForwardingPlayer`, custom `Media3BitmapLoader`) |
| **Tagging Audio & Metadati** | `jaudiotagger` Android `2.2.3` (LGPL v2.1 compatibile, privo di AWT), `androidx.exifinterface:exifinterface:1.4.2` |
| **Persistenza Locale** | `AtomicFile` JSON (`playlists.json`, `track_stats.json`), `LibraryCacheRepository` (`library_cache.json`), Room `2.6.1` (`scrobble_cache.db`), `SharedPreferences` |
| **Immagini & Rete** | Coil Compose `2.7.0`, OkHttp `4.12.0` (Deezer API, LRCLIB API, Last.fm REST API, GitHub API) |
| **Localizzazione** | Android 16 Per-App Language (`LocaleManager`), `strings.xml` (Italiano) e `values-en/strings.xml` (Inglese) |
| **Test Automatizzati** | JUnit 4, Mockito Core, suite completa di **295 unit test** su JVM |

```
app/src/main/java/com/example/musicplayer/
├── MainActivity.kt                         # Single-Activity Edge-to-Edge, Material 3 e gestione dinamica locale
├── data/
│   ├── AudioTrack.kt / Album.kt / Playlist.kt / TrackStats.kt / SortOption.kt / AudioSourceMode.kt
│   ├── AppLanguage.kt                      # Modello lingue supportate (Italiano, English, System)
│   ├── AppPreferences.kt                   # Tema, autoplay, lingua, cartelle ignorate e preferenze persistenti
│   ├── AudioRepository.kt                  # Scansione MediaStore (Intelligente, Music/, Tutto, Cartelle SAF)
│   ├── LibraryCacheRepository.kt           # Cache istantanea library_cache.json (pattern Stale-While-Revalidate)
│   ├── LyricsRepository.kt                 # Salvataggio e gestione file testi sincronizzati .lrc su disco
│   ├── PlaylistRepository.kt               # Persistenza atomica playlist e copertine personalizzate Photo Picker
│   ├── StatsRepository.kt                  # Persistenza atomica statistiche d'ascolto (AtomicFile)
│   ├── lastfm/                             # Client REST Last.fm, firma MD5, sessione e database Room offline
│   ├── tagger/                             # Client Deezer & LRCLIB, QuerySanitizer, AudioTagWriter e Scoped Storage
│   └── update/                             # GithubUpdateChecker per verifica nuove release su GitHub
├── playback/
│   ├── PlaybackService.kt                  # MediaSessionService con ExoPlayer, Audio Focus e WakeLock
│   ├── EqualizerManager.kt                 # Gestore equalizzatore hardware DSP 5 bande, Bass Boost e Virtualizer
│   ├── SleepTimerCoordinator.kt            # Coordinatore spegnimento programmato con fade-out volume
│   └── Media3BitmapLoader.kt               # Caricamento miniature per notifiche e lock screen Android 16
└── ui/
    ├── MainScreen.kt                       # Scaffold con Collapsing Header zero-jank, HorizontalPager e Drawer/Tabs
    ├── MusicViewModel.kt                   # Gestore di stato unificato, Coda Utente, Autoplay (15 brani), Coordinatori
    ├── components/                         # Componenti modulari Jetpack Compose
    │   ├── MainModalsHost.kt               # Host centralizzato disaccoppiato per tutti i dialoghi e sheet modali
    │   ├── PlayerBottomBar.kt              # Mini-player a pillola con seekbar a filo e Floating Time Badge
    │   ├── FullScreenPlayerSheet.kt        # Player immersivo a schermo intero con Palette e lyrics karaoke
    │   ├── QueueBottomSheet.kt             # Foglio della coda a due livelli (Coda Utente + Autoplay 15 brani)
    │   ├── AlbumDetailView.kt              # Scheda dettaglio album con header compatto e Swipe-to-Queue
    │   ├── AlbumDuplicatesSheet.kt         # Confronto e gestione duplicati (anteprima, pulizia, ignora duplicati)
    │   ├── PlaylistsView.kt                # Viste playlist con ordinamento Drag & Drop e "Brani preferiti"
    │   ├── LastFmAvatar.kt                 # Avatar Last.fm con supporto hardware a GIF animate
    │   ├── SyncedLyricsView.kt             # Visualizzatore karaoke con scrolling automatico dei testi .lrc
    │   ├── SettingsDialog.kt               # Finestra impostazioni (Tema, Sorgenti, Autoplay, Lingua, Last.fm)
    │   ├── EqualizerDialog.kt              # Interfaccia grafica equalizzatore audio a 5 bande
    │   ├── SleepTimerDialog.kt             # Dialog timer spegnimento programmato con fade-out
    │   ├── PlaybackStatsDialog.kt          # Dashboard statistiche e classifiche di riproduzione
    │   ├── ArtistDetailSheet.kt            # Scheda monografica artista con discografia e carosello album
    │   ├── UpdateAvailableDialog.kt        # Dialog aggiornamenti con anteprima changelog Markdown
    │   └── markdown/                       # Parser Markdown puro Kotlin per note di rilascio
    ├── tagger/                             # MetadataEnricherSheet e MetadataEnricherCoordinator (Auto-Tagger)
    └── theme/                              # Obsidian & Electric Violet Palette e Material You Dynamic Color
```

---

## 🔒 Sicurezza delle Chiavi API

Il repository e gli APK distribuiti sono concepiti per proteggere rigorosamente qualsiasi informazione riservata:
1. **Configurazione In-App (per chi scarica l'APK dalle Releases)**: Aprendo **Impostazioni → Last.fm Scrobbler** è possibile configurare o modificare direttamente la propria `API Key` e il proprio `Shared Secret` (generabili gratuitamente su [last.fm/api/account/create](https://www.last.fm/api/account/create)), anche a sessione già attiva, salvandoli in modo cifrato e sicuro localmente sul dispositivo.
2. **Configurazione in fase di compilazione (`local.properties`)**: Chi compila dai sorgenti può definire le chiavi in `local.properties` (escluso dal tracking Git) prendendo come modello il file [`local.properties.example`](local.properties.example).
3. Se non vengono fornite chiavi Last.fm, **tutte le funzionalità di lettore musicale locale, playlist, auto-tagger e karaoke rimangono pienamente attive e utilizzabili**.

---

## 📲 Download Diretto APK

Se desideri installare subito **MusiCal v0.2.0** sul tuo dispositivo Android 16 senza compilare il sorgente, scarica il file `.apk` direttamente dalla sezione [**Releases**](https://github.com/PublicGiu/MusiCal/releases/tag/v0.2.0) di questo repository:

👉 **[Scarica MusiCal-v0.2.0.apk](https://github.com/PublicGiu/MusiCal/releases/download/v0.2.0/MusiCal-v0.2.0.apk)**

---

## 🚀 Come Compilare ed Eseguire il Progetto

### Prerequisiti
- **Android Studio** (Ladybug o successivo) con **Android 16 SDK (API 36)**.
- **JDK 21**.
- Dispositivo fisico o emulatore con **Android 16**.

### Installazione e Compilazione Rapida

1. **Clona il repository**:
   ```bash
   git clone https://github.com/PublicGiu/MusiCal.git
   cd MusiCal
   ```

2. **Configura `local.properties`**:
   Copia il file modello `local.properties.example` in `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```
   Apri `local.properties` e inserisci il percorso del tuo Android SDK e, se desideri usare lo scrobbling Last.fm, le tue chiavi API:
   ```properties
   sdk.dir=C\:\\Users\\TUO_UTENTE\\AppData\\Local\\Android\\Sdk

   # Opzionale: credenziali API Last.fm
   lastfm.apiKey=LA_TUA_API_KEY
   lastfm.sharedSecret=IL_TUO_SHARED_SECRET
   ```

3. **Compila o installa l'APK Debug**:
   ```powershell
   .\gradlew.bat assembleDebug
   # oppure per installare direttamente sul dispositivo collegato via ADB:
   .\gradlew.bat installDebug
   ```

4. **Compila l'APK Release Ottimizzato (R8 / ProGuard)**:
   ```powershell
   .\gradlew.bat assembleRelease -PexcludeLastFmKeys=true
   ```

5. **Esegui la suite di Test Unitari (295 test su JVM)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
