# CLAUDE.md — Guía del proyecto Musarum v2

> **Léeme antes de hacer nada**. Este fichero es el handoff entre conversaciones de Claude Code. Cada nueva sesión debe leerlo primero para no re-explicar contexto. El usuario (Carlos) y Claude lo mantienen actualizado conforme avanza el proyecto.

---

## 1. QUÉ ES MUSARUM

**Musarum v2** es un reproductor de música auto-alojado — equivalente privado a Spotify / YT Music para una red pequeña de 4 usuarios como máximo. Construido desde cero por Carlos, sin ánimo comercial.

- **Escala**: 4 usuarios, biblioteca inicial de ~549 canciones, crecerá hasta ~7000.
- **Red**: Tailscale (nada expuesto a internet público). El backend corre en un portátil viejo del dueño.
- **Despliegue previsto**: Docker + Watchtower + GitHub Actions. Aún no desplegado.
- **No se paga nada**. No Google Play. Distribución de APK vía GitHub Releases (y en el futuro Obtainium o diálogo in-app `/version`).

El dueño del portátil es el **OWNER** (puede gestionar usuarios, carpetas, borrar canciones). Los demás son **MEMBER**.

---

## 2. STACK

### Backend (`backend/`)
- **Spring Boot 4.0.8**, Java 21, Maven.
- **PostgreSQL 17** en Docker (único contenedor desplegado hoy, en `docker-compose.yml` en raíz).
- **Flyway** gestiona el schema (migraciones versionadas V1..Vn). `ddl-auto=validate`.
- **Spring Security + JWT** propio (`JwtService`, `JwtAuthFilter`, `AuthService`). El JWT lleva nick + role.
- **jaudiotagger 3.0.1** para leer tags ID3.
- **springdoc-openapi** → Swagger en `/api/v1/swagger-ui.html`.
- **context-path**: `/api/v1`.
- **Spring Modulith**: **FUERA**. Lo quitamos en bloque 1. No volver a meterlo.

### Emendatium (`emendatium/`)
- Otro Spring Boot **independiente**, hermano del backend. Vive aparte, cliente HTTP entre ellos.
- Hace enriquecimiento pesado: AcoustID (ChromaprintClient), MusicBrainz, Discogs, Deezer, LastFM, LrcLib, CoverArtArchive, TheAudioDB, BPM/key local (FFT), ReplayGain, tag writing, file renaming.
- **No desplegado todavía**. Cuando sea, va como contenedor aparte. `EMENDATIUM_BASE_URL` en `.env` lo activa; sin eso, el backend sigue funcional (sin enriquecimiento auto).
- `EmendatiumClient` en backend hace fire-and-forget.

### Mobile (`mobile/`)
- **Kotlin + Jetpack Compose + Material 3**.
- **Media3 1.5.1** (ExoPlayer + MediaSession).
- **Retrofit + Gson**, **Coil3** para imágenes, **androidx.palette** para color dominante del cover, **Navigation Compose**.
- Compose BOM `2026.02.01`.
- Gradle AGP 9.1.1.
- Package: `com.musarum.mobile`.

### Legacy / no tocar
- `musarumViejo/` → carpeta **read-only** histórica. **Nunca modificar**. Carlos la borrará él mismo cuando v2 esté 100% operativo.

---

## 3. ARQUITECTURA DE ALTO NIVEL

```
┌────────────────────────────────┐
│   Mobile (Android, Kotlin)     │
│   - MediaController cliente    │
│   - PlayerController (+aux)    │
└──────────┬─────────────────────┘
           │ Retrofit + JWT
           ↓
┌────────────────────────────────┐    HTTP     ┌─────────────┐
│   Backend (Spring Boot)        │ ─────────→  │ Emendatium  │
│   /api/v1/*                    │             │ (opcional)  │
│   JWT auth, Flyway, Modulith=0 │             └─────────────┘
└──────────┬─────────────────────┘
           │ JPA
           ↓
     ┌──────────┐
     │ Postgres │
     └──────────┘

Carpeta paraguas (MUSARUM_MUSIC_PATH):
  ├── carpeta1/*.mp3
  ├── carpeta2/*.flac
  ├── _covers/            ← auto-gestionada por el backend
  ├── _lyrics/            ← .lrc generados
  ├── _profilepictures/   ← avatars subidos
  └── _videos/            ← vídeos .mp4 asociados a canciones
```

### Modelo de datos (tablas Postgres)
- `users` — nick (UK), password (BCrypt), user_role (OWNER/MEMBER), avatar_url, blocked, timestamps.
- `artists` — name, name_key (normalizado para dedup), mb_id, country, biography, image_url, begin_year, type, sort_name.
- `albums` — title, title_key, release_year, cover_url, mb_id, type, artist_id (FK).
- `genres` — name (UK).
- `songs` — title, audio_path (UK desde V2), content_hash (UK), bpm, musical_key, replay_gain, year, bitrate, sample_rate, channels, format, file_size, disc_number, composer, isrc, mb_id, lyrics_path, album_id, genre_id.
- `song_artists` — N:M songs ↔ artists.
- `playlists` — name, description, is_public, owner_id.
- `playlist_entries` — playlist_id, song_id, position. UK (playlist_id, song_id).
- `playlist_collaborators` — N:M playlists ↔ users.
- `favorites` — user_id, song_id. UK (user_id, song_id).
- `reproductions` — user_id, song_id, timestamps. Índices sobre (user_id, created_at) y (user_id, song_id).
- `library_folders` — path (UK), active. Sub-carpetas que escanea el scanner, gestionables desde la app.
- `flyway_schema_history` — gestionada por Flyway.
- `event_publication` — HUÉRFANA (era de Modulith, no se usa, sigue en la DB real pero no en V1).

### Entidades Java
Todas extienden `BaseEntity` (id BIGINT identity + createdAt + updatedAt con `AuditingEntityListener`).

### Módulos (paquetes) del backend
```
musarum.backend.
├── album/        — Album, AlbumController, AlbumService, AlbumRepository, mapper, DTOs
├── artist/       — igual patrón
├── auth/         — AuthController, AuthService, InvalidCredentialsException
├── enrichment/   — Cliente a Emendatium + LrcLibClient (letras directas sin pasar por emendatium), LyricsWriter
├── favorite/     — FavoriteChecker, FavoriteRepository (public), ...
├── genre/
├── library/      — LibraryFolderService, LibraryFolderController (/admin/folders), LibraryBootstrap
├── playlist/     — Playlist, PlaylistEntry, PlaylistController, PlaylistEntryRepository (public)
├── reproduction/ — Reproduction, ReproductionController (/me/plays), stats
├── scanner/      — LibraryScanner, LibraryWatcher, FileHasher, TagReader, CoverExtractor, ScannerStatus
├── search/       — SearchService, SearchController
├── shared/       — BaseEntity, CoverStorage, AvatarStorage, VideoStorage, Normalizer, ConsoleDesign,
│                   JpaConfig, GlobalExceptionHandler, config/{SecurityConfig,AsyncConfig},
│                   security/{JwtService,JwtAuthFilter,CurrentUserResolver,UnauthenticatedException}
├── song/         — Song, SongController, SongService, SongDeletionService, LyricsLookupService,
│                   SongMapper, SongRepository, DTOs
├── streaming/    — StreamingController (/stream/{songId}) sirve bytes del fichero
└── user/         — User, UserController, UserService, UserMapper, UserRole, UserStatsService,
                     OwnerBootstrap, DTOs
```

### Módulos del mobile
```
com.musarum.mobile.
├── AppContainer           — Grafo de dependencias simple sin DI framework
├── MainActivity           — Entry point; onStart/onStop llaman connect/disconnect del PlayerController
├── MusarumApplication
├── auth/                  — LoginScreen, LoginViewModel
├── data/
│   ├── auth/              — AuthRepository
│   ├── TokenManager       — Guarda JWT en EncryptedSharedPreferences
│   ├── library/           — LibraryFolderRepository
│   ├── music/             — Song/Album/Artist/Playlist/Favorite/Search/PlayHistory repositories
│   ├── network/           — ApiService (interface Retrofit), AuthInterceptor, UnauthorizedInterceptor, dto/
│   ├── preferences/       — PlayerPreferences (SharedPreferences: crossfade, replayGain, recentSearches)
│   └── user/              — UserRepository (incluye me StateFlow + avatar + admin users)
├── music/
│   ├── CoverImage, ArtistAvatar, ClickableArtists, CamelotBadge, GenreVisuals, LibraryStatsHeader,
│   │   AlphabetJumpBar, SongActionsSheet
│   ├── home/              — HomeScreen con GreetingHeader + QuickTiles diferenciados por color/forma
│   ├── albums/            — AlbumsScreen (scroll infinito), AlbumDetailScreen
│   ├── artists/           — ArtistsScreen (grid), ArtistDetailScreen
│   ├── favorites/
│   ├── genres/            — GenreDetailScreen
│   ├── library/           — LibraryScreen (favorites + playlists), PlaylistDetailScreen,
│   │                        CreatePlaylistDialog, AddToPlaylistDialog
│   ├── search/            — SearchScreen con filter chips 2x2
│   ├── SongsScreen        — sparse list con placeholders + AlphabetJumpBar
│   └── SongListViewModel  — gestiona lista sparse paginada
├── navigation/            — MusarumNavHost, Routes, MusarumBottomNav
├── player/                — PlayerController, PlayerService (MediaSessionService), NowPlayingScreen,
│                            LyricsScreen, InfoScreen, VideoScreen, MiniPlayer, ModePill,
│                            SleepTimerSheet, CamelotWheel, LrcParser, DominantCoverColor
├── profile/               — ProfileScreen (avatar clicable), ProfileViewModel,
│                            folders/LibraryFoldersScreen, users/UsersScreen
├── session/               — SessionViewModel (observa auth state)
└── ui/                    — EmptyView, ErrorView, LoadingView, GlassSurface, DurationFormat
```

