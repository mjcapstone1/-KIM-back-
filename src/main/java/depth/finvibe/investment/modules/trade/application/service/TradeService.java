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
import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
    private static final Set<String> ALLOWED_SIDES = Set.of("buy", "sell");
    private static final Set<String> ALLOWED_PRICE_TYPES = Set.of("market", "limit", "scheduled");

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
        String stockId = Maps.str(payload, "stockId");
        if (stockId == null || stockId.isBlank()) {
            throw ApiException.badRequest("INVALID_STOCK_ID", "stockId 값이 필요합니다.");
        }
        StockEntity stock = stockQueryService.requireStockEntity(stockId.trim());
        Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
        double marketPrice = Maps.doubleVal(snapshot, "price");
        String priceType = Maps.str(payload, "priceType", "market").toLowerCase();
        if (!ALLOWED_PRICE_TYPES.contains(priceType)) {
            throw ApiException.badRequest("INVALID_PRICE_TYPE", "priceType은 market, limit, scheduled 중 하나여야 합니다.");
        }
        double rawPrice = ("market".equals(priceType) || payload.get("price") == null) ? marketPrice : Maps.doubleVal(payload, "price");
        double roundedPrice = stockQueryService.roundPrice(stock, rawPrice);
        if (roundedPrice <= 0) {
            throw ApiException.badRequest("INVALID_PRICE", "주문 가격은 0보다 커야 합니다.");
        }
        BigDecimal orderPrice = BigDecimal.valueOf(roundedPrice).setScale(4, RoundingMode.HALF_UP);
        int quantityValue = Maps.intVal(payload, "quantity");
        if (quantityValue <= 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "주문 수량은 1 이상이어야 합니다.");
        }
        BigDecimal quantity = BigDecimal.valueOf(quantityValue).setScale(8, RoundingMode.HALF_UP);
        String rawSide = Maps.str(payload, "type");
        if (rawSide == null || rawSide.isBlank()) {
            throw ApiException.badRequest("INVALID_ORDER_SIDE", "type은 buy 또는 sell이어야 합니다.");
        }
        String side = rawSide.toLowerCase();
        if (!ALLOWED_SIDES.contains(side)) {
            throw ApiException.badRequest("INVALID_ORDER_SIDE", "type은 buy 또는 sell이어야 합니다.");
        }
        String autoCondition = Maps.str(payload, "autoCondition");
        BigDecimal triggerPrice = payload.get("triggerPrice") == null ? null : BigDecimal.valueOf(Maps.doubleVal(payload, "triggerPrice")).setScale(4, RoundingMode.HALF_UP);
        String status = forcePending || "limit".equals(priceType) || "scheduled".equals(priceType) || autoCondition != null
                ? "pending"
                : "completed";
        long totalKrw = stockQueryService.resolvePriceKrw(stock, orderPrice.multiply(quantity).doubleValue());

        WalletEntity wallet = walletService.requireWalletForUpdate(userId);
        TradeOrderEntity order = new TradeOrderEntity();
        order.setOrderId(newOrderId());
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
        if ("pending".equals(status) && "sell".equals(side)) {
            requireSellableAsset(userId, stock, quantity, "");
        }

        tradeOrderRepository.saveAndFlush(order);

        if ("completed".equals(status)) {
            if ("buy".equals(side)) {
                settleBuy(wallet, userId, stock, orderPrice, quantity, totalKrw, order.getOrderId());
            } else {
                settleSell(wallet, userId, stock, orderPrice, quantity, totalKrw, order.getOrderId());
            }
            order.setCompletedAt(LocalDateTime.now());
            tradeOrderRepository.save(order);
        }

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
        TradeOrderEntity order = tradeOrderRepository.lockByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: " + orderId));
        if (!"pending".equals(order.getOrderStatus())) {
            throw ApiException.conflict("ORDER_NOT_CANCELABLE", "대기 중인 주문만 취소할 수 있습니다.");
        }

        WalletEntity wallet = walletService.requireWalletForUpdate(userId);
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

    @Transactional
    public String processPendingOrder(String orderId) {
        TradeOrderEntity order = tradeOrderRepository.lockPendingByOrderId(orderId).orElse(null);
        if (order == null) {
            return "skipped";
        }

        StockEntity stock = stockQueryService.requireStockEntity(order.getStockId());
        Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
        double marketPrice = Maps.doubleVal(snapshot, "price");
        if (marketPrice <= 0) {
            return "waiting";
        }

        double roundedMarketPrice = stockQueryService.roundPrice(stock, marketPrice);
        if (!shouldExecutePendingOrder(order, roundedMarketPrice)) {
            return "waiting";
        }

        BigDecimal executionPrice = BigDecimal.valueOf(roundedMarketPrice).setScale(4, RoundingMode.HALF_UP);
        BigDecimal quantity = order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0
                ? order.getRemainingQuantity()
                : order.getQuantity();
        long totalKrw = stockQueryService.resolvePriceKrw(stock, executionPrice.multiply(quantity).doubleValue());
        WalletEntity wallet = walletService.requireWalletForUpdate(order.getUserId());

        if ("buy".equals(order.getSide())) {
            long additionalCashNeeded = Math.max(0L, totalKrw - order.getReservedAmountKrw());
            if (additionalCashNeeded > wallet.getWithdrawableCashKrw()) {
                failPendingOrder(order, stock, wallet, "INSUFFICIENT_BALANCE", "예약 주문 체결 시 잔액이 부족합니다.");
                return "failed";
            }
            settleReservedBuy(wallet, order.getUserId(), stock, executionPrice, quantity, totalKrw, order);
        } else {
            if (!hasSellableQuantityBeforeOrder(order, stock, quantity)) {
                failPendingOrder(order, stock, wallet, "INSUFFICIENT_HOLDINGS", "예약 주문 체결 시 보유 수량이 부족합니다.");
                return "failed";
            }
            settlePendingSell(wallet, order.getUserId(), stock, executionPrice, quantity, totalKrw, order);
        }

        order.setOrderPrice(executionPrice);
        order.setFilledQuantity(quantity);
        order.setRemainingQuantity(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP));
        order.setReservedAmountKrw(0L);
        order.setOrderStatus("completed");
        order.setCompletedAt(LocalDateTime.now());
        tradeOrderRepository.save(order);

        appendOrderExecutedEvent(order, stock, totalKrw);
        appendPortfolioUpdatedEvent(order.getUserId(), stock.getStockId(), order.getOrderId(), order.getSide(), order.getOrderStatus());
        return "executed";
    }

    private TradeOrderEntity requireOrder(String userId, String orderId) {
        return tradeOrderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: " + orderId));
    }

    private Map<String, Object> toOrderMap(TradeOrderEntity order) {
        StockEntity stock = stockQueryService.requireStockEntity(order.getStockId());
        String legacyTransactionType = "buy".equals(order.getSide()) ? "BUY" : "SELL";
        String legacyTradeType = "canceled".equals(order.getOrderStatus())
                ? "CANCELLED"
                : (order.getAutoCondition() != null || "scheduled".equals(order.getPriceType()) ? "RESERVED" : "NORMAL");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", order.getOrderId());
        row.put("tradeId", order.getOrderId());
        row.put("stockId", stock.getStockId());
        row.put("stockName", stock.getNameKr());
        row.put("type", order.getSide());
        row.put("transactionType", legacyTransactionType);
        row.put("priceType", order.getPriceType());
        row.put("price", order.getOrderPrice().doubleValue());
        row.put("quantity", order.getQuantity().stripTrailingZeros());
        row.put("amount", order.getQuantity().stripTrailingZeros());
        row.put("total", order.getOrderPrice().multiply(order.getQuantity()).doubleValue());
        row.put("totalKrw", stockQueryService.resolvePriceKrw(stock, order.getOrderPrice().multiply(order.getQuantity()).doubleValue()));
        row.put("status", order.getOrderStatus());
        row.put("tradeType", legacyTradeType);
        row.put("createdAt", order.getCreatedAt() == null ? null : order.getCreatedAt().toString());
        row.put("autoCondition", order.getAutoCondition());
        row.put("triggerPrice", order.getTriggerPrice() == null ? null : order.getTriggerPrice().doubleValue());
        row.put("kind", order.getAutoCondition() != null || "scheduled".equals(order.getPriceType()) ? "auto" : "manual");
        return row;
    }

    private String newOrderId() {
        return "order-" + UUID.randomUUID();
    }

    private String newExecutionId() {
        return "exec-" + UUID.randomUUID();
    }

    private boolean shouldExecutePendingOrder(TradeOrderEntity order, double marketPrice) {
        String condition = order.getAutoCondition() == null ? "" : order.getAutoCondition().trim().toLowerCase();
        double threshold = order.getTriggerPrice() == null
                ? order.getOrderPrice().doubleValue()
                : order.getTriggerPrice().doubleValue();

        if (!condition.isBlank()) {
            return switch (condition) {
                case "above", "gte", "greater_than_or_equal", "up" -> marketPrice >= threshold;
                case "below", "lte", "less_than_or_equal", "down" -> marketPrice <= threshold;
                default -> shouldExecuteLimitLikeOrder(order, marketPrice);
            };
        }

        if ("market".equals(order.getPriceType())) {
            return true;
        }
        return shouldExecuteLimitLikeOrder(order, marketPrice);
    }

    private boolean shouldExecuteLimitLikeOrder(TradeOrderEntity order, double marketPrice) {
        double orderPrice = order.getOrderPrice().doubleValue();
        if ("buy".equals(order.getSide())) {
            return marketPrice <= orderPrice;
        }
        return marketPrice >= orderPrice;
    }

    private AssetEntity requireSellableAsset(String userId, StockEntity stock, BigDecimal quantity, String excludedOrderId) {
        AssetEntity asset = assetRepository.lockByUserIdAndStockId(userId, stock.getStockId())
                .orElseThrow(() -> ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다."));
        if (availableSellQuantity(asset, userId, stock.getStockId(), excludedOrderId).compareTo(quantity) < 0) {
            throw ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다.");
        }
        return asset;
    }

    private boolean hasSellableQuantityBeforeOrder(TradeOrderEntity order, StockEntity stock, BigDecimal quantity) {
        AssetEntity asset = assetRepository.lockByUserIdAndStockId(order.getUserId(), stock.getStockId()).orElse(null);
        if (asset == null) {
            return false;
        }
        return availableSellQuantityBeforeOrder(asset, order, stock.getStockId()).compareTo(quantity) >= 0;
    }

    private AssetEntity requireSellableAssetBeforeOrder(String userId, StockEntity stock, BigDecimal quantity, TradeOrderEntity order) {
        AssetEntity asset = assetRepository.lockByUserIdAndStockId(userId, stock.getStockId())
                .orElseThrow(() -> ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다."));
        if (availableSellQuantityBeforeOrder(asset, order, stock.getStockId()).compareTo(quantity) < 0) {
            throw ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다.");
        }
        return asset;
    }

    private BigDecimal availableSellQuantity(AssetEntity asset, String userId, String stockId, String excludedOrderId) {
        BigDecimal pendingSellQuantity = tradeOrderRepository.sumPendingSellRemainingQuantityExcludingOrder(
                userId,
                stockId,
                excludedOrderId == null ? "" : excludedOrderId
        );
        if (pendingSellQuantity == null) {
            pendingSellQuantity = BigDecimal.ZERO;
        }
        return asset.getQuantity().subtract(pendingSellQuantity);
    }

    private BigDecimal availableSellQuantityBeforeOrder(AssetEntity asset, TradeOrderEntity order, String stockId) {
        LocalDateTime acceptedAt = order.getAcceptedAt() == null ? LocalDateTime.MIN : order.getAcceptedAt();
        LocalDateTime createdAt = order.getCreatedAt() == null ? LocalDateTime.MIN : order.getCreatedAt();
        BigDecimal pendingSellQuantity = tradeOrderRepository.sumEarlierPendingSellRemainingQuantity(
                order.getUserId(),
                stockId,
                acceptedAt,
                createdAt,
                order.getOrderId()
        );
        if (pendingSellQuantity == null) {
            pendingSellQuantity = BigDecimal.ZERO;
        }
        return asset.getQuantity().subtract(pendingSellQuantity);
    }

    private void settleBuy(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, String orderId) {
        if (wallet.getWithdrawableCashKrw() < totalKrw) {
            throw ApiException.conflict("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
        }

        wallet.setCashBalanceKrw(wallet.getCashBalanceKrw() - totalKrw);
        wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() - totalKrw);

        AssetEntity asset = assetRepository.lockByUserIdAndStockId(userId, stock.getStockId()).orElse(null);
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
        execution.setExecutionId(newExecutionId());
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

    private void settleReservedBuy(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, TradeOrderEntity order) {
        long reservedAmount = Math.max(0L, order.getReservedAmountKrw());
        long additionalCashNeeded = Math.max(0L, totalKrw - reservedAmount);
        if (additionalCashNeeded > wallet.getWithdrawableCashKrw()) {
            throw ApiException.conflict("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
        }

        wallet.setReservedCashKrw(Math.max(0L, wallet.getReservedCashKrw() - reservedAmount));
        if (additionalCashNeeded > 0) {
            wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() - additionalCashNeeded);
        }
        long refundKrw = Math.max(0L, reservedAmount - totalKrw);
        if (refundKrw > 0) {
            wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() + refundKrw);
        }
        wallet.setCashBalanceKrw(wallet.getCashBalanceKrw() - totalKrw);

        AssetEntity asset = assetRepository.lockByUserIdAndStockId(userId, stock.getStockId()).orElse(null);
        if (asset == null) {
            asset = new AssetEntity();
            asset.setUserId(userId);
            asset.setStockId(stock.getStockId());
            asset.setFolderId(null);
            asset.setQuantity(quantity);
            asset.setAvgBuyPriceKrw(Math.round((double) totalKrw / quantity.doubleValue()));
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
        execution.setExecutionId(newExecutionId());
        execution.setOrderId(order.getOrderId());
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

        walletService.writeLedger(wallet, userId, "BUY_SETTLEMENT", "OUT", totalKrw, "ORDER", order.getOrderId(), stock.getNameKr() + " 예약 매수 체결");
        appendTradeFeed(userId, stock, order.getOrderId(), "buy", quantity);
    }

    private void settlePendingSell(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, TradeOrderEntity order) {
        AssetEntity asset = requireSellableAssetBeforeOrder(userId, stock, quantity, order);
        settleSellWithAsset(wallet, userId, stock, orderPrice, quantity, totalKrw, order.getOrderId(), asset);
    }

    private void settleSell(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, String orderId) {
        AssetEntity asset = requireSellableAsset(userId, stock, quantity, orderId);
        settleSellWithAsset(wallet, userId, stock, orderPrice, quantity, totalKrw, orderId, asset);
    }

    private void settleSellWithAsset(WalletEntity wallet, String userId, StockEntity stock, BigDecimal orderPrice, BigDecimal quantity, long totalKrw, String orderId, AssetEntity asset) {
        long avgPrice = asset.getAvgBuyPriceKrw();
        long costBasis = Math.round(avgPrice * quantity.doubleValue());
        long realizedPnl = totalKrw - costBasis;

        BigDecimal newQty = asset.getQuantity().subtract(quantity);
        asset.setRealizedPnlKrw(asset.getRealizedPnlKrw() + realizedPnl);
        asset.setCurrentPriceKrw(stockQueryService.resolvePriceKrw(stock, orderPrice.doubleValue()));
        if (newQty.compareTo(BigDecimal.ZERO) == 0) {
            asset.setQuantity(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP));
            asset.setInvestedAmountKrw(0L);
            asset.setAvgBuyPriceKrw(0L);
            asset.setFolderId(null);
            assetRepository.save(asset);
        } else {
            asset.setQuantity(newQty);
            asset.setInvestedAmountKrw(Math.max(0L, asset.getInvestedAmountKrw() - costBasis));
            assetRepository.save(asset);
        }

        wallet.setCashBalanceKrw(wallet.getCashBalanceKrw() + totalKrw);
        wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() + totalKrw);

        TradeExecutionEntity execution = new TradeExecutionEntity();
        execution.setExecutionId(newExecutionId());
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

    private void failPendingOrder(TradeOrderEntity order, StockEntity stock, WalletEntity wallet, String reasonCode, String message) {
        long reservedAmount = Math.max(0L, order.getReservedAmountKrw());
        if (reservedAmount > 0) {
            wallet.setReservedCashKrw(Math.max(0L, wallet.getReservedCashKrw() - reservedAmount));
            wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() + reservedAmount);
            walletService.appendWalletChangedEvent(
                    wallet,
                    order.getUserId(),
                    "ORDER_FAILED_RELEASE",
                    reservedAmount,
                    "ORDER",
                    order.getOrderId(),
                    stock.getNameKr() + " 예약 주문 실패 환원"
            );
        }

        order.setOrderStatus("failed");
        order.setRemainingQuantity(order.getQuantity());
        order.setReservedAmountKrw(0L);
        order.setCanceledAt(LocalDateTime.now());
        tradeOrderRepository.save(order);
        appendOrderFailedEvent(order, stock, reasonCode, message, reservedAmount);
    }

    private void appendOrderFailedEvent(TradeOrderEntity order, StockEntity stock, String reasonCode, String message, long releasedReservedAmount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("userId", order.getUserId());
        payload.put("stockId", order.getStockId());
        payload.put("stockName", stock.getNameKr());
        payload.put("side", order.getSide());
        payload.put("status", order.getOrderStatus());
        payload.put("reasonCode", reasonCode);
        payload.put("message", message);
        payload.put("releasedReservedAmountKrw", releasedReservedAmount);
        payload.put("failedAt", order.getCanceledAt() == null ? null : order.getCanceledAt().toString());

        outboxJdbcRepository.append(
                "ORDER",
                order.getOrderId(),
                "finvibe.order.failed",
                order.getUserId(),
                payload
        );
    }
}
