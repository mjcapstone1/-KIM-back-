package depth.finvibe.shared.persistence.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_learning_profiles")
public class UserLearningProfileEntity {
    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "level_no", nullable = false)
    private int level;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "total_xp", nullable = false)
    private int totalXp;

    @Column(name = "xp_to_next_level", nullable = false)
    private int xpToNextLevel;

    @Column(name = "study_minutes", nullable = false)
    private int studyMinutes;

    @Column(name = "user_ranking", nullable = false)
    private int userRanking;

    @Column(name = "squad_ranking", nullable = false)
    private int squadRanking;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getTotalXp() { return totalXp; }
    public void setTotalXp(int totalXp) { this.totalXp = totalXp; }
    public int getXpToNextLevel() { return xpToNextLevel; }
    public void setXpToNextLevel(int xpToNextLevel) { this.xpToNextLevel = xpToNextLevel; }
    public int getStudyMinutes() { return studyMinutes; }
    public void setStudyMinutes(int studyMinutes) { this.studyMinutes = studyMinutes; }
    public int getUserRanking() { return userRanking; }
    public void setUserRanking(int userRanking) { this.userRanking = userRanking; }
    public int getSquadRanking() { return squadRanking; }
    public void setSquadRanking(int squadRanking) { this.squadRanking = squadRanking; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
