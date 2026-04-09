package depth.finvibe.shared.persistence.market;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FavoriteStockId implements Serializable {
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "stock_id", length = 20, nullable = false)
    private String stockId;

    public FavoriteStockId() {}

    public FavoriteStockId(String userId, String stockId) {
        this.userId = userId;
        this.stockId = stockId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoriteStockId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(stockId, that.stockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, stockId);
    }
}
