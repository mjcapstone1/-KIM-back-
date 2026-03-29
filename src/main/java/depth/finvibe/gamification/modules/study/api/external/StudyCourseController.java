package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyCourseController {
    private final AppState state;

    public StudyCourseController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/screen")
    public Object learningScreen() {
        return state.learningDashboard();
    }

    @GetMapping("/api/v1/learning/dashboard")
    public Object learningDashboard() {
        return state.learningDashboard();
    }

    @GetMapping("/api/v1/learning/courses")
    public Object courses() {
        return Maps.of("items", state.listCourses());
    }

    @GetMapping("/study/courses/me")
    public Object coursesAlias() {
        return state.listCourses();
    }

    @PostMapping("/api/v1/learning/courses/preview")
    public Object previewCourse(@RequestBody(required = false) Map<String, Object> body) {
        return state.previewCourse(parseCourseBody(body == null ? new LinkedHashMap<>() : body));
    }

    @PostMapping("/study/courses/preview")
    public Object previewCourseAlias(@RequestBody(required = false) Map<String, Object> body) {
        return state.previewCourse(parseCourseBody(body == null ? new LinkedHashMap<>() : body));
    }

    @PostMapping("/api/v1/learning/courses")
    public Object createCourse(@RequestBody(required = false) Map<String, Object> body) {
        return state.createCourse(parseCourseBody(body == null ? new LinkedHashMap<>() : body));
    }

    @PostMapping("/study/courses")
    public Object createCourseAlias(@RequestBody(required = false) Map<String, Object> body) {
        return state.createCourse(parseCourseBody(body == null ? new LinkedHashMap<>() : body));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCourseBody(Map<String, Object> body) {
        Object name = body.get("courseName");
        if (name == null || String.valueOf(name).isBlank()) {
            throw ApiException.badRequest("INVALID_COURSE_NAME", "courseName 값이 필요합니다.");
        }
        Object keywordsObj = body.get("keywords");
        if (!(keywordsObj instanceof List<?> list) || list.isEmpty()) {
            throw ApiException.badRequest("INVALID_KEYWORDS", "keywords 배열이 필요합니다.");
        }
        List<String> keywords = new ArrayList<>();
        for (Object item : list) {
            keywords.add(String.valueOf(item));
        }
        return Maps.of(
                "courseName", String.valueOf(name),
                "keywords", keywords
        );
    }
}
