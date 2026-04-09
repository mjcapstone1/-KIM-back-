package depth.finvibe.investment.modules.trade.api.external;

import depth.finvibe.investment.modules.trade.application.service.TradeService;
import depth.finvibe.investment.modules.wallet.application.service.WalletService;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController {
    private final TradeService tradeService;
    private final WalletService walletService;
    private final AuthService authService;

    public TradeController(TradeService tradeService, WalletService walletService, AuthService authService) {
        this.tradeService = tradeService;
        this.walletService = walletService;
        this.authService = authService;
    }

    @GetMapping("/api/v1/simulator/orders")
    public Object orders(@RequestHeader(name = "Authorization", required = false) String authorization,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String kind) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", tradeService.listOrders(currentUser.userId(), status, kind));
    }

    @PostMapping("/api/v1/simulator/orders")
    public Object createOrder(@RequestHeader(name = "Authorization", required = false) String authorization,
                              @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> order = tradeService.createOrder(currentUser.userId(), parseOrderBody(body == null ? new LinkedHashMap<>() : body), false);
        return Maps.of(
                "message", "주문이 접수되었습니다.",
                "order", order,
                "wallet", walletService.getWalletSummary(currentUser.userId())
        );
    }

    @PostMapping("/api/v1/simulator/auto-orders")
    public Object createAutoOrder(@RequestHeader(name = "Authorization", required = false) String authorization,
                                  @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> order = tradeService.createOrder(currentUser.userId(), parseOrderBody(body == null ? new LinkedHashMap<>() : body), true);
        return Maps.of(
                "message", "자동 주문이 예약되었습니다.",
                "order", order,
                "wallet", walletService.getWalletSummary(currentUser.userId())
        );
    }

    @GetMapping("/api/v1/simulator/orders/{orderId}")
    public Object order(@PathVariable String orderId,
                        @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return tradeService.getOrder(currentUser.userId(), orderId);
    }

    @DeleteMapping("/api/v1/simulator/orders/{orderId}")
    public Object cancelOrder(@PathVariable String orderId,
                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> order = tradeService.cancelOrder(currentUser.userId(), orderId);
        return Maps.of(
                "message", "주문이 취소되었습니다.",
                "order", order,
                "wallet", walletService.getWalletSummary(currentUser.userId())
        );
    }

    @GetMapping("/trades/history")
    public Object tradeHistory(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return tradeService.listOrders(currentUser.userId(), null, null);
    }

    @GetMapping("/trades/reserved/stock-ids")
    public Object reservedStockIds(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return tradeService.reservedStockIds(currentUser.userId());
    }

    @GetMapping("/trades/users/{userId}/history")
    public Object userTradeHistory(@PathVariable String userId,
                                   @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        if (!userId.equals(currentUser.userId())) {
            throw ApiException.forbidden("FORBIDDEN", "본인 거래내역만 조회할 수 있습니다.");
        }
        return Maps.of("userId", userId, "items", tradeService.listOrders(userId, null, null));
    }

    @PostMapping("/trades")
    public Object tradesAlias(@RequestHeader(name = "Authorization", required = false) String authorization,
                              @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return tradeService.createOrder(currentUser.userId(), parseOrderBody(body == null ? new LinkedHashMap<>() : body), false);
    }

    @GetMapping("/trades/{tradeId}")
    public Object tradeAlias(@PathVariable String tradeId,
                             @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return tradeService.getOrder(currentUser.userId(), tradeId);
    }

    @DeleteMapping("/trades/{tradeId}")
    public Object cancelTradeAlias(@PathVariable String tradeId,
                                   @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return tradeService.cancelOrder(currentUser.userId(), tradeId);
    }

    private Map<String, Object> parseOrderBody(Map<String, Object> body) {
        String stockId = required(body, "stockId");
        String type = required(body, "type");
        String priceType = body.containsKey("priceType") ? required(body, "priceType") : "market";
        int quantity = Maps.intVal(body, "quantity");
        if (!List.of("buy", "sell").contains(type)) {
            throw ApiException.badRequest("INVALID_ORDER_TYPE", "type은 buy 또는 sell 이어야 합니다.");
        }
        if (quantity <= 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "quantity는 1 이상이어야 합니다.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stockId", stockId);
        payload.put("type", type);
        payload.put("priceType", priceType);
        payload.put("quantity", quantity);
        if (body.containsKey("price")) {
            payload.put("price", Maps.doubleVal(body, "price"));
        }
        if (body.containsKey("autoCondition")) {
            payload.put("autoCondition", required(body, "autoCondition"));
        }
        if (body.containsKey("triggerPrice")) {
            payload.put("triggerPrice", Maps.doubleVal(body, "triggerPrice"));
        }
        return payload;
    }

    private String required(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw ApiException.badRequest("INVALID_" + field.toUpperCase(), field + " 값이 필요합니다.");
        }
        return String.valueOf(value);
    }
}
