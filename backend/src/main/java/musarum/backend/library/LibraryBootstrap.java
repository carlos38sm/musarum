package musarum.backend.library;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, si la tabla {@code library_folders} está vacía, siembra la
 * carpeta paraguas (MUSARUM_MUSIC_PATH) como activa. Así el escaneo inicial
 * en una instalación nueva funciona sin que el OWNER tenga que configurar
 * nada antes de reproducir su primera canción.
 *
 * Orden: 1 → corre ANTES del {@code LibraryWatcher.startWatching()} (que es
 * disparado por {@code ApplicationReadyEvent}) para que cuando el watcher
 * pregunte "qué carpetas están activas" ya haya una fila.
 */
@Component
@Order(1)
class LibraryBootstrap implements ApplicationRunner {

    private final LibraryFolderService folderService;

    LibraryBootstrap(LibraryFolderService folderService) {
        this.folderService = folderService;
    }

    @Override
    public void run(ApplicationArguments args) {
        folderService.seedUmbrellaIfEmpty();
    }
}
