package depth.finvibe.shared.persistence.mongo.insight;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiInsightRepository extends MongoRepository<AiInsightDocument, String> {
    List<AiInsightDocument> findByUserIdOrderByGeneratedAtDesc(String userId);
    Optional<AiInsightDocument> findFirstByUserIdAndSymbolOrderByGeneratedAtDesc(String userId, String symbol);
}