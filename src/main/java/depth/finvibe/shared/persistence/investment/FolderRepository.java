package depth.finvibe.shared.persistence.investment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<FolderEntity, String> {
    List<FolderEntity> findAllByUserIdOrderByCreatedAtAsc(String userId);
    Optional<FolderEntity> findByFolderIdAndUserId(String folderId, String userId);
}
