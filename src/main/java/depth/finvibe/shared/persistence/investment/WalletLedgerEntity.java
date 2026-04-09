package depth.finvibe.shared.persistence.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "wallet_ledger")
public class WalletLedgerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "entry_type", nullable = false, length = 30)
    private String entryType;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "amount_krw", nullable = false)
    private long amountKrw;

    @Column(name = "balance_after_krw", nullable = false)
    private long balanceAfterKrw;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "memo", length = 255)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getLedgerId() { return ledgerId; }
    public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }
    public Long getWalletId() { return walletId; }
    public void setWalletId(Long walletId) { this.walletId = walletId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public long getAmountKrw() { return amountKrw; }
    public void setAmountKrw(long amountKrw) { this.amountKrw = amountKrw; }
    public long getBalanceAfterKrw() { return balanceAfterKrw; }
    public void setBalanceAfterKrw(long balanceAfterKrw) { this.balanceAfterKrw = balanceAfterKrw; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