---

## 4. CONVENCIONES Y PATRONES APRENDIDOS

### Backend
- **Package-private por defecto**. `interface`, `class`, `@Service`, `@Controller` → sin `public` salvo que se consuman cross-package. Si otro paquete necesita el bean, se marca `public` (ej. `FavoriteRepository`, `PlaylistEntryRepository` son `public interface` tras el bloque de borrar canciones).
- **DTOs como `record`**. Nunca entidades directas en API.
- **Nombres de endpoints**: todo bajo `/api/v1`. Admin solo-OWNER vive en el mismo controller pero bajo `/{resource}/admin/*` con `@PreAuthorize("hasRole('OWNER')")`.
- **@Transactional**: evita mezclar llamadas HTTP externas dentro de tx. Ejemplo: `LyricsLookupService` separa lectura DB (tx corta readOnly) + fetch LrcLib (sin tx) + persistir en tx corta.
- **Self-invocation en Spring**: llamar un método `@Transactional` desde la misma clase NO pasa por el proxy. Resuelto extrayendo a bean separado (ej. `LyricsLookupService`, `SongDeletionService`).
- **Scanner escalable**: hash parcial (SHA-256 primer 1 MB + ":" + size), NO del fichero entero. 10× menos IO.
- **Flyway migraciones**: V1 es baseline (NO se aplica a instalaciones existentes, se marcó manualmente con INSERT en `flyway_schema_history`). V2+ sí se aplican normalmente. Nombres: `V{n}__descripcion_corta.sql`.

### Mobile
- **No hay DI framework**. Todo pasa por `AppContainer(context)` instanciado en `MusarumApplication`. Repos y services se crean ahí.
- **ViewModels**: patrón sealed `UiState` (Loading/Ready/Error) + `MutableStateFlow`. Factory en companion object.
- **Bug recurrente — async+awaitAll crashea en viewModelScope.launch (no supervisor)**: 
  ```kotlin
  // ❌ MAL: si uno falla, el launch se cancela y la excepción llega al main → crash fatal
  viewModelScope.launch {
      runCatching { awaitAll(async { ... }, async { ... }) }
  }
  // ✅ BIEN: secuencial con try/catch, re-throw CancellationException
  viewModelScope.launch {
      try {
          val a = repo.a()
          val b = runCatching { repo.b() }.getOrDefault(emptyList())
          _state.value = Ready(a, b)
      } catch (ce: CancellationException) { throw ce }
      catch (t: Throwable) { _state.value = Error(t.message ?: "Error") }
  }
  ```
  Aplicado en GenreDetail, ArtistDetail, AlbumDetail, HomeViewModel, LibraryFoldersViewModel.
- **Scroll infinito + sparse list** (Canciones): `List<SongSummaryDto?>` dimensionado por `totalElements`, placeholders en los null, `ensurePageLoadedFor(absoluteIndex)` idempotente. Álbumes/Artistas usan patrón más simple (append).
- **AlphabetJumpBar**: recibe `Map<String, Int>` precomputado por el backend (`/songs/letter-offsets`), no la lista completa (que no cabría con 7000 canciones).
- **Compose Material 3 pitfall**: `SheetAction(fillMaxWidth)` dentro de Row con `Column(weight 1f)` → aplasta la Column. Resuelto en ActiveTimerRow con dos Columns en vertical.
- **Coil cache por URL completa**: al cambiar avatar, el URL debe cambiar o Coil sirve la foto vieja. Resuelto con cache-busting (`?v={timestamp}`) incrustado en `avatar_url` desde `AvatarStorage.saveAvatar`.

### Media3
- **MediaController** del cliente vs **MediaSessionService** del server (`PlayerService`). El `PlayerController` del mobile es wrapper alto nivel que gestiona cola, estado, crossfade, replaygain, sleep timer, radio.
- **`onTaskRemoved`**: SIEMPRE para y libera el player. Sin esto, Musarum seguía sonando tras barrer la app de recents.
- **ReplayGain**: aplicado como `controller.volume = min(1, 10^(dB/20))`. Nunca subimos por encima de 1. Toggle en Ajustes (default ON).
- **Crossfade real "estilo Spotify"**:
  1. En `remaining <= fadeMs + 2000ms` (pre-buffer): se crea ExoPlayer aux local con `setMediaSource(source, previousPos)` + `prepare()` + `playWhenReady = false`.
  2. Listener `onPlaybackStateChanged`: cuando aux pasa a `STATE_READY`, se dispara `armCrossfade`.
  3. `armCrossfade`: aux `playWhenReady = true` + principal `volume=0` + `seekToNextMediaItem()` + ramp cruzado.
  4. Al terminar ramp: `releaseAuxPlayer()` (stop, clearMediaItems, release).
  5. `togglePlayPause` propaga pause/resume al aux si está activo.
  6. Timeout de seguridad: `remaining - 500ms` para abortar si el aux no se prepara a tiempo.

### Flyway baseline en la DB real
La DB de Carlos tiene una fila manual en `flyway_schema_history`:
```sql
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (1, '1', 'baseline', 'BASELINE', '<< Flyway Baseline >>', NULL, 'bootstrap', NOW(), 0, true);
```
Esto marca V1 como aplicada sin ejecutarla. Las siguientes V2, V3, V4... sí se aplican normalmente al arrancar. **No tocar esta fila.**

### Convenciones de código
- Comentarios en castellano (es el idioma del dueño).
- No añadir "comentarios obvios" tipo `// incrementar counter`. Solo "por qué no es obvio", decisiones, workarounds.
- Nunca `/stream/*` sin auth: el `AuthInterceptor` del móvil mete el JWT en todas las requests.

---

## 5. LO QUE YA ESTÁ HECHO (bloques completados)

Listado cronológico con 1-2 líneas de qué aporta cada uno. Si Carlos pide repasar algo, busca la referencia en el código.

### Backend
1. **Modulith + credenciales admin/admin fuera + DevTools scope developmentOnly**. 13 endpoints huérfanos eliminados.
2. **Flyway inicial** — V1 baseline con el schema completo, `baseline-on-migrate=true`.
3. **Roles OWNER/MEMBER**: ya existía `UserRole` enum y `OwnerBootstrap` (lee `MUSARUM_OWNER_NICK/PASSWORD` del `.env` y crea el OWNER al primer arranque). Se cerró `POST /users` a OWNER.
4. **Scanner escalable** — hash parcial, batches, estado en vivo `/scanner/status`, LibraryWatcher con debounce in-memory de 5s (no más O(N²)).
5. **Carpetas de música gestionables** — V3 `library_folders`, endpoints `/admin/folders/*` con `@PreAuthorize("hasRole('OWNER')")`.
6. **Paginación real** — `/songs/letter-offsets` + Pageable en todos los list endpoints.
7. **Borrar canciones (OWNER)** — `DELETE /songs/{id}`, `SongDeletionService` que limpia favoritos/entries/reproductions + fichero del disco.
8. **Lyrics on-demand** — `LyricsLookupService` separado, timeouts (3s connect + 4s read) en LrcLibClient, cache en disco.
9. **Radio** — `/songs/{id}/radio?size=50` basado en género + artistas + aleatorio.
10. **V4 Avatar + blocked** — `AvatarStorage` en `_profilepictures/`, endpoints `PUT/DELETE /users/me/avatar`, `GET /users/{id}/avatar`. Admin de usuarios `/users/admin/*` (listar, bloquear, borrar). Login rechaza silenciosamente a usuarios bloqueados.
11. **V2 dedupe + UNIQUE audio_path** — fix de duplicados tras cambio de hash. **IMPORTANTE**: `V2__dedupe_and_unique_audio_path.sql` reasigna favoritos/entries/reproductions a la fila canónica antes de borrar duplicadas.
12. **ReplayGain opcional** — toggle en Ajustes, default ON.
13. **Cache-busting avatar**: `avatar_url` incluye `?v={timestamp}` desde `AvatarStorage.saveAvatar`.

