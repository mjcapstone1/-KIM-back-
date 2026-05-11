package depth.finvibe.shared.persistence.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserLessonId implements Serializable {
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "lesson_id", length = 96, nullable = false)
    private String lessonId;

    public UserLessonId() {}

    public UserLessonId(String userId, String lessonId) {
        this.userId = userId;
        this.lessonId = lessonId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserLessonId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(lessonId, that.lessonId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, lessonId);
    }
}
