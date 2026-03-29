package depth.finvibe.manifest.modules.search.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.state.AppState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {
    private final AppState state;

    public SearchController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/search")
    public Object search(@RequestParam String query, @RequestParam(defaultValue = "6") int limit) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest("INVALID_QUERY", "query가 필요합니다.");
        }
        int resolvedLimit = Math.max(1, Math.min(30, limit));
        return state.search(query, resolvedLimit);
    }

    @GetMapping("/market/stocks/search")
    public Object stockSearch(@RequestParam(required = false) String query,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "20") int limit) {
        String resolvedQuery = (query == null || query.isBlank()) ? (keyword == null ? "" : keyword) : query;
        int resolvedLimit = Math.max(1, Math.min(100, limit));
        return state.stockSearchOnly(resolvedQuery, resolvedLimit);
    }
}
