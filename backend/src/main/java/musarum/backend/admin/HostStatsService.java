package musarum.backend.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OperatingSystem;

/**
 * Lee métricas del SO con OSHI. Dos usos:
 *   1) CPU % sistema — necesita DOS lecturas de "ticks" espaciadas en el
 *      tiempo. Mantenemos un snapshot previo por {@link CentralProcessor}
 *      para que la primera petición del móvil ya pueda devolver una cifra
 *      razonable (baseline tomado en {@link #init()} al arrancar el bean).
 *   2) Red y disco IO: delta entre la lectura anterior y la actual, sobre
 *      el tiempo transcurrido. Si el móvil pide snapshots muy seguidos la
 *      velocidad instantánea tiene granularidad fina; si pasa mucho tiempo
 *      entre uno y otro, se promedia sobre ese intervalo.
 *
 * Es un {@code @Service} singleton: todos los móviles ven los MISMOS
 * valores porque leen el mismo buffer rodante. Así la velocidad de red
 * no "parpadea" por varios clientes pidiendo a destiempo.
 */
@Service
public class HostStatsService {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private final CentralProcessor processor = hardware.getProcessor();

    // Snapshot previo para calcular % CPU (OSHI exige dos lecturas).
    private final AtomicReference<long[]> prevTicks =
            new AtomicReference<>(processor.getSystemCpuLoadTicks());

    // Snapshot previo para calcular velocidades de red y disco IO.
    private final AtomicReference<IoSnapshot> prevIo =
            new AtomicReference<>(takeIoSnapshot());

    // ── Historial (30 min) para sparklines del panel Máquina ────────────
    // Buffer circular con 180 muestras cada 10s. El @Scheduled del método
    // sampleHistory lo rellena; GET /admin/server/host-history lo lee en
    // orden cronológico (más antigua → más reciente). Se mantiene APARTE
    // del prevIo del endpoint ad-hoc {@link #snapshot()} para que las
    // peticiones del móvil en "modo live" (cada 2s) no distorsionen los
    // intervalos de muestreo de 10s del scheduler.
    private static final int HISTORY_SIZE = 180;
    private static final long HISTORY_INTERVAL_MS = 10_000L;
    private final HostHistorySample[] history = new HostHistorySample[HISTORY_SIZE];
    private int historyHead = 0;   // próxima posición a escribir
    private int historyFilled = 0; // nº de muestras válidas (0..HISTORY_SIZE)
    private final Object historyLock = new Object();

    private final AtomicReference<long[]> prevHistoryTicks =
            new AtomicReference<>(processor.getSystemCpuLoadTicks());
    private final AtomicReference<IoSnapshot> prevHistoryIo =
            new AtomicReference<>(takeIoSnapshot());

    /**
     * Toma una muestra ligera cada {@value #HISTORY_INTERVAL_MS}ms y la
     * añade al buffer circular. Agrega red y disco a UNA suma por
     * tipo — el móvil pinta sparklines globales, no por interfaz.
     */
    @Scheduled(fixedRate = HISTORY_INTERVAL_MS, initialDelay = HISTORY_INTERVAL_MS)
    public void sampleHistory() {
        long now = System.currentTimeMillis();

        long[] ticks = processor.getSystemCpuLoadTicks();
        double cpu = processor.getSystemCpuLoadBetweenTicks(prevHistoryTicks.getAndSet(ticks));
        if (Double.isNaN(cpu) || cpu < 0) cpu = 0.0;

        GlobalMemory mem = hardware.getMemory();
        long memTotal = mem.getTotal();
        long memUsed = Math.max(0, memTotal - mem.getAvailable());

        IoSnapshot current = takeIoSnapshot();
        IoSnapshot prev = prevHistoryIo.getAndSet(current);
        long elapsedMs = Math.max(1L, current.capturedAtMs - prev.capturedAtMs);

        long rxBps = 0L, txBps = 0L, readBps = 0L, writeBps = 0L;
        for (NetworkCounter nc : current.network) {
            NetworkCounter before = prev.networkByName(nc.name);
            if (before != null) {
                rxBps += Math.max(0L, nc.bytesReceived - before.bytesReceived) * 1000L / elapsedMs;
                txBps += Math.max(0L, nc.bytesSent - before.bytesSent) * 1000L / elapsedMs;
            }
        }
        for (DiskCounter dc : current.disks) {
            DiskCounter before = prev.diskByName(dc.name);
            if (before != null) {
                readBps += Math.max(0L, dc.readBytes - before.readBytes) * 1000L / elapsedMs;
                writeBps += Math.max(0L, dc.writeBytes - before.writeBytes) * 1000L / elapsedMs;
            }
        }

        HostHistorySample s = new HostHistorySample(
                now, cpu, memUsed, memTotal, rxBps, txBps, readBps, writeBps);
        synchronized (historyLock) {
            history[historyHead] = s;
            historyHead = (historyHead + 1) % HISTORY_SIZE;
            if (historyFilled < HISTORY_SIZE) historyFilled++;
        }
    }

