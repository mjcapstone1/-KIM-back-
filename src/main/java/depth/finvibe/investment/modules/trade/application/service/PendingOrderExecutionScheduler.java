package depth.finvibe.investment.modules.trade.application.service;

import depth.finvibe.shared.persistence.trade.TradeOrderRepository;
import depth.finvibe.shared.persistence.trade.TradeOrderEntity;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingOrderExecutionScheduler {
    private static final Logger log = LoggerFactory.getLogger(PendingOrderExecutionScheduler.class);

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeService tradeService;
    private final AtomicInteger nextPageCursor = new AtomicInteger(0);

    @Value("${finvibe.trade.pending-orders.enabled:true}")
    private boolean enabled;

    @Value("${finvibe.trade.pending-orders.max-per-run:100}")
    private int maxPerRun;

    public PendingOrderExecutionScheduler(TradeOrderRepository tradeOrderRepository, TradeService tradeService) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeService = tradeService;
    }

    @Scheduled(fixedDelayString = "${finvibe.trade.pending-orders.poll-delay-ms:5000}")
    public void executePendingOrders() {
        if (!enabled) {
            return;
        }

        int limit = Math.max(1, Math.min(maxPerRun, 500));
        long pendingCount = tradeOrderRepository.countByOrderStatus("pending");
        if (pendingCount <= 0) {
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil((double) pendingCount / limit));
        int page = Math.floorMod(nextPageCursor.getAndUpdate(current -> current + 1), totalPages);
        List<TradeOrderEntity> orders = tradeOrderRepository.findAllByOrderStatusOrderByAcceptedAtAscCreatedAtAscOrderIdAsc("pending", PageRequest.of(page, limit));
        if (orders.isEmpty() && page > 0) {
            nextPageCursor.set(1);
            orders = tradeOrderRepository.findAllByOrderStatusOrderByAcceptedAtAscCreatedAtAscOrderIdAsc("pending", PageRequest.of(0, limit));
        }
        if (orders.isEmpty()) {
            return;
        }

        int executed = 0;
        int failed = 0;
        int waiting = 0;
        int skipped = 0;

        for (TradeOrderEntity order : orders) {
            String orderId = order.getOrderId();
            try {
                String result = tradeService.processPendingOrder(orderId);
                switch (result) {
                    case "executed" -> executed++;
                    case "failed" -> failed++;
                    case "skipped" -> skipped++;
                    default -> waiting++;
                }
            } catch (Exception error) {
                failed++;
                log.warn("예약 주문 처리 중 오류가 발생했습니다. orderId={}, message={}", orderId, error.getMessage());
            }
        }

        if (executed > 0 || failed > 0) {
            log.info("예약 주문 처리 완료. checked={}, executed={}, failed={}, waiting={}, skipped={}",
                    orders.size(), executed, failed, waiting, skipped);
        }
    }
}
