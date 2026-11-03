package musarum.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import musarum.backend.shared.ConsoleDesign;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BackendApplication.class);
        app.setBanner(new ConsoleDesign());
        app.run(args);	
    }
}