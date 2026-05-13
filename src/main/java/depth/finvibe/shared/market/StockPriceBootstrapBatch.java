package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockPriceBootstrapBatch {
    private static final Logger log = LoggerFactory.getLogger(StockPriceBootstrapBatch.class);

    private final StockRepository stockRepository;
    private final StockQueryService stockQueryService;
    private final MarketService marketService;
    private final AtomicBoolean startupExecuted = new AtomicBoolean(false);
    private final AtomicInteger nextPageCursor = new AtomicInteger(0);

    @Value("${finvibe.market.bootstrap-prices.enabled:true}")
    private boolean enabled;

    @Value("${finvibe.market.bootstrap-prices.run-on-startup:false}")
    private boolean runOnStartup;

    @Value("${finvibe.market.bootstrap-prices.schedule-enabled:false}")
    private boolean scheduleEnabled;

    @Value("${finvibe.market.bootstrap-prices.page-size:50}")
    private int pageSize;

    @Value("${finvibe.market.bootstrap-prices.max-pages-per-run:4}")
    private int maxPagesPerRun;

    @Value("${finvibe.market.bootstrap-prices.request-delay-ms:150}")
    private long requestDelayMs;

    @Value("${finvibe.market.bootstrap-prices.refresh-zero-only:true}")
    private boolean refreshZeroOnly;

    @Value("${finvibe.market.bootstrap-prices.stale-after-minutes:180}")
    private long staleAfterMinutes;

    public StockPriceBootstrapBatch(
            StockRepository stockRepository,
            StockQueryService stockQueryService,
            MarketService marketService
    ) {
        this.stockRepository = stockRepository;
        this.stockQueryService = stockQueryService;
        this.marketService = marketService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!enabled || !runOnStartup || !startupExecuted.compareAndSet(false, true)) {
            return;
        }
        bootstrapActiveDomesticPrices("startup");
    }

    @Scheduled(cron = "${finvibe.market.bootstrap-prices.cron:0 0/20 9-15 * * MON-FRI}", zone = "Asia/Seoul")
    public void bootstrapOnSchedule() {
        if (!enabled || !scheduleEnabled) {
            return;
        }
        bootstrapActiveDomesticPrices("schedule");
    }

    public void bootstrapActiveDomesticPrices(String trigger) {
        if (!marketService.kisEnabled()) {
            log.info("KRX 가격 초기화를 건너뜁니다. trigger={}, reason=kis-disabled", trigger);
            return;
        }

        int processed = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int safePageSize = Math.max(1, pageSize);
        int totalPages = resolveTotalPages(safePageSize);

        if (totalPages <= 0) {
            log.info("KRX 가격 초기화를 건너뜁니다. trigger={}, reason=no-stocks", trigger);
            return;
        }

        int pageWindow = Math.min(Math.max(1, maxPagesPerRun), totalPages);
        int startPage = nextPageCursor.getAndUpdate(current -> Math.floorMod(current + pageWindow, totalPages));

        for (int offset = 0; offset < pageWindow; offset++) {
            int pageNumber = Math.floorMod(startPage + offset, totalPages);
            Pageable pageable = PageRequest.of(pageNumber, safePageSize);
            Page<StockEntity> page = stockRepository.findByActiveTrueAndStockTypeAndMarket(
                    "domestic",
                    "KRX",
                    pageable
            );

            if (page.isEmpty()) {
                break;
            }

            for (StockEntity stock : page.getContent()) {
                if (!shouldRefresh(stock)) {
                    continue;
                }

                processed++;
                try {
                    Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
                    String source = String.valueOf(snapshot.getOrDefault("dataSource", "unknown"));
                    double price = toDouble(snapshot.get("price"));
                    double changeRate = toDouble(snapshot.get("changeRate"));

                    if (!"kis".equals(source) || price <= 0) {
                        skipped++;
                        continue;
                    }

                    updateLastPrice(stock.getStockId(), price, changeRate);
                    updated++;
                } catch (Exception e) {
                    failed++;
                    log.warn("가격 초기화 실패 stockId={}, symbol={}, message={}",
                            stock.getStockId(), stock.getSymbol(), e.getMessage());
                }

                if (requestDelayMs > 0) {
                    LockSupport.parkNanos(Duration.ofMillis(requestDelayMs).toNanos());
                }
            }

            if (!page.hasNext()) {
                break;
            }
        }

        log.info("KRX 가격 초기화 완료 trigger={}, startPage={}, pageWindow={}, totalPages={}, processed={}, updated={}, skipped={}, failed={}, refreshZeroOnly={}, staleAfterMinutes={}",
                trigger, startPage, pageWindow, totalPages, processed, updated, skipped, failed, refreshZeroOnly, staleAfterMinutes);
    }

    @Transactional
    protected void updateLastPrice(String stockId, double price, double changeRate) {
        StockEntity entity = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("종목이 없습니다: " + stockId));
        entity.setLastPrice(BigDecimal.valueOf(price));
        entity.setLastChangeRate(BigDecimal.valueOf(changeRate));
        stockRepository.save(entity);
    }

    private boolean shouldRefresh(StockEntity stock) {
        if (stock == null || !stock.isActive()) {
            return false;
        }
        if (!"domestic".equalsIgnoreCase(stock.getStockType())) {
            return false;
        }
        if (!"KRX".equalsIgnoreCase(stock.getMarket())) {
            return false;
        }
        if (stock.getLastPrice() == null || stock.getLastPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        if (!refreshZeroOnly) {
            return true;
        }
        return isStale(stock);
    }

    private int resolveTotalPages(int safePageSize) {
        Pageable pageable = PageRequest.of(0, safePageSize);
        Page<StockEntity> firstPage = stockRepository.findByActiveTrueAndStockTypeAndMarket(
                "domestic",
                "KRX",
                pageable
        );
        return Math.max(0, firstPage.getTotalPages());
    }

    private boolean isStale(StockEntity stock) {
        if (staleAfterMinutes <= 0) {
            return false;
        }
        if (stock.getUpdatedAt() == null) {
            return true;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, staleAfterMinutes));
        return stock.getUpdatedAt().isBefore(cutoff);
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}