### Mobile
1. **UI Camelot con gradient por tonalidad** (sin bloque sólido).
2. **ClickableArtists** — cada nombre en NowPlaying es su propio link al ArtistDetail.
3. **Fix bug canción cambia-en-background**: `syncStateFromController` re-deriva `currentSong` y limpia `currentCoverUrl` si cambió.
4. **Scroll infinito + sparse list** en Canciones, scroll infinito simple en Álbumes/Artistas.
5. **Long-press SongRow → BottomSheet** (Añadir / Borrar OWNER + confirmación).
6. **ReplayGain + Sleep timer + Radio** — todos integrados en ActionsRow del NowPlaying.
7. **Crossfade real estilo Spotify** — ver sección Media3 arriba para detalle completo.
8. **Fotos de perfil subibles** — `rememberLauncherForActivityResult(PickVisualMedia.ImageOnly)`, upload multipart.
9. **Pantalla Usuarios (OWNER)** — bloquear con switch, eliminar con confirmación.
10. **Carpetas de música** en Perfil — activar/desactivar sub-carpetas descubiertas + añadir manual.
11. **Rediseño "Tus números"** en Perfil — 3 big-stats con separadores verticales + filas con icono circular para los tops.
12. **Nav bar** — orden Inicio · Buscar · Biblioteca · Perfil.
13. **Rediseño Home** — fuera TopAppBar, `GreetingHeader` con avatar 72dp + saludo según hora + nick. Los 3 QuickTiles con colores (primary/tertiary/secondary) y formas (bevel asimétrico/píldora/bevel inverso) distintos.
14. **Transición fluida en píldora del player** — las 4 rutas (NOW_PLAYING, LYRICS, INFO, VIDEO) animan slide-horizontal + fade de 250ms.
15. **Toggle ReplayGain** en Perfil.
16. **Botón Descargar** en ActionsRow — placeholder "próximamente" por ahora.
17. **Fix búsqueda**:
    - Filtros 2x2 (`FlowRow maxItemsInEachRow=2`).
    - Recent searches solo al pulsar lupa del teclado (`imeAction=Search`) o tocar un resultado.
18. **PlayerService.onTaskRemoved** siempre para → música no se queda sonando tras barrer la app.
19. **Fix avatar segunda subida** — cache-busting por timestamp en el propio `avatar_url`.
20. **DominantCoverColor** — fondo dinámico de NowPlaying según color extraído con Palette.

### Varios
- `musarumViejo/` **borrado por el usuario** (confirmado en conversación anterior).
- Dependencia `emendatium/` existe pero `EMENDATIUM_BASE_URL` no está puesto → sin enriquecimiento auto.

### Bloques recientes (nov 2026)
21. **Vista Vídeo = Vista Audio**: `VideoScreen` reescrita sobre `PlayerShellScaffold`. Mismo header, mismo título clicable, mismo ActionsRow, mismos TrackControls, mismo mini-letra toggle. Donde iba el cover ahora va un `SurfaceView` dentro de un hero cuadrado (bandas negras si el vídeo no es 1:1). Un `ExoPlayer` local reproduce el vídeo; play/pause/seek de los controles operan sobre ese player, mientras shuffle/prev/next/repeat van al controller global. Al cambiar canción (prev/next desde la vista), el `LaunchedEffect(song.id)` recarga el media source; si la nueva canción tiene `details.hasVideo == false`, vuelve a Audio con snackbar.
22. **Icono progress bar — corchea estilizada**: `QuarterNoteThumb` dibujado con Canvas de 14x24dp. Cabeza elíptica inclinada -18° (rotate del drawscope), plica vertical a la derecha y banderilla curva Bezier arriba. Todo pintado con primary. Reemplaza al mini-vinilo (quedaba como círculo sin identidad). Compartido por NowPlaying y VideoScreen (`internal`). Mantenemos `ic_treble_clef.xml` sin referenciar.
24. **Descargas offline reales (base)**: nuevo paquete `data/downloads/` sin WorkManager ni Room — persistencia con JSON ligero en `filesDir/downloads/index.json`.
    - `DownloadInfo` (songId, fileName, downloadedAt, expiresAt nullable, sizeBytes, title, artistsLabel).
    - `DownloadsRepository`: expone `StateFlow<Map<Long, DownloadInfo>>`, operaciones `upsert/remove/clearAll` sincronizadas; `fileFor(songId)` devuelve `File?`.
    - `DownloadsManager`: serializa descargas con `Mutex`; reutiliza `OkHttpClient` del AppContainer (ya autenticado JWT); descarga a `.part` → rename atómico; `setKeepPermanent`, `cleanExpired(currentlyPlayingId?)`.
    - `PlayerPreferences`: nuevo toggle `downloadsTemporaryByDefault` (default ON = temporales 24 h).
    - `PlayerController.toMediaItem` y crossfade aux usan nuevo `uriFor(songId)` que prefiere `file://` local si existe la descarga.
    - AppContainer instancia repo + manager y llama `cleanExpired(null)` al arrancar.
    - UI: botón descarga en `NowPlayingScreen` y `VideoScreen` con 4 estados (`NOT_DOWNLOADED` → cloud, `IN_PROGRESS` → spinner, `TEMPORARY` → check-primary, `PERMANENT` → check-tertiary). Un toque sobre temporal la convierte en permanente; un toque sobre permanente la borra. En Perfil: toggle "Descargas temporales por defecto" + "Borrar todas las descargas" con contador y MB.
    - Long-press `SongActionsSheet`: ahora el sheet mismo lee `downloadsRepository.entries` + `downloadsManager.inProgress` y muestra la acción correspondiente (Descargar / Descargando… / Mantener permanente + Borrar descarga / Borrar descarga). No se añadieron parámetros nuevos a su firma — el sheet se autoabastece vía `LocalAppContainer` + `LocalSnackbar`.
    - `PlaylistDetailScreen`: botón "Descargar todo / Descargar (N) / Todo descargado" junto al de Reproducir. Encola solo las que no están ya descargadas (evita rebotes al mutex del manager).
    - `LibraryScreen`: entrada "Descargadas" en Fijadas (análoga a Favoritas pero con color tertiary) con contador y MB. Lleva a `DownloadsScreen` nueva: lista offline-first ordenada por fecha de descarga, con botón "Reproducir todo", distinción visual temporal vs permanente y acción borrar por canción. Nueva ruta `Routes.DOWNLOADS = "downloads"`.
    - `SongRow` ahora pinta un badge de 16dp en la esquina inferior derecha de la portada cuando la canción está descargada (reloj primary = temporal, check tertiary = permanente). Lee del `DownloadsRepository` para reaccionar en vivo.
26. **ModePill con highlight animado + transición player suave**:
    - `ModePill` reescrita: tabs de ancho fijo (68dp) para que la píldora se adapte al número de modos (3 o 4) sin estirar hasta el borde. Fondo `surface` sólido con alpha 0.92 + borde 1dp onSurface alpha 0.08 + shadow 4dp — así destaca sobre el gradient dinámico del shell en vez de desaparecer. Un único Box "highlight" ámbar se desliza con `animateDpAsState(280ms, FastOutSlowInEasing)` sobre el offset en pasos de 68dp. Los textos interpolan color onSurface↔onPrimary con `animateFloatAsState` + `Color.lerp`.
    - `MusarumNavHost`: las transiciones entre Audio/Letra/Info/Vídeo volvieron a `slideIntoContainer` + `fadeIn` 300ms (antes era scale+fade y "saltaba"). La dirección se decide por el orden lógico de la píldora (LYRICS · VIDEO · NOW_PLAYING · INFO), no por el stack de navegación — así el movimiento acompaña al highlight de la píldora. Fallback a `SlideDirection.Up` cuando venimos de fuera del player (MiniPlayer).
29. **Análisis del servidor (OWNER)**:
    - Backend: nuevo paquete `musarum.backend.admin` con `ServerStatsService` + `AdminController`. Endpoint `GET /admin/server/stats` protegido `@PreAuthorize("hasRole('OWNER')")`. Agrega 6 secciones: Catálogo (songs/artists/albums/genres/playlists/favorites/users), Letras (con/sin cobertura + synced/plain leyendo cada `.lrc`), Disco (sum file_size + `Files.walk` por carpeta + `pg_database_size()` nativo + `FileStore.getUsableSpace()`), Enriquecimiento (missing cover/bpm/key/replayGain/mbid), Actividad (reproducciones total + last 7/30d + usuarios activos 30d + `MAX(createdAt)` + último scan), Técnico (uptime JVM, memoria usada/total/max, letras huérfanas comparando `_lyrics/*.lrc` con IDs existentes).
    - Nuevas queries agregadas: `SongRepository.countWithLyrics/sumFileSize/countMissing*` + `ReproductionRepository.countSince/countDistinctUsersSince/findLastReproductionAt` + `UserRepository.countByBlocked`. `GenreRepository`, `ArtistRepository`, `PlaylistRepository` subidos a `public` para que el service los pueda inyectar cross-package.
    - Mobile: `ServerStatsDto` + `ServerStatsScreen` + `ServerStatsViewModel`. Pantalla con 6 tarjetas (Catálogo con 3 "big stats", Letras con `LinearProgressIndicator` de cobertura, Disco con barra de uso del volumen, Enriquecimiento con "N/total" por campo, Actividad con 7d/30d/total, Técnico). Formatos helpers `humanBytes/humanDuration/humanInstant`. Accesible desde Perfil (OWNER-only) con `Icons.Filled.Insights`. Ruta nueva `Routes.SERVER_STATS`.
