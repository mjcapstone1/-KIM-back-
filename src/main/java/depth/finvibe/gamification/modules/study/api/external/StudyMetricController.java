package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.gamification.modules.study.application.service.LearningService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyMetricController {
    private final AuthService authService;
    private final LearningService learningService;

    public StudyMetricController(AuthService authService, LearningService learningService) {
        this.authService = authService;
        this.learningService = learningService;
    }

    @GetMapping("/api/v1/learning/stats")
    public Object stats(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> metrics = new LinkedHashMap<>(learningService.metricsMe(currentUser.userId()));
        metrics.put("weeklyGoal", learningService.weeklyGoalFor(currentUser.userId()));
        return metrics;
    }

    @GetMapping("/study/metrics/me")
    public Object statsAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return stats(authorization);
    }
}
