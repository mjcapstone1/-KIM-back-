package depth.finvibe.shared.persistence.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "stocks")
public class StockEntity {
    @Id
    @Column(name = "stock_id", nullable = false, length = 20)
    private String stockId;

    @Column(name = "market", nullable = false, length = 20)
    private String market;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "name_kr", nullable = false, length = 200)
    private String nameKr;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "stock_type", nullable = false, length = 20)
    private String stockType;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "last_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "last_change_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal lastChangeRate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getNameKr() { return nameKr; }
    public void setNameKr(String nameKr) { this.nameKr = nameKr; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getStockType() { return stockType; }
    public void setStockType(String stockType) { this.stockType = stockType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal lastPrice) { this.lastPrice = lastPrice; }
    public BigDecimal getLastChangeRate() { return lastChangeRate; }
    public void setLastChangeRate(BigDecimal lastChangeRate) { this.lastChangeRate = lastChangeRate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
