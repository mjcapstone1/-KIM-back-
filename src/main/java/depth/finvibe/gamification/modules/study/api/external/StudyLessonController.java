package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.gamification.modules.study.application.service.LearningService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyLessonController {
    private final AuthService authService;
    private final LearningService learningService;

    public StudyLessonController(AuthService authService, LearningService learningService) {
        this.authService = authService;
        this.learningService = learningService;
    }

    @GetMapping("/api/v1/learning/lessons/completions")
    public Object lessonCompletions(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", learningService.lessonCompletions(currentUser.userId()));
    }

    @GetMapping("/study/lessons/completions/me")
    public Object lessonCompletionsAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return lessonCompletions(authorization);
    }

    @GetMapping("/api/v1/learning/lessons/{lessonId}")
    public Object lesson(@PathVariable String lessonId,
                         @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return learningService.getLesson(currentUser.userId(), lessonId);
    }

    @GetMapping("/study/lessons/{lessonId}")
    public Object lessonAlias(@PathVariable String lessonId,
                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        return lesson(lessonId, authorization);
    }

    @PostMapping("/api/v1/learning/lessons/{lessonId}/complete")
    public Object completeLesson(@PathVariable String lessonId,
                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> result = learningService.completeLesson(currentUser.userId(), lessonId);
        return Maps.of(
                "message", "학습이 완료되었습니다.",
                "lesson", result.get("lesson"),
                "course", result.get("course"),
                "metrics", result.get("stats"),
                "xp", result.get("xp")
        );
    }

    @PostMapping("/study/lessons/{lessonId}/complete")
    public Object completeLessonAlias(@PathVariable String lessonId,
                                      @RequestHeader(name = "Authorization", required = false) String authorization) {
        return completeLesson(lessonId, authorization);
    }

    @PostMapping("/study/lessons/{lessonId}/metrics/one-minute")
    public Object addStudyMinute(@PathVariable String lessonId,
                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of(
                "lessonId", lessonId,
                "studyMinutes", learningService.addStudyMinute(currentUser.userId(), lessonId)
        );
    }
}
