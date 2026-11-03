package musarum.backend.scanner;

public record ImportResult(
        int totalOnDisk,
        int imported,
        int failed,
        int deleted,
        int duplicates,
        int relocated,
        int hashErrors
) {}