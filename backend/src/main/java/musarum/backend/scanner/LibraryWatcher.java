package musarum.backend.scanner;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import musarum.backend.library.LibraryFolderService;
import musarum.backend.song.SongService;

@Component
public class LibraryWatcher {

    private static final Logger log = LoggerFactory.getLogger(LibraryWatcher.class);

    // Debounce para borrados: cuando el watcher ve un DELETE, no borra la
    // canción al instante. La marca aquí con un timestamp y un @Scheduled
    // revisa la lista cada pocos segundos. Si en ese intervalo llega un
    // CREATE con el mismo content_hash (p. ej. porque el usuario renombró
    // el archivo), relocateByHash reubica la fila sin perder favoritos ni
    // playlists, y nosotros borramos la entrada del mapa.
    //
    // Reemplaza el antiguo O(N²): antes handleDelete rehasheaba TODO el disco
    // para ver si el contenido seguía existiendo en otra ruta. Con 7000
    // archivos eso era inviable.
    private static final long PENDING_DELETE_MS = 5_000L;
    private final Map<String, Long> pendingDeletesByPath = new ConcurrentHashMap<>();

    private final Path musicFolder;
    private final LibraryScanner libraryScanner;
    private final SongService songService;
    private final FileHasher fileHasher;
    private final LibraryFolderService libraryFolderService;

    LibraryWatcher(@Value("${MUSARUM_MUSIC_PATH}") String musicPath,
                   LibraryScanner libraryScanner,
                   SongService songService,
                   FileHasher fileHasher,
                   LibraryFolderService libraryFolderService) {
        this.musicFolder = Path.of(musicPath);
        this.libraryScanner = libraryScanner;
        this.songService = songService;
        this.fileHasher = fileHasher;
        this.libraryFolderService = libraryFolderService;
    }

    /**
     * Si la ruta vigilada no cae bajo ninguna carpeta activa, ignoramos el
     * evento. Útil cuando el OWNER desactiva una sub-carpeta pero sigue
     * tocando archivos ahí: el watcher no los importa ni los borra.
     */
    private boolean isUnderActiveFolder(Path file) {
        List<Path> active = libraryFolderService.activePaths();
        Path abs = file.toAbsolutePath().normalize();
        return active.stream().anyMatch(root -> abs.startsWith(root.toAbsolutePath().normalize()));
    }

    // Si el último escaneo completo terminó hace menos de este umbral,
    // saltamos el escaneo inicial al arrancar. Evita que un restart cercano
    // (p. ej. durante despliegue) relance un escaneo de 5-10 min contra el
    // bind mount lento de Docker en Windows mientras el usuario intenta
    // reproducir una canción. El watcher sigue vigilando cambios en vivo,
    // así que los ficheros nuevos que aparezcan durante el "silencio" se
    // detectan por evento; sólo perdemos los que se añadieron MIENTRAS el
    // backend estaba apagado — y para eso el OWNER tiene el botón manual
    // "Escanear" en la app.
    private static final long SKIP_INITIAL_SCAN_IF_WITHIN_MIN = 10L;

