package depth.finvibe.manifest.modules.home.api.external;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    private final AppConfig config;
    private final AppState state;

    public HomeController(AppConfig config, AppState state) {
        this.config = config;
        this.state = state;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        return Maps.of(
                "service", config.appName(),
                "description", "업로드된 프론트 API 스펙과 참고 백엔드 구조를 반영한 Spring Boot 통합 백엔드입니다.",
                "health", "/health",
                "docs", "/docs",
                "version", config.appVersion(),
                "javaVersion", Runtime.version().toString()
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Maps.of(
                "status", "ok",
                "checkedAt", String.valueOf(state.getMarketStatus().get("checkedAt")),
                "service", config.appName()
        );
    }

    @GetMapping("/api/v1/home/screen")
    public Map<String, Object> homeScreen() {
        return state.getHomeScreen();
    }

    @GetMapping("/api/v1/home/indices")
    public Map<String, Object> homeIndices() {
        return Maps.of("items", state.getIndices());
    }

    @GetMapping("/api/v1/home/indices/{indexName}/chart")
    public Map<String, Object> indexChart(@PathVariable String indexName, @RequestParam(defaultValue = "30") int points) {
        int resolvedPoints = clamp(points, 5, 180);
        return Maps.of(
                "indexName", indexName,
                "points", resolvedPoints,
                "chartData", state.getIndexChart(indexName, resolvedPoints)
        );
    }

    @GetMapping("/api/v1/home/stock-rankings")
    public Map<String, Object> stockRankings(
            @RequestParam(defaultValue = "trading") String metric,
            @RequestParam(defaultValue = "all") String market,
            @RequestParam(defaultValue = "10") int limit
    ) {
        String normalizedMetric = switch (metric.toLowerCase()) {
            case "value" -> "trading";
            case "rising" -> "surge";
            case "falling" -> "drop";
            default -> metric.toLowerCase();
        };
        int resolvedLimit = clamp(limit, 1, 100);
        return Maps.of(
                "metric", normalizedMetric,
                "market", market,
                "items", state.getHomeRankings(normalizedMetric, market, resolvedLimit)
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
