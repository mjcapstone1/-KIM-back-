package depth.finvibe.investment.modules.market.api.external;

import depth.finvibe.investment.modules.portfolio.application.service.PortfolioService;
import depth.finvibe.investment.modules.wallet.application.service.WalletService;
import depth.finvibe.shared.redis.RedisJsonCacheService;
import depth.finvibe.shared.redis.RedisKeys;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.user.modules.user.application.service.UserService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketController {
    private final AppState state;
    private final AuthService authService;
    private final UserService userService;
    private final PortfolioService portfolioService;
    private final WalletService walletService;
    private final RedisJsonCacheService cache;

    public MarketController(
            AppState state,
            AuthService authService,
            UserService userService,
            PortfolioService portfolioService,
            WalletService walletService,
            RedisJsonCacheService cache
    ) {
        this.state = state;
        this.authService = authService;
        this.userService = userService;
        this.portfolioService = portfolioService;
        this.walletService = walletService;
        this.cache = cache;
    }

    @GetMapping("/api/v1/simulator/screen")
    public Object simulatorScreen(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.optionalUser(authorization);
        Map<String, Object> payload = state.getSimulatorScreen();
        Map<String, Object> simulatorState = Maps.map(payload.get("state"));
        applyUserSimulatorState(simulatorState, currentUser);
        return payload;
    }

    @GetMapping("/api/v1/simulator/state")
    public Object simulatorState(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.optionalUser(authorization);
        Map<String, Object> payload = state.getSimulatorState();
        applyUserSimulatorState(payload, currentUser);
        return payload;
    }

    @GetMapping("/api/v1/simulator/market-status")
    public Object marketStatus() {
        return state.getMarketStatus();
    }

    @GetMapping("/api/v1/simulator/stocks")
    public Object stocks(@RequestParam(defaultValue = "all") String market,
                         @RequestParam(defaultValue = "") String query,
                         @RequestParam(defaultValue = "50") int limit) {
        int resolvedLimit = Math.max(1, Math.min(200, limit));

        return cache.getOrLoadMap(
                RedisKeys.stockList(market, query, resolvedLimit),
                Duration.ofSeconds(5),
                () -> Maps.of(
                        "market", market,
                        "query", query,
                        "items", state.listStocks(market, query, resolvedLimit)
                )
        );
    }

    @GetMapping("/api/v1/simulator/stocks/{stockId}")
    public Object stockDetail(@PathVariable String stockId) {
        return cache.getOrLoadMap(
                RedisKeys.stockDetail(stockId),
                Duration.ofSeconds(5),
                () -> state.getStockDetail(stockId)
        );
    }

    @GetMapping("/api/v1/simulator/stocks/{stockId}/screen")
    public Object stockScreen(@PathVariable String stockId,
                              @RequestParam(defaultValue = "day") String timeframe,
                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.optionalUser(authorization);
        Map<String, Object> payload = state.getStockScreen(stockId, timeframe);
        applyUserStockScreen(payload, stockId, currentUser);
        return payload;
    }

    @GetMapping("/api/v1/simulator/stocks/{stockId}/candles")
    public Object stockCandles(@PathVariable String stockId,
                               @RequestParam(defaultValue = "day") String timeframe,
                               @RequestParam(required = false) Integer points) {
        Integer resolvedPoints = points == null ? null : Math.max(5, Math.min(365, points));
        return Maps.of(
                "stockId", Maps.str(state.getStockDetail(stockId), "id"),
                "timeframe", timeframe,
                "chartData", state.getStockCandles(stockId, timeframe, resolvedPoints)
        );
    }

    @GetMapping("/api/v1/simulator/stocks/{stockId}/orderbook")
    public Object orderBook(@PathVariable String stockId) {
        return state.getOrderBook(stockId);
    }

    @GetMapping("/api/v1/simulator/stocks/{stockId}/holding")
    public Object holding(@PathVariable String stockId,
                          @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of(
                "stockId", Maps.str(state.getStockDetail(stockId), "id"),
                "holding", ownedStockForUser(stockId, currentUser)
        );
    }

    @GetMapping("/api/v1/simulator/favorites")
    public Object favorites(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", favoriteItemsForUser(currentUser.userId()));
    }

    @PostMapping("/api/v1/simulator/favorites/{stockId}")
    public Object addFavorite(@PathVariable String stockId,
                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        String canonical = addFavoriteForUser(stockId, currentUser.userId());
        List<Map<String, Object>> items = favoriteItemsForUser(currentUser.userId());
        cache.delete(RedisKeys.interestStocks(currentUser.userId()));
        cache.delete(RedisKeys.homeSummary(currentUser.userId()));

        return Maps.of(
                "message", "관심 종목에 추가되었습니다.",
                "stockId", canonical,
                "items", items
        );
    }

    @DeleteMapping("/api/v1/simulator/favorites/{stockId}")
    public Object removeFavorite(@PathVariable String stockId,
                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        String canonical = removeFavoriteForUser(stockId, currentUser.userId());
        List<Map<String, Object>> items = favoriteItemsForUser(currentUser.userId());
        cache.delete(RedisKeys.interestStocks(currentUser.userId()));
        cache.delete(RedisKeys.homeSummary(currentUser.userId()));

        return Maps.of(
                "message", "관심 종목에서 제거되었습니다.",
                "stockId", canonical,
                "items", items
        );
    }

    @GetMapping("/api/v1/simulator/watchlists")
    public Object watchlists(@RequestParam(defaultValue = "favorite") String tab,
                             @RequestParam(defaultValue = "all") String market,
                             @RequestHeader(name = "Authorization", required = false) String authorization) {
        if ("favorite".equals(tab)) {
            CurrentUser currentUser = authService.requireUser(authorization);
            List<Map<String, Object>> items = favoriteItemsForUser(currentUser.userId());
            if (!"all".equals(market)) {
                items.removeIf(item -> !market.equals(Maps.str(item, "type")));
            }
            return Maps.of("tab", tab, "market", market, "items", items);
        }
        return Maps.of("tab", tab, "market", market, "items", state.watchlist(tab, market));
    }

    @GetMapping("/market/stocks/closing-prices")
    public Object closingPrices(@RequestParam(required = false) List<String> stockIds) {
        List<String> ids = parseStockIds(stockIds);
        if (ids.isEmpty()) {
            for (Map<String, Object> item : state.listStocks("all", "", 10)) {
                ids.add(Maps.str(item, "id"));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String stockId : ids) {
            Map<String, Object> detail = state.getStockDetail(stockId);
            rows.add(Maps.of(
                    "stockId", Maps.str(detail, "id"),
                    "code", Maps.str(detail, "code"),
                    "name", Maps.str(detail, "name"),
                    "closingPrice", detail.get("price"),
                    "currency", detail.get("currency")
            ));
        }
        return rows;
    }

    @GetMapping("/market/stocks/top-by-value")
    public Object topByValue(@RequestParam(defaultValue = "10") int limit) {
        return state.getHomeRankings("trading", "all", Math.max(1, Math.min(100, limit)));
    }

    @GetMapping("/market/stocks/top-by-volume")
    public Object topByVolume(@RequestParam(defaultValue = "10") int limit) {
        return state.getHomeRankings("volume", "all", Math.max(1, Math.min(100, limit)));
    }

    @GetMapping("/market/stocks/top-rising")
    public Object topRising(@RequestParam(defaultValue = "10") int limit) {
        return state.getHomeRankings("surge", "all", Math.max(1, Math.min(100, limit)));
    }

    @GetMapping("/market/stocks/top-falling")
    public Object topFalling(@RequestParam(defaultValue = "10") int limit) {
        return state.getHomeRankings("drop", "all", Math.max(1, Math.min(100, limit)));
    }

    @GetMapping("/market/status")
    public Object marketStatusAlias() {
        return state.getMarketStatus();
    }

    @GetMapping("/market/indexes/{indexName}/candles")
    public Object marketIndexCandles(@PathVariable String indexName, @RequestParam(defaultValue = "30") int points) {
        return state.getIndexChart(indexName, Math.max(5, Math.min(180, points)));
    }

    @GetMapping("/market/stocks/{stockId}/candles")
    public Object marketStockCandles(@PathVariable String stockId,
                                     @RequestParam(defaultValue = "day") String timeframe,
                                     @RequestParam(required = false) Integer points) {
        Integer resolvedPoints = points == null ? null : Math.max(5, Math.min(365, points));
        return state.getStockCandles(stockId, timeframe, resolvedPoints);
    }

    @GetMapping("/market/stocks/{stockId}")
    public Object marketStockDetail(@PathVariable String stockId) {
        return state.getStockDetail(stockId);
    }

    @GetMapping("/assets/top-100")
    public Object top100() {
        return state.listStocks("all", "", 100);
    }

    @PostMapping("/api/v1/dev/reset")
    public Object resetPost() {
        state.reset();
        return Maps.of("message", "시드 데이터 기준으로 상태를 초기화했습니다.");
    }

    @GetMapping("/api/v1/dev/reset")
    public Object resetGet() {
        state.reset();
        return Maps.of("message", "시드 데이터 기준으로 상태를 초기화했습니다.");
    }

    private void applyUserSimulatorState(Map<String, Object> payload, CurrentUser currentUser) {
        if (currentUser == null) {
            payload.put("favorites", List.of());
            payload.put("portfolios", List.of());
            payload.put("wallet", null);
            return;
        }
        payload.put("favorites", userService.listFavoriteStockIds(currentUser.userId()));
        payload.put("portfolios", portfolioService.listPortfolios(currentUser.userId()));
        payload.put("wallet", walletService.getWalletSummary(currentUser.userId()));
    }

    private void applyUserStockScreen(Map<String, Object> payload, String stockId, CurrentUser currentUser) {
        if (currentUser == null) {
            payload.put("ownedStock", null);
            payload.put("ownedQuantity", 0);
            payload.put("folders", List.of());
            payload.put("portfolioHoldings", List.of());
            payload.put("wallet", null);
            return;
        }
        List<Map<String, Object>> holdings = portfolioService.listHoldings(currentUser.userId(), null);
        Map<String, Object> owned = ownedStockFromHoldings(stockId, holdings);
        payload.put("ownedStock", owned);
        payload.put("ownedQuantity", owned == null ? 0 : Maps.intVal(owned, "quantity"));
        payload.put("folders", portfolioService.listFolders(currentUser.userId()));
        payload.put("portfolioHoldings", holdings);
        payload.put("wallet", walletService.getWalletSummary(currentUser.userId()));
    }

    private List<Map<String, Object>> favoriteItemsForUser(String userId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String stockId : userService.listFavoriteStockIds(userId)) {
            rows.add(state.getStockDetail(stockId));
        }
        return rows;
    }

    private String addFavoriteForUser(String stockId, String userId) {
        String canonical = Maps.str(state.resolveStock(stockId), "id");
        userService.addFavoriteStock(userId, canonical);
        return canonical;
    }

    private String removeFavoriteForUser(String stockId, String userId) {
        String canonical = Maps.str(state.resolveStock(stockId), "id");
        userService.removeFavoriteStock(userId, canonical);
        return canonical;
    }

    private Map<String, Object> ownedStockForUser(String stockId, CurrentUser currentUser) {
        return ownedStockFromHoldings(stockId, portfolioService.listHoldings(currentUser.userId(), null));
    }

    private Map<String, Object> ownedStockFromHoldings(String stockId, List<Map<String, Object>> holdings) {
        String canonical = Maps.str(state.getStockDetail(stockId), "id");
        for (Map<String, Object> item : holdings) {
            if (canonical.equals(Maps.str(item, "id"))) {
                return item;
            }
        }
        return null;
    }

    private List<String> parseStockIds(List<String> stockIds) {
        List<String> parsed = new ArrayList<>();
        if (stockIds == null) {
            return parsed;
        }
        for (String item : stockIds) {
            if (item == null) continue;
            for (String part : item.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    parsed.add(trimmed);
                }
            }
        }
        return parsed;
    }
}
