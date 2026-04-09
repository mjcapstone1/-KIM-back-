package depth.finvibe.insight.modules.news.api.external;

import depth.finvibe.shared.http.ApiException;
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
public class NewsController {
    private final AppState state;
    private final ThemeNewsRepository themeNewsRepository;

    public NewsController(AppState state, ThemeNewsRepository themeNewsRepository) {
        this.state = state;
        this.themeNewsRepository = themeNewsRepository;
    }

    @GetMapping("/news")
    public Object news(@RequestParam(required = false) String themeId,
                       @RequestParam(defaultValue = "20") int limit) {
        int resolvedLimit = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> rows = flattenNews(themeId);
        return rows.subList(0, Math.min(resolvedLimit, rows.size()));
    }

    @GetMapping("/news/{newsId}")
    public Object newsDetail(@PathVariable String newsId) {
        ThemeNewsDocument doc = themeNewsRepository.findById(newsId)
                .orElseThrow(() -> ApiException.notFound("NEWS_NOT_FOUND", "뉴스를 찾을 수 없습니다: " + newsId));
        return toNewsRow(doc);
    }

    @GetMapping("/news/keywords/trending")
    public Object trendingKeywords() {
        return Maps.of("items", state.getTrendingKeywords());
    }

    private List<Map<String, Object>> flattenNews(String themeId) {
        List<ThemeNewsDocument> docs = (themeId == null || themeId.isBlank())
                ? themeNewsRepository.findAllByOrderByPublishedAtDesc()
                : themeNewsRepository.findByThemeIdOrderByPublishedAtDesc(themeId);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ThemeNewsDocument doc : docs) {
            rows.add(toNewsRow(doc));
        }
        return rows;
    }

    private Map<String, Object> toNewsRow(ThemeNewsDocument doc) {
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
        return row;
    }
}