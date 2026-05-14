package depth.finvibe.gamification.modules.study.application.service;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.util.Maps;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GeminiTutorService {
    private static final int MAX_MESSAGE_LENGTH = 1200;
    private static final int MAX_HISTORY_ITEMS = 10;

    private final AppConfig config;
    private final HttpClient client;

    public GeminiTutorService(AppConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.geminiTimeoutMs()))
                .build();
    }

    public Map<String, Object> answer(String userId, String message, String investmentType, List<Map<String, Object>> history) {
        String trimmed = normalizeMessage(message);
        if (!config.geminiEnabled()) {
            throw new ApiException(503, "GEMINI_NOT_CONFIGURED", "Gemini API 키가 설정되어 있지 않습니다.");
        }

        Map<String, Object> requestBody = Maps.of(
                "systemInstruction", Maps.of("parts", List.of(Maps.of("text", systemPrompt(investmentType)))),
                "contents", buildContents(history, trimmed),
                "generationConfig", Maps.of(
                        "temperature", 0.65,
                        "topP", 0.9,
                        "maxOutputTokens", 700
                )
        );

        Map<String, Object> payload = requestGemini(requestBody);
        String answer = extractText(payload);
        if (answer.isBlank()) {
            throw new ApiException(502, "GEMINI_EMPTY_RESPONSE", "Gemini 응답이 비어 있습니다.");
        }

        return Maps.of(
                "message", answer,
                "provider", "gemini",
                "model", config.geminiModel(),
                "userId", userId
        );
    }

    private String normalizeMessage(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isBlank()) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "질문 내용을 입력해 주세요.");
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "질문은 1,200자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private List<Map<String, Object>> buildContents(List<Map<String, Object>> history, String latestMessage) {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null) {
            int start = Math.max(0, history.size() - MAX_HISTORY_ITEMS);
            for (int i = start; i < history.size(); i++) {
                Map<String, Object> item = history.get(i);
                String content = Maps.str(item, "content", "").trim();
                if (content.isBlank()) {
                    continue;
                }
                String role = "assistant".equalsIgnoreCase(Maps.str(item, "role")) ? "model" : "user";
                contents.add(content(role, content));
            }
        }
        contents.add(content("user", latestMessage));
        return contents;
    }

    private Map<String, Object> content(String role, String text) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", role);
        row.put("parts", List.of(Maps.of("text", text)));
        return row;
    }

    private String systemPrompt(String investmentType) {
        String typeLabel = switch (investmentType == null ? "" : investmentType) {
            case "stable" -> "안정형";
            case "balanced" -> "균형형";
            case "aggressive" -> "공격형";
            case "daytrader" -> "단타형";
            default -> "미선택";
        };
        return """
                너는 FinVibe의 AI 투자 학습 튜터다.
                사용자의 투자 성향: %s.
                한국어로 답하고, 초보자도 이해할 수 있게 짧은 문단과 예시로 설명한다.
                특정 종목의 매수/매도 지시나 확정 수익 보장은 하지 않는다.
                투자 판단은 사용자가 직접 해야 하며, 필요하면 리스크 관리와 학습 포인트를 함께 알려준다.
                답변은 5~8문장 안에서 친절하게 마무리한다.
                """.formatted(typeLabel);
    }

    private Map<String, Object> requestGemini(Map<String, Object> body) {
        String endpoint = config.geminiBaseUrl()
                + "/v1beta/models/"
                + config.geminiModel()
                + ":generateContent";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(config.geminiTimeoutMs()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-goog-api-key", config.geminiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "GEMINI_REQUEST_FAILED", geminiErrorMessage(response.statusCode(), response.body()));
            }
            return Json.parseObject(response.body());
        } catch (IOException e) {
            throw new ApiException(502, "GEMINI_NETWORK_ERROR", "Gemini 네트워크 요청 중 오류가 발생했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "GEMINI_INTERRUPTED", "Gemini 요청이 중단되었습니다.");
        }
    }

    private String geminiErrorMessage(int statusCode, String body) {
        String upstreamMessage = "";
        try {
            Map<String, Object> payload = Json.parseObject(body);
            Object errorValue = payload.get("error");
            if (errorValue instanceof Map<?, ?> error) {
                upstreamMessage = Maps.str((Map<String, Object>) error, "message", "");
            }
        } catch (Exception ignored) {
        }

        if (statusCode == 403) {
            return "Gemini API 접근 권한이 없습니다. API 키, Google AI Studio 프로젝트 권한, 결제/무료 티어 상태를 확인해 주세요.";
        }
        if (statusCode == 400) {
            return "Gemini 요청 형식이 올바르지 않습니다." + (upstreamMessage.isBlank() ? "" : " " + upstreamMessage);
        }
        if (statusCode == 429) {
            return "Gemini API 사용량 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.";
        }
        return "Gemini 요청이 실패했습니다. status=" + statusCode + (upstreamMessage.isBlank() ? "" : " message=" + upstreamMessage);
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> payload) {
        Object candidatesValue = payload.get("candidates");
        if (!(candidatesValue instanceof List<?> candidates) || candidates.isEmpty()) {
            return "";
        }
        Object firstCandidate = candidates.get(0);
        if (!(firstCandidate instanceof Map<?, ?> candidate)) {
            return "";
        }
        Object contentValue = ((Map<String, Object>) candidate).get("content");
        if (!(contentValue instanceof Map<?, ?> content)) {
            return "";
        }
        Object partsValue = ((Map<String, Object>) content).get("parts");
        if (!(partsValue instanceof List<?> parts)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Object partValue : parts) {
            if (partValue instanceof Map<?, ?> part) {
                String text = Maps.str((Map<String, Object>) part, "text", "");
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append("\n");
                    }
                    builder.append(text);
                }
            }
        }
        return builder.toString().trim();
    }
}
