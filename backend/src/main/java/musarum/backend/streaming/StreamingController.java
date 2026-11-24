package musarum.backend.streaming;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stream")
class StreamingController {

    private final StreamingService streamingService;

    StreamingController(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @GetMapping("/{songId}")
    ResponseEntity<Resource> stream(@PathVariable Long songId) {
        Resource audio = streamingService.loadAudio(songId);

        return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(mediaTypeFor(audio.getFilename())))
                .body(audio);
    }

    /**
     * Mapa extensión → MIME. Antes todo era "audio/mpeg", lo que rompía al
     * abrir URLs de FLAC/OGG/M4A fuera de ExoPlayer (que detecta por bytes
     * y pasaba, pero cualquier navegador o descargador fallaba).
     */
    private static String mediaTypeFor(String filename) {
        if (filename == null) return "audio/mpeg";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg") || lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a") || lower.endsWith(".aac")) return "audio/mp4";
        if (lower.endsWith(".aiff") || lower.endsWith(".aif")) return "audio/aiff";
        if (lower.endsWith(".wma")) return "audio/x-ms-wma";
        return "audio/mpeg";
    }
}