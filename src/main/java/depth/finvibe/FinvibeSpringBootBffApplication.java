package depth.finvibe;

import depth.finvibe.boot.config.ServiceBeanConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import(ServiceBeanConfig.class)
public class FinvibeSpringBootBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinvibeSpringBootBffApplication.class, args);
    }
}