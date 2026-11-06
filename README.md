# 🎵 MusiCal — Lettore Musicale Nativo per Android 16

![Versione](https://img.shields.io/badge/versione-0.1.5-8B5CF6)
![Android](https://img.shields.io/badge/Android-16%20(API%2036)-7C3AED)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-A855F7)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-C084FC)
![Media3](https://img.shields.io/badge/Audio-AndroidX%20Media3%20(ExoPlayer)-6D28D9)

**MusiCal** è un lettore musicale locale moderno, veloce e privo di pubblicità o tracciamento, progettato nativamente ed esclusivamente per **Android 16 (API 36)**.

Realizzato interamente in **Kotlin** e **Jetpack Compose** con un'identità visiva **Obsidian & Electric Violet**, combina un motore audio solido basato su **AndroidX Media3 (ExoPlayer)**, animazioni fluide a zero-jank (60/120fps), gestione intelligente della coda di riproduzione, playlist personalizzabili con copertine proprie e integrazione nativa con **Last.fm** (inclusa sincronizzazione automatica offline).

---

## ✨ Cosa fa l'App — Funzionalità Principali

### 🎧 1. Esperienza di Ascolto e Player Doppio Livello
- **Mini-Player Fluttuante a Pillola (`PlayerBottomBar`)**:
  - Design compatto con bordo luminescente viola neon, ombra morbida e testi scorrevoli (`basicMarquee`).
  - **Seekbar a filo superiore**: barra di avanzamento bianca integrata nel profilo superiore della pillola che si espande dinamicamente al tocco mostrando un cursore luminoso e un **Floating Time Badge** rialzato sopra il dito per leggere il minutaggio preciso durante lo scrubbing.
  - **Gesti rapidi**: swipe orizzontale con ritorno elastico a molla per cambiare brano (precedente/successivo), swipe verso l'alto o tap per aprire il player a tutto schermo.
  - Pulsante rapido **`+`** per aggiungere subito il brano in ascolto a una playlist o ai preferiti, e pulsante circolare **Play/Pausa**.
- **Player a Schermo Intero (`FullScreenPlayerSheet`)**:
  - Sfondo dinamico immersivo generato estraendo i colori dominanti dalla copertina del brano tramite **AndroidX Palette**.
  - Scorrimento orizzontale (`HorizontalPager`) tra le copertine dei brani in coda.
  - Intestazione dell'album interattiva: toccando il nome dell'album in alto si salta direttamente alla pagina di dettaglio dell'album.

### 📋 2. Coda Utente Prioritaria & Autoplay Intelligente (15 Brani)
- **Separazione netta tra Coda Utente e Autoplay**:
  - **Coda Utente (Prioritaria)**: tutti i brani, album o playlist aggiunti tramite *"Aggiungi alla coda"* vengono inseriti subito dopo la traccia in riproduzione secondo un ordine FIFO naturale, avendo sempre precedenza assoluta. Se vuota, la sezione si nasconde automaticamente.
  - **Riproduzione Automatica (Finestra Rolling di 15 brani)**: mantiene costantemente pronti 15 brani successivi (proseguimento dell'album/playlist o selezione intelligente anti-ripetizione dalla libreria), reintegrando automaticamente un nuovo brano ogni volta che uno viene riprodotto o rimosso.
- **Controllo totale in `QueueBottomSheet`**:
  - Riordinamento fluido tramite **Drag & Drop**, pulsante **`↑`** per spostare istantaneamente qualsiasi traccia in cima alla coda e pulsante **`X`** per rimuoverla.
- **Swipe-to-Queue negli Album e nelle Playlist**:
  - Nelle viste di dettaglio di album e playlist (inclusa *"Brani preferiti"*), basta trascinare un brano verso sinistra con il dito per metterlo rapidamente in coda con resistenza elastica e feedback visivo animato.
- **Menu contestuale rapido (Long Press o `⋮`)**:
  - Puoi aprire le opzioni di qualsiasi brano sia toccando l'icona a tre puntini (`⋮`) sia tenendo premuto direttamente sulla riga del brano.

### 💜 3. "Brani preferiti" & Playlist con Copertina Personalizzata
- **Playlist di sistema "Brani preferiti"**:
  - Sempre presente e fissata automaticamente al primo posto (`indice 0`) con qualsiasi criterio di ordinamento, dotata di copertina dedicata e protetta contro l'eliminazione.
  - Dal menu a tre puntini (`⋮`) di qualsiasi brano (in Brani, Album, Playlist o Ricerca) puoi aggiungerlo o rimuoverlo rapidamente: quando il brano è già nei preferiti, la voce mostra **"Aggiunto ai brani preferiti"** con il **cuore pieno**.
- **Playlist create dall'utente**:
  - Creazione, eliminazione e gestione completa dei brani con persistenza atomica locale (`playlists.json`).
  - **Copertina personalizzata**: possibilità di scegliere un'immagine dalla galleria tramite il **Photo Picker di Android 16** sia al momento della creazione della playlist sia in seguito (toccando la copertina o dal menu `⋮` con *"Cambia copertina"* / *"Rimuovi copertina"*).

### 🗂️ 4. Libreria Locale, Ordinamenti Animati e Ricerca Globale
- **Scansione selettiva `Music/`**: interroga `MediaStore` filtrando esclusivamente la cartella `Music/` e le sue sottocartelle, ignorando note vocali, suonerie o notifiche di altre app.
- **Navigazione fluida a 3 schede (`Brani`, `Album`, `Playlist`)**:
  - Supporto allo swipe orizzontale tra le schede e **Collapsing Header** pilotato direttamente nelle fasi di Layout e Draw (zero ricomposizioni durante lo scroll).
  - **Viste di dettaglio compatte**: sia gli Album sia le Playlist adottano un'intestazione compatta su singola riga con copertina adattiva (`62dp → 44dp`), titolo scorrevole e pulsante Play circolare.
- **4 Criteri di Ordinamento con direzione invertibile (`↑` / `↓`)**:
  1. *Ascoltati di recente*
  2. *Più ascoltati* (con contatore ascolti visibile su ogni traccia)
  3. *Aggiunti di recente*
  4. *Alfabetico (`A-Z` / `Z-A`)*
- **Ricerca Globale in tempo reale**: filtra istantaneamente brani, album e playlist in un'unica vista categorizzata.

### 🔴 5. Scrobbling Nativo su Last.fm con Cache Offline
- **Autenticazione integrata**: login diretto nell'app (`auth.getMobileSession`) con firma crittografica MD5 conforme alle specifiche ufficiali Last.fm.
- **Now Playing & Scrobble**: aggiorna lo stato *"In riproduzione"* all'avvio di ogni traccia e invia lo scrobble al raggiungimento del **50% della durata** (o dopo **4 minuti** di ascolto).
- **Resilienza Offline (Room Database)**: se ascolti musica senza connessione Internet, gli scrobble vengono salvati automaticamente in un database SQLite locale (`scrobble_cache.db`) e inviati in automatico non appena il dispositivo torna online (`ConnectivityManager.NetworkCallback`).

---

## 🛠️ Stack Tecnologico e Architettura

| Livello | Tecnologie Utilizzate |
| :--- | :--- |
| **Sistema Operativo Target** | **Android 16** (`compileSdk = 36`, `minSdk = 36`, `targetSdk = 36`) |
| **Linguaggio & Build** | Kotlin `2.1.0`, Java `21`, Android Gradle Plugin `8.8.0`, KSP |
| **UI & Design System** | Jetpack Compose (BOM `2025.01.00`), Material 3, Edge-to-Edge nativo, AndroidX Palette |
| **Audio & Background** | AndroidX Media3 `1.5.1` (`ExoPlayer`, `MediaSessionService`, `MediaController`, `ForwardingPlayer`, custom `Media3BitmapLoader`) |
| **Persistenza Locale** | `AtomicFile` JSON (`playlists.json`, `track_stats.json`), Room Database `2.6.1` (`scrobble_cache.db`), `SharedPreferences` |
| **Immagini & Rete** | Coil Compose `2.7.0`, OkHttp `4.12.0` |
| **Test Automatizzati** | JUnit 4, Mockito Core, suite di **224 unit test** su JVM |

```
app/src/main/java/com/example/musicplayer/
├── MainActivity.kt                         # Single-Activity Edge-to-Edge e tema Material 3
├── data/
│   ├── AudioTrack.kt / Album.kt / Playlist.kt / TrackStats.kt / SortOption.kt
│   ├── AudioRepository.kt                  # Query MediaStore filtrata sulla cartella Music/
│   ├── PlaylistRepository.kt               # Persistenza atomica playlist e copertine personalizzate
│   ├── StatsRepository.kt                  # Persistenza atomica contatori e timestamp d'ascolto
│   ├── AppPreferences.kt                   # Tema (Sistema/Chiaro/Scuro), Dynamic Color e Autoplay
│   └── lastfm/                             # Client REST Last.fm, firma MD5 e cache offline Room
├── playback/
│   ├── PlaybackService.kt                  # MediaSessionService con ExoPlayer, AudioFocus e WakeLock
│   └── Media3BitmapLoader.kt               # Caricamento miniature per notifiche e lock screen Android 16
└── ui/
    ├── MainScreen.kt                       # Scaffold con Collapsing Header zero-jank e HorizontalPager
    ├── MusicViewModel.kt                   # Gestione stato, Coda Utente, Autoplay (15 brani) e Scrobbling
    ├── components/                         # PlayerBottomBar, FullScreenPlayerSheet, QueueBottomSheet,
    │                                       # AlbumDetailView, PlaylistsView, TrackListItem, SettingsDialog...
    └── theme/                              # Palette Obsidian & Electric Violet e armonizzazione Material You
```

---

## 🔒 Sicurezza delle Chiavi API

Il repository e gli APK distribuiti sono progettati per non esporre mai credenziali o chiavi segrete:
1. **Configurazione in-app (per chi scarica l'APK dalle Releases)**: Aprendo **Impostazioni → Last.fm Scrobbler** è possibile inserire direttamente la propria `API Key` e il proprio `Shared Secret` (creabili gratuitamente su [last.fm/api/account/create](https://www.last.fm/api/account/create)), che vengono salvati localmente sul dispositivo.
2. **Configurazione in fase di build (`local.properties`)**: Per chi compila da sorgente, le chiavi possono essere inserite nel file locale `local.properties` (escluso da Git tramite `.gitignore`) usando il template [`local.properties.example`](local.properties.example).
3. Se non vengono configurate chiavi Last.fm, **l'applicazione funziona regolarmente** in tutte le sue funzioni di lettore musicale locale.

---

## 📲 Download Diretto APK

Se vuoi installare subito **MusiCal** sul tuo dispositivo Android 16 senza compilare il codice sorgente, scarica il file `.apk` dalla sezione [**Releases**](https://github.com/PublicGiu/MusiCal/releases) di questo repository.

---

## 🚀 Come Compilare ed Eseguire il Progetto

### Prerequisiti
- **Android Studio** (Ladybug o successivo) con **Android 16 SDK (API 36)**.
- **JDK 21**.
- Un dispositivo fisico o emulatore con **Android 16**.

### Installazione Rapida

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
   Apri `local.properties` e inserisci il percorso del tuo Android SDK e, se desideri usare lo scrobbling Last.fm, le tue chiavi API gratuite (ottenibili su [last.fm/api/account/create](https://www.last.fm/api/account/create)):
   ```properties
   sdk.dir=C\:\\Users\\TUO_UTENTE\\AppData\\Local\\Android\\Sdk

   # Opzionale: credenziali API Last.fm
   lastfm.apiKey=LA_TUA_API_KEY
   lastfm.sharedSecret=IL_TUO_SHARED_SECRET
   ```

3. **Compila o installa l'APK Debug**:
   ```powershell
   .\gradlew.bat assembleDebug
   # oppure per installare direttamente sul dispositivo collegato:
   .\gradlew.bat installDebug
   ```

4. **Esegui la suite di Test Unitari (224 test su JVM)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
