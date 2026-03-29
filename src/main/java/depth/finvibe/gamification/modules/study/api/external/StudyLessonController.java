package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyLessonController {
    private final AppState state;

    public StudyLessonController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/lessons/completions")
    public Object lessonCompletions() {
        return Maps.of("items", state.lessonCompletions());
    }

    @GetMapping("/study/lessons/completions/me")
    public Object lessonCompletionsAlias() {
        return Maps.of("items", state.lessonCompletions());
    }

    @GetMapping("/api/v1/learning/lessons/{lessonId}")
    public Object lesson(@PathVariable String lessonId) {
        return state.getLesson(lessonId);
    }

    @GetMapping("/study/lessons/{lessonId}")
    public Object lessonAlias(@PathVariable String lessonId) {
        return state.getLesson(lessonId);
    }

    @PostMapping("/api/v1/learning/lessons/{lessonId}/complete")
    public Object completeLesson(@PathVariable String lessonId) {
        Map<String, Object> result = state.completeLesson(lessonId);
        return Maps.of(
                "message", "학습이 완료되었습니다.",
                "lesson", result.get("lesson"),
                "course", result.get("course"),
                "metrics", result.get("stats"),
                "xp", result.get("xp")
        );
    }

    @PostMapping("/study/lessons/{lessonId}/complete")
    public Object completeLessonAlias(@PathVariable String lessonId) {
        return completeLesson(lessonId);
    }

    @PostMapping("/study/lessons/{lessonId}/metrics/one-minute")
    public Object addStudyMinute(@PathVariable String lessonId) {
        return Maps.of(
                "lessonId", lessonId,
                "studyMinutes", state.addStudyMinute(lessonId)
        );
    }
}
