package depth.finvibe.shared.persistence.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PortfolioStockId implements Serializable {
    @Column(name = "portfolio_id", nullable = false, length = 20)
    private String portfolioId;

    @Column(name = "stock_id", nullable = false, length = 20)
    private String stockId;

    public PortfolioStockId() {}

    public PortfolioStockId(String portfolioId, String stockId) {
        this.portfolioId = portfolioId;
        this.stockId = stockId;
    }

    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }
    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioStockId that)) return false;
        return Objects.equals(portfolioId, that.portfolioId) && Objects.equals(stockId, that.stockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioId, stockId);
    }
}
