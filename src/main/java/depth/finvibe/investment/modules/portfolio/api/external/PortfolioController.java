package depth.finvibe.investment.modules.portfolio.api.external;

import depth.finvibe.investment.modules.portfolio.application.service.PortfolioService;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final AuthService authService;

    public PortfolioController(PortfolioService portfolioService, AuthService authService) {
        this.portfolioService = portfolioService;
        this.authService = authService;
    }

    @GetMapping("/api/v1/simulator/portfolios")
    public Object portfolios(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", portfolioService.listPortfolios(currentUser.userId()));
    }

    @PostMapping("/api/v1/simulator/portfolios")
    public Object createPortfolio(@RequestHeader(name = "Authorization", required = false) String authorization,
                                  @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        String name = required(request, "name");
        List<String> stocks = toStringList(request.get("stocks"));
        return Maps.of(
                "message", "포트폴리오가 생성되었습니다.",
                "portfolio", portfolioService.createPortfolio(currentUser.userId(), name, stocks)
        );
    }

    @PatchMapping("/api/v1/simulator/portfolios/{portfolioId}")
    public Object updatePortfolio(@PathVariable String portfolioId,
                                  @RequestHeader(name = "Authorization", required = false) String authorization,
                                  @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return Maps.of(
                "message", "포트폴리오가 수정되었습니다.",
                "portfolio", portfolioService.updatePortfolio(currentUser.userId(), portfolioId,
                        request.containsKey("name") ? required(request, "name") : null,
                        request.containsKey("stocks") ? toStringList(request.get("stocks")) : null)
        );
    }

    @DeleteMapping("/api/v1/simulator/portfolios/{portfolioId}")
    public Object deletePortfolio(@PathVariable String portfolioId,
                                  @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        portfolioService.deletePortfolio(currentUser.userId(), portfolioId);
        return Maps.of("message", "포트폴리오가 삭제되었습니다.", "portfolioId", portfolioId);
    }

    @GetMapping("/api/v1/simulator/portfolio-folders")
    public Object folders(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", portfolioService.listFolders(currentUser.userId()));
    }

    @PostMapping("/api/v1/simulator/portfolio-folders")
    public Object createFolder(@RequestHeader(name = "Authorization", required = false) String authorization,
                               @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return Maps.of(
                "message", "보관함이 생성되었습니다.",
                "folder", portfolioService.createFolder(currentUser.userId(), required(request, "name"), request.containsKey("color") ? required(request, "color") : "#3b82f6")
        );
    }

    @PatchMapping("/api/v1/simulator/portfolio-folders/{folderId}")
    public Object updateFolder(@PathVariable String folderId,
                               @RequestHeader(name = "Authorization", required = false) String authorization,
                               @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return Maps.of(
                "message", "보관함이 수정되었습니다.",
                "folder", portfolioService.updateFolder(currentUser.userId(), folderId,
                        request.containsKey("name") ? required(request, "name") : null,
                        request.containsKey("color") ? required(request, "color") : null)
        );
    }

    @DeleteMapping("/api/v1/simulator/portfolio-folders/{folderId}")
    public Object deleteFolder(@PathVariable String folderId,
                               @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        portfolioService.deleteFolder(currentUser.userId(), folderId);
        return Maps.of("message", "보관함이 삭제되었습니다.", "folderId", folderId);
    }

    @GetMapping("/api/v1/simulator/portfolio-holdings")
    public Object portfolioHoldings(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @RequestParam(required = false) String folderId) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("folderId", folderId, "items", portfolioService.listHoldings(currentUser.userId(), folderId));
    }

    @GetMapping("/portfolios")
    public Object portfoliosAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return portfolioService.listPortfolios(currentUser.userId());
    }

    @PostMapping("/portfolios")
    public Object createPortfolioAlias(@RequestHeader(name = "Authorization", required = false) String authorization,
                                       @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return portfolioService.createPortfolio(currentUser.userId(), required(request, "name"), toStringList(request.get("stocks")));
    }

    @PatchMapping("/portfolios/{portfolioId}")
    public Object updatePortfolioAlias(@PathVariable String portfolioId,
                                       @RequestHeader(name = "Authorization", required = false) String authorization,
                                       @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        return portfolioService.updatePortfolio(currentUser.userId(), portfolioId,
                request.containsKey("name") ? required(request, "name") : null,
                request.containsKey("stocks") ? toStringList(request.get("stocks")) : null);
    }

    @DeleteMapping("/portfolios/{portfolioId}")
    public Object deletePortfolioAlias(@PathVariable String portfolioId,
                                       @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        portfolioService.deletePortfolio(currentUser.userId(), portfolioId);
        return Maps.of("deleted", true, "portfolioId", portfolioId);
    }

    @GetMapping("/portfolios/{portfolioId}/assets")
    public Object portfolioAssets(@PathVariable String portfolioId,
                                  @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return portfolioService.portfolioAssets(currentUser.userId(), portfolioId);
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
