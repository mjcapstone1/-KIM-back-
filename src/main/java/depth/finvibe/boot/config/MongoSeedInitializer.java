package depth.finvibe.boot.config;

import depth.finvibe.shared.persistence.mongo.news.NewsArticleDocument;
import depth.finvibe.shared.persistence.mongo.news.NewsArticleRepository;
import depth.finvibe.shared.persistence.mongo.news.ThemeNewsDocument;
import depth.finvibe.shared.persistence.mongo.news.ThemeNewsRepository;
import depth.finvibe.shared.util.FinvibeUtils;
import depth.finvibe.shared.util.Maps;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MongoSeedInitializer {
    private final ThemeNewsRepository themeNewsRepository;
    private final NewsArticleRepository newsArticleRepository;

    public MongoSeedInitializer(ThemeNewsRepository themeNewsRepository, NewsArticleRepository newsArticleRepository) {
        this.themeNewsRepository = themeNewsRepository;
        this.newsArticleRepository = newsArticleRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (themeNewsRepository.count() == 0) {
            seedThemeNews();
        }
        if (newsArticleRepository.count() == 0) {
            seedNewsArticlesFromThemeNews();
        }
    }

    private void seedThemeNews() {
        Map<String, Map<String, Object>> themeById = loadThemeMap();
        Map<String, Object> raw = Maps.map(FinvibeUtils.loadJsonResource("theme_news.json"));

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String themeId = entry.getKey();
            Map<String, Object> theme = themeById.getOrDefault(themeId, Map.of());
            List<Map<String, Object>> items = Maps.listOfMaps(entry.getValue());

            for (Map<String, Object> item : items) {
                ThemeNewsDocument doc = new ThemeNewsDocument();
                doc.setId(Maps.str(item, "id"));
                doc.setThemeId(themeId);
                doc.setThemeName(Maps.str(theme, "name"));
                doc.setSymbol(Maps.str(theme, "topStockId"));
                doc.setPublisher(Maps.str(item, "publisher"));
                doc.setTimeAgo(Maps.str(item, "timeAgo"));
                doc.setTitle(Maps.str(item, "title"));
                doc.setSummary(Maps.str(item, "summary"));
                doc.setUrl(Maps.str(item, "url"));
                doc.setPublishedAt(resolvePublishedAt(Maps.str(item, "timeAgo")));
                themeNewsRepository.save(doc);
            }
        }
    }

    private void seedNewsArticlesFromThemeNews() {
        List<ThemeNewsDocument> source = themeNewsRepository.findAllByOrderByPublishedAtDesc();
        for (ThemeNewsDocument item : source) {
            NewsArticleDocument doc = new NewsArticleDocument();
            doc.setId(item.getId());
            doc.setSymbol(item.getSymbol());
            doc.setPublisher(item.getPublisher());
            doc.setTitle(item.getTitle());
            doc.setSummary(item.getSummary());
            doc.setUrl(item.getUrl());
            doc.setPublishedAt(item.getPublishedAt());
            newsArticleRepository.save(doc);
        }
    }

    private Map<String, Map<String, Object>> loadThemeMap() {
        List<Map<String, Object>> themes = Maps.listOfMaps(FinvibeUtils.loadJsonResource("themes.json"));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> theme : themes) {
            result.put(Maps.str(theme, "id"), theme);
        }
        return result;
    }

    private LocalDateTime resolvePublishedAt(String timeAgo) {
        LocalDateTime now = LocalDateTime.now();
        if (timeAgo == null || timeAgo.isBlank()) {
            return now;
        }

        String text = timeAgo.trim();
        int number = extractNumber(text);

        if (text.contains("시간")) {
            return now.minusHours(number <= 0 ? 1 : number);
        }
        if (text.contains("일")) {
            return now.minusDays(number <= 0 ? 1 : number);
        }
        if (text.contains("분")) {
            return now.minusMinutes(number <= 0 ? 1 : number);
        }
        return now;
    }

    private int extractNumber(String text) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits.toString());
    }
}