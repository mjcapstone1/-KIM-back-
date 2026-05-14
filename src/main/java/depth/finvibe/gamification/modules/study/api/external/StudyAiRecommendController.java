package depth.finvibe.gamification.modules.study.api.external;

import depth.finvibe.gamification.modules.study.application.service.GeminiTutorService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyAiRecommendController {
    private final AppState state;
    private final AuthService authService;
    private final GeminiTutorService geminiTutorService;

    public StudyAiRecommendController(AppState state, AuthService authService, GeminiTutorService geminiTutorService) {
        this.state = state;
        this.authService = authService;
        this.geminiTutorService = geminiTutorService;
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

    @PostMapping("/api/v1/learning/ai-tutor/chat")
    public Object aiTutorChat(@RequestHeader(name = "Authorization", required = false) String authorization,
                              @RequestBody Map<String, Object> payload) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return geminiTutorService.answer(
                currentUser.userId(),
                Maps.str(payload, "message"),
                Maps.str(payload, "investmentType"),
                historyRows(payload.get("history"))
        );
    }

    @PostMapping("/study/ai-tutor/chat")
    public Object aiTutorChatAlias(@RequestHeader(name = "Authorization", required = false) String authorization,
                                   @RequestBody Map<String, Object> payload) {
        return aiTutorChat(authorization, payload);
    }

    private List<Map<String, Object>> historyRows(Object value) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return rows;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    row.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
