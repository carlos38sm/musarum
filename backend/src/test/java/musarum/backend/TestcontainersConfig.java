package musarum.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levanta un Postgres real por test-run. El bean lo detecta Spring Boot
 * vía {@code @ServiceConnection} y auto-configura {@code spring.datasource.*}
 * contra ese contenedor, sin tocar application.properties ni envs.
 *
 * Usamos la misma versión de Postgres que el docker-compose del dueño
 * (postgres:17-alpine) para que queries nativas y feature-detection de
 * Hibernate casen con lo que verá producción.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
