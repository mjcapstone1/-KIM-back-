package depth.finvibe.boot.config;

import depth.finvibe.shared.config.AppConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI finvibeOpenApi(AppConfig config) {
        return new OpenAPI().info(new Info()
                .title(config.appName())
                .version(config.appVersion())
                .description("React/Vite 프론트 연동용 통합 Spring Boot BFF"));
    }
}
