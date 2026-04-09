package depth.finvibe.shared.persistence.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "assets")
public class AssetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "stock_id", nullable = false, length = 20)
    private String stockId;

    @Column(name = "folder_id", length = 50)
    private String folderId;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_buy_price_krw", nullable = false)
    private long avgBuyPriceKrw;

    @Column(name = "current_price_krw", nullable = false)
    private long currentPriceKrw;

    @Column(name = "invested_amount_krw", nullable = false)
    private long investedAmountKrw;

    @Column(name = "realized_pnl_krw", nullable = false)
    private long realizedPnlKrw;

    @Version
    @Column(name = "version_no", nullable = false)
    private Long versionNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public long getAvgBuyPriceKrw() { return avgBuyPriceKrw; }
    public void setAvgBuyPriceKrw(long avgBuyPriceKrw) { this.avgBuyPriceKrw = avgBuyPriceKrw; }
    public long getCurrentPriceKrw() { return currentPriceKrw; }
    public void setCurrentPriceKrw(long currentPriceKrw) { this.currentPriceKrw = currentPriceKrw; }
    public long getInvestedAmountKrw() { return investedAmountKrw; }
    public void setInvestedAmountKrw(long investedAmountKrw) { this.investedAmountKrw = investedAmountKrw; }
    public long getRealizedPnlKrw() { return realizedPnlKrw; }
    public void setRealizedPnlKrw(long realizedPnlKrw) { this.realizedPnlKrw = realizedPnlKrw; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
