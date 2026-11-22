package musarum.backend.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import musarum.backend.scanner.FileHasher;
import musarum.backend.scanner.LibraryScanner;
import musarum.backend.song.Song;
import musarum.backend.song.SongRepository;

/**
 * Endpoint OWNER para subir canciones desde el móvil al servidor sin
 * tocar la carpeta del host directamente. El fichero aterriza en
 * {@code MUSARUM_MUSIC_PATH/_inbox/}, se hashea con el mismo algoritmo
 * que el scanner y se importa en caliente. Emendatium (si está activo)
 * la renombrará y la moverá a la carpeta canónica por su cuenta. Si no
 * está, el .mp3 vive en {@code _inbox/} hasta que el OWNER la ordene a
 * mano o Emendatium arranque.
 *
 * <p>No duplicamos: antes de persistir, miramos si ya existe una fila
 * con el mismo content_hash. Si la hay, borramos el fichero recién
 * guardado y devolvemos {@code 409 Conflict} con el id existente.
 */
@RestController
@RequestMapping("/admin/songs")
class SongUploadController {

    private static final Logger log = LoggerFactory.getLogger(SongUploadController.class);
    private static final String INBOX_SUBDIR = "_inbox";

    private final Path musicFolder;
    private final LibraryScanner libraryScanner;
    private final FileHasher fileHasher;
    private final SongRepository songRepository;

    SongUploadController(@Value("${MUSARUM_MUSIC_PATH}") String musicPath,
                         LibraryScanner libraryScanner,
                         FileHasher fileHasher,
                         SongRepository songRepository) {
        this.musicFolder = Path.of(musicPath);
        this.libraryScanner = libraryScanner;
        this.fileHasher = fileHasher;
        this.songRepository = songRepository;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<UploadResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new UploadResult(null, null, "Archivo vacío", false));
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            original = "unnamed_" + System.currentTimeMillis();
        }
        String safeName = sanitizeFilename(original);
        if (!isAudioFilename(safeName)) {
            return ResponseEntity.badRequest().body(
                    new UploadResult(null, safeName, "Extensión no soportada", false));
        }

        // Bandeja de entrada dentro de la paraguas. El scanner la trata
        // como cualquier otra sub-carpeta y la imagen/portada embebida
        // se extraerá como siempre. Emendatium puede moverla luego.
        Path inbox = musicFolder.resolve(INBOX_SUBDIR);
        Files.createDirectories(inbox);
        Path target = uniquePath(inbox, safeName);
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Upload OWNER: fichero guardado en {}", target);

        String hash = fileHasher.hash(target);
        // ¿Ya existe en catálogo?
        Long existingId = songRepository.findByContentHash(hash)
                .map(Song::getId)
                .orElse(null);
        if (existingId != null) {
            // Duplicado exacto. El binario ya vive en otro sitio del
            // catálogo; borramos este para no dejar basura en _inbox.
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.warn("No se pudo borrar duplicado {} tras rechazar upload: {}",
                        target, e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new UploadResult(existingId, safeName, "Ya existe en el catálogo", false));
        }

        // Importar en caliente: una transacción corta, cabe aquí sin
        // invadir el scanner global. Si falla, dejamos el fichero en
        // _inbox para que el próximo scan complete la fila.
        String audioPath = target.toString().replace("\\", "/");
        try {
            libraryScanner.importSingleSong(audioPath, hash);
        } catch (Exception e) {
            log.warn("Upload: no se pudo importar {} en caliente: {}", target, e.getMessage());
            return ResponseEntity.accepted().body(
                    new UploadResult(null, safeName,
                            "Guardado — importará en el próximo escaneo", true));
        }

        Long songId = songRepository.findByContentHash(hash).map(Song::getId).orElse(null);
        return ResponseEntity.ok(new UploadResult(
                songId, safeName, "Importada", true));
    }

    private static String sanitizeFilename(String raw) {
        String base = raw.trim()
                .replace('\\', '/')
                .substring(raw.lastIndexOf('/') + 1);
        // Reemplaza caracteres problemáticos del filesystem sin
        // tocar los acentos (los soportan NTFS y ext4 sin drama).
        return base.replaceAll("[\\x00-\\x1F<>:\"|?*]", "_");
    }

    private static boolean isAudioFilename(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".m4a")
                || n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".opus")
                || n.endsWith(".aac") || n.endsWith(".wma") || n.endsWith(".aiff")
                || n.endsWith(".aif");
    }

    /**
     * Resuelve conflictos de nombre añadiendo " (2)", " (3)"... antes
     * de la extensión. Lo hace a nivel de FS para que el fichero quepa
     * siempre, sin pisar el que estuviera ahí.
     */
    private static Path uniquePath(Path dir, String name) {
        Path candidate = dir.resolve(name);
        if (!Files.exists(candidate)) return candidate;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 10_000; i++) {
            Path c = dir.resolve(stem + " (" + i + ")" + ext);
            if (!Files.exists(c)) return c;
        }
        // 10k colisiones ya es patológico; dejamos un sufijo con millis.
        return dir.resolve(stem + " (" + System.currentTimeMillis() + ")" + ext);
    }

    public record UploadResult(Long songId, String fileName, String message, boolean imported) {}
}
