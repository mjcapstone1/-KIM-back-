package depth.finvibe.insight.modules.news.api.external;

import depth.finvibe.shared.http.ApiException;
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
public class NewsController {
    private final AppState state;

    public NewsController(AppState state) {
        this.state = state;
    }

    @GetMapping("/news")
    public Object news(@RequestParam(required = false) String themeId, @RequestParam(defaultValue = "20") int limit) {
        int resolvedLimit = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> rows = flattenNews(themeId);
        return rows.subList(0, Math.min(resolvedLimit, rows.size()));
    }

    @GetMapping("/news/{newsId}")
    public Object newsDetail(@PathVariable String newsId) {
        for (Map<String, Object> item : flattenNews(null)) {
            if (newsId.equals(Maps.str(item, "id"))) {
                return item;
            }
        }
        throw ApiException.notFound("NEWS_NOT_FOUND", "뉴스를 찾을 수 없습니다: " + newsId);
    }

    @GetMapping("/news/keywords/trending")
    public Object trendingKeywords() {
        return Maps.of("items", state.getTrendingKeywords());
    }

    private List<Map<String, Object>> flattenNews(String themeId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Map<String, Object>> themeMap = new LinkedHashMap<>();
        for (Map<String, Object> theme : state.listThemes("all")) {
            themeMap.put(Maps.str(theme, "id"), theme);
        }
        for (Map<String, Object> theme : state.listThemes("all")) {
            String currentThemeId = Maps.str(theme, "id");
            if (themeId != null && !themeId.equals(currentThemeId)) {
                continue;
            }
            for (Map<String, Object> item : state.getThemeNews(currentThemeId)) {
                Map<String, Object> row = new LinkedHashMap<>(item);
                row.put("themeId", currentThemeId);
                row.put("themeName", Maps.str(themeMap.get(currentThemeId), "name"));
                rows.add(row);
            }
        }
        return rows;
    }
}
