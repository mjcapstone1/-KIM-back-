package depth.finvibe.investment.modules.trade.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController {
    private final AppState state;

    public TradeController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/simulator/orders")
    public Object orders(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String kind) {
        return Maps.of("items", normalizeOrders(state.listOrders(status, kind)));
    }

    @PostMapping("/api/v1/simulator/orders")
    public Object createOrder(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> order = normalizeOrder(state.createOrder(parseOrderBody(body == null ? new LinkedHashMap<>() : body), false));
        return Maps.of(
                "message", "주문이 접수되었습니다.",
                "order", order,
                "wallet", state.getWalletSummary()
        );
    }

    @PostMapping("/api/v1/simulator/auto-orders")
    public Object createAutoOrder(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> order = normalizeOrder(state.createOrder(parseOrderBody(body == null ? new LinkedHashMap<>() : body), true));
        return Maps.of(
                "message", "자동 주문이 예약되었습니다.",
                "order", order,
                "wallet", state.getWalletSummary()
        );
    }

    @GetMapping("/api/v1/simulator/orders/{orderId}")
    public Object order(@PathVariable String orderId) {
        return normalizeOrder(state.getOrder(orderId));
    }

    @DeleteMapping("/api/v1/simulator/orders/{orderId}")
    public Object cancelOrder(@PathVariable String orderId) {
        Map<String, Object> order = normalizeOrder(state.cancelOrder(orderId));
        return Maps.of(
                "message", "주문이 취소되었습니다.",
                "order", order,
                "wallet", state.getWalletSummary()
        );
    }

    @GetMapping("/trades/history")
    public Object tradeHistory() {
        return normalizeOrders(state.listOrders(null, null));
    }

    @GetMapping("/trades/reserved/stock-ids")
    public Object reservedStockIds() {
        return state.reservedStockIds();
    }

    @GetMapping("/trades/users/{userId}/history")
    public Object userTradeHistory(@PathVariable String userId) {
        return Maps.of("userId", userId, "items", normalizeOrders(state.listOrders(null, null)));
    }

    @PostMapping("/trades")
    public Object tradesAlias(@RequestBody(required = false) Map<String, Object> body) {
        return normalizeOrder(state.createOrder(parseOrderBody(body == null ? new LinkedHashMap<>() : body), false));
    }

    @GetMapping("/trades/{tradeId}")
    public Object tradeAlias(@PathVariable String tradeId) {
        return normalizeOrder(state.getOrder(tradeId));
    }

    @DeleteMapping("/trades/{tradeId}")
    public Object cancelTradeAlias(@PathVariable String tradeId) {
        return normalizeOrder(state.cancelOrder(tradeId));
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

    private List<Map<String, Object>> normalizeOrders(List<Map<String, Object>> items) {
        items.replaceAll(this::normalizeOrder);
        return items;
    }

    private Map<String, Object> normalizeOrder(Map<String, Object> item) {
        Map<String, Object> row = new LinkedHashMap<>(item);
        row.put("kind", item.get("autoCondition") != null || "scheduled".equals(Maps.str(item, "priceType")) ? "auto" : "manual");
        return row;
    }

    private String required(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw ApiException.badRequest("INVALID_" + field.toUpperCase(), field + " 값이 필요합니다.");
        }
        return String.valueOf(value);
    }
}
