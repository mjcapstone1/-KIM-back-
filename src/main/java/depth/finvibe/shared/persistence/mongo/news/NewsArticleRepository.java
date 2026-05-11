package depth.finvibe.shared.persistence.mongo.news;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NewsArticleRepository extends MongoRepository<NewsArticleDocument, String> {
    List<NewsArticleDocument> findBySymbolOrderByPublishedAtDesc(String symbol);
}