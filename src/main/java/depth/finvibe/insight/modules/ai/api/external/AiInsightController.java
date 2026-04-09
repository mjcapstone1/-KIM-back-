package depth.finvibe.insight.modules.ai.api.external;

import depth.finvibe.shared.persistence.mongo.insight.AiInsightDocument;
import depth.finvibe.shared.persistence.mongo.insight.AiInsightRepository;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiInsightController {
    private final AuthService authService;
    private final AiInsightRepository aiInsightRepository;

    public AiInsightController(AuthService authService, AiInsightRepository aiInsightRepository) {
        this.authService = authService;
        this.aiInsightRepository = aiInsightRepository;
    }

    @GetMapping("/api/v1/insights/me")
    public Object myInsights(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", aiInsightRepository.findByUserIdOrderByGeneratedAtDesc(currentUser.userId()));
    }

    @GetMapping("/api/v1/insights/me/{symbol}")
    public Object myInsightBySymbol(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @PathVariable String symbol) {
        CurrentUser currentUser = authService.requireUser(authorization);
        AiInsightDocument doc = aiInsightRepository
                .findFirstByUserIdAndSymbolOrderByGeneratedAtDesc(currentUser.userId(), symbol)
                .orElse(null);

        if (doc == null) {
            return Maps.of("message", "해당 종목 인사이트가 없습니다.", "symbol", symbol);
        }
        return doc;
    }

    @PostMapping("/api/v1/insights/me/{symbol}")
    public Object saveInsight(@RequestHeader(name = "Authorization", required = false) String authorization,
                              @PathVariable String symbol,
                              @RequestBody Map<String, Object> payload) {
        CurrentUser currentUser = authService.requireUser(authorization);

        AiInsightDocument doc = new AiInsightDocument();
        doc.setUserId(currentUser.userId());
        doc.setSymbol(symbol);
        doc.setGeneratedAt(LocalDateTime.now());
        doc.setSummary(Maps.str(payload, "summary"));
        doc.setSignals(toMapList(payload.get("signals")));
        doc.setSourceArticles(toMapList(payload.get("sourceArticles")));
        doc.setModelVersion(Maps.str(payload, "modelVersion", "insight-v1"));

        return aiInsightRepository.save(doc);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
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