package depth.finvibe.shared.security;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.json.Json;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtService {
    private final AppConfig config;

    public JwtService(AppConfig config) {
        this.config = config;
    }

    public Map<String, Object> createAccessToken(String userId) {
        return encodeToken(userId, "access", config.accessTokenMinutes() * 60L);
    }

    public Map<String, Object> createRefreshToken(String userId) {
        return encodeToken(userId, "refresh", config.refreshTokenDays() * 24L * 60L * 60L);
    }

    public Map<String, Object> createTokenPair(String userId) {
        Map<String, Object> access = createAccessToken(userId);
        Map<String, Object> refresh = createRefreshToken(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", access.get("token"));
        result.put("accessExpiresAt", access.get("expiresAt"));
        result.put("refreshToken", refresh.get("token"));
        result.put("refreshExpiresAt", refresh.get("expiresAt"));
        return result;
    }

    public Map<String, Object> decodeAndValidate(String token, String expectedType) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw ApiException.unauthorized("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
            }
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadText = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> headerMap = Json.parseObject(header);
            if (!"HS256".equals(String.valueOf(headerMap.get("alg")))) {
                throw ApiException.unauthorized("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
            }
            String actualSignature = sign(parts[0] + "." + parts[1]);
            if (!constantEquals(parts[2], actualSignature)) {
                throw ApiException.unauthorized("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
            }
            Map<String, Object> payload = Json.parseObject(payloadText);
            long now = Instant.now().getEpochSecond();
            long exp = toLong(payload.get("exp"));
            long nbf = toLong(payload.get("nbf"));
            if (now > exp) {
                throw ApiException.unauthorized("TOKEN_EXPIRED", "토큰이 만료되었습니다.");
            }
            if (now < nbf) {
                throw ApiException.unauthorized("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
            }
            if (expectedType != null && !expectedType.equals(String.valueOf(payload.get("token_type")))) {
                throw ApiException.unauthorized("INVALID_TOKEN_TYPE", "토큰 타입이 올바르지 않습니다.");
            }
            return payload;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
        }
    }

    private Map<String, Object> encodeToken(String userId, String tokenType, long expiresInSeconds) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expiresInSeconds;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("token_type", tokenType);
        payload.put("role", "USER");
        payload.put("iss", config.appName());
        payload.put("iat", now);
        payload.put("nbf", now);
        payload.put("exp", exp);
        payload.put("jti", UUID.randomUUID().toString());

        String headerPart = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.stringify(header).getBytes(StandardCharsets.UTF_8));
        String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.stringify(payload).getBytes(StandardCharsets.UTF_8));
        String signature = sign(headerPart + "." + payloadPart);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", headerPart + "." + payloadPart + "." + signature);
        result.put("expiresAt", Instant.ofEpochSecond(exp).toString());
        return result;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.jwtSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT sign failed", e);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