    @EventListener(ApplicationReadyEvent.class)
    @Async("scannerExecutor")
    public void startWatching() {
        long sinceMin = libraryScanner.minutesSinceLastScan();
        if (sinceMin < SKIP_INITIAL_SCAN_IF_WITHIN_MIN) {
            log.info("Escaneo inicial OMITIDO (último fue hace {} min < {} min)",
                    sinceMin, SKIP_INITIAL_SCAN_IF_WITHIN_MIN);
        } else {
            log.info("Escaneo inicial al arranque (último fue hace {} min)...",
                    sinceMin == Long.MAX_VALUE ? "∞" : String.valueOf(sinceMin));
            try {
                libraryScanner.importNewSongs().join();
                log.info("Escaneo inicial completado");
            } catch (Exception e) {
                log.warn("Fallo en el escaneo inicial: {}", e.getMessage());
            }
        }

        log.info("Vigilancia de carpeta iniciada en hilo: {}", Thread.currentThread().getName());

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            // Antes solo se registraba la carpeta paraguas: los ficheros nuevos
            // dentro de sub-carpetas (p. ej. /music/Musica/NuevoArtista/x.mp3)
            // NO se detectaban hasta un rescan completo. Ahora recorremos cada
            // carpeta activa y registramos todas sus sub-carpetas a mano,
            // igual que inotify en Linux permite — WatchService de Java no
            // tiene flag recursivo, así que lo emulamos.
            //
            // Key → path: lo usamos en handleEvent para reconstruir la ruta
            // absoluta del fichero (WatchEvent.context() es solo el nombre).
            Map<WatchKey, Path> keyToPath = new ConcurrentHashMap<>();
            // Publicamos refs para dynamicRegister (se usan al crear
            // sub-directorios en caliente desde handleEvent).
            watchServiceRef = watchService;
            keyToPathRef = keyToPath;
            int registered = registerRecursive(watchService, keyToPath);
            log.info("WatchService: {} carpetas registradas recursivamente", registered);

            while (true) {
                WatchKey key = watchService.take();
                Path parentDir = keyToPath.get(key);
                if (parentDir == null) {
                    // Key perdida (poco frecuente): reseteamos y seguimos.
                    key.reset();
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) handleEvent(event, parentDir);
                boolean valid = key.reset();
                if (!valid) {
                    keyToPath.remove(key);
                    log.debug("Carpeta ya no vigilable: {}", parentDir);
                }
                // Si nos quedamos sin keys (p. ej. el user desactivó todas las
                // carpetas activas y Java invalidó las keys), re-registramos
                // para no caer en un busy-break.
                if (keyToPath.isEmpty()) {
                    registered = registerRecursive(watchService, keyToPath);
                    if (registered == 0) {
                        log.warn("Sin carpetas vigilables, deteniendo watcher");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error al iniciar la vigilancia de la carpeta", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Vigilancia interrumpida");
        }
    }

    /**
     * Registra en el WatchService cada carpeta ACTIVA y todas sus
     * sub-carpetas (recursivo, respetando las reservadas _covers/_lyrics/
     * _videos/_profilepictures). Devuelve el número de keys creadas.
     */
    private int registerRecursive(WatchService watchService, Map<WatchKey, Path> keyToPath) {
        int count = 0;
        for (Path root : libraryFolderService.activePaths()) {
            if (!Files.isDirectory(root)) continue;
            try {
                int before = count;
                count += walkAndRegister(watchService, root, keyToPath);
                log.debug("Watcher: {} sub-carpetas registradas bajo {}", count - before, root);
            } catch (IOException e) {
                log.warn("No se pudo registrar recursivamente {}: {}", root, e.getMessage());
            }
        }
        return count;
    }

    private int walkAndRegister(WatchService ws, Path root, Map<WatchKey, Path> keyToPath)
            throws IOException {
        int[] n = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                // Esquiva las carpetas internas de Musarum: no contienen audio
                // del usuario, solo recursos auto-gestionados.
                if (name.equals("_covers") || name.equals("_lyrics")
                        || name.equals("_videos") || name.equals("_profilepictures")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                try {
                    WatchKey key = dir.register(ws,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    keyToPath.put(key, dir);
                    n[0]++;
                } catch (IOException e) {
                    log.warn("No se pudo vigilar {}: {}", dir, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return n[0];
    }

    private void handleEvent(WatchEvent<?> event, Path parentDir) {
        WatchEvent.Kind<?> kind = event.kind();

        @SuppressWarnings("unchecked")
        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
        Path fileName = pathEvent.context();
        // Antes se resolvía siempre contra musicFolder (raíz) → al meter un
        // fichero en una sub-carpeta, fullPath apuntaba a la raíz con el
        // nombre y no existía en disco. Con el watcher recursivo cada key
        // conoce SU directorio padre y resolvemos contra él.
        Path fullPath = parentDir.resolve(fileName);

        // Si el evento es ENTRY_CREATE sobre un directorio nuevo, lo
        // registramos sobre la marcha para que sus audios futuros también
        // disparen eventos. Lo hacemos dentro de un flag "silencioso":
        // directorios nuevos no disparan handleCreate.
        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
            log.debug("Nuevo directorio detectado, lo añado a la vigilancia: {}", fullPath);
            // Reutilizamos la cola global de keys mediante newDynamicKeys;
            // walkAndRegister va a entrar al directorio y registrar los
            // sub-directorios que haya (aunque normalmente al crearse
            // estará vacío).
            dynamicRegister(fullPath);
            return;
        }

        if (!libraryScanner.isAudioFile(fullPath)) return;
        if (!isUnderActiveFolder(fullPath)) {
            log.debug("Evento ignorado (fuera de carpetas activas): {}", fullPath);
            return;
        }
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) handleCreate(fullPath);
        else if (kind == StandardWatchEventKinds.ENTRY_DELETE) handleDelete(fullPath);
    }

    // Referencia al WatchService y mapa activos para poder registrar
    // directorios creados en caliente. startWatching los asigna nada más
    // abrir el service; dynamicRegister los usa.
    private volatile WatchService watchServiceRef;
    private volatile Map<WatchKey, Path> keyToPathRef;

    private void dynamicRegister(Path dir) {
        WatchService ws = watchServiceRef;
        Map<WatchKey, Path> keyToPath = keyToPathRef;
        if (ws == null || keyToPath == null) return;
        try {
            walkAndRegister(ws, dir, keyToPath);
        } catch (IOException e) {
            log.warn("No se pudo registrar dinámicamente {}: {}", dir, e.getMessage());
        }
    }

    private void handleCreate(Path fullPath) {
        String normalizedPath = libraryScanner.normalize(fullPath);
        try {
            Thread.sleep(500); // esperar a que el archivo termine de copiarse
            String hash = fileHasher.hash(fullPath);

            boolean relocated = songService.relocateByHash(hash, normalizedPath);
            if (relocated) {
                log.info("Archivo reubicado (mismo contenido, nueva ruta): {}", normalizedPath);
                // No hace falta tocar pendingDeletesByPath aquí: relocateByHash
                // actualizó audio_path al nuevo, así que cuando flushPendingDeletes
                // intente borrar el oldPath, findEntityByAudioPath devolverá vacío
                // y no borra nada. Antes se hacía un clear() global que cancelaba
                // TODOS los pendientes aunque solo se hubiera reubicado uno — bug
                // visible solo al renombrar varios ficheros en bloque.
                return;
            }

            libraryScanner.importSingleSong(normalizedPath, hash);
            log.info("Archivo detectado e importado: {}", normalizedPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Error importando archivo detectado {}: {}", normalizedPath, e.getMessage());
        }
    }

    /**
     * No borra inmediatamente: encola la ruta en pendingDeletesByPath. Si en
     * los próximos PENDING_DELETE_MS llega un CREATE con el mismo hash
     * (rename), relocateByHash salva la fila y la limpiamos del mapa. Si
     * nadie reclama el hash, {@link #flushPendingDeletes()} la borra.
     */
    private void handleDelete(Path fullPath) {
        String normalizedPath = libraryScanner.normalize(fullPath);
        if (songService.findEntityByAudioPath(normalizedPath).isEmpty()) {
            log.warn("Se eliminó un archivo no registrado en base: {}", normalizedPath);
            return;
        }
        pendingDeletesByPath.put(normalizedPath, System.currentTimeMillis());
        log.info("Borrado pendiente ({}ms): {}", PENDING_DELETE_MS, normalizedPath);
    }

    /**
     * Barrido periódico de borrados en cuarentena. Si el archivo sigue sin
     * existir en disco pasados PENDING_DELETE_MS, borramos la fila. Si el
     * archivo reapareció (p. ej. el usuario deshizo), lo dejamos.
     */
    @Scheduled(fixedDelay = 2_000)
    void flushPendingDeletes() {
        if (pendingDeletesByPath.isEmpty()) return;
        long now = System.currentTimeMillis();
        pendingDeletesByPath.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < PENDING_DELETE_MS) return false;
            String path = entry.getKey();
            if (Files.exists(Path.of(path))) {
                log.info("Archivo reapareció, cancelo borrado: {}", path);
                return true;
            }
            songService.findEntityByAudioPath(path).ifPresent(song -> {
                songService.deleteByContentHash(song.getContentHash());
                log.info("Archivo eliminado, canción borrada de la base: {}", path);
            });
            return true;
        });
    }
}