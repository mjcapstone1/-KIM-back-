package depth.finvibe.shared.persistence.mongo.insight;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExternalApiRawLogRepository extends MongoRepository<ExternalApiRawLogDocument, String> {
}