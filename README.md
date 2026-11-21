# 🎵 MusiCal — Lettore Musicale Nativo per Android 16

![Versione](https://img.shields.io/badge/versione-0.2.9-8B5CF6)
![Android](https://img.shields.io/badge/Android-16%20(API%2036)-7C3AED)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-A855F7)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-C084FC)
![Media3](https://img.shields.io/badge/Audio-AndroidX%20Media3%20(ExoPlayer)-6D28D9)
![Test](https://img.shields.io/badge/test-373%20passed-success)

**MusiCal** è un lettore musicale locale moderno, veloce e privo di pubblicità o tracciamento, progettato nativamente ed esclusivamente per **Android 16 (API 36)**.

Realizzato interamente in **Kotlin** e **Jetpack Compose** con un'identità visiva **Obsidian & Electric Violet** (Edge-to-Edge nativo e Tema Chiaro anti-glare), combina un motore audio ad alte prestazioni basato su **AndroidX Media3 (ExoPlayer)**, animazioni fluide a zero-jank (60/120fps), gestione intelligente della coda di riproduzione, playlist con ritaglio/zoom delle copertine, **Fast Scroller** con indicatore alfabetico, profili artista avanzati con foto HD e biografie enciclopediche personalizzabili, **Smart Auto-Tagger** con testi sincronizzati (Karaoke), gestione duplicati avanzata, **Backup e Ripristino locale JSON**, supporto multilingua seamless (Italiano & English) e integrazione nativa con **Last.fm** (inclusa sincronizzazione offline ed avatar con GIF animate hardware).

---

## 🆕 Novità Principali fino alla v0.2.9

Dalla v0.2.0 alla **v0.2.9** MusiCal è stato ampliato con numerose funzionalità e miglioramenti strutturali:
- **v0.2.9**: **Scansione Intelligente potenziata** con filtri MediaStore Android 16 e anti-registrazioni OEM (anche sotto `Music/`), **rifiuto automatico delle pagine di brani/album** nelle biografie Wikipedia degli artisti, **unificazione dell'`Album Artist` principale** per evitare frammentazioni, **isolamento gesti verticali** nella vista testi karaoke (`SyncedLyricsView`) e **nuovi selettori a tile** in Impostazioni per Lingua e Tema.
- **v0.2.8**: **Resilienza riproduzione in background** (promozione proattiva a Foreground Service, canale notifica dedicato, `Media3BitmapLoader` con `loadThumbnail` e guida bilingue in Impostazioni per l'ottimizzazione batteria OEM), **disambiguazione anti-omonimia** dei profili artista tramite incrocio con brani/album locali, supporto Auto-Tagger al formato **`Artista - Canzone`** e paginazione album Deezer fino a 300 tracce, e **allineamento alfabetico** condiviso tra ordinamento e Fast Scroller.
- **v0.2.4**: **Ritaglio 1:1 interattivo e Zoom con blur** per le copertine delle playlist (`PlaylistCoverCropperDialog`, `CoverZoomDialog`), esportazione/ripristino delle **copertine nel backup** (`coverBase64`), **modifica diretta del singolo brano** dalla modalità *Uniforma/Separa Album*, opzione dedicata per `Album Artist` e archivio persistente deduplicato SHA-1 **`TaggedCoverStore`** per copertine immuni alle invalidazioni `MediaStore`.
- **v0.2.3**: **Backup e Ripristino locale JSON** tramite Storage Access Framework (playlist, preferiti, statistiche, scrobble pendenti e preferenze non sensibili con matching portabile delle tracce), architettura modulare a **Coordinatori** (`LibraryCoordinator`, `QueueCoordinator`), client HTTP condiviso (`SharedHttpClient`) ed equalizzatore Bass Boost con architettura ibrida.
- **v0.2.2**: **Biografie autorevoli Last.fm** con disambiguazione multi-artista, **effetto "Peek" sfumato** con pillola speculare di espansione/compressione oltre i 10 brani nel profilo artista, **cambio lingua istantaneo in-place** senza ricreazione dell'Activity (zero flash al cold start) e **parser bilingue `ReleaseNotesParser`** per le note di rilascio GitHub.
- **v0.2.1**: **Profili Artista avanzati** (foto HD Deezer, anteprima biografia cliccabile, dialogo biografia a schermo intero, personalizzazione manuale foto/bio con `EditArtistSheet`, gestione collaborazioni/duo, card *"Sull'artista"* nel player full-screen e sezione *"Artisti trovati"* nella ricerca globale), **Fast Scroller** a 60/120 FPS con bolla alfabetica e **Swipe-to-Queue** nei profili artista.

---

## ✨ Cosa fa l'App — Funzionalità Principali

### 🎧 1. Esperienza di Ascolto, Player a Doppio Livello & Resilienza Background
- **Mini-Player Fluttuante a Pillola (`PlayerBottomBar`)**:
  - Design compatto con bordo luminescente viola neon, ombra morbida e titoli scorrevoli continui (`basicMarquee`).
  - **Seekbar a filo superiore con Floating Time Badge**: barra di avanzamento integrata nel profilo superiore della pillola che si espande dinamicamente al tocco mostrando un cursore luminoso e un badge rialzato sopra il dito per visualizzare il minutaggio esatto durante lo scrubbing.
  - **Gesti rapidi**: carosello interattivo a scorrimento (`HorizontalPager`) dell'intera pillola per passare al brano precedente o successivo con snap elastico su `settledPage`, swipe verso l'alto o tocco per aprire il player a schermo intero.
  - Pulsante rapido **`+`** per aggiungere subito il brano in ascolto a una playlist o ai preferiti, e pulsante circolare **Play/Pausa** con animazione di morphing.
- **Player a Schermo Intero (`FullScreenPlayerSheet`)**:
  - Sfondo dinamico immersivo a gradiente generato estraendo i colori dominanti della copertina del brano tramite **AndroidX Palette**.
  - Scorrimento orizzontale (`HorizontalPager`) tra le copertine dei brani in coda.
  - **Effetto respiro e profondità**: lieve animazione dinamica della copertina (scala `1.0f` in riproduzione ⇄ `0.92f` in pausa).
  - **Intestazione contestuale dinamica**: visualizza l'origine della riproduzione (*"IN RIPRODUZIONE DALL'ALBUM"*, *"DALLA PLAYLIST"*, *"DALL'ARTISTA"*, *"DALLA LIBRERIA"*); toccandola si naviga istantaneamente alla schermata d'origine, mentre toccando il nome dell'artista si apre la scheda monografica dedicata.
  - **Card "Sull'artista"**: scorrendo verso il basso nel player a schermo intero si rivela una card elegante con la foto profilo dell'artista, l'estratto della biografia e l'accesso diretto alla sua scheda monografica.
  - **Ripristino persistente della sessione (Cold Start)**: all'avvio a freddo dell'app viene ripristinato fedelmente in stato di pausa l'ultimo brano ascoltato, il minutaggio esatto, la coda e il contesto attivo (zero avvii audio inaspettati).
- **Riproduzione in Background Affidabile (`PlaybackService`, `Media3BitmapLoader`)**:
  - Promozione proattiva a Foreground Service (`ACTION_START_FOREGROUND`), canale di notifica dedicato (`musical_playback_channel`), `BackHandler` protetto per evitare interruzioni durante l'ascolto e caricamento copertine sicuro fino a `512px` tramite `ContentResolver.loadThumbnail`.
  - Sezione dedicata **"Riproduzione in background (Batteria)"** in Impostazioni con stato in tempo reale delle ottimizzazioni batteria, accesso diretto alle impostazioni di sistema e **guida bilingue passo-passo** per i dispositivi OEM.

### 📋 2. Coda Intelligente a Due Livelli, Sincronizzazione Ripeti/Shuffle & Autoplay
- **Separazione netta tra Coda Utente e Autoplay (`QueueCoordinator`)**:
  - **Coda Utente (Prioritaria)**: tutti i brani, album o playlist aggiunti tramite *"Aggiungi alla coda"* vengono inseriti subito dopo la traccia corrente (`currentIndex + 1`) con priorità assoluta. Se vuota, la sezione si nasconde automaticamente.
  - **Riproduzione Automatica (Rolling Window di 15 brani)**: mantiene costantemente pronti i 15 brani successivi, reintegrando automaticamente una nuova traccia ogni volta che un brano viene riprodotto o rimosso.
  - **Sincronizzazione 1:1 con Ripeti e Shuffle**: attivando la ripetizione (`REPEAT_MODE_ALL` o `REPEAT_MODE_ONE`) o lo Shuffle, la lista di Riproduzione Automatica nella coda riflette al 100% l'esatta sequenza di riproduzione dei brani successivi (incluso il riavvolgimento ciclico dell'album o della playlist).
- **Controllo totale in `QueueBottomSheet`**:
  - Riordinamento fluido tramite **Drag & Drop**, pulsante **`↑`** per portare istantaneamente qualsiasi traccia in cima alla sequenza dei successivi, pulsante **`X`** e gesti di swipe orizzontali per rimuovere o promuovere elementi.
  - Card del brano in riproduzione dotata di equalizzatore grafico animato disegnato su GPU Canvas (`AnimatedEqualizerBars`).
- **Swipe-to-Queue in Album, Playlist, Ricerca & Artista**:
  - Trascinando un brano verso sinistra (`SwipeToQueueTrackItem`) lo si accoda all'istante con animazione elastica e feedback visivo.

### 💜 3. Playlist Personalizzate, Crop/Zoom Copertine, Drag & Drop e "Brani preferiti"
- **Playlist di sistema permanente "Brani preferiti" (`Favorite songs`)**:
  - Sempre presente, fissata automaticamente al primo posto (`indice 0`) con qualsiasi criterio di ordinamento, protetta contro l'eliminazione accidentale e dotata di copertina dedicata immutabile.
  - Voce dinamica nel menu contestuale a tre puntini (`⋮`) di qualsiasi brano: mostra *"Aggiungi ai brani preferiti"* con cuore vuoto oppure *"Aggiunto ai brani preferiti"* con cuore pieno / *"Rimuovi dai brani preferiti"*.
- **Playlist create dall'utente**:
  - Creazione, eliminazione, ridenominazione e persistenza atomica locale sicura (`playlists.json`).
  - **Riordinamento manuale dei brani**: maniglia Drag & Drop touch in `PlaylistDetailView` per riordinare liberamente la scaletta.
  - **Copertina personalizzata con Ritaglio & Zoom (`PlaylistCoverCropperDialog`, `CoverZoomDialog`)**: selezione tramite il **Photo Picker di Android 16**, editor in-app con ritaglio 1:1, zoom e rotazione per centrare l'immagine prima del salvataggio, e visualizzatore a schermo intero con pinch-to-zoom, doppio tocco e sfondo sfocato.

### 🎚️ 4. Equalizzatore Audio DSP Ibrido, Sleep Timer, Statistiche & Profilo Artista Avanzato
- **Equalizzatore Audio ed Effetti Sonori (`EqualizerDialog`, `EqualizerManager`)**: equalizzatore hardware a 5 bande (-15 dB / +15 dB), preset musicali integrati (Flat, Rock, Pop, Jazz, Hip Hop, Elettronica, Classica, Bass Boost, Voce), **Bass Boost ibrido** (effetto hardware combinato con boost acustico low-shelf sulle basse frequenze) e spazializzatore 3D (**Virtualizer**), con persistenza autonoma in `AppPreferences`.
- **Sleep Timer con Dissolvenza Volume (`SleepTimerDialog`)**: spegnimento programmato con preset rapidi (15, 30, 45, 60 min), slider personalizzato fino a 120 min o opzione *"Al termine del brano"*, con dissolvenza progressiva del volume (fade-out morbido negli ultimi 15 secondi).
- **Dashboard Statistiche d'Ascolto (`PlaybackStatsDialog`)**: riepilogo del tempo totale d'ascolto, ascolti complessivi e brani unici esplorati, con classifiche interattive Top 10 Brani, Top 5 Artisti e Top 5 Album.
- **Scheda Monografica Artista Avanzata (`ArtistDetailSheet`, `ArtistBiographyDialog`, `EditArtistSheet`)**:
  - **Foto HD & Biografie Enciclopediche Anti-Omonimia**: recupero automatico di foto profilo in alta risoluzione da **Deezer** (1000×1000px) e biografie da **Last.fm** e **Wikipedia** (`ArtistInfoRepository`), con disambiguazione anti-omonimia tramite incrocio dei brani/album locali e filtro automatico contro voci dedicate a singoli brani o album, oltre a cache atomica locale (`artist_cache.json`).
  - **Anteprima Biografia & Lettura Full-Screen**: anteprima a 2 righe sotto il nome artista cliccabile per aprire `ArtistBiographyDialog` con testo integrale e fonte informativa.
  - **Personalizzazione Manuale (`EditArtistSheet`)**: possibilità di scegliere una foto dalla Galleria o dalle copertine album locali e modificare liberamente la biografia con memorizzazione permanente protetta da sovrascritture di rete.
  - **Gestione Collaborazioni & Effetto "Peek"**: separazione automatica dei progetti in duo dai profili solisti con sezione dedicata *"Collaborazioni"*, carosello album e lista brani con effetto "peek" sfumato e pillola speculare di espansione/compressione oltre i 10 brani.

### 🗂️ 5. Libreria Locale Multi-Sorgente, Fast Scroller Alfabetico & Ricerca Globale
- **Selezione Multi-Sorgente (`AudioSourceMode`)**:
  - **Scansione Intelligente** (predefinita): analizza lo storage sfruttando i flag nativi MediaStore Android 16 (`IS_RECORDING`, `IS_RINGTONE`, `IS_NOTIFICATION`, `IS_ALARM`, `IS_PODCAST`, `IS_AUDIOBOOK`), escludendo directory di note vocali e registrazioni OEM/IT/EN anche se annidate sotto `Music/`, estensioni dedicate (`.amr`, `.3gp`, `.3ga`, `.awb`, `.qcp`, `.spx`), pattern di nomi/timestamp tipici di registratori e applicando una doppia soglia di durata (`<15s` e `>20min` senza metadati musicali).
  - **Cartella Music**: interrogazione circoscritta a `Music/` e relative sottocartelle.
  - **Tutto**: tutti i file audio indicizzati senza restrizioni.
  - **Cartelle a scelta**: selezione granulare di directory tramite Storage Access Framework (`OpenDocumentTree`).
- **Instant-On Library Cache**: avvio a zero latenza (<15ms) tramite `LibraryCacheRepository` e pattern *Stale-While-Revalidate*.
- **Navigazione fluida a 3 schede (`Brani`, `Album`, `Playlist`)**:
  - Swipe orizzontale tra schede principali con `HorizontalPager`, ritorno interattivo con swipe verso destra dal dettaglio album e **Collapsing Header** compatto a zero jank.
  - **Barra di Scorrimento Rapido (`FastScrollbar`)**: barra reattiva a 60/120 FPS disegnata su GPU Canvas nelle schede Brani, Album e Playlist, con dissolvenza automatica dopo 1,5s e **indicatore alfabetico flottante** sincronizzato con l'ordinamento (`A-Z` / `Z-A` e gruppo `#`).
  - **Pulsante fluttuante "Torna in alto"**: compare fluidamente dopo aver scorso la lista per risalire all'istante con un tocco.
- **4 Criteri di Ordinamento con direzione invertibile (`↑` / `↓`)**:
  1. *Ascoltati di recente*
  2. *Più ascoltati* (con badge contatore ascolti visibile)
  3. *Aggiunti di recente*
  4. *Alfabetico (`A-Z` / `Z-A`)*
- **Ricerca Globale in tempo reale**: barra unificata con sezione prioritaria **"Artisti trovati"** in cima ai risultati (conteggio brani/album e accesso al profilo), brani con swipe-to-queue, album e playlist.

### 🔍 6. Gestione Intelligente dei Brani Duplicati (Confronto, Pulizia & "Ignora Duplicati")
- **Rilevamento intelligente**: algoritmo di normalizzazione dei titoli (senza punteggiatura o suffissi rumore) e tolleranza di durata ($\le 5$s) per individuare file audio doppi all'interno dello stesso album.
- **Foglio di Confronto Dedicato (`AlbumDuplicatesSheet`)**:
  - Confronto fianco a fianco dei brani con badge *"Conserva"* / *"Elimina"*, metadati tecnici completi (percorso file, bitrate, frequenza di campionamento e dimensione).
  - Ascolto dell'anteprima audio in tempo reale.
  - Eliminazione fisica sicura dallo storage tramite `MediaStore.createDeleteRequest` (Android 16 Scoped Storage).
- **Funzione "Ignora file duplicati" (Ignore Duplicates)**:
  - Possibilità di contrassegnare come ignorati i file duplicati segnalati (per singolo gruppo o per l'intero album) con persistenza in `ignored_duplicate_keys` e ripristino in qualsiasi momento dal menu dell'album o dalle Impostazioni.

### 🏷️ 7. Smart Auto-Tagger, Metadati HD & Testi Sincronizzati (Karaoke)
- **Identificazione e Arricchimento ad Alta Precisione**:
  - Interrogazione concorrente delle API pubbliche di **Deezer** (con paginazione tracklist fino a 300 tracce e fallback autoritativo `/track/{id}`) e **LRCLIB** per il recupero di metadati canonici, anno, genere, numero traccia/disco e copertine HD a 1000×1000px.
  - Sanitizzazione avanzata (`QuerySanitizer`) per rimuovere tag rumorosi (`[Official Video]`, bitrate, prefissi traccia) e separare automaticamente il pattern **`Artista - Canzone`** (inclusi trattini tipografici e suffissi `- Topic`).
  - **Unificazione `Album Artist` Unico**: estrazione automatica del solo artista principale su `ALBUM_ARTIST` (`extractPrimaryAlbumArtist`) mantenendo tutti i collaboratori su `ARTIST`, con allineamento automatico della scaletta (`alignAlbumTracks`) per evitare frammentazioni degli album.
- **Scrittura Atomica su Scoped Storage & `TaggedCoverStore`**:
  - Modifica fisica dei frame ID3/Vorbis/FLAC/MP4 e inserimento copertina JPEG embedded tramite `jaudiotagger` Android e `MediaStore.createWriteRequest`.
  - Archivio locale persistente deduplicato SHA-1 (`TaggedCoverStore` in `filesDir/tagged_covers/`) per garantire visualizzazione immediata delle copertine scritte dall'Auto-Tagger, immune dalle invalidazioni di `MediaStore` su Android 16.
- **Interfaccia Interattiva (`MetadataEnricherSheet`)**:
  - Confronto visivo "Prima vs Dopo", selezione copertina da galleria (Photo Picker), Quick-Edit (inclusa modifica dedicata dell'`Album Artist`) con **Cursor Scrubbing** orizzontale, ricerca manuale personalizzata e modalità batch per interi album.
  - **Modifica Singolo Brano dalla Vista Album**: icona matita su ogni riga in *Uniforma/Separa Album* per rifinire il singolo brano e tornare automaticamente alla scaletta aggiornata.
  - **Rilevamento e Separazione Intelligente Album Misti**: banner contestuale con opzioni per separare file aggregati erroneamente o uniformarli in un unico album.
- **Visualizzazione Testi Sincronizzati (Karaoke)**:
  - Vista dedicata [`SyncedLyricsView`](app/src/main/java/com/example/musicplayer/ui/components/SyncedLyricsView.kt) integrata nel player a schermo intero con auto-scroll dinamico centrato, compensazione latenza, tap-to-seek interattivo, isolamento dei gesti verticali (`LyricsVerticalScrollBlocker`) per evitare chiusure accidentali durante lo scorrimento (anche su brani strumentali), regolazione fine offset e salvataggio locale dei file `.lrc` in `filesDir/lyrics/`.

### 🔴 8. Integrazione Last.fm Avanzata (Scrobbling, Cache Offline, Avatar GIF & Chiavi Inline)
- **Autenticazione & Scrobbling automatico**:
  - Login diretto in-app (`auth.getMobileSession`) con firma crittografica MD5 conforme alle specifiche Last.fm e salvataggio sessione protetto con `AndroidKeyStore`.
  - Invio dello stato *"Now Playing"* all'avvio e scrobble al raggiungimento del **50% della durata** (o dopo **4 minuti** di ascolto).
- **Profilo Utente con Avatar Animati (GIF Hardware)**:
  - Visualizzazione dell'avatar utente Last.fm con supporto nativo sia per immagini statiche sia per **GIF animate** tramite decodifica hardware (`ImageDecoder` + `AnimatedImageDrawable`).
- **Configurazione Chiavi API Inline**:
  - Possibilità di inserire o modificare `API Key` e `Shared Secret` direttamente dalla finestra del profilo Last.fm, anche a sessione già attiva, senza dover effettuare il logout.
- **Resilienza Offline (Room Database)**:
  - In assenza di connessione Internet, gli scrobble vengono salvati automaticamente in un database SQLite locale (`scrobble_cache.db`) e inviati in background non appena il dispositivo torna online (`ConnectivityManager.NetworkCallback`).

### 🌐 9. Supporto Multilingua Nativo (Italiano & English) & Selettori Rapidi
- **Supporto Per-App Language nativo per Android 16**:
  - Integrazione completa con `android.app.LocaleManager` e `res/xml/locales_config.xml`.
  - Card selettore a 3 tile orizzontali (`SettingsTileSelectorCard`) direttamente in **Impostazioni** per scegliere **Lingua dell'applicazione** (*Sistema*, *Italiano*, *English*) e **Aspetto** (*Scuro*, *Chiaro*, *Sistema*).
  - **Cambio lingua seamless in-place** (`android:configChanges`) senza riavvio dell'Activity né schermate nere al cold start (`windowBackground` allineato al tema).
  - Traduzione dinamica della playlist di sistema permanente (*"Brani preferiti"* $\leftrightarrow$ *"Favorite songs"*).

### 💾 10. Backup e Ripristino Locale Sicuro (`BackupRepository`)
- **Esportazione e Importazione JSON (SAF)**: direttamente da **Impostazioni**, esporta e importa un file JSON locale tramite Storage Access Framework (`CreateDocument` / `OpenDocument`).
- **Contenuto Completo & Portabile**: salva playlist personalizzate (comprese le **copertine personalizzate in Base64**), *"Brani preferiti"*, statistiche d'ascolto, scrobble pendenti in Room e preferenze non sensibili.
- **Riaggancio Intelligente Senza Duplicati**: durante il ripristino, le tracce vengono abbinate su nuovi percorsi/dispositivi tramite `relativePath + displayName` e fallback `title + artist + durationMs` (±2s), preservando la sicurezza delle credenziali Last.fm (sempre escluse dal backup).

### 🔔 11. Verifica Aggiornamenti In-App & Note di Rilascio Bilingue in Markdown Nativo
- **Verifica Automatica e Manuale**: controllo asincrono delle release ufficiali da GitHub (`PublicGiu/MusiCal`).
- **Dialog Note di Rilascio (`UpdateAvailableDialog`, `ReleaseNotesParser`)**: estrazione automatica delle note nella lingua attiva (Italiano o Inglese), rendering tramite parser Markdown nativo in puro Kotlin (`MarkdownContent`) e pulsanti orizzontali su singola linea per download diretto dell'APK o opzione *"Ignora"*.

---

## 🛠️ Stack Tecnologico e Architettura

| Livello | Tecnologie Utilizzate |
| :--- | :--- |
| **Sistema Operativo Target** | **Android 16** (`compileSdk = 36`, `minSdk = 36`, `targetSdk = 36`) |
| **Linguaggio & Toolchain** | Kotlin `2.1.0`, Java `21`, Android Gradle Plugin `8.8.0`, KSP `2.1.0-1.0.29`, Gradle `8.11.1` |
| **UI & Design System** | Jetpack Compose (BOM `2025.01.00`), Material 3, Edge-to-Edge nativo, AndroidX Palette |
| **Audio & Background** | AndroidX Media3 `1.5.1` (`ExoPlayer`, `MediaSessionService`, `MediaController`, `ForwardingPlayer`, custom `Media3BitmapLoader`) |
| **Tagging Audio & Metadati** | `jaudiotagger` Android `2.2.3` (LGPL v2.1 compatibile, privo di AWT), `androidx.exifinterface:exifinterface:1.4.2` |
| **Persistenza Locale & Backup** | `AtomicFile` JSON (`playlists.json`, `track_stats.json`, `artist_cache.json`), `LibraryCacheRepository` (`library_cache.json`), `TaggedCoverStore` (SHA-1), Backup/Ripristino JSON portabile SAF, Room `2.6.1` (`scrobble_cache.db`), `SharedPreferences` |
| **Immagini & Rete** | Coil Compose `2.7.0`, `SharedHttpClient` (OkHttp `4.12.0`: Deezer API, LRCLIB API, Wikipedia/MediaWiki API, Last.fm REST API, GitHub API) |
| **Localizzazione** | Android 16 Per-App Language (`LocaleManager`), `strings.xml` (Italiano) e `values-en/strings.xml` (Inglese) |
| **Test Automatizzati** | JUnit 4, Mockito Core, `org.json`, suite completa di **373 unit test** su JVM |

```
app/src/main/java/com/example/musicplayer/
├── MainActivity.kt                         # Single-Activity Edge-to-Edge, Material 3 e cambio lingua in-place
├── data/
│   ├── AudioTrack.kt / Album.kt / Playlist.kt / TrackStats.kt / SortOption.kt / AudioSourceMode.kt
│   ├── AppLanguage.kt / PlaybackContext.kt # Modelli lingua supportata (IT, EN, System) e contesto riproduzione
│   ├── AppPreferences.kt                   # Tema, autoplay, lingua, equalizzatore, duplicati ignorati e sessione
│   ├── ArtistInfo.kt / ArtistSearchResult.kt / ArtistInfoRepository.kt  # Profili artista HD, bio anti-omonimia & cache
│   ├── AudioRepository.kt                  # Scansione MediaStore (Intelligente con filtri Android 16, Music/, Tutto, SAF)
│   ├── LibraryCacheRepository.kt           # Cache istantanea library_cache.json (pattern Stale-While-Revalidate)
│   ├── LibraryCoordinator.kt               # Coordinatore libreria, cache instant-on, ordinamenti e ricerca globale
│   ├── LyricsRepository.kt                 # Salvataggio, parsing ed offset dei testi sincronizzati .lrc
│   ├── PlaylistRepository.kt               # Persistenza atomica playlist e copertine ritagliate Photo Picker
│   ├── StatsRepository.kt                  # Persistenza atomica statistiche d'ascolto (AtomicFile)
│   ├── TaggedCoverStore.kt                 # Archivio locale SHA-1 per copertine Auto-Tagger immuni a MediaStore
│   ├── backup/                             # BackupModels e BackupRepository (export/import JSON SAF con coverBase64)
│   ├── lastfm/                             # LastFmClient, LastFmConfig, LastFmCoordinator, SessionManager & Room DB
│   ├── network/SharedHttpClient.kt         # Singleton OkHttpClient condiviso tra tutti i client HTTP
│   ├── tagger/                             # Client Deezer & LRCLIB, QuerySanitizer, AudioTagWriter, StorageWriteManager
│   └── update/                             # GithubUpdateChecker e ReleaseNotesParser (note di rilascio bilingue)
├── playback/
│   ├── PlaybackService.kt                  # MediaSessionService con ExoPlayer, Foreground proattivo e WakeLock
│   ├── QueueCoordinator.kt                 # Gestione Coda Utente prioritaria, Autoplay (15 brani) e Shuffle/Repeat 1:1
│   ├── EqualizerManager.kt                 # Equalizzatore hardware DSP 5 bande, Bass Boost ibrido e Virtualizer
│   ├── SleepTimerCoordinator.kt            # Coordinatore spegnimento programmato con fade-out volume (e SleepTimerState)
│   └── Media3BitmapLoader.kt               # Caricamento thumbnail MediaStore per notifiche e lock screen Android 16
└── ui/
    ├── MainScreen.kt                       # Scaffold con Collapsing Header zero-jank, HorizontalPager e swipe album
    ├── MusicViewModel.kt                   # Gestore di stato unificato che orchestra i coordinatori
    ├── components/                         # Componenti modulari Jetpack Compose
    │   ├── MainModalsHost.kt               # Host centralizzato disaccoppiato per tutti i dialoghi e sheet modali
    │   ├── PlayerBottomBar.kt              # Mini-player a pillola con seekbar a filo e Floating Time Badge
    │   ├── FullScreenPlayerSheet.kt        # Player immersivo con Palette, lyrics karaoke e card "Sull'artista"
    │   ├── QueueBottomSheet.kt             # Foglio della coda a due livelli (Coda Utente + Autoplay 15 brani)
    │   ├── AlbumDetailView.kt / AlbumsGridView.kt  # Scheda e griglia album con swipe-to-queue e rilevamento album misti
    │   ├── AlbumDuplicatesSheet.kt         # Confronto e gestione duplicati (anteprima, pulizia, ignora duplicati)
    │   ├── PlaylistsView.kt / PlaylistCoverCropperDialog.kt  # Playlist Drag & Drop, crop 1:1 e zoom blur copertine
    │   ├── ArtistDetailSheet.kt / ArtistBiographyDialog.kt / EditArtistSheet.kt  # Profilo artista, bio e editor
    │   ├── FastScrollbar.kt                # Fast Scroller GPU Canvas 60/120 FPS con indicatore alfabetico flottante
    │   ├── GlobalSearchResultsView.kt      # Ricerca globale live (Artisti trovati, Brani swipe-to-queue, Album, Playlist)
    │   ├── AnimatedEqualizerBars.kt        # Equalizzatore grafico animato su GPU Canvas
    │   ├── CollapsingScrollConnection.kt   # NestedScrollConnection riutilizzabile per collapsing header
    │   ├── LastFmAvatar.kt / LastFmDialog.kt  # Avatar GIF hardware, login e chiavi API Last.fm inline
    │   ├── SyncedLyricsView.kt             # Visualizzatore karaoke con isolamento gesti e auto-scroll centrato
    │   ├── SettingsDialog.kt               # Impostazioni con card tile Lingua/Tema, Batteria OEM, Sorgenti e Backup
    │   ├── EqualizerDialog.kt / SleepTimerDialog.kt / PlaybackStatsDialog.kt  # Controlli audio e statistiche
    │   ├── SortSelectorRow.kt / TrackListItem.kt  # Controlli ordinamento e riga brano riutilizzabile
    │   ├── UpdateAvailableDialog.kt        # Dialog aggiornamenti bilingue con anteprima changelog Markdown
    │   └── markdown/                       # Parser Markdown puro Kotlin per note di rilascio
    ├── tagger/                             # MetadataEnricherSheet e MetadataEnricherCoordinator (Auto-Tagger)
    └── theme/                              # Obsidian & Electric Violet, Tema Chiaro anti-glare e Dynamic Color
```

---

## 🔒 Sicurezza delle Chiavi API

Il repository e gli APK distribuiti sono concepiti per proteggere rigorosamente qualsiasi informazione riservata:
1. **Configurazione In-App (per chi scarica l'APK dalle Releases)**: Aprendo **Impostazioni → Last.fm Scrobbler** è possibile configurare o modificare direttamente la propria `API Key` e il proprio `Shared Secret` (generabili gratuitamente su [last.fm/api/account/create](https://www.last.fm/api/account/create)), anche a sessione già attiva, salvandoli localmente sul dispositivo protetti con **AndroidKeyStore** (ed esclusi automaticamente dai backup JSON).
2. **Configurazione in fase di compilazione (`local.properties`)**: Chi compila dai sorgenti può definire le chiavi in `local.properties` (escluso dal tracking Git) prendendo come modello il file [`local.properties.example`](local.properties.example).
3. Se non vengono fornite chiavi Last.fm, **tutte le funzionalità di lettore musicale locale, playlist, profili artista, auto-tagger, karaoke e backup rimangono pienamente attive e utilizzabili**.

---

## 📲 Download Diretto APK

Se desideri installare subito **MusiCal v0.2.9** sul tuo dispositivo Android 16 senza compilare il sorgente, scarica il file `.apk` direttamente dalla sezione [**Releases**](https://github.com/PublicGiu/MusiCal/releases/tag/v0.2.9) di questo repository:

👉 **[Scarica MusiCal-v0.2.9.apk](https://github.com/PublicGiu/MusiCal/releases/download/v0.2.9/MusiCal-v0.2.9.apk)**

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

5. **Esegui la suite di Test Unitari (373 test su JVM)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
