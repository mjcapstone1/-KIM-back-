package depth.finvibe.boot.config;

import depth.finvibe.shared.config.AppConfig;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppConfig config;

    public WebMvcConfig(AppConfig config) {
        this.config = config;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        List<String> origins = config.allowedOrigins();

        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}