package depth.finvibe.investment.modules.portfolio.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioController {
    private final AppState state;

    public PortfolioController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/simulator/portfolios")
    public Object portfolios() {
        return Maps.of("items", state.listPortfolios());
    }

    @PostMapping("/api/v1/simulator/portfolios")
    public Object createPortfolio(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        String name = required(request, "name");
        List<String> stocks = toStringList(request.get("stocks"));
        return Maps.of(
                "message", "포트폴리오가 생성되었습니다.",
                "portfolio", state.createPortfolio(name, stocks)
        );
    }

    @PatchMapping("/api/v1/simulator/portfolios/{portfolioId}")
    public Object updatePortfolio(@PathVariable String portfolioId,
                                  @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return Maps.of(
                "message", "포트폴리오가 수정되었습니다.",
                "portfolio", state.updatePortfolio(portfolioId,
                        request.containsKey("name") ? required(request, "name") : null,
                        request.containsKey("stocks") ? toStringList(request.get("stocks")) : null)
        );
    }

    @DeleteMapping("/api/v1/simulator/portfolios/{portfolioId}")
    public Object deletePortfolio(@PathVariable String portfolioId) {
        state.deletePortfolio(portfolioId);
        return Maps.of("message", "포트폴리오가 삭제되었습니다.", "portfolioId", portfolioId);
    }

    @GetMapping("/api/v1/simulator/portfolio-folders")
    public Object folders() {
        return Maps.of("items", state.listFolders());
    }

    @PostMapping("/api/v1/simulator/portfolio-folders")
    public Object createFolder(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return Maps.of(
                "message", "보관함이 생성되었습니다.",
                "folder", state.createFolder(required(request, "name"), request.containsKey("color") ? required(request, "color") : "#3b82f6")
        );
    }

    @PatchMapping("/api/v1/simulator/portfolio-folders/{folderId}")
    public Object updateFolder(@PathVariable String folderId,
                               @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return Maps.of(
                "message", "보관함이 수정되었습니다.",
                "folder", state.updateFolder(folderId,
                        request.containsKey("name") ? required(request, "name") : null,
                        request.containsKey("color") ? required(request, "color") : null)
        );
    }

    @DeleteMapping("/api/v1/simulator/portfolio-folders/{folderId}")
    public Object deleteFolder(@PathVariable String folderId) {
        state.deleteFolder(folderId);
        return Maps.of("message", "보관함이 삭제되었습니다.", "folderId", folderId);
    }

    @GetMapping("/api/v1/simulator/portfolio-holdings")
    public Object portfolioHoldings(@RequestParam(required = false) String folderId) {
        return Maps.of("folderId", folderId, "items", state.listHoldings(folderId));
    }

    @GetMapping("/portfolios")
    public Object portfoliosAlias() {
        return state.listPortfolios();
    }

    @PostMapping("/portfolios")
    public Object createPortfolioAlias(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return state.createPortfolio(required(request, "name"), toStringList(request.get("stocks")));
    }

    @PatchMapping("/portfolios/{portfolioId}")
    public Object updatePortfolioAlias(@PathVariable String portfolioId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return state.updatePortfolio(portfolioId,
                request.containsKey("name") ? required(request, "name") : null,
                request.containsKey("stocks") ? toStringList(request.get("stocks")) : null);
    }

    @DeleteMapping("/portfolios/{portfolioId}")
    public Object deletePortfolioAlias(@PathVariable String portfolioId) {
        state.deletePortfolio(portfolioId);
        return Maps.of("deleted", true, "portfolioId", portfolioId);
    }

    @GetMapping("/portfolios/{portfolioId}/assets")
    public Object portfolioAssets(@PathVariable String portfolioId) {
        Map<String, Object> portfolio = null;
        for (Map<String, Object> item : state.listPortfolios()) {
            if (portfolioId.equals(Maps.str(item, "id"))) {
                portfolio = item;
                break;
            }
        }
        if (portfolio == null) {
            throw ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + portfolioId);
        }
        List<String> wanted = toStringList(portfolio.get("stocks"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> holding : state.listHoldings(null)) {
            if (wanted.contains(Maps.str(holding, "id"))) {
                rows.add(holding);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        List<String> rows = new ArrayList<>();
        if (value == null) {
            return rows;
        }
        for (Object item : (List<Object>) value) {
            rows.add(String.valueOf(item));
        }
        return rows;
    }

    private String required(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw ApiException.badRequest("INVALID_" + field.toUpperCase(), field + " 값이 필요합니다.");
        }
        return String.valueOf(value);
    }
}
