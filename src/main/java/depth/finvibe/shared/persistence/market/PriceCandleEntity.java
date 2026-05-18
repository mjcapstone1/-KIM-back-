package depth.finvibe.shared.persistence.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "price_candles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_price_candles_stock_timeframe_at", columnNames = {"stock_id", "timeframe", "candle_at"})
        }
)
public class PriceCandleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "candle_id", nullable = false)
    private Long candleId;

    @Column(name = "stock_id", nullable = false, length = 20)
    private String stockId;

    @Column(name = "timeframe", nullable = false, length = 20)
    private String timeframe;

    @Column(name = "candle_at", nullable = false)
    private LocalDateTime candleAt;

    @Column(name = "open_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Column(name = "trading_value_krw", nullable = false)
    private long tradingValueKrw;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getCandleId() { return candleId; }
    public void setCandleId(Long candleId) { this.candleId = candleId; }
    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }
    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
    public LocalDateTime getCandleAt() { return candleAt; }
    public void setCandleAt(LocalDateTime candleAt) { this.candleAt = candleAt; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal openPrice) { this.openPrice = openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal highPrice) { this.highPrice = highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal lowPrice) { this.lowPrice = lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }
    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }
    public long getTradingValueKrw() { return tradingValueKrw; }
    public void setTradingValueKrw(long tradingValueKrw) { this.tradingValueKrw = tradingValueKrw; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
