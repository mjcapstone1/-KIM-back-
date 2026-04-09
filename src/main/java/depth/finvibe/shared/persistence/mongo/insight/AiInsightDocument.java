package depth.finvibe.shared.persistence.mongo.insight;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ai_insights")
@CompoundIndex(name = "idx_ai_insights_user_generated", def = "{'userId': 1, 'generatedAt': -1}")
public class AiInsightDocument {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String symbol;

    private LocalDateTime generatedAt;
    private String summary;
    private List<Map<String, Object>> signals;
    private List<Map<String, Object>> sourceArticles;
    private String modelVersion;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<Map<String, Object>> getSignals() { return signals; }
    public void setSignals(List<Map<String, Object>> signals) { this.signals = signals; }

    public List<Map<String, Object>> getSourceArticles() { return sourceArticles; }
    public void setSourceArticles(List<Map<String, Object>> sourceArticles) { this.sourceArticles = sourceArticles; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}