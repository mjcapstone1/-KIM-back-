package depth.finvibe.shared.persistence.mongo.feed;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SearchLogRepository extends MongoRepository<SearchLogDocument, String> {
}