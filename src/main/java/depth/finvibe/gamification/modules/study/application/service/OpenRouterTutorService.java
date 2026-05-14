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
public class OpenRouterTutorService {
    private static final int MAX_MESSAGE_LENGTH = 1200;
    private static final int MAX_HISTORY_ITEMS = 10;

    private final AppConfig config;
    private final HttpClient client;

    public OpenRouterTutorService(AppConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.openRouterTimeoutMs()))
                .build();
    }

    public Map<String, Object> answer(String userId, String message, String investmentType, List<Map<String, Object>> history) {
        String trimmed = normalizeMessage(message);
        if (!config.openRouterEnabled()) {
            throw new ApiException(503, "OPENROUTER_NOT_CONFIGURED", "OpenRouter API 키가 설정되어 있지 않습니다.");
        }

        Map<String, Object> requestBody = Maps.of(
                "model", config.openRouterModel(),
                "messages", buildMessages(history, trimmed, investmentType),
                "temperature", 0.65,
                "max_tokens", 700,
                "reasoning", Maps.of("enabled", true)
        );

        Map<String, Object> payload = requestOpenRouter(requestBody);
        String answer = extractText(payload);
        if (answer.isBlank()) {
            throw new ApiException(502, "OPENROUTER_EMPTY_RESPONSE", "OpenRouter 응답이 비어 있습니다.");
        }

        return Maps.of(
                "message", answer,
                "provider", "openrouter",
                "model", config.openRouterModel(),
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

    private List<Map<String, Object>> buildMessages(List<Map<String, Object>> history, String latestMessage, String investmentType) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt(investmentType)));
        if (history != null) {
            int start = Math.max(0, history.size() - MAX_HISTORY_ITEMS);
            for (int i = start; i < history.size(); i++) {
                Map<String, Object> item = history.get(i);
                String content = Maps.str(item, "content", "").trim();
                if (content.isBlank()) {
                    continue;
                }
                String role = "assistant".equalsIgnoreCase(Maps.str(item, "role")) ? "assistant" : "user";
                messages.add(message(role, content));
            }
        }
        messages.add(message("user", latestMessage));
        return messages;
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", role);
        row.put("content", content);
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

    private Map<String, Object> requestOpenRouter(Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.openRouterBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(config.openRouterTimeoutMs()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + config.openRouterApiKey())
                .header("HTTP-Referer", config.openRouterSiteUrl())
                .header("X-Title", "FinVibe AI Tutor")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "OPENROUTER_REQUEST_FAILED", openRouterErrorMessage(response.statusCode(), response.body()));
            }
            return Json.parseObject(response.body());
        } catch (IOException e) {
            throw new ApiException(502, "OPENROUTER_NETWORK_ERROR", "OpenRouter 네트워크 요청 중 오류가 발생했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "OPENROUTER_INTERRUPTED", "OpenRouter 요청이 중단되었습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private String openRouterErrorMessage(int statusCode, String body) {
        String upstreamMessage = "";
        try {
            Map<String, Object> payload = Json.parseObject(body);
            Object errorValue = payload.get("error");
            if (errorValue instanceof Map<?, ?> error) {
                upstreamMessage = Maps.str((Map<String, Object>) error, "message", "");
            }
        } catch (Exception ignored) {
        }

        if (statusCode == 401 || statusCode == 403) {
            return "OpenRouter API 키 권한이 없거나 사용할 수 없습니다.";
        }
        if (statusCode == 429) {
            return "OpenRouter API 사용량 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.";
        }
        return "OpenRouter 요청이 실패했습니다. status=" + statusCode + (upstreamMessage.isBlank() ? "" : " message=" + upstreamMessage);
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> payload) {
        Object choicesValue = payload.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            return "";
        }
        Object messageValue = ((Map<String, Object>) choice).get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            return "";
        }
        return Maps.str((Map<String, Object>) message, "content", "").trim();
    }
}