30. **Fijadas de Biblioteca en 2 columnas**: `LibraryScreen` ahora pinta Descargadas + Favoritas como `Row` de dos tiles cuadrados `weight(1f)` en vez de 2 rows apiladas. `DownloadsTile` (tertiary) y `FavoritesTile` (primary) con layout vertical (icono 44dp arriba, título + contador abajo). Las entradas de fila antiguas (`FavoritesEntry`/`DownloadsEntry`) quedan sin usar por si vuelven.
31. **Long-press en el ♥ de NowPlaying → Favoritas**: nuevo `GhostCircleButtonLong` con `combinedClickable`. Tap sigue haciendo toggle; mantener pulsado lanza `onOpenFavorites` que navega a `Routes.FAVORITES`. Propagado desde el NavHost.
32. **Textos de ajustes compactados**: "Descargas temporales por defecto" → "Descargas temporales" (toggle lo deja claro). Subtítulo "Caducan a las 24 h · toca el icono…" → "Caducan a las 24 h". ReplayGain: "ReplayGain · iguala el nivel…" → "Igualar volumen entre pistas". "Alta de nuevo oyente (solo tú puedes)" → "Alta de nuevo oyente". Objetivo: altura uniforme en las SettingRow para que la columna respire.
38. **CI/CD release pipeline (nov-03 2026)**:
    - `mobile/app/build.gradle.kts`: nuevo `signingConfigs { release { ... } }` que lee de `mobile/keystore.properties` en local o de env vars (`MUSARUM_KEYSTORE_PATH/PASSWORD`, `MUSARUM_KEY_ALIAS/PASSWORD`) en CI. Si no hay keystore, el task `:assembleRelease` falla con mensaje claro — nunca firmamos con la debug key.
    - `mobile/keystore.properties.example` + `.gitignore` extendido para no commitear nunca el `.jks` ni los passwords.
    - `.github/workflows/android-release.yml`: dispara en tag `android-v*`. Decodifica keystore de `ANDROID_KEYSTORE_BASE64`, compila APK release firmada, la adjunta a una GitHub Release auto-generada con release notes del commit log.
    - `.github/workflows/backend-release.yml` y `emendatium-release.yml`: disparan en tag `backend-v*` / `emendatium-v*` (con filtro `paths` para evitar rebuilds triviales). Login a GHCR con `GITHUB_TOKEN`, build con buildx + cache GHA, push a `ghcr.io/<owner>/musarum-{backend,emendatium}:{latest,tag}`.
    - `RELEASE.md` en la raíz: guía paso a paso para generar keystore, meter secrets en GitHub, levantar el compose la primera vez, publicar APK/backend/emendatium, avisar a los 4 users con `MUSARUM_APK_*`.
43. **Home convivencia (Bloque B — nov-14 2026)**: migración V7 añade `share_now_playing` + `share_taste` (default TRUE) a `users`. Backend: nuevo paquete `musarum.backend.home` con `HomeService` + `HomeController` exponiendo `/home/presence` (last reproduction por usuario desde windowDays), `/home/discoveries` (canciones nuevas del catálogo con paginación) y `/home/taste-profiles` (artista/género/canción top por usuario desde queries nativas). `UserController` tiene `PATCH /users/me/privacy`. `ReproductionRepository` expone `findLastPerUserSince`, `findTopArtistForUserSince` (join nativo song_artists), `findTopGenreForUserSince`. `UserMapper.toPublicResponse` oculta los toggles con null.
    Mobile: `HomeViewModel` con `supervisorScope` + 7 `async` paralelos (presence/discoveries/taste/radio/forYou/stats/smart) que NO revientan si uno falla. Tres tarjetas nuevas en Home (`PresenceCard`, `DiscoveryCard`, `TasteProfileCard`). Pantalla dedicada `NewDiscoveriesScreen` con lista paginada completa. Toggles "Compartir lo que escucho" y "Compartir mi perfil de gustos" en Perfil con `ProfileViewModel.updatePrivacyNowPlaying/Taste`. Respeta privacidad: si un user tiene toggle off, no aparece en `/home/presence` ni en `/home/taste-profiles` para los demás.

44. **Listas inteligentes "Hecho para ti" (Bloque C — nov-14 2026)**: 4 playlists dinámicas que se calculan al vuelo sin tabla propia.
    Backend: `SongRepository` con 4 queries nativas: `findPartySongs` (BPM>=120 OR género dance/reggaeton/electro/latin), `findFocusSongs` (BPM 1..90 OR classical/ambient/jazz/lofi), `findUndiscoveredForUser` (NOT EXISTS sobre reproductions del user), `findForgottenForUser` (HAVING MAX(created_at)<NOW()-'60 days' AND COUNT>=2, "canciones que te gustaban y has dejado"). `HomeService.listSmartPlaylists()` devuelve slugs + metadatos, `smartPlaylist(slug, userId, size)` resuelve canciones.
    Mobile: `SmartPlaylistInfoDto` + `SmartPlaylistCard` (220×132dp, gradient por `iconHint`) en carrusel horizontal del Home. Al tocar la tarjeta, el VM carga las canciones y reproduce; si está vacía, snackbar.

45. **Polish UX + drag queue + lupa integrada (Bloque D — nov-14 2026)**:
    - `InlineSearchBar` + `SearchActionIcon`: lupa en TopAppBar de PlaylistDetail/AlbumDetail/ArtistDetail que colapsa en `OutlinedTextField` con focus requester (delay 60ms para que la animación acabe antes del foco). Filtra la lista en memoria case-insensitive sobre title + artistas.
    - `MiniPlayer` con blur real: `Surface` transparente + `AsyncImage(currentCoverUrl).blur(28dp)` como fondo + gradient vertical 78%→92% `surfaceVariant` encima para legibilidad del texto. Da sensación de material pulido y translúcido al rollo Carlos.
    - PlaylistDetail: `dragEnabled = query.isBlank()` — no se permite reordenar mientras hay filtro aplicado (las positions no cuadrarían con DB).

46. **TAG editor (Bloque E — nov-14 2026)**: OWNER puede editar ID3 de una canción sin tocar el fichero a mano.
    Backend: `PATCH /songs/{id}/tags` (OWNER) con `TagEditRequest(title, artist, album, genre, year, trackNumber, renameFile)` todos nullables. `TagEditService.editTags`:
    1. **Reescribe ID3 con jaudiotagger PRIMERO**. Si falla (fichero bloqueado, formato no soportado), lanza antes de tocar la DB → el estado nunca queda inconsistente.
    2. Aplica cambios al JPA usando `ArtistService.findOrCreate` / `AlbumService.findOrCreate` / `GenreService.findOrCreate` → deduplica solo.
    3. Si `renameFile=true`: construye nuevo nombre a partir del título saneado + extensión original, `Files.move(ATOMIC_MOVE)`, sufijo `(N)` en caso de colisión, actualiza `song.audio_path`.
    `Song` expone setters explícitos (`setTitle`, `setAlbum`, `setGenre`, `setYear`, `setTrackNumber`, `setArtists`) porque los generados por Lombok no estaban visibles cross-package.
    Mobile: `EditTagsDialog` con 6 OutlinedTextField + Switch "Renombrar archivo" con preview del nombre resultante. Icono ✏ junto a "Metadata" en `InfoScreen` solo si `isOwner`. `NowPlayingViewModel.editTags` refresca la canción al terminar.

48. **Auditoría de bugs + performance pass (nov-14 2026)**:
    - **DominantCoverColor usaba ImageLoader "pelado"**: `rememberDominantCoverColor` instanciaba un `ImageLoader(context)` nuevo por recomposición — SIN el `OkHttpNetworkFetcherFactory` autenticado. Resultado: las portadas servidas por el backend (`/albums/{id}/cover`, que exigen JWT) siempre devolvían 401 a Coil y el fondo del NowPlaying se quedaba en el fallback en vez de tomar el color real de la portada. Fix: `SingletonImageLoader.get(context)` reutiliza el ImageLoader configurado en `MusarumApplication.newImageLoader`.
    - **`UserService.delete` cargaba toda la tabla de reproducciones en memoria**: `reproductionRepository.findAll().stream().filter(userId)` → si el histórico crece a decenas de miles de filas (4 users × 7k canciones), OOM garantizado. Fix: `ReproductionRepository.deleteByUserId(userId)` (query derivada → `DELETE FROM reproductions WHERE user_id = ?`, usa el índice `(user_id, created_at)`). Mismo patrón para `countByUserRole(OWNER)` → sustituye `findAll().stream().filter().count()`.
    - **PlayerService duplicaba el OkHttpClient**: montaba uno nuevo con solo `AuthInterceptor`, perdiéndose el `UnauthorizedInterceptor`. Si el JWT expiraba durante streaming, Musarum entraba en bucle 401 sin tirar al login. Fix: reusar `container.okHttpClient`.
    - **V8 — índices de FK que faltaban**: Postgres no crea índice sobre FOREIGN KEY automáticamente. Añadidos: `songs(album_id)`, `songs(genre_id)`, `songs(created_at DESC)`, `favorites(song_id)`, `playlist_entries(song_id)`, `playlist_entries(playlist_id, position)`, `reproductions(song_id)`, `song_artists(artist_id)`, `playlists(owner_id)`. Impacto directo: borrar una canción, abrir AlbumDetail/GenreDetail, el "top artista" de taste profile y "Olvidadas" dejan de hacer full table scan.

