package depth.finvibe.shared.persistence.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "wallets")
public class WalletEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private String userId;

    @Column(name = "cash_balance_krw", nullable = false)
    private long cashBalanceKrw;

    @Column(name = "reserved_cash_krw", nullable = false)
    private long reservedCashKrw;

    @Column(name = "withdrawable_cash_krw", nullable = false)
    private long withdrawableCashKrw;

    @Version
    @Column(name = "version_no", nullable = false)
    private Long versionNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getWalletId() { return walletId; }
    public void setWalletId(Long walletId) { this.walletId = walletId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getCashBalanceKrw() { return cashBalanceKrw; }
    public void setCashBalanceKrw(long cashBalanceKrw) { this.cashBalanceKrw = cashBalanceKrw; }
    public long getReservedCashKrw() { return reservedCashKrw; }
    public void setReservedCashKrw(long reservedCashKrw) { this.reservedCashKrw = reservedCashKrw; }
    public long getWithdrawableCashKrw() { return withdrawableCashKrw; }
    public void setWithdrawableCashKrw(long withdrawableCashKrw) { this.withdrawableCashKrw = withdrawableCashKrw; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
