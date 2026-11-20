package musarum.backend.enrichment;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Contador compartido para seguir el progreso de "Re-enriquecer todo".
 * Como las {@code enrichAsync} viajan por el pool {@code scannerExecutor}
 * (múltiples hilos), usamos AtomicLong. El móvil lo consulta con un
 * endpoint tipo polling (igual que ScannerStatus).
 *
 * <p>Modelo simple:
 *   - {@code startRun(total)} resetea y marca inicio.
 *   - {@code onDone(ok)} lo incrementa cada vez que una canción termina
 *     (ok o error, ambos cuentan como "procesada").
 *   - {@code snapshot()} devuelve la foto actual.
 * El scanner llama a {@code startRun} cuando dispara masa, cada
 * {@code enrichAsync} termina llamando a {@code onDone}.
 */
@Component
public class EnrichmentProgress {

    private final AtomicLong total = new AtomicLong(0);
    private final AtomicLong processed = new AtomicLong(0);
    private final AtomicLong succeeded = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private volatile long startedAtMs = 0L;
    private volatile long finishedAtMs = 0L;

    public void startRun(long newTotal) {
        total.set(newTotal);
        processed.set(0);
        succeeded.set(0);
        failed.set(0);
        startedAtMs = System.currentTimeMillis();
        finishedAtMs = 0L;
    }

    public void onDone(boolean ok) {
        if (ok) succeeded.incrementAndGet();
        else failed.incrementAndGet();
        long p = processed.incrementAndGet();
        // Marcamos fin cuando llegamos al total. total==0 → no hay run
        // activo, así que no entramos aquí.
        long t = total.get();
        if (t > 0 && p >= t && finishedAtMs == 0L) {
            finishedAtMs = System.currentTimeMillis();
        }
    }

    public EnrichmentProgressDto snapshot() {
        long t = total.get();
        long p = processed.get();
        boolean running = t > 0 && p < t;
        return new EnrichmentProgressDto(
                running,
                t,
                p,
                succeeded.get(),
                failed.get(),
                startedAtMs == 0L ? null : startedAtMs,
                finishedAtMs == 0L ? null : finishedAtMs
        );
    }

    public record EnrichmentProgressDto(
            boolean running,
            long total,
            long processed,
            long succeeded,
            long failed,
            Long startedAtMs,
            Long finishedAtMs
    ) {}
}