    /**
     * Devuelve el buffer circular en orden cronológico (más antigua
     * primero). Si el backend arrancó hace menos de 30 min devolverá
     * menos muestras — el móvil las pinta tal cual.
     */
    public HostHistory history() {
        synchronized (historyLock) {
            List<HostHistorySample> out = new ArrayList<>(historyFilled);
            int start = (historyHead - historyFilled + HISTORY_SIZE) % HISTORY_SIZE;
            for (int i = 0; i < historyFilled; i++) {
                out.add(history[(start + i) % HISTORY_SIZE]);
            }
            return new HostHistory(HISTORY_INTERVAL_MS, HISTORY_SIZE, out);
        }
    }

    public HostStats snapshot() {
        long now = System.currentTimeMillis();

        // CPU % sistema (entre esta llamada y la anterior)
        long[] ticks = processor.getSystemCpuLoadTicks();
        double cpuLoadSystem = processor.getSystemCpuLoadBetweenTicks(prevTicks.getAndSet(ticks));
        if (Double.isNaN(cpuLoadSystem) || cpuLoadSystem < 0) cpuLoadSystem = 0.0;

        // Memoria física
        GlobalMemory mem = hardware.getMemory();
        long memTotal = mem.getTotal();
        long memAvailable = mem.getAvailable();
        long memUsed = Math.max(0, memTotal - memAvailable);

        // Swap
        VirtualMemory vm = mem.getVirtualMemory();
        long swapTotal = vm.getSwapTotal();
        long swapUsed = vm.getSwapUsed();

        // Uptime del SISTEMA (no de la JVM) — segundos desde boot.
        long osUptimeSeconds = os.getSystemUptime();

        // Red + disco (deltas vs. la lectura anterior)
        IoSnapshot current = takeIoSnapshot();
        IoSnapshot previous = prevIo.getAndSet(current);
        long elapsedMs = Math.max(1, current.capturedAtMs - previous.capturedAtMs);

        List<NetworkInterfaceStats> netInterfaces = new ArrayList<>();
        for (NetworkCounter nc : current.network) {
            NetworkCounter before = previous.networkByName(nc.name);
            long rxBytesPerSec = 0, txBytesPerSec = 0;
            if (before != null) {
                long dRx = Math.max(0, nc.bytesReceived - before.bytesReceived);
                long dTx = Math.max(0, nc.bytesSent - before.bytesSent);
                rxBytesPerSec = (dRx * 1000L) / elapsedMs;
                txBytesPerSec = (dTx * 1000L) / elapsedMs;
            }
            netInterfaces.add(new NetworkInterfaceStats(
                    nc.name,
                    nc.displayName,
                    nc.ipv4,
                    nc.ipv6,
                    nc.up,
                    nc.speedBitsPerSec,
                    nc.bytesReceived,
                    nc.bytesSent,
                    rxBytesPerSec,
                    txBytesPerSec
            ));
        }

        List<DiskIoStats> diskIo = new ArrayList<>();
        for (DiskCounter dc : current.disks) {
            DiskCounter before = previous.diskByName(dc.name);
            long readBps = 0, writeBps = 0;
            if (before != null) {
                long dR = Math.max(0, dc.readBytes - before.readBytes);
                long dW = Math.max(0, dc.writeBytes - before.writeBytes);
                readBps = (dR * 1000L) / elapsedMs;
                writeBps = (dW * 1000L) / elapsedMs;
            }
            diskIo.add(new DiskIoStats(
                    dc.name,
                    dc.model,
                    dc.sizeBytes,
                    dc.readBytes,
                    dc.writeBytes,
                    readBps,
                    writeBps
            ));
        }

        // CPU del proceso Java (útil para ver cuánto consume Musarum solo)
        double cpuLoadProcess = os.getProcess(os.getProcessId()).getProcessCpuLoadCumulative();
        if (Double.isNaN(cpuLoadProcess) || cpuLoadProcess < 0) cpuLoadProcess = 0.0;

        int threadCount = Thread.activeCount();

        return new HostStats(
                now,
                os.toString(),
                processor.getProcessorIdentifier().getName(),
                processor.getLogicalProcessorCount(),
                cpuLoadSystem,
                cpuLoadProcess,
                memUsed,
                memTotal,
                swapUsed,
                swapTotal,
                osUptimeSeconds,
                threadCount,
                netInterfaces,
                diskIo
        );
    }