47. **YouTube + BaseURL + Visualizador + Voz (Bloques F/G/H/I — nov-14 2026)**:
    - **F — YouTube search**: entrada "Buscar en YouTube" en el DropdownMenu "⋯ Más acciones" de NowPlaying y VideoScreen. Abre `https://www.youtube.com/results?search_query={artist}+{title}` con `Intent.ACTION_VIEW + FLAG_ACTIVITY_NEW_TASK`. Sin API key, sin dependencia extra.
    - **G.1 — BaseURL editable desde Login**: icono Dns en Login → `ServerUrlDialog` con `BaseUrlStore` (SharedPreferences). Al guardar, `Process.killProcess(Process.myPid())` mata la app para que al reabrirla todo el grafo (Retrofit, DownloadsManager, PlayerController, CoverImage, DominantCoverColor, VideoScreen) lea el nuevo `AppContainer.baseUrl`. **IMPORTANTE**: todos los sitios que antes hacían `BuildConfig.MUSARUM_BASE_URL` ahora hacen `container.baseUrl`. Retrofit es inmutable después del `build()`, por eso el kill.
    - **G.2 — Errores del scan**: `LibraryScanner.recentErrors: ArrayDeque<String>(100)` acumula `path → razón` en el catch de cada canción. `ServerStatsService.buildTechnical()` los incluye en `TechnicalStats.recentScanErrors`. `ServerStatsScreen.TechnicalCard` pinta los últimos 10 reversed con divider.
    - **H — Visualizador FFT**: `AudioVisualizer` composable con `android.media.audiofx.Visualizer` enganchado al `audioSessionId` que `PlayerService.Player.Listener.onAudioSessionIdChanged` publica en `AppContainer.audioSessionIdFlow`. 32 barras + caps que caen con `CAP_GRAVITY=0.012f`, estilo Winamp/Ares. Requiere `RECORD_AUDIO` (permission añadido al manifest + pedido en runtime). Si el user lo deniega, no dibuja. Toggle en DropdownMenu "⋯ Más acciones" de NowPlaying.
    - **I — Voz básica**: icono Mic en `SearchScreen` (solo cuando query está vacía). `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` con `EXTRA_LANGUAGE="es-ES"` lanza la UI del sistema (no requiere RECORD_AUDIO en Musarum). `VoiceCommandRouter.route()` enruta con regex en castellano: pausa/play/siguiente/anterior/aleatorio sin parámetro; "pon X" / "reproduce X" → `searchRepository.search(X)` + `controller.playQueue(firstResult, 0)`; "pon playlist X" → `playlistRepository.refresh/details` + playQueue. Si no casa con nada, mete el texto en la barra. **Nota**: PlayerController no tiene `pause()` explícito, así que PAUSE y PLAY usan `togglePlayPause()`.

42. **Re-enriquecer todo + shuffle real + batería + snackbars (nov-10 2026)**:
    - **Re-enriquecer todo (OWNER)**: nuevo endpoint `POST /admin/server/re-enrich-all` en `AdminController`. `ServerStatsService.reEnrichAll()` pagina el catálogo (200 por lote para no cargar 7k entidades en memoria) y por cada canción llama a `emendatiumEnricher.enrichAsync(id, path)` fire-and-forget. Devuelve `ReEnrichSummary(queued, total)`. Mobile: botón "Re-enriquecer todo (N canciones)" en la tarjeta "Mantenimiento" del Análisis del servidor, por encima del botón rojo de "Vaciar biblioteca". Útil cuando el catálogo se importó antes de que Emendatium estuviera corriendo → bio de artistas, BPM, tonalidad y replaygain aparecen poco a poco.
    - **Shuffle real desde Canciones**: fuera el chip "Aleatorio" de arriba de la lista (feo). Nuevo endpoint `GET /songs/all` que devuelve todo el catálogo ordenado como la vista paginada (query nativa `findAllOrdered` idéntica al orden de `findPageOrdered`). `SongListViewModel.loadAllOrdered(onReady)` cachea el resultado en el VM y lo invalida en `refresh()`. Al tocar una canción en Canciones, la cola del player = TODO el catálogo (startIndex = posición de la canción tocada), así el botón shuffle de NowPlaying barajea de verdad las ~7000 canciones y no solo la ventana paginada. `HEADER_ITEMS` pasó de 2 a 1.
    - **Permitir en segundo plano (Bluetooth)**: nueva fila en Perfil que abre `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (y cae al detail de la app si ese intent falla en algún ROM). Permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` añadido al manifest. Sin desactivar la optimización, Android/Doze corta la red con la pantalla apagada y algunos auriculares Bluetooth se quedan sin stream → handshake → algunos se apagan.
    - **Snackbars silenciados**: `NowPlayingScreen`, `VideoScreen`, `SongActionsSheet` y `AddToPlaylistDialog` ya no muestran snackbar en éxitos obvios (canción eliminada, descarga pendiente/permanente/borrada, añadida a playlist, sleep timer activado/cancelado). Antes tapaban los controles de play/next/prev. Los snackbars de ERROR siguen apareciendo (no se puede eliminar, no se pudo añadir, no se pudo crear playlist).

41. **Vaciar biblioteca desde el móvil (nov-05 2026)**: tras un cambio de carpeta de música (p. ej. biblioteca nueva de ~7000 canciones en vez de la vieja de ~11000), el OWNER vacía el catálogo entero desde Perfil → Análisis del servidor → tarjeta "Mantenimiento".
    - Backend: `POST /admin/server/wipe-library` (OWNER). `ServerStatsService.wipeLibrary()` hace `TRUNCATE TABLE songs, albums, artists, genres, song_artists, playlist_entries, favorites, reproductions RESTART IDENTITY CASCADE` como native query — es instantáneo aunque haya 11k canciones. No toca usuarios, playlists (quedan como contenedores vacíos), carpetas configuradas ni ajustes. Devuelve `WipeSummary(songs, artists, albums, genres)` con las cifras de ANTES para confirmar al OWNER.
    - Mobile: `ApiService.wipeLibrary` + `WipeLibrarySummaryDto`. `ServerStatsViewModel.wipeLibrary { ok, msg -> snackbar }`. `MaintenanceCard` al final de la lista de tarjetas con botón rojo errorContainer ("Vaciar biblioteca (N canciones)" / "Biblioteca ya vacía" si N=0). `AlertDialog` de confirmación que lista qué se borra y qué se mantiene.
    - Flujo típico: wipe → copiar carpeta nueva al servidor → scanner la detecta sola (o manual `POST /scanner/import`) y empieza de cero con IDs desde 1.

40. **Publicar actualización desde el móvil (nov-04 2026)**: en vez de tocar el `.env` del servidor + reiniciar cada vez que hay APK nueva, el OWNER anuncia la versión desde el propio móvil.
    - Backend: migración V5 crea `app_updates` (tabla singleton con id=1, fila pre-insertada por la migración). Nueva entity `AppUpdate` + `AppUpdateRepository` + `AppUpdateService`. `VersionController` ahora consulta la DB primero y cae al legacy `musarum.apk.*` de properties si la fila sigue vacía — así la actualización del backend es no-disruptiva. Nuevo `AppUpdateController` bajo `/admin/version` (OWNER): `GET` devuelve el anuncio actual y `POST` lo actualiza en caliente.
    - Mobile: `ApiService.currentPublishedUpdate` + `publishUpdate`. Nueva `PublishUpdateScreen` (ruta `Routes.PUBLISH_UPDATE`) con tarjeta "publicado ahora" + form (versión + URL + switch obligatoria) + botón "Rellenar URL desde versión" (compone `https://github.com/carlos38sm/musarum/releases/download/android-v{v}/musarum-{v}.apk` con `PublishUpdateViewModel.suggestApkUrl`) + "Retirar anuncio actual" (envía vacíos). Entrada en Perfil solo-OWNER con `Icons.Filled.NewReleases` bajo "Análisis del servidor".
    - Flujo humano ahora: pushear tag `android-v1.0.4` → esperar workflow verde → abrir Musarum en el móvil → Perfil → Publicar actualización → teclear "1.0.4" → "Rellenar URL" → Publicar. Los demás móviles ven el diálogo al abrir. Sin tocar servidor.
