package depth.finvibe.shared.persistence.mongo.feed;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserActivityFeedRepository extends MongoRepository<UserActivityFeedDocument, String> {
    List<UserActivityFeedDocument> findTop50ByUserIdOrderByCreatedAtDesc(String userId);
}