package depth.finvibe.boot.config;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.market.MarketService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.JwtService;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.user.modules.user.application.service.UserStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceBeanConfig {
    @Bean
    public UserStore userStore(AppConfig config) {
        return new UserStore(config.dataDir());
    }

    @Bean
    public JwtService jwtService(AppConfig config) {
        return new JwtService(config);
    }

    @Bean
    public AuthService authService(JwtService jwtService, UserStore userStore) {
        return new AuthService(jwtService, userStore);
    }

    @Bean
    public MarketService marketService(AppConfig config) {
        return new MarketService(config);
    }

    @Bean
    public AppState appState(AppConfig config, MarketService marketService) {
        return new AppState(config.dataDir(), marketService);
    }
}
