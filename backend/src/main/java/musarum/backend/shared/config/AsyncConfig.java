package musarum.backend.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// EnableScheduling activa @Scheduled(fixedDelay=...) — lo usa LibraryWatcher
// para procesar el buffer de borrados pendientes cada 2 segundos.
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "scannerExecutor")
    Executor scannerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // Antes 50: "Re-enriquecer todo" encola ~3000 tareas de golpe
        // y la 51ª disparaba TaskRejectedException → el controller
        // devolvía 500 al móvil. Con 10 000 cabe toda una biblioteca
        // incluso duplicada (watcher + re-enrich simultáneos).
        executor.setQueueCapacity(10_000);
        // Red de seguridad: si por lo que sea la cola se llenase, en
        // lugar de rechazar y propagar excepción, el hilo que
        // encola (el controller) ejecuta la tarea en sync. Eso
        // ralentiza el encolado pero evita el 500.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("scanner-");
        executor.initialize();
        return executor;
    }
}