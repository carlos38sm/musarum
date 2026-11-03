package musarum.backend.enrichment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import musarum.backend.enrichment.dto.BatchResultLine;
import musarum.backend.enrichment.exception.BatchEnrichmentException;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;

@Component
class JsonlReader {

    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(JsonlReader.class);

    JsonlReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<BatchResultLine> read(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            return lines.stream()
                    .filter(line -> !line.isBlank())
                    .map(this::parseLine)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new BatchEnrichmentException("No se pudo leer el archivo de resultados: " + filePath, e);
        }
    }

    private BatchResultLine parseLine(String line) {
        try {
            return objectMapper.readValue(line, BatchResultLine.class);
        } catch (Exception e) {
            log.error("No se pudo parsear línea del JSONL: {} | Error: {}", line, e.getMessage());
            return null;
        }
    }
}