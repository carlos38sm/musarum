package musarum.backend.auth;

import musarum.backend.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login end-to-end:
 *  - {@code OwnerBootstrap} crea el OWNER al arrancar con las credenciales
 *    de {@code application-test.properties} (owner / owner123).
 *  - POST /auth/login con las correctas → 200 + JWT.
 *  - POST /auth/login con una password incorrecta → 401.
 *
 * Esto cubre el camino que más usan los 4 usuarios en producción. Si algo
 * en Spring Security o en AuthService se rompe, lo detectamos aquí antes
 * de desplegar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void loginOk_returnsJwt() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick\":\"owner\",\"password\":\"owner123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick\":\"owner\",\"password\":\"noesesta\"}"))
                .andExpect(status().isUnauthorized());
    }
}