39. **Lote nov-03 2026: EQ + Cola visible + Letras karaoke + Sleep fade + /version + Dockerfiles**:
    - **Sleep timer con fade largo**: antes 1 s de fade al final (sonaba a corte seco). Ahora rampa de 10 s suave desde `targetTime - 10s` hasta pausa. `cancelSleepTimer` restaura el volumen a `currentRgTarget` por si el usuario cancela a mitad del fade.
    - **Botón limpiar letras huérfanas** en Análisis del servidor. Backend: `POST /admin/server/clean-orphan-lyrics` (OWNER) + `ServerStatsService.deleteOrphanLyricFiles()`. Mobile: TextButton bajo "Letras huérfanas" en `TechnicalCard` llama a `viewModel.cleanOrphanLyrics()` y refresca el panel.
    - **Equalizer 5 bandas**: `android.media.audiofx.Equalizer` enganchado al `audioSessionId` del `ExoPlayer` del `PlayerService`. Preferencias nuevas: `equalizerEnabled`, `equalizerBands: List<Int>` (ganancias en milibelios, ±1500). El service observa el flow combinado y re-aplica bandas en caliente. `MODIFY_AUDIO_SETTINGS` añadido al manifest. UI: `EqualizerScreen` con switch + 5 sliders horizontales (60 Hz · 230 · 910 · 3.6 k · 14 k) + botón "Restablecer plano". Nueva entrada en Perfil con `Icons.Filled.Tune`. Ruta `Routes.EQUALIZER`. Puede fallar silenciosamente en ROMs Google limpias — es limitación conocida de AudioEffect.
    - **Cola de reproducción visible**: nueva `QueueScreen` (`Routes.QUEUE`). `PlayerController` añade `jumpToIndex`, `addToQueueEnd`, `playNext`, `removeFromQueue`, `moveInQueue`. UI con ReorderableLazyList (lib que ya metimos para playlist), key = índice (no song.id — las repetidas conviven), fila actual destacada con badge play y texto primary. Icono `PlaylistPlay` en `ActionsRow` de NowPlaying abre la pantalla.
    - **Letras pantalla completa karaoke**: `SyncedLyricsView` ahora escala por "distancia a la línea activa": activa = `displayMedium` ExtraBold primary, ±1 = `headlineSmall` SemiBold 90 % alpha, resto = `titleMedium` 60 % alpha. Las pasadas quedan al 45 % surfaceVariant. Antes todas eran igualitas y la activa se perdía — ahora destaca como karaoke de verdad.
    - **Endpoint `/version` + diálogo "Actualizar"**: backend `VersionController` lee `musarum.apk.latest/url/mandatory` de `application.properties` (vars `MUSARUM_APK_*` del `.env`). Ruta pública (`permitAll` en SecurityConfig). Mobile `UpdateChecker` compara con `BuildConfig.VERSION_NAME` (parseo semver + fallback lexicográfico) y expone `StateFlow<AvailableUpdate?>`. `MusarumNavHost` pinta un `AlertDialog` global cuando hay update: botón "Descargar" abre el URL con `Intent.ACTION_VIEW`, "Más tarde" solo si no es `mandatory`.
    - **Dockerfiles + compose extendido**: `backend/Dockerfile` y `emendatium/Dockerfile` multi-stage con `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`. Healthcheck del backend contra `/api/v1/version` (ruta pública y rápida). `docker-compose.yml` ahora levanta postgres + backend + emendatium + watchtower; backend usa `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/...` y `EMENDATIUM_BASE_URL=http://emendatium:8081`. Volumen `${MUSARUM_MUSIC_PATH}:/music` compartido entre backend y emendatium. `.env.example` ampliado con `MUSARUM_APK_*` + `GITHUB_OWNER` para los tags GHCR. Watchtower polling 30 min, solo actualiza contenedores con label `com.centurylinklabs.watchtower.enable: "true"`.
37. **Pulido UI nov-02 2026 + drag-and-drop en playlists**:
    - **Fix botón descarga en NowPlaying/Video**: ahora SIEMPRE hace ciclo claro de 3 estados (nube → temporal 24h → permanente → nube), independiente del toggle "Descargas temporales por defecto" del Perfil. Antes, si el toggle estaba OFF, el ciclo saltaba de "nube" a "permanente" directamente y se perdía el estado intermedio. `DownloadsManager.download()` acepta ahora `forceTemporary: Boolean` además de `forceKeep`. El toggle del Perfil sigue aplicando a long-press del SongActionsSheet y a "Descargar" en PlaylistDetail — solo el botón de NowPlaying/Video fuerza siempre el ciclo visual.
    - Icono `TEMPORARY` cambiado de `DownloadDone` (check pequeño) a `Schedule` (reloj) para distinguirse de `PERMANENT` (`CheckCircle`).
    - **Rediseño "Tus Números"**: fuera la triple columna con separadores. Nuevo formato bento: header + tile destacado full-width con "tiempo total escuchado" (icono circular 52dp primary + número headlineMedium) + dos tiles 1:1 (escuchas / pistas distintas) con colores distintos + tarjeta de tops por debajo. Helpers `HeroStatTile` y `BentoStatTile`.
    - **Logout sin icono**: fuera `Icons.Filled.Logout`, solo texto "Cerrar sesión". Botón más limpio.
    - **"canciones" → "pistas"**: en los tiles de Descargadas y Favoritas de Biblioteca (ahorra ancho horizontal).
    - **PlaylistDetail "Descargar todo" → "Descargar"** (y "Todo descargado" → "Descargada"). El `(N)` sigue para descargas parciales.
    - **Drag-and-drop reordenar canciones en playlist**:
        - Backend: `PATCH /playlists/{id}/entries/{entryId}/position` con `MoveEntryRequest(position: Int)`. `PlaylistService.moveEntry` reordena las positions de todas las entries de forma densa (0..n-1) tras cada move.
        - Dependencia nueva mobile: `sh.calvin.reorderable:reorderable:2.5.1` (FOSS, cero deps extra).
        - UI: `PlaylistDetailScreen` usa `rememberReorderableLazyListState` + `ReorderableItem` por fila. `EntryRow` tiene un icono `DragIndicator` a la derecha con `Modifier.draggableHandle()`. Durante el drag, la fila se eleva con `shadowElevation` + fondo `surfaceVariant`.
        - `PlaylistDetailViewModel.moveEntry(entryId, from, to)` aplica el reorden OPTIMISTAMENTE antes del POST (el drag se nota instantáneo). Si el backend falla, `refresh()` re-sincroniza.
35. **Letras — refuerzo sobrehumano (backend)**: el bloque 25 solo mejoraba Emendatium (que no está desplegado). Ahora el pipeline rico vive también en el backend, se usa en el fetch on-demand (`/songs/{id}/lyrics`) y además hay refresh forzado.
    - **`LrcLibClient` del backend reescrito** copiando el de Emendatium: 5 estrategias (GET exacto, GET sin álbum, SEARCH, SEARCH título limpio, SEARCH stripped) + verificación de artista en todas (anti-cover/suplantación) + **normalización tipográfica** (U+2019 → U+0027, U+201C → U+0022, guiones em/en → ASCII). Esta normalización es la causa #1 por la que LrcLib "no encontraba" canciones muy populares: MusicBrainz entrega "Don't" con apóstrofo tipográfico pero LrcLib indexa con ASCII.
    - **Nuevo paquete `backend/.../enrichment/lyrics/`** con `LyricsProvider` + 3 impls ordenadas por `@Order`:
        - `LocalTagLyricsProvider` (1) — lee el frame USLT/LYRICS del fichero con jaudiotagger desde `song.getAudioPath()`. Prioridad máxima: lo que trae el fichero es incuestionable.
        - `LrcLibLyricsProvider` (2) — adapter sobre el `LrcLibClient` mejorado.
        - `NeteaseLyricsProvider` (3) — red de seguridad enorme. `music.163.com/api/search/get` → verifica artista → `/api/song/lyric`. Requiere `Referer: https://music.163.com/` o 403.
    - **`LyricsLookupService` recorre `List<LyricsProvider>`** inyectada en orden hasta la primera coincidencia. Mantiene cache en disco (`_lyrics/{id}.lrc`) y la columna `songs.lyrics_path`.
    - **Nuevo endpoint `POST /songs/{id}/lyrics/refresh`**: ignora cache y vuelve a buscar. 200 con la nueva letra, 204 si no hubo mejor opción (en ese caso el móvil mantiene la que había — nunca borramos ciegamente).
    - **Mobile**: `ApiService.refreshLyrics` + `SongRepository.refreshLyrics` + `NowPlayingViewModel.refreshLyrics { ok, msg -> snackbar }`. `LyricsScreen` tiene botón "Buscar letra de nuevo" al pie cuando hay letra (para la que esté mal) y botón grande "Buscar letra ahora" cuando no hay (en vez del texto plano "Sin letra disponible").
    - **No hace falta borrar DB ni re-escanear**: las letras se cachean como `.lrc` + path en `songs`. Las canciones SIN letra se re-buscarán sola la próxima vez (ahora con el pipeline bueno). Las que tienen letra MALA cacheada se arreglan desde el botón del móvil.
