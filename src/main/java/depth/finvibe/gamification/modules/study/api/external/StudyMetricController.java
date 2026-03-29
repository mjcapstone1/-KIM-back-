package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.shared.state.AppState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyMetricController {
    private final AppState state;

    public StudyMetricController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/stats")
    public Object stats() {
        Map<String, Object> metrics = new LinkedHashMap<>(state.metricsMe());
        metrics.put("weeklyGoal", state.learningDashboard().get("weeklyGoal"));
        return metrics;
    }

    @GetMapping("/study/metrics/me")
    public Object statsAlias() {
        Map<String, Object> metrics = new LinkedHashMap<>(state.metricsMe());
        metrics.put("weeklyGoal", state.learningDashboard().get("weeklyGoal"));
        return metrics;
    }
}
