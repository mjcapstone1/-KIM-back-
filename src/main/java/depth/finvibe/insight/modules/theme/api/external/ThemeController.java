package depth.finvibe.insight.modules.theme.api.external;

import depth.finvibe.shared.persistence.mongo.news.ThemeNewsDocument;
import depth.finvibe.shared.persistence.mongo.news.ThemeNewsRepository;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThemeController {
    private final AppState state;
    private final ThemeNewsRepository themeNewsRepository;

    public ThemeController(AppState state, ThemeNewsRepository themeNewsRepository) {
        this.state = state;
        this.themeNewsRepository = themeNewsRepository;
    }

    @GetMapping("/api/v1/home/themes")
    public Map<String, Object> themes(@RequestParam(defaultValue = "all") String category) {
        String normalized = normalizeCategory(category);
        return Maps.of("category", normalized, "items", state.listThemes(normalized));
    }

    @GetMapping("/api/v1/home/themes/{themeId}")
    public Map<String, Object> themeDetail(@PathVariable String themeId) {
        return Maps.of(
                "theme", state.getTheme(themeId),
                "chartData", state.getThemeChart(themeId, 30),
                "news", themeNewsItems(themeId)
        );
    }

    @GetMapping("/api/v1/home/themes/{themeId}/chart")
    public Map<String, Object> themeChart(@PathVariable String themeId,
                                          @RequestParam(defaultValue = "30") int days) {
        int resolvedDays = clamp(days, 5, 365);
        return Maps.of(
                "themeId", themeId,
                "days", resolvedDays,
                "chartData", state.getThemeChart(themeId, resolvedDays)
        );
    }

    @GetMapping("/api/v1/home/themes/{themeId}/news")
    public Map<String, Object> themeNews(@PathVariable String themeId) {
        return Maps.of(
                "themeId", themeId,
                "items", themeNewsItems(themeId)
        );
    }

    @GetMapping("/themes/today")
    public Object themesToday() {
        return state.listThemes("all");
    }

    @GetMapping("/themes/today/{categoryId}")
    public Object themesTodayByCategory(@PathVariable String categoryId) {
        String normalized = normalizeCategory(categoryId);
        if (List.of("all", "industry", "style").contains(normalized)) {
            return state.listThemes(normalized);
        }
        return state.getTheme(categoryId);
    }

    @GetMapping("/market/categories")
    public Object marketCategories() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(categorySummary("industry"));
        items.add(categorySummary("style"));
        return items;
    }

    @GetMapping("/market/categories/{categoryId}/change-rate")
    public Map<String, Object> categoryChangeRate(@PathVariable String categoryId) {
        Map<String, Object> summary = categorySummary(normalizeCategory(categoryId));
        return Maps.of(
                "categoryId", summary.get("id"),
                "averageChangeRate", summary.get("averageChangeRate")
        );
    }

    @GetMapping("/market/categories/{categoryId}/stocks")
    public Object categoryStocks(@PathVariable String categoryId) {
        String category = normalizeCategory(categoryId);
        List<Map<String, Object>> themes = state.listThemes(category);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> theme : themes) {
            String stockId = Maps.str(theme, "topStockId");
            if (stockId != null) {
                rows.add(state.getStockDetail(stockId));
            }
        }
        return rows;
    }

    private List<Map<String, Object>> themeNewsItems(String themeId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ThemeNewsDocument doc : themeNewsRepository.findByThemeIdOrderByPublishedAtDesc(themeId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", doc.getId());
            row.put("themeId", doc.getThemeId());
            row.put("themeName", doc.getThemeName());
            row.put("symbol", doc.getSymbol());
            row.put("publisher", doc.getPublisher());
            row.put("timeAgo", doc.getTimeAgo());
            row.put("title", doc.getTitle());
            row.put("summary", doc.getSummary());
            row.put("url", doc.getUrl());
            row.put("publishedAt", doc.getPublishedAt() == null ? null : doc.getPublishedAt().toString());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> categorySummary(String categoryId) {
        List<Map<String, Object>> themes = state.listThemes(categoryId);
        double total = 0.0;
        int count = 0;
        for (Map<String, Object> theme : themes) {
            String text = Maps.str(theme, "change", "0").replace("%", "");
            try {
                total += Double.parseDouble(text);
                count++;
            } catch (NumberFormatException ignored) {
            }
        }
        double average = count == 0 ? 0.0 : Math.round((total / count) * 100.0) / 100.0;
        return Maps.of(
                "id", categoryId,
                "name", switch (categoryId) {
                    case "industry" -> "산업";
                    case "style" -> "스타일";
                    default -> categoryId;
                },
                "themeCount", themes.size(),
                "averageChangeRate", average,
                "topThemes", themes.subList(0, Math.min(3, themes.size()))
        );
    }

    private String normalizeCategory(String category) {
        if (category == null) return "all";
        return switch (category.toLowerCase()) {
            case "industry", "style", "all" -> category.toLowerCase();
            default -> category.toLowerCase();
        };
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
