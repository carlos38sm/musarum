package musarum.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: Spring Boot arranca de verdad contra un Postgres real
 * (Testcontainers), Flyway aplica V1..Vn y Hibernate valida el schema.
 *
 * Si alguien añade una entidad sin migración acompañante, este test falla
 * aquí — es nuestra red de seguridad para "funciona en mi máquina" vs
 * "revienta al arrancar el contenedor real".
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Boot + Flyway + Hibernate.validate. Si llega hasta aquí, todo
        // está alineado con la DB real.
    }
}
