package musarum.backend.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.library.dto.AddFolderRequest;
import musarum.backend.library.dto.DiscoveredFolderDto;
import musarum.backend.library.dto.LibraryFolderDto;
import musarum.backend.library.exception.FolderNotFoundException;
import musarum.backend.library.exception.FolderOutsideUmbrellaException;
import musarum.backend.song.SongService;

/**
 * Gestiona la lista de sub-carpetas que el OWNER tiene activas.
 *
 * Reglas de dominio:
 *  - Toda carpeta añadida debe estar bajo la carpeta "paraguas" (MUSARUM_MUSIC_PATH).
 *  - La carpeta paraguas en sí también se puede registrar: útil si prefieres
 *    "escanear todo" sin discriminar sub-carpetas. OwnerBootstrap la siembra
 *    por defecto en el primer arranque.
 *  - Al desactivar una carpeta, las canciones ya importadas no se tocan; el
 *    scanner simplemente deja de visitarla en los siguientes escaneos.
 */
@Service
public class LibraryFolderService {

    private static final Logger log = LoggerFactory.getLogger(LibraryFolderService.class);

    private final LibraryFolderRepository repository;
    private final SongService songService;
    private final Path umbrella;

    public LibraryFolderService(LibraryFolderRepository repository,
                                SongService songService,
                                @Value("${MUSARUM_MUSIC_PATH}") String umbrella) {
        this.repository = repository;
        this.songService = songService;
        this.umbrella = Path.of(umbrella).toAbsolutePath().normalize();
    }

    /** Carpeta paraguas (lo que ve Docker como volumen). */
    public Path umbrellaPath() {
        return umbrella;
    }

    /** Rutas activas que el scanner debe recorrer. */
    @Transactional(readOnly = true)
    public List<Path> activePaths() {
        List<LibraryFolder> active = repository.findAllByActiveTrueOrderByPathAsc();
        return active.stream()
                .map(f -> Path.of(f.getPath()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LibraryFolderDto> listAll() {
        return repository.findAllByOrderByPathAsc().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Devuelve sub-carpetas inmediatas de la paraguas que aún no están en
     * DB — las "candidatas" a añadir. Carpetas que empiezan por "_" (p. ej.
     * {@code _covers}, {@code _lyrics}, {@code _videos}) se filtran porque
     * son generadas por Musarum, no contienen música del usuario.
     */
    public List<DiscoveredFolderDto> listDiscoverable() {
        if (!Files.isDirectory(umbrella)) return List.of();
        Set<String> registered = new HashSet<>(
                repository.findAllByOrderByPathAsc().stream()
                        .map(LibraryFolder::getPath)
                        .collect(Collectors.toSet())
        );
        try (Stream<Path> entries = Files.list(umbrella)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> !registered.contains(p.toString().replace("\\", "/")))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .map(p -> new DiscoveredFolderDto(
                            p.toString().replace("\\", "/"),
                            p.getFileName().toString()
                    ))
                    .toList();
        } catch (IOException e) {
            log.warn("No se pudieron listar sub-carpetas de {}: {}", umbrella, e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public LibraryFolderDto add(AddFolderRequest request) {
        Path requested = Path.of(request.path()).toAbsolutePath().normalize();
        if (!requested.startsWith(umbrella)) {
            throw new FolderOutsideUmbrellaException(requested.toString(), umbrella.toString());
        }
        String normalized = requested.toString().replace("\\", "/");
        LibraryFolder folder = repository.findByPath(normalized).orElseGet(() ->
                repository.save(LibraryFolder.builder()
                        .path(normalized)
                        .active(true)
                        .build())
        );
        if (!folder.isActive()) {
            folder.setActive(true);
        }
        return toDto(folder);
    }

    @Transactional
    public LibraryFolderDto setActive(Long id, boolean active) {
        LibraryFolder folder = repository.findById(id)
                .orElseThrow(() -> new FolderNotFoundException(id));
        folder.setActive(active);
        return toDto(folder);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new FolderNotFoundException(id);
        repository.deleteById(id);
    }

    /**
     * Siembra la carpeta paraguas como activa si la tabla está vacía. Lo
     * llama {@code LibraryBootstrap} al arrancar: así en una instalación
     * nueva el scanner tiene algo que escanear sin que el OWNER tenga que
     * configurar nada antes.
     */
    @Transactional
    public void seedUmbrellaIfEmpty() {
        if (repository.count() > 0) return;
        String normalized = umbrella.toString().replace("\\", "/");
        repository.save(LibraryFolder.builder()
                .path(normalized)
                .active(true)
                .build());
        log.info("Carpeta de biblioteca sembrada: {} (paraguas)", normalized);
    }

    private LibraryFolderDto toDto(LibraryFolder f) {
        Path p = Path.of(f.getPath());
        return new LibraryFolderDto(
                f.getId(),
                f.getPath(),
                p.getFileName() != null ? p.getFileName().toString() : f.getPath(),
                f.isActive(),
                Files.isDirectory(p),
                songService.countByAudioPathPrefix(f.getPath())
        );
    }
}