28. **Letras en cascada (Emendatium)** (bloque anterior): nuevo paquete `emendatium.backend.sources.lyrics` con interfaz `LyricsProvider` y 3 implementaciones ordenadas por `@Order`:
    - `LocalTagLyricsProvider` (1) — lee el frame USLT/LYRICS del fichero con jaudiotagger desde `context.mp3Path()`. Prioridad máxima: lo que ya trae el fichero es incuestionable.
    - `LrcLibLyricsProvider` (2) — adapter sobre el `LrcLibClient` existente. Prefiere synced, cae a plain.
    - `NeteaseLyricsProvider` (3) — nueva red de seguridad. `music.163.com/api/search/get` → coge el primer resultado cuyo artista encaje (misma heurística `simplifyArtist` que LrcLib) → `/api/song/lyric` para traer el LRC. Requiere `Referer: https://music.163.com/` o devuelve 403. Rechaza respuestas sin timestamps (filtra instrumentales marcados por NetEase).
    - `LyricsSource` (en `sources/lrclib/`) refactorizado: inyecta `List<LyricsProvider>` ordenada y recorre hasta la primera coincidencia. Se mantiene en `syncedLyrics` por historia; LrcParser del mobile ya trata vacío-de-timestamps como letra plana.

---

## 6. LO QUE FALTA (pendientes priorizados)

### 🟢 Descargas offline — COMPLETADO
Bloques 24, long-press, playlist, sección Biblioteca e indicador en SongRow. Ya hay flujo end-to-end.

### ✅ P0 completados (nov-2026)
- ~~Descargas offline reales~~ → bloque 24 + indicador en SongRow.
- ~~Despliegue real~~ → bloque 38 (CI/CD release pipeline, GHCR + Watchtower + GitHub Actions).
- ~~Endpoint `/version` + diálogo "Actualizar"~~ → bloque 39 + bloque 40 (publicar desde el móvil).
- ~~YouTube fallback~~ → bloque 47-F (search externo, sin API key).
- ~~Letras karaoke pantalla completa~~ → bloque 39 (SyncedLyricsView escalada por distancia a línea activa).
- ~~Equalizer~~ → bloque 39 (5 bandas via `audiofx.Equalizer`).
- ~~Visualizador de audio~~ → bloque 47-H (FFT 32 barras + caps estilo Ares).
- ~~Home convivencia~~ → bloque 43.
- ~~Listas inteligentes~~ → bloque 44.
- ~~TAG editor OWNER~~ → bloque 46.
- ~~BaseURL editable desde Login~~ → bloque 47-G.1.
- ~~Panel de errores del scan~~ → bloque 47-G.2.
- ~~Voz básica~~ → bloque 47-I.

### 🟡 Media prioridad (vivos)
1. **Multi-artista en nombres de archivo** — hoy el renombrado (lógica en Emendatium) solo toma 1 artista. Juntar `artist-credit[]` de MusicBrainz con separador ", ". **Nota**: el TAG editor del bloque 46 ya acepta multi-artista por coma en `artist` del request, pero solo sobre edición manual — el renombrado automático al escanear sigue siendo single-artist.
2. **Capitalización inteligente** en títulos — preservar "de", "la", "el", "the", "and", "para"... en minúsculas. ~15 líneas en `TitleBuilder` de Emendatium.
3. **Mover definición de ReplayGain de Info a Ajustes** — pendiente de localizar la referencia exacta en InfoScreen; si la sigue mencionando, moverla.
4. **Playlists colaborativas end-to-end** — backend existe (`canEdit`, `addCollaborator`, `removeCollaborator`), diálogo invitar en mobile existe, pero falta probar flujo e2e. Sin notificaciones push (Tailscale, 4 users).
5. **Emendatium desplegado** — hoy sigue sin contenedor levantado. El Dockerfile existe desde el bloque 39; falta meterlo en el compose real del portátil + variable `EMENDATIUM_BASE_URL` en `.env`. Hasta entonces, las letras y el bloque 42 "Re-enriquecer todo" dependen del pipeline interno del backend (bloque 35), no del Emendatium real.
6. **Comandos de voz avanzados** — el router del bloque 47-I solo cubre comandos cortos. Faltaría "añade a favoritos", "quita de favoritos", "abre playlist X", "cancela sleep", "pon modo fiesta" (apoyarse en smart playlists del bloque 44).

### 🟢 Baja prioridad / nice-to-have
7. **Modo gapless** real (más allá del crossfade).
8. **Export/import de playlists** JSON.
9. **"Playlists del año" / "Año en resumen"**.
10. **Rate limit en `/auth/login`** — rechazado por Carlos: con Tailscale no hay superficie pública.
11. **YouTube embebido in-app** (vs. fallback externo del bloque 47-F) — requiere Google Cloud API key gratuita 10K/día.
12. **Letras bilingües** — traducción + letra original en paralelo.
13. **Smart crossfade por género** — tiempos distintos según BPM / tipo de canción (fade más largo en ambient, corte seco en hip-hop).

---

## 7. DECISIONES DE DISEÑO (y por qué)

### Dejadas como están
- **Crossfade estilo Spotify (avanza principal al EMPEZAR el crossfade, aux reproduce la cola de A fading)**: evita duplicación de B porque B solo suena desde una fuente. Pre-buffer de 2s para que el aux esté `STATE_READY` cuando toca.
- **No encriptación en descargas offline**: es contenido propio del usuario, no hay licencia.
- **Hash parcial SHA-256(1MB) + size**: 10× más rápido que hash completo, colisión real imposible en música legítima.
- **Borrado de canción con pending-delete de 5s**: permite rename sin perder favoritos (watcher detecta DELETE → encola → si llega CREATE con mismo hash → `relocateByHash` → cancelar borrado).
- **Múltiples carpetas de música bajo una paraguas (`MUSARUM_MUSIC_PATH`)**: Docker solo "ve" lo montado como volumen. Carpeta paraguas = una vez en compose.yml. Sub-carpetas gestionadas desde la app (tabla `library_folders`) → cambios en caliente sin reiniciar.
- **ReplayGain con tope de 1.0**: Media3 no permite >1 sin distorsión. Solo bajamos las fuertes, no subimos las flojas. Toggle para desactivar si Emendatium calculó mal.
- **MediaSession en el principal, aux es ExoPlayer local efímero**: mantiene notificación/Bluetooth/lockscreen con la canción "actual".
- **Spring Modulith fuera**: Carlos lo intentó, "es un engorro". No volverlo a meter.
- **Backend package-private por defecto**: encapsula módulos sin DI framework.

### Rechazadas
- **Rate limit `/auth/login`**: Tailscale limita quién puede conectarse. 4 users confiables. No merece la pena.
- **Obtainium**: Carlos prefiere diálogo in-app `/version` para que sus 4 amigos no instalen apps extra.
- **Google Play (25€)**: no se paga nada.
- **Backups de Postgres**: Carlos dice que la música vive en disco y favoritos/historial son prescindibles. **No implementar backups automáticos salvo que lo pida explícitamente.**

---

## 8. BUGS CAZADOS (para no repetir)

1. **async + awaitAll en viewModelScope.launch (no supervisor) crashea** → usar try/catch secuencial con `runCatching` para los opcionales.
2. **Media3 release() sin stop() previo deja el audio decoder vivo unos ms más** → secuencia: `playWhenReady = false` → `stop()` → `clearMediaItems()` → `release()`.
3. **Coil cachea por URL completa** → cambiar avatar sin cambiar URL = foto vieja. Fix: cache-busting con timestamp.
4. **Hibernate updated_at no refresca si el valor del campo es idéntico** → setear `avatarUrl = "/users/{id}/avatar"` dos veces NO dispara `@LastModifiedDate`. Fix: incluir el timestamp dentro del valor del campo.
5. **Spring @Transactional self-invocation**: llamar un método `@Transactional` desde la misma clase NO abre tx (no pasa por el proxy). Fix: extraer a bean separado, O inyectar self-reference `@Lazy @Autowired SelfBean self` y llamar `self.metodoTx(...)`.
   - **Variante encontrada en nov 2026 (LyricsLookupService)**: el extract-a-bean-aparte ya se había hecho (SongService → LyricsLookupService), PERO dentro de LyricsLookupService los métodos `@Transactional` eran `protected` y se llamaban desde `getLyrics` del mismo bean. Doble fallo: (1) Spring por defecto solo intercepta `@Transactional` en métodos **públicos** — en `protected` lo IGNORA en silencio; (2) la llamada seguía siendo self-invocation. `song.getArtists()` (lazy ManyToMany) reventaba con `LazyInitializationException`. Fix: métodos a `public` + self-reference `@Lazy @Autowired LyricsLookupService self` y llamar `self.readLocalOrSnapshot(id)` / `self.cacheLyrics(...)`.
