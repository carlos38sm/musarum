package musarum.backend.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import musarum.backend.scanner.exception.FileHashException;

/**
 * Firma del contenido del fichero. Antes se hasheaba el fichero ENTERO con
 * SHA-256; para 7000 MP3 de ~10 MB eso son ~70 GB de IO (20-40 min en HDD).
 *
 * Ahora: SHA-256 del primer 1 MB + ":" + tamaño del fichero. Dos ficheros
 * MP3 reales con el mismo primer MB (cabecera ID3 + arranque del audio) Y
 * el mismo tamaño exacto son el mismo fichero con probabilidad práctica
 * del 100%. El coste cae de O(tamaño_total) a O(número_de_ficheros).
 *
 * El hash resultante NO es compatible con los generados por la versión
 * antigua, así que el primer escaneo tras actualizar tratará todas las
 * canciones existentes como "nuevas" y las re-importará (la UNIQUE sobre
 * audio_path hace relocate en vez de duplicar; el content_hash se actualiza).
 */
@Component
class FileHasher {

    private static final int BUFFER_SIZE = 8192;
    private static final long SAMPLE_BYTES = 1024L * 1024L; // 1 MB

    String hash(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = Files.size(filePath);

            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = Math.min(size, SAMPLE_BYTES);
                int bytesRead;
                while (remaining > 0
                        && (bytesRead = is.read(buffer, 0,
                                (int) Math.min(buffer.length, remaining))) != -1) {
                    digest.update(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }

            String partialHex = HexFormat.of().formatHex(digest.digest());
            return partialHex + ":" + size;

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new FileHashException(filePath, e);
        }
    }
}