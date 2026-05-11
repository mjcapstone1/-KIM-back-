package depth.finvibe.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.market.MarketService;
import depth.finvibe.shared.market.StockPriceStore;
import depth.finvibe.shared.market.StockQueryService;
import depth.finvibe.shared.persistence.market.StockRepository;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.JwtService;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.user.modules.user.application.service.UserService;
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
    public AuthService authService(JwtService jwtService, UserService userService) {
        return new AuthService(jwtService, userService);
    }

    @Bean
    public MarketService marketService(AppConfig config, StockPriceStore stockPriceStore) {
        return new MarketService(config, stockPriceStore);
    }

    @Bean
    public AppState appState(
            AppConfig config,
            MarketService marketService,
            StockQueryService stockQueryService,
            StockRepository stockRepository
    ) {
        return new AppState(config.dataDir(), marketService, stockQueryService, stockRepository);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
