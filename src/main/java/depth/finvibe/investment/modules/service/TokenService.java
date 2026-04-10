package depth.finvibe.investment.modules.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TokenService {

    @Value("${finvibe.kis.app-key}")
    private String appKey;

    @Value("${finvibe.kis.app-secret}")
    private String appSecret;

    @Value("${finvibe.kis.base-url}")
    private String baseUrl;

    // 🔥 토큰 캐싱 (중요)
    private String cachedToken;
    private long tokenTime;

    public String getAccessToken() {

        // 👉 1분 안 지났으면 기존 토큰 재사용
        if (cachedToken != null && (System.currentTimeMillis() - tokenTime) < 60000) {
            return cachedToken;
        }

        RestTemplate restTemplate = new RestTemplate();

        String url = baseUrl + "/oauth2/tokenP";

        // 요청 바디
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "appsecret", appSecret
        );

        // 헤더
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(body, headers);

        // 요청
        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, request, Map.class);

        Map<String, Object> responseBody = response.getBody();

        if (responseBody == null) {
            throw new RuntimeException("토큰 응답이 비어있음");
        }

        cachedToken = (String) responseBody.get("access_token");
        tokenTime = System.currentTimeMillis();

        return cachedToken;
    }

    // 🔥 OrderBook에서 쓸 getter
    public String getAppKey() {
        return appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }
}