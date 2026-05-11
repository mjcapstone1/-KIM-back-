package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyAiRecommendController {
    private final AppState state;

    public StudyAiRecommendController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/keywords/recommended")
    public Object recommendedKeywords() {
        return Maps.of("items", state.recommendedKeywordsList());
    }

    @GetMapping("/study/keywords/recommended")
    public Object recommendedKeywordsAlias() {
        return Maps.of("items", state.recommendedKeywordsList());
    }

    @GetMapping("/api/v1/learning/recommendations/today")
    public Object recommendationToday() {
        return state.aiRecommendationToday();
    }

    @GetMapping("/study/ai-recommends/today")
    public Object recommendationTodayAlias() {
        return state.aiRecommendationToday();
    }
}
