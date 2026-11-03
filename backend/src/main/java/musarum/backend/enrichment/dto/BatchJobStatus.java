package musarum.backend.enrichment.dto;

public record BatchJobStatus(
        String jobId,
        String status,
        Integer totalFiles,
        Integer processed,
        Integer failed,
        Integer elapsedSeconds,
        Integer estimatedRemainingSeconds,
        String lastError,
        String outputFile
) {}