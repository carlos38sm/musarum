package emendatium.backend.fingerprint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Identifica una canción por su audio: huella con Chromaprint + consulta a
 * AcoustID por el MBID exacto. Primer paso del enriquecimiento y el que hace
 * innecesario adivinar el título.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final ChromaprintClient chromaprint;
    private final AcoustIdClient acoustId;

    public Optional<AcoustIdMatch> identify(Path mp3) {
        Optional<AcoustIdMatch> match = chromaprint.fingerprint(mp3)
                .flatMap(acoustId::lookup);

        match.ifPresentOrElse(
                m -> log.info("🎯 '{}' → {} (score {})",
                        mp3.getFileName(), m.recordingMbid(), String.format("%.2f", m.score())),
                () -> log.info("🔍 Sin identificación por huella para '{}'", mp3.getFileName()));

        return match;
    }
}