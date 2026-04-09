package depth.finvibe.investment.modules.trade.application.service;

import depth.finvibe.investment.modules.wallet.application.service.WalletService;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.market.StockQueryService;
import depth.finvibe.shared.outbox.OutboxJdbcRepository;
import depth.finvibe.shared.persistence.investment.AssetEntity;
import depth.finvibe.shared.persistence.investment.AssetRepository;
import depth.finvibe.shared.persistence.investment.WalletEntity;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.mongo.feed.UserActivityFeedDocument;
import depth.finvibe.shared.persistence.mongo.feed.UserActivityFeedRepository;
import depth.finvibe.shared.persistence.trade.TradeExecutionEntity;
import depth.finvibe.shared.persistence.trade.TradeExecutionRepository;
import depth.finvibe.shared.persistence.trade.TradeOrderEntity;
import depth.finvibe.shared.persistence.trade.TradeOrderRepository;
import depth.finvibe.shared.util.Maps;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
    private final TradeOrderRepository tradeOrderRepository;
    private final TradeExecutionRepository tradeExecutionRepository;
    private final AssetRepository assetRepository;
    private final WalletService walletService;
    private final StockQueryService stockQueryService;
    private final OutboxJdbcRepository outboxJdbcRepository;
    private final UserActivityFeedRepository userActivityFeedRepository;

    public TradeService(
            TradeOrderRepository tradeOrderRepository,
            TradeExecutionRepository tradeExecutionRepository,
            AssetRepository assetRepository,
            WalletService walletService,
            StockQueryService stockQueryService,
            OutboxJdbcRepository outboxJdbcRepository,
            UserActivityFeedRepository userActivityFeedRepository
    ) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeExecutionRepository = tradeExecutionRepository;
        this.assetRepository = assetRepository;
        this.walletService = walletService;
        this.stockQueryService = stockQueryService;
        this.outboxJdbcRepository = outboxJdbcRepository;
        this.userActivityFeedRepository = userActivityFeedRepository;
    }

    public List<Map<String, Object>> listOrders(String userId, String status, String kind) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TradeOrderEntity order : tradeOrderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
            String orderKind = order.getAutoCondition() != null || "scheduled".equals(order.getPriceType()) ? "auto" : "manual";
            if (status != null && !status.equals(order.getOrderStatus())) {
                continue;
            }
            if (kind != null && !kind.equals(orderKind)) {
                continue;
            }
            rows.add(toOrderMap(order));
        }
        return rows;
    }

    public Map<String, Object> getOrder(String userId, String orderId) {
        TradeOrderEntity order = requireOrder(userId, orderId);
        return toOrderMap(order);
    }

    public List<String> reservedStockIds(String userId) {
        return tradeOrderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> "pending".equals(item.getOrderStatus()))
                .map(TradeOrderEntity::getStockId)
                .distinct()
                .toList();
    }

    @Transactional
    public Map<String, Object> createOrder(String userId, Map<String, Object> payload, boolean forcePending) {
        StockEntity stock = stockQueryService.requireStockEntity(Maps.str(payload, "stockId"));
        Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
        double marketPrice = Maps.doubleVal(snapshot, "price");
        String priceType = Maps.str(payload, "priceType", "market");
        double rawPrice = ("market".equals(priceType) || payload.get("price") == null) ? marketPrice : Maps.doubleVal(payload, "price");
        double roundedPrice = stockQueryService.roundPrice(stock, rawPrice);
        BigDecimal orderPrice = BigDecimal.valueOf(roundedPrice).setScale(4, RoundingMode.HALF_UP);
        BigDecimal quantity = BigDecimal.valueOf(Maps.intVal(payload, "quantity")).setScale(8, RoundingMode.HALF_UP);
        String side = Maps.str(payload, "type");
        String autoCondition = Maps.str(payload, "autoCondition");
        BigDecimal triggerPrice = payload.get("triggerPrice") == null ? null : BigDecimal.valueOf(Maps.doubleVal(payload, "triggerPrice")).setScale(4, RoundingMode.HALF_UP);
        String status = forcePending || "scheduled".equals(priceType) || autoCondition != null ? "pending" : "completed";
        long totalKrw = stockQueryService.resolvePriceKrw(stock, orderPrice.multiply(quantity).doubleValue());

        WalletEntity wallet = walletService.requireWallet(userId);
        TradeOrderEntity order = new TradeOrderEntity();
        order.setOrderId(nextOrderId());
        order.setUserId(userId);
        order.setStockId(stock.getStockId());
        order.setSide(side);
        order.setPriceType(priceType);
        order.setOrderPrice(orderPrice);
        order.setQuantity(quantity);
        order.setFilledQuantity("completed".equals(status) ? quantity : BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP));
        order.setRemainingQuantity("completed".equals(status) ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : quantity);
        order.setReservedAmountKrw(0L);
        order.setOrderStatus(status);
        order.setAutoCondition(autoCondition);
        order.setTriggerPrice(triggerPrice);
        order.setAcceptedAt(LocalDateTime.now());

        if ("pending".equals(status) && "buy".equals(side)) {
            if (wallet.getWithdrawableCashKrw() < totalKrw) {
                throw ApiException.conflict("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
            }
            wallet.setReservedCashKrw(wallet.getReservedCashKrw() + totalKrw);
            wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() - totalKrw);
            order.setReservedAmountKrw(totalKrw);
        }

        if ("completed".equals(status)) {
            if ("buy".equals(side)) {
                settleBuy(wallet, userId, stock, orderPrice, quantity, totalKrw, order.getOrderId());
            } else {
                settleSell(wallet, userId, stock, orderPrice, quantity, totalKrw, order.getOrderId());
            }
            order.setCompletedAt(LocalDateTime.now());
        }

        tradeOrderRepository.save(order);

        appendOrderCreatedEvent(order, stock, totalKrw);

        if ("pending".equals(status) && "buy".equals(side)) {
            walletService.appendWalletChangedEvent(
                    wallet,
                    userId,
                    "ORDER_RESERVED",
                    totalKrw,
                    "ORDER",
                    order.getOrderId(),
                    stock.getNameKr() + " 매수 예약"
            );
        }

        if ("completed".equals(status)) {
            appendOrderExecutedEvent(order, stock, totalKrw);
            appendPortfolioUpdatedEvent(userId, stock.getStockId(), order.getOrderId(), side, status);
        }

        return toOrderMap(order);
    }

    @Transactional
    public Map<String, Object> cancelOrder(String userId, String orderId) {
        TradeOrderEntity order = requireOrder(userId, orderId);
        if (!"pending".equals(order.getOrderStatus())) {
            throw ApiException.conflict("ORDER_NOT_CANCELABLE", "대기 중인 주문만 취소할 수 있습니다.");
        }

        WalletEntity wallet = walletService.requireWallet(userId);
        if (order.getReservedAmountKrw() > 0) {
            wallet.setReservedCashKrw(Math.max(0, wallet.getReservedCashKrw() - order.getReservedAmountKrw()));
            wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() + order.getReservedAmountKrw());
        }

        order.setOrderStatus("canceled");
        order.setCanceledAt(LocalDateTime.now());
        tradeOrderRepository.save(order);

        StockEntity stock = stockQueryService.requireStockEntity(order.getStockId());
        appendOrderCanceledEvent(order, stock);

        if (order.getReservedAmountKrw() > 0) {
            walletService.appendWalletChangedEvent(
                    wallet,
                    userId,
                    "ORDER_CANCELED",
                    order.getReservedAmountKrw(),
                    "ORDER",
                    order.getOrderId(),
                    stock.getNameKr() + " 주문 취소 환원"
            );
        }

        return toOrderMap(order);
    }

    private TradeOrderEntity requireOrder(String userId, String orderId) {
        return tradeOrderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: " + orderId));
    }

    private Map<String, Object> toOrderMap(TradeOrderEntity order) {
        StockEntity stock = stockQueryService.requireStockEntity(order.getStockId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", order.getOrderId());
        row.put("stockId", stock.getStockId());
        row.put("stockName", stock.getNameKr());
        row.put("type", order.getSide());
        row.put("priceType", order.getPriceType());
        row.put("price", order.getOrderPrice().doubleValue());
        row.put("quantity", order.getQuantity().stripTrailingZeros());
        row.put("total", order.getOrderPrice().multiply(order.getQuantity()).doubleValue());
        row.put("totalKrw", stockQueryService.resolvePriceKrw(stock, order.getOrderPrice().multiply(order.getQuantity()).doubleValue()));
        row.put("status", order.getOrderStatus());
        row.put("createdAt", order.getCreatedAt() == null ? null : order.getCreatedAt().toString());
        row.put("autoCondition", order.getAutoCondition());
        row.put("triggerPrice", order.getTriggerPrice() == null ? null : order.getTriggerPrice().doubleValue());
        row.put("kind", order.getAutoCondition() != null || "scheduled".equals(order.getPriceType()) ? "auto" : "manual");
        return row;
    }

    private String nextOrderId() {
        int next = tradeOrderRepository.findAll().stream()
                .map(TradeOrderEntity::getOrderId)
                .filter(id -> id.matches("order-\\d+"))
                .map(id -> Integer.parseInt(id.substring("order-".length())))
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
        return "order-" + String.format("%04d", next);
    }

    private String nextExecutionId() {
        int next = tradeExecutionRepository.findAll().stream()
                .map(TradeExecutionEntity::getExecutionId)
                .filter(id -> id.matches("exec-\\d+"))
                .map(id -> Integer.parseInt(id.substring("exec-".length())))
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
        return "exec-" + String.format("%04d", next);
    }

    private void settleBuy(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, String orderId) {
        if (wallet.getWithdrawableCashKrw() < totalKrw) {
            throw ApiException.conflict("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
        }

        wallet.setCashBalanceKrw(wallet.getCashBalanceKrw() - totalKrw);
        wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() - totalKrw);

        AssetEntity asset = assetRepository.findByUserIdAndStockId(userId, stock.getStockId()).orElse(null);
        if (asset == null) {
            asset = new AssetEntity();
            asset.setUserId(userId);
            asset.setStockId(stock.getStockId());
            asset.setFolderId(null);
            asset.setQuantity(quantity);
            asset.setAvgBuyPriceKrw(totalKrw / quantity.intValue());
            asset.setCurrentPriceKrw(stockQueryService.resolvePriceKrw(stock, orderPrice.doubleValue()));
            asset.setInvestedAmountKrw(totalKrw);
            asset.setRealizedPnlKrw(0L);
        } else {
            BigDecimal newQty = asset.getQuantity().add(quantity);
            long existingInvested = asset.getInvestedAmountKrw();
            long newInvested = existingInvested + totalKrw;
            asset.setQuantity(newQty);
            asset.setInvestedAmountKrw(newInvested);
            asset.setAvgBuyPriceKrw(Math.round((double) newInvested / newQty.doubleValue()));
            asset.setCurrentPriceKrw(stockQueryService.resolvePriceKrw(stock, orderPrice.doubleValue()));
        }
        assetRepository.save(asset);

        TradeExecutionEntity execution = new TradeExecutionEntity();
        execution.setExecutionId(nextExecutionId());
        execution.setOrderId(orderId);
        execution.setUserId(userId);
        execution.setStockId(stock.getStockId());
        execution.setSide("buy");
        execution.setExecutedPrice(orderPrice);
        execution.setExecutedQuantity(quantity);
        execution.setGrossAmountKrw(totalKrw);
        execution.setFeeKrw(0L);
        execution.setTaxKrw(0L);
        execution.setNetAmountKrw(totalKrw);
        execution.setExecutedAt(LocalDateTime.now());
        tradeExecutionRepository.save(execution);

        walletService.writeLedger(wallet, userId, "BUY_SETTLEMENT", "OUT", totalKrw, "ORDER", orderId, stock.getNameKr() + " 매수 체결");
        appendTradeFeed(userId, stock, orderId, "buy", quantity);
    }

    private void settleSell(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, String orderId) {
        AssetEntity asset = assetRepository.findByUserIdAndStockId(userId, stock.getStockId())
                .orElseThrow(() -> ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다."));

        if (asset.getQuantity().compareTo(quantity) < 0) {
            throw ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다.");
        }

        long avgPrice = asset.getAvgBuyPriceKrw();
        long costBasis = Math.round(avgPrice * quantity.doubleValue());
        long realizedPnl = totalKrw - costBasis;

        BigDecimal newQty = asset.getQuantity().subtract(quantity);
        if (newQty.compareTo(BigDecimal.ZERO) == 0) {
            assetRepository.delete(asset);
        } else {
            asset.setQuantity(newQty);
            asset.setInvestedAmountKrw(Math.max(0L, asset.getInvestedAmountKrw() - costBasis));
            asset.setCurrentPriceKrw(stockQueryService.resolvePriceKrw(stock, orderPrice.doubleValue()));
            asset.setRealizedPnlKrw(asset.getRealizedPnlKrw() + realizedPnl);
            assetRepository.save(asset);
        }

        wallet.setCashBalanceKrw(wallet.getCashBalanceKrw() + totalKrw);
        wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() + totalKrw);

        TradeExecutionEntity execution = new TradeExecutionEntity();
        execution.setExecutionId(nextExecutionId());
        execution.setOrderId(orderId);
        execution.setUserId(userId);
        execution.setStockId(stock.getStockId());
        execution.setSide("sell");
        execution.setExecutedPrice(orderPrice);
        execution.setExecutedQuantity(quantity);
        execution.setGrossAmountKrw(totalKrw);
        execution.setFeeKrw(0L);
        execution.setTaxKrw(0L);
        execution.setNetAmountKrw(totalKrw);
        execution.setExecutedAt(LocalDateTime.now());
        tradeExecutionRepository.save(execution);

        walletService.writeLedger(wallet, userId, "SELL_SETTLEMENT", "IN", totalKrw, "ORDER", orderId, stock.getNameKr() + " 매도 체결");
        appendTradeFeed(userId, stock, orderId, "sell", quantity);
    }

    private void appendTradeFeed(String userId, StockEntity stock, String orderId, String side, BigDecimal quantity) {
        UserActivityFeedDocument feed = new UserActivityFeedDocument();
        feed.setUserId(userId);
        feed.setType("TRADE_EXECUTED");
        feed.setTitle("buy".equals(side) ? "매수 체결" : "매도 체결");
        feed.setDescription(
                stock.getNameKr() + " " + quantity.stripTrailingZeros().toPlainString() + "주 "
                        + ("buy".equals(side) ? "매수 체결" : "매도 체결")
        );
        feed.setStockId(stock.getStockId());
        feed.setOrderId(orderId);
        userActivityFeedRepository.save(feed);
    }

    private void appendOrderCreatedEvent(TradeOrderEntity order, StockEntity stock, long totalKrw) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("userId", order.getUserId());
        payload.put("stockId", order.getStockId());
        payload.put("stockName", stock.getNameKr());
        payload.put("side", order.getSide());
        payload.put("priceType", order.getPriceType());
        payload.put("orderPrice", order.getOrderPrice().doubleValue());
        payload.put("quantity", order.getQuantity().doubleValue());
        payload.put("totalKrw", totalKrw);
        payload.put("status", order.getOrderStatus());
        payload.put("autoCondition", order.getAutoCondition());
        payload.put("triggerPrice", order.getTriggerPrice() == null ? null : order.getTriggerPrice().doubleValue());

        outboxJdbcRepository.append(
                "ORDER",
                order.getOrderId(),
                "finvibe.order.created",
                order.getUserId(),
                payload
        );
    }

    private void appendOrderExecutedEvent(TradeOrderEntity order, StockEntity stock, long totalKrw) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("userId", order.getUserId());
        payload.put("stockId", order.getStockId());
        payload.put("stockName", stock.getNameKr());
        payload.put("side", order.getSide());
        payload.put("executedPrice", order.getOrderPrice().doubleValue());
        payload.put("executedQuantity", order.getQuantity().doubleValue());
        payload.put("totalKrw", totalKrw);
        payload.put("completedAt", order.getCompletedAt() == null ? null : order.getCompletedAt().toString());

        outboxJdbcRepository.append(
                "ORDER",
                order.getOrderId(),
                "finvibe.order.executed",
                order.getUserId(),
                payload
        );
    }

    private void appendPortfolioUpdatedEvent(String userId, String stockId, String orderId, String side, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("stockId", stockId);
        payload.put("orderId", orderId);
        payload.put("side", side);
        payload.put("status", status);

        outboxJdbcRepository.append(
                "PORTFOLIO",
                userId,
                "finvibe.portfolio.updated",
                userId,
                payload
        );
    }

    private void appendOrderCanceledEvent(TradeOrderEntity order, StockEntity stock) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("userId", order.getUserId());
        payload.put("stockId", order.getStockId());
        payload.put("stockName", stock.getNameKr());
        payload.put("side", order.getSide());
        payload.put("status", order.getOrderStatus());
        payload.put("reservedAmountKrw", order.getReservedAmountKrw());
        payload.put("canceledAt", order.getCanceledAt() == null ? null : order.getCanceledAt().toString());

        outboxJdbcRepository.append(
                "ORDER",
                order.getOrderId(),
                "finvibe.order.canceled",
                order.getUserId(),
                payload
        );
    }
}