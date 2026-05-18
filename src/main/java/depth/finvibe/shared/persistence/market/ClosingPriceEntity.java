package depth.finvibe.shared.persistence.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "closing_prices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_closing_prices_stock_date", columnNames = {"stock_id", "trade_date"})
        }
)
public class ClosingPriceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "closing_price_id", nullable = false)
    private Long closingPriceId;

    @Column(name = "stock_id", nullable = false, length = 20)
    private String stockId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "prev_close_price", precision = 20, scale = 4)
    private BigDecimal prevClosePrice;

    @Column(name = "change_rate", precision = 10, scale = 4)
    private BigDecimal changeRate;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Column(name = "trading_value_krw", nullable = false)
    private long tradingValueKrw;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getClosingPriceId() { return closingPriceId; }
    public void setClosingPriceId(Long closingPriceId) { this.closingPriceId = closingPriceId; }

    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }

    public BigDecimal getPrevClosePrice() { return prevClosePrice; }
    public void setPrevClosePrice(BigDecimal prevClosePrice) { this.prevClosePrice = prevClosePrice; }

    public BigDecimal getChangeRate() { return changeRate; }
    public void setChangeRate(BigDecimal changeRate) { this.changeRate = changeRate; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }
    public long getTradingValueKrw() { return tradingValueKrw; }
    public void setTradingValueKrw(long tradingValueKrw) { this.tradingValueKrw = tradingValueKrw; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
