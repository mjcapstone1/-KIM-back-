package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import java.time.Duration;
import java.util.List;
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

@Component
public class StockCandleBackfillBatch {
    private static final Logger log = LoggerFactory.getLogger(StockCandleBackfillBatch.class);

    private final StockRepository stockRepository;
    private final StockQueryService stockQueryService;
    private final MarketService marketService;
    private final StockPriceStore stockPriceStore;
    private final AtomicBoolean startupExecuted = new AtomicBoolean(false);
    private final AtomicInteger nextPageCursor = new AtomicInteger(0);

    @Value("${finvibe.market.candle-backfill.enabled:true}")
    private boolean enabled;

    @Value("${finvibe.market.candle-backfill.run-on-startup:true}")
    private boolean runOnStartup;

    @Value("${finvibe.market.candle-backfill.schedule-enabled:true}")
    private boolean scheduleEnabled;

    @Value("${finvibe.market.candle-backfill.page-size:50}")
    private int pageSize;

    @Value("${finvibe.market.candle-backfill.max-pages-per-run:1}")
    private int maxPagesPerRun;

    @Value("${finvibe.market.candle-backfill.points:60}")
    private int points;

    @Value("${finvibe.market.candle-backfill.minimum-points:20}")
    private int minimumPoints;

    @Value("${finvibe.market.candle-backfill.request-delay-ms:500}")
    private long requestDelayMs;

    public StockCandleBackfillBatch(
            StockRepository stockRepository,
            StockQueryService stockQueryService,
            MarketService marketService,
            StockPriceStore stockPriceStore
    ) {
        this.stockRepository = stockRepository;
        this.stockQueryService = stockQueryService;
        this.marketService = marketService;
        this.stockPriceStore = stockPriceStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (!enabled || !runOnStartup || !startupExecuted.compareAndSet(false, true)) {
            return;
        }
        backfillDailyCandles("startup");
    }

    @Scheduled(cron = "${finvibe.market.candle-backfill.cron:0 3/10 * * * *}", zone = "Asia/Seoul")
    public void backfillOnSchedule() {
        if (!enabled || !scheduleEnabled) {
            return;
        }
        backfillDailyCandles("schedule");
    }

    public void backfillDailyCandles(String trigger) {
        if (!marketService.kisEnabled()) {
            log.info("일봉 백필을 건너뜁니다. trigger={}, reason=kis-disabled", trigger);
            return;
        }

        int safePageSize = Math.max(1, pageSize);
        int totalPages = resolveTotalPages(safePageSize);
        if (totalPages <= 0) {
            log.info("일봉 백필을 건너뜁니다. trigger={}, reason=no-stocks", trigger);
            return;
        }

        int pageWindow = Math.min(Math.max(1, maxPagesPerRun), totalPages);
        int startPage = nextPageCursor.getAndUpdate(current -> Math.floorMod(current + pageWindow, totalPages));
        int checked = 0;
        int refreshed = 0;
        int skipped = 0;
        int failed = 0;

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
                checked++;
                if (!stockPriceStore.needsCandleBackfill(stock.getStockId(), "day", minimumPoints)) {
                    skipped++;
                    continue;
                }
                try {
                    Map<String, Object> marketStock = stockQueryService.toMarketStock(stock);
                    List<Map<String, Object>> candles = marketService.refreshStoredCandles(
                            marketStock,
                            "day",
                            Math.max(minimumPoints, points)
                    );
                    if (candles.isEmpty()) {
                        skipped++;
                    } else {
                        refreshed++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("일봉 백필 실패 stockId={}, symbol={}, message={}",
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

        log.info("일봉 백필 완료 trigger={}, startPage={}, pageWindow={}, totalPages={}, checked={}, refreshed={}, skipped={}, failed={}, points={}, minimumPoints={}",
                trigger, startPage, pageWindow, totalPages, checked, refreshed, skipped, failed, points, minimumPoints);
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
}
