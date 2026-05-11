package depth.finvibe.manifest.modules.search.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.persistence.mongo.feed.SearchLogDocument;
import depth.finvibe.shared.persistence.mongo.feed.SearchLogRepository;
import depth.finvibe.shared.redis.RedisJsonCacheService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {
    private final AppState state;
    private final RedisJsonCacheService cache;
    private final AuthService authService;
    private final SearchLogRepository searchLogRepository;

    public SearchController(AppState state,
                            RedisJsonCacheService cache,
                            AuthService authService,
                            SearchLogRepository searchLogRepository) {
        this.state = state;
        this.cache = cache;
        this.authService = authService;
        this.searchLogRepository = searchLogRepository;
    }

    @GetMapping("/api/v1/search")
    public Object search(@RequestHeader(name = "Authorization", required = false) String authorization,
                         @RequestParam String query,
                         @RequestParam(defaultValue = "6") int limit) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest("INVALID_QUERY", "query가 필요합니다.");
        }

        int resolvedLimit = Math.max(1, Math.min(30, limit));
        cache.incrementKeywordScore(query);
        saveSearchLog(authorization, query, "/api/v1/search");

        return state.search(query, resolvedLimit);
    }

    @GetMapping("/api/v1/search/popular-keywords")
    public Object popularKeywords(@RequestParam(defaultValue = "10") int limit) {
        int resolvedLimit = Math.max(1, Math.min(30, limit));
        return Maps.of("items", cache.topKeywords(resolvedLimit));
    }

    @GetMapping("/market/stocks/search")
    public Object stockSearch(@RequestHeader(name = "Authorization", required = false) String authorization,
                              @RequestParam(required = false) String query,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "20") int limit) {
        String resolvedQuery = (query == null || query.isBlank()) ? (keyword == null ? "" : keyword) : query;
        int resolvedLimit = Math.max(1, Math.min(100, limit));

        if (!resolvedQuery.isBlank()) {
            saveSearchLog(authorization, resolvedQuery, "/market/stocks/search");
        }

        return state.stockSearchOnly(resolvedQuery, resolvedLimit);
    }

    private void saveSearchLog(String authorization, String query, String source) {
        SearchLogDocument doc = new SearchLogDocument();
        doc.setUserId(optionalUserId(authorization));
        doc.setQuery(query);
        doc.setSource(source);
        searchLogRepository.save(doc);
    }

    private String optionalUserId(String authorization) {
        try {
            CurrentUser currentUser = authService.optionalUser(authorization);
            return currentUser == null ? null : currentUser.userId();
        } catch (Exception ignored) {
            return null;
        }
    }
}