package emendatium.backend.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient enricherRestClient(EnricherProperties props) {
        Duration timeout = Duration.ofSeconds(props.http().timeoutSeconds());

        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", props.http().userAgent())
                .build();
    }
}