package depth.finvibe.shared.persistence.trade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "trade_executions")
public class TradeExecutionEntity {
    @Id
    @Column(name = "execution_id", nullable = false, length = 50)
    private String executionId;

    @Column(name = "order_id", nullable = false, length = 50)
    private String orderId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "stock_id", nullable = false, length = 20)
    private String stockId;

    @Column(name = "side", nullable = false, length = 10)
    private String side;

    @Column(name = "executed_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "executed_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal executedQuantity;

    @Column(name = "gross_amount_krw", nullable = false)
    private long grossAmountKrw;

    @Column(name = "fee_krw", nullable = false)
    private long feeKrw;

    @Column(name = "tax_krw", nullable = false)
    private long taxKrw;

    @Column(name = "net_amount_krw", nullable = false)
    private long netAmountKrw;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }
    public BigDecimal getExecutedQuantity() { return executedQuantity; }
    public void setExecutedQuantity(BigDecimal executedQuantity) { this.executedQuantity = executedQuantity; }
    public long getGrossAmountKrw() { return grossAmountKrw; }
    public void setGrossAmountKrw(long grossAmountKrw) { this.grossAmountKrw = grossAmountKrw; }
    public long getFeeKrw() { return feeKrw; }
    public void setFeeKrw(long feeKrw) { this.feeKrw = feeKrw; }
    public long getTaxKrw() { return taxKrw; }
    public void setTaxKrw(long taxKrw) { this.taxKrw = taxKrw; }
    public long getNetAmountKrw() { return netAmountKrw; }
    public void setNetAmountKrw(long netAmountKrw) { this.netAmountKrw = netAmountKrw; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
