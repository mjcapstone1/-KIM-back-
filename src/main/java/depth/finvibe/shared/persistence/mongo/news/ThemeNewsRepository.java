package depth.finvibe.shared.persistence.mongo.news;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ThemeNewsRepository extends MongoRepository<ThemeNewsDocument, String> {
    List<ThemeNewsDocument> findByThemeIdOrderByPublishedAtDesc(String themeId);
    List<ThemeNewsDocument> findAllByOrderByPublishedAtDesc();
}