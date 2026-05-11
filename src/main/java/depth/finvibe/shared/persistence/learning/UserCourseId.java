package depth.finvibe.shared.persistence.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserCourseId implements Serializable {
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "course_id", length = 64, nullable = false)
    private String courseId;

    public UserCourseId() {}

    public UserCourseId(String userId, String courseId) {
        this.userId = userId;
        this.courseId = courseId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserCourseId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(courseId, that.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, courseId);
    }
}
