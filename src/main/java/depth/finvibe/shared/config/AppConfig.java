package depth.finvibe.shared.config;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {
    private static final String INSECURE_JWT_SECRET = "please-change-this-secret-key";

    @Value("${spring.application.name:FinVibe Spring Boot BFF}")
    private String appName;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${server.port:8080}")
    private int port;

    @Value("${finvibe.jwt.secret-key:please-change-this-secret-key}")
    private String jwtSecretKey;

    @Value("${finvibe.jwt.access-token-minutes:30}")
    private int accessTokenMinutes;

    @Value("${finvibe.jwt.refresh-token-days:14}")
    private int refreshTokenDays;

    @Value("${finvibe.kis.enabled:false}")
    private boolean kisEnabled;

    @Value("${finvibe.kis.base-url:https://openapi.koreainvestment.com:9443}")
    private String kisBaseUrl;

    @Value("${finvibe.kis.app-key:}")
    private String kisAppKey;

    @Value("${finvibe.kis.app-secret:}")
    private String kisAppSecret;

    @Value("${finvibe.kis.timeout-ms:5000}")
    private int kisTimeoutMs;

    @Value("${finvibe.data-dir:./runtime}")
    private String dataDir;

    @Value("${finvibe.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginsRaw;

    @PostConstruct
    public void validateProductionSettings() {
        if (!isProductionProfile()) {
            return;
        }
        if (jwtSecretKey == null || jwtSecretKey.isBlank() || INSECURE_JWT_SECRET.equals(jwtSecretKey) || jwtSecretKey.length() < 32) {
            throw new IllegalStateException("Production profile requires JWT_SECRET_KEY with at least 32 characters.");
        }
    }

    public String appName() {
        return appName;
    }

    public String appVersion() {
        return appVersion;
    }

    public int port() {
        return port;
    }

    public String jwtSecretKey() {
        return jwtSecretKey;
    }

    public int accessTokenMinutes() {
        return accessTokenMinutes;
    }

    public int refreshTokenDays() {
        return refreshTokenDays;
    }

    public boolean kisEnabled() {
        return kisEnabled && kisAppKey() != null && !kisAppKey().isBlank() && kisAppSecret() != null && !kisAppSecret().isBlank();
    }

    public String kisBaseUrl() {
        return kisBaseUrl;
    }

    public String kisAppKey() {
        return kisAppKey == null ? "" : kisAppKey;
    }

    public String kisAppSecret() {
        return kisAppSecret == null ? "" : kisAppSecret;
    }

    public int kisTimeoutMs() {
        return kisTimeoutMs;
    }

    public Path dataDir() {
        return Paths.get(dataDir).toAbsolutePath().normalize();
    }

    public List<String> allowedOrigins() {
        List<String> rows = new ArrayList<>();
        if (allowedOriginsRaw == null || allowedOriginsRaw.isBlank()) {
            return rows;
        }
        for (String item : allowedOriginsRaw.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank()) {
                rows.add(trimmed);
            }
        }
        return rows;
    }

    private boolean isProductionProfile() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        for (String profile : activeProfiles.split(",")) {
            if ("prod".equals(profile.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