    private IoSnapshot takeIoSnapshot() {
        long t = System.currentTimeMillis();
        List<NetworkCounter> net = new ArrayList<>();
        for (NetworkIF nif : hardware.getNetworkIFs()) {
            nif.updateAttributes();
            String ipv4 = nif.getIPv4addr().length > 0 ? nif.getIPv4addr()[0] : null;
            String ipv6 = nif.getIPv6addr().length > 0 ? nif.getIPv6addr()[0] : null;
            // Filtramos interfaces loopback / sin IP para no llenar el panel
            // de ruido. Tailscale, Wi-Fi y Ethernet siempre pasan.
            if (ipv4 == null && ipv6 == null) continue;
            if ("lo".equals(nif.getName()) || nif.getName().startsWith("loopback")) continue;
            net.add(new NetworkCounter(
                    nif.getName(),
                    nif.getDisplayName(),
                    ipv4,
                    ipv6,
                    nif.getIfOperStatus() == NetworkIF.IfOperStatus.UP,
                    nif.getSpeed(),
                    nif.getBytesRecv(),
                    nif.getBytesSent()
            ));
        }
        List<DiskCounter> disks = new ArrayList<>();
        for (HWDiskStore d : hardware.getDiskStores()) {
            d.updateAttributes();
            disks.add(new DiskCounter(
                    d.getName(),
                    d.getModel(),
                    d.getSize(),
                    d.getReadBytes(),
                    d.getWriteBytes()
            ));
        }
        return new IoSnapshot(t, net, disks);
    }

    // ── Estructuras internas ───────────────────────────────────────────

    private record NetworkCounter(
            String name, String displayName, String ipv4, String ipv6,
            boolean up, long speedBitsPerSec,
            long bytesReceived, long bytesSent
    ) {}

    private record DiskCounter(
            String name, String model, long sizeBytes,
            long readBytes, long writeBytes
    ) {}

    private record IoSnapshot(
            long capturedAtMs,
            List<NetworkCounter> network,
            List<DiskCounter> disks
    ) {
        NetworkCounter networkByName(String n) {
            for (NetworkCounter c : network) if (c.name.equals(n)) return c;
            return null;
        }
        DiskCounter diskByName(String n) {
            for (DiskCounter c : disks) if (c.name.equals(n)) return c;
            return null;
        }
    }

    // ── DTOs públicos (los usa AdminController) ─────────────────────────

    public record HostStats(
            long capturedAtMs,
            String osName,
            String cpuModel,
            int cpuThreads,
            double cpuLoadSystem,     // 0.0..1.0
            double cpuLoadProcess,    // 0.0..1.0 del proceso Java
            long memUsedBytes,
            long memTotalBytes,
            long swapUsedBytes,
            long swapTotalBytes,
            long osUptimeSeconds,
            int jvmThreadCount,
            List<NetworkInterfaceStats> networkInterfaces,
            List<DiskIoStats> diskIo
    ) {}

    public record NetworkInterfaceStats(
            String name,
            String displayName,
            String ipv4,
            String ipv6,
            boolean up,
            long speedBitsPerSec,
            long totalRxBytes,
            long totalTxBytes,
            long rxBytesPerSec,
            long txBytesPerSec
    ) {}

    public record DiskIoStats(
            String name,
            String model,
            long sizeBytes,
            long totalReadBytes,
            long totalWriteBytes,
            long readBytesPerSec,
            long writeBytesPerSec
    ) {}

    // ── Historial (sparklines del panel Máquina) ─────────────────────────

    public record HostHistorySample(
            long capturedAtMs,
            double cpuLoadSystem,     // 0..1
            long memUsedBytes,
            long memTotalBytes,
            long rxBytesPerSec,       // suma de todas las interfaces
            long txBytesPerSec,
            long readBytesPerSec,     // suma de todos los discos
            long writeBytesPerSec
    ) {}

    public record HostHistory(
            long sampleIntervalMs,
            int capacity,
            List<HostHistorySample> samples
    ) {}
}