6. **Spring JPA sin UNIQUE en campo "lógicamente único"** permite duplicados si JPA no lo verifica. V2 dedupe + ADD CONSTRAINT fix.
7. **PlayerService.onTaskRemoved con condición "solo si no suena"** → música seguía al barrer la app. Fix: `stopSelf()` siempre.
8. **MediaController no sabe nada de ExoPlayer aux** → pausa solo afectaba al principal. Fix: `togglePlayPause` propaga a `auxPlayer?.playWhenReady`.
9. **Search ViewModel guardaba query reciente en `onQueryChange`** → se guardaban todos los intermedios ("Ba", "Bad", ...). Fix: solo en `commitSearch()` (teclado) o `onResultTapped()`.
10. **`SheetAction(fillMaxWidth)` dentro de Row con `Column(weight 1f)` aplasta la Column** a 1 carácter de ancho. Fix: dos Columns verticales en vez de Row.
11. **jlink / release attribute en pom.xml para Java 21**: ya resuelto, pom tiene `<java.version>21</java.version>`.
12. **Buffering del ExoPlayer aux hace que el crossfade empiece con silencio**: fix con pre-buffer de 2s + `setMediaSource(source, previousPos)` ANTES del `prepare()` (no seekTo después).
13. **`OkHttpDataSource.Factory` directo no lee `file://`** (solo HTTP). Las descargas offline no sonaban porque `PlayerService` y el aux del crossfade usaban OkHttpDataSource como data source directo del ExoPlayer. Fix: envolver con `DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttp))` — delega en OkHttp para HTTP y usa el reader nativo de Android para `file://`, `content://`, `asset://`.
14. **`MediaController` "zombi" tras bloqueo de pantalla** → play/pause no funcionaba al desbloquear. Causas combinadas en `PlayerController`:
    - `connect()` tenía como única guardia `if (mediaController != null) return`, pero el `buildAsync()` deja `mediaController` null hasta que el future completa. Si entre tanto llegaba otro `connect()` o un `whenConnected`, se lanzaba un segundo `buildAsync` en paralelo → leak + state inconsistente.
    - `runCatching { future.get() }.getOrNull()` se tragaba silenciosamente cualquier fallo de conexión → `mediaController` quedaba null para siempre sin log.
    - `whenConnected` solo miraba `mediaController != null`, no `isConnected`. Tras desbloquear la pantalla el controller podía estar instanciado pero con la conexión muerta → `play()/pause()` fallaban en silencio.
    - Primera tanda de fixes: flag `connecting` para evitar conexiones duplicadas, log explícito en `future.get()` fallido, `whenConnected` chequea `isConnected` y si detecta zombi lo libera + reconecta encolando la acción, cola `ArrayDeque<pendingAction>` en vez de un único slot (varios taps mientras conecta se preservan en orden).
    - **Fix definitivo (nov-03 2026)**: NO desconectar en `onStop` de la Activity. El MediaController cliente es ligero; mantenerlo vivo mientras la Activity exista evita toda la gimnasia de reconectar y la ventana de taps "al aire" cuando el usuario desbloquea rápido. Ahora `MainActivity.onCreate` → `connect()`; `onStart` → `connect()` como red de seguridad (idempotente, no-op si conectado); solo `onDestroy` → `disconnect()`. El `PlayerService` sigue cerrando por `onTaskRemoved` cuando se barre la app.
15. **Coil3 `ImageLoader(context)` sin OkHttpNetworkFetcherFactory → 401 silencioso**: si construyes un `ImageLoader` directamente en vez de usar `SingletonImageLoader.get(context)`, NO hereda el OkHttpClient autenticado del `MusarumApplication.newImageLoader`. Coil pide la imagen sin JWT → backend responde 401 → Coil devuelve null sin excepción visible. Síntoma: cualquier cosa que lea Bitmap manualmente (DominantCoverColor + Palette) se queda en fallback, el `AsyncImage` normal sigue funcionando porque ESE sí usa el singleton. Fix: SIEMPRE `SingletonImageLoader.get(context)`.
16. **Spring Data `findAll().stream().filter(...)` como atajo sobre tabla grande → OOM**: carga la tabla completa a memoria. Solo es aceptable con pocas filas (users, genres). En reproductions / favorites / playlist_entries siempre query derivada (`deleteByUserId`, `countByX`) o `@Query`.
17. **Postgres NO crea índice sobre FOREIGN KEY** (solo PK/UNIQUE). Cualquier JOIN o WHERE sobre una FK recorre toda la tabla salvo que haya un `CREATE INDEX` explícito. V8 añadió los 9 que faltaban (songs.album_id, songs.genre_id, reproductions.song_id, favorites.song_id, playlist_entries.song_id, song_artists.artist_id, etc.).

---

## 9. PREFERENCIAS DEL USUARIO (Carlos)

- **Idioma**: castellano siempre.
- **Terminal**: prefiere **cmd** a PowerShell. Si hay opción, usar cmd.
- **Comentarios en código**: en castellano.
- **No pagar nada**: cero suscripciones, cero servicios de pago.
- **No notificaciones push**: 4 users en Tailscale, no vale la pena montar Firebase.
- **Diseño**: le gusta el rollo "pastilla / gradient / sutil / translúcido". Fondos sólidos saturados = no.
- **UI**: prefiere accesos directos sin diálogos intermedios cuando es obvio ("tap en nombre de artista → al artista, sin bottom sheet con 1 opción").
- **Flujo de trabajo**: le gusta ir "bloque a bloque" con pausa para probar entre medias. No lotes gigantes sin verificación.
- **Email**: carlos38sm@gmail.com (solo para atribución Git).
- **Repo**: en GitHub. User local `Carlos Sánchez Miranda`.

### Hábitos observados
- Suele reportar bugs con detalle incluyendo lo que ha hecho ("cuando llevo 50 canciones", "cuando salgo de la app en Rihanna y vuelvo con Lady Gaga"). Mejor darle fixes con causa raíz explicada.
- Interrumpe para explicar nuevas ideas → si plantea idea nueva mid-bloque, apuntar en pendientes, NO cambiar de rumbo.
- A veces pega código legacy preguntando "¿esto se puede tirar?". Responder con equivalencia precisa en el stack actual.
- Pide "recapitular" cuando hay ambigüedad acumulada → responder con listas numeradas.

---

## 10. SETUP RÁPIDO

### Para arrancar la DB
```bash
cd musarum/
docker compose up -d
```

### Para arrancar el backend
```bash
cd musarum/backend/
./mvnw spring-boot:run
```
Variables del `.env` (en raíz, `musarum/.env`): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `MUSARUM_MUSIC_PATH`, `MUSARUM_OWNER_NICK`, `MUSARUM_OWNER_PASSWORD`, `JWT_SECRET`, opcionales `EMENDATIUM_BASE_URL`, `MUSARUM_COVERS_PATH`, `MUSARUM_AVATARS_PATH`, `MUSARUM_VIDEOS_PATH`.

### Para arrancar el móvil
Android Studio → Run. Base URL del backend en `BuildConfig.MUSARUM_BASE_URL` (configurable en `app/build.gradle.kts`).

### Para compilar y verificar
Backend:
```bash
cd backend && ./mvnw compile
```
Mobile:
```bash
cd mobile && ./gradlew :app:compileDebugKotlin
```

### Para añadir una migración
Nuevo fichero `backend/src/main/resources/db/migration/V{n}__descripcion.sql`. Al reiniciar el backend, Flyway la aplica.

### Para generar APK release
Ver `RELEASE.md` en la raíz del repo — guía paso a paso:
1. Una vez: `keytool -genkey` → `~/.musarum/release.jks` + `mobile/keystore.properties`.
2. Secrets de GitHub Actions: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, (opcional) `MUSARUM_BASE_URL`.
3. `git tag android-v1.0.3 && git push origin android-v1.0.3` → el workflow firma la APK y crea la GitHub Release.
4. `git tag backend-v1.0.0` / `emendatium-v1.0.0` → publican imágenes a GHCR; Watchtower en el portátil actualiza en 30 min.

---

## 11. GLOSARIO DE TÉRMINOS INTERNOS

- **OWNER**: usuario con permisos admin (gestiona usuarios, carpetas, borra canciones). Hoy solo hay uno, se crea desde `.env`.
- **MEMBER**: usuario normal. Puede escuchar, crear playlists, favoritos.
- **Carpeta paraguas**: la raíz montada en Docker como volumen (`MUSARUM_MUSIC_PATH`). Dentro vive la música + `_covers`/`_lyrics`/`_videos`/`_profilepictures`.
- **Sub-carpeta activa**: entrada en `library_folders` con `active=true`. El scanner solo recorre las activas.
- **Pending delete**: canción en el buffer de 5s del `LibraryWatcher` tras un DELETE del filesystem — si llega CREATE con mismo hash → cancela el borrado.
- **Pre-buffer**: 2s de margen antes del crossfade teórico para que el ExoPlayer aux tenga tiempo de llegar a `STATE_READY`.
- **Camelot**: notación DJ para tonalidades musicales (1A..12B).
- **Píldora**: la barra de modos en NowPlaying/Lyrics/Info/Vídeo (`ModePill` composable).

---

## 12. CÓMO MANTENER ESTE FICHERO

Después de cada bloque completado, actualizar:
- Sección **5** (lo que ya está hecho) añadiendo una línea nueva.
- Sección **6** (pendientes) tachando o eliminando lo recién terminado.
- Sección **7** (decisiones) si se tomó una decisión nueva relevante.
- Sección **8** (bugs cazados) si descubrimos uno nuevo que podría volver.

Nada más. No reescribir todo el fichero; mantenerlo acumulativo.
