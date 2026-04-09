package depth.finvibe.shared.persistence.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_profit_snapshot_daily")
public class UserProfitSnapshotDailyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "cash_balance_krw", nullable = false)
    private long cashBalanceKrw;

    @Column(name = "reserved_cash_krw", nullable = false)
    private long reservedCashKrw;

    @Column(name = "invested_amount_krw", nullable = false)
    private long investedAmountKrw;

    @Column(name = "evaluation_amount_krw", nullable = false)
    private long evaluationAmountKrw;

    @Column(name = "realized_pnl_krw", nullable = false)
    private long realizedPnlKrw;

    @Column(name = "unrealized_pnl_krw", nullable = false)
    private long unrealizedPnlKrw;

    @Column(name = "total_asset_krw", nullable = false)
    private long totalAssetKrw;

    @Column(name = "daily_return_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal dailyReturnRate;

    @Column(name = "total_return_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalReturnRate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public long getCashBalanceKrw() { return cashBalanceKrw; }
    public void setCashBalanceKrw(long cashBalanceKrw) { this.cashBalanceKrw = cashBalanceKrw; }
    public long getReservedCashKrw() { return reservedCashKrw; }
    public void setReservedCashKrw(long reservedCashKrw) { this.reservedCashKrw = reservedCashKrw; }
    public long getInvestedAmountKrw() { return investedAmountKrw; }
    public void setInvestedAmountKrw(long investedAmountKrw) { this.investedAmountKrw = investedAmountKrw; }
    public long getEvaluationAmountKrw() { return evaluationAmountKrw; }
    public void setEvaluationAmountKrw(long evaluationAmountKrw) { this.evaluationAmountKrw = evaluationAmountKrw; }
    public long getRealizedPnlKrw() { return realizedPnlKrw; }
    public void setRealizedPnlKrw(long realizedPnlKrw) { this.realizedPnlKrw = realizedPnlKrw; }
    public long getUnrealizedPnlKrw() { return unrealizedPnlKrw; }
    public void setUnrealizedPnlKrw(long unrealizedPnlKrw) { this.unrealizedPnlKrw = unrealizedPnlKrw; }
    public long getTotalAssetKrw() { return totalAssetKrw; }
    public void setTotalAssetKrw(long totalAssetKrw) { this.totalAssetKrw = totalAssetKrw; }
    public BigDecimal getDailyReturnRate() { return dailyReturnRate; }
    public void setDailyReturnRate(BigDecimal dailyReturnRate) { this.dailyReturnRate = dailyReturnRate; }
    public BigDecimal getTotalReturnRate() { return totalReturnRate; }
    public void setTotalReturnRate(BigDecimal totalReturnRate) { this.totalReturnRate = totalReturnRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
