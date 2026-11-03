package musarum.backend.scanner;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
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

    @EventListener(ApplicationReadyEvent.class)
    @Async("scannerExecutor")
    public void startWatching() {
        log.info("Escaneo inicial al arranque...");
        try {
            libraryScanner.importNewSongs().join();
            log.info("Escaneo inicial completado");
        } catch (Exception e) {
            log.warn("Fallo en el escaneo inicial: {}", e.getMessage());
        }

        log.info("Vigilancia de carpeta iniciada en hilo: {}", Thread.currentThread().getName());

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            musicFolder.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) handleEvent(event);
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("La carpeta vigilada ya no es accesible, deteniendo vigilancia");
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Error al iniciar la vigilancia de la carpeta", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Vigilancia interrumpida");
        }
    }

    private void handleEvent(WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        @SuppressWarnings("unchecked")
        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
        Path fileName = pathEvent.context();
        Path fullPath = musicFolder.resolve(fileName);

        if (!libraryScanner.isAudioFile(fullPath)) return;
        if (!isUnderActiveFolder(fullPath)) {
            log.debug("Evento ignorado (fuera de carpetas activas): {}", fullPath);
            return;
        }
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) handleCreate(fullPath);
        else if (kind == StandardWatchEventKinds.ENTRY_DELETE) handleDelete(fullPath);
    }

    private void handleCreate(Path fullPath) {
        String normalizedPath = libraryScanner.normalize(fullPath);
        try {
            Thread.sleep(500); // esperar a que el archivo termine de copiarse
            String hash = fileHasher.hash(fullPath);

            boolean relocated = songService.relocateByHash(hash, normalizedPath);
            if (relocated) {
                log.info("Archivo reubicado (mismo contenido, nueva ruta): {}", normalizedPath);
                // Si llegó este CREATE porque el usuario está renombrando y
                // el DELETE todavía está pendiente, cancelamos ese borrado:
                // la fila sigue viva (ahora apuntando a la nueva ruta).
                pendingDeletesByPath.values().removeIf(ts -> false); // no-op legible
                pendingDeletesByPath.clear();
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