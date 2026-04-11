package depth.finvibe.investment.modules.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class StockService {

    private final TokenService tokenService;

    @Value("${finvibe.kis.base-url}")
    private String baseUrl;

    public StockService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    // 🔥 캐싱 데이터
    private List<Map<String, Object>> cachedVolume = new ArrayList<>();
    private List<Map<String, Object>> cachedGainers = new ArrayList<>();
    private List<Map<String, Object>> cachedAll = new ArrayList<>(); // ✅ 추가

    // 🔥 종목 30개
    private final String[][] STOCKS = {
            {"005930","삼성전자"},{"000660","SK하이닉스"},{"035420","NAVER"},{"035720","카카오"},
            {"005380","현대차"},{"012330","현대모비스"},{"068270","셀트리온"},{"207940","삼성바이오로직스"},
            {"051910","LG화학"},{"006400","삼성SDI"},{"373220","LG에너지솔루션"},{"105560","KB금융"},
            {"055550","신한지주"},{"003550","LG"},{"096770","SK이노베이션"},{"034020","두산에너빌리티"},
            {"009830","한화솔루션"},{"030200","KT"},{"017670","SK텔레콤"},{"032830","삼성생명"},
            {"090430","아모레퍼시픽"},{"011170","롯데케미칼"},{"000270","기아"},{"010130","고려아연"},
            {"066570","LG전자"},{"018260","삼성SDS"},{"028260","삼성물산"},{"003490","대한항공"},
            {"086790","하나금융지주"},{"015760","한국전력"}
    };

    // 🔥 서버 시작 시 1회 실행
    @PostConstruct
    public void init() {
        updateStockCache();
    }

    // 🔥 API 호출
    public Map<String, Object> getStockPrice(String code) {
        try {
            String token = tokenService.getAccessToken();

            RestTemplate restTemplate = new RestTemplate();
            String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("appkey", tokenService.getAppKey());
            headers.set("appsecret", tokenService.getAppSecret());
            headers.set("tr_id", "FHKST01010100");
            headers.setContentType(MediaType.APPLICATION_JSON);

            String fullUrl = url + "?fid_cond_mrkt_div_code=J&fid_input_iscd=" + code;

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(fullUrl, HttpMethod.GET, entity, Map.class);

            return response.getBody();

        } catch (Exception e) {
            return null;
        }
    }

    // 🔥 데이터 가공
    public Map<String, Object> getStockSimple(String code) {

        Map<String, Object> data = getStockPrice(code);

        if (data == null || data.get("output") == null) return null;

        Map<String, Object> output = (Map<String, Object>) data.get("output");

        Map<String, Object> result = new HashMap<>();
        result.put("price", output.getOrDefault("stck_prpr", "0"));
        result.put("rate", output.getOrDefault("prdy_ctrt", "0"));
        result.put("volume", output.getOrDefault("acml_vol", "0"));

        return result;
    }

    // 🔥 1분마다 자동 갱신
    @Scheduled(fixedRate = 60000)
    public void updateStockCache() {

        System.out.println("🔥 주식 캐시 갱신 시작");

        List<Map<String, Object>> temp = new ArrayList<>();

        // ✅ 30개 전부 조회
        for (String[] stock : STOCKS) {

            String code = stock[0];
            String name = stock[1];

            Map<String, Object> info = getStockSimple(code);
            if (info == null) continue;

            info.put("name", name);
            info.put("code", code);

            temp.add(info);

            // 🔥 API 보호
            try { Thread.sleep(120); } catch (Exception ignored) {}
        }

        // ✅ 전체 캐시 저장 (핵심🔥)
        cachedAll = new ArrayList<>(temp);

        // 🔥 거래량 TOP 5
        temp.sort((a, b) -> {
            long v1 = Long.parseLong(a.get("volume").toString());
            long v2 = Long.parseLong(b.get("volume").toString());
            return Long.compare(v2, v1);
        });
        cachedVolume = new ArrayList<>(temp.subList(0, Math.min(5, temp.size())));

        // 🔥 급등 TOP 5
        temp.sort((a, b) -> {
            double r1 = Double.parseDouble(a.get("rate").toString());
            double r2 = Double.parseDouble(b.get("rate").toString());
            return Double.compare(r2, r1);
        });
        cachedGainers = new ArrayList<>(temp.subList(0, Math.min(5, temp.size())));

        System.out.println("✅ 캐시 갱신 완료");
    }

    // 🔥 전체 종목 반환 (API 호출 ❌)
    public List<Map<String, Object>> getAllStocks() {
        return cachedAll;
    }

    // 🔥 TOP 반환
    public List<Map<String, Object>> getTopVolumeStocks() {
        return cachedVolume;
    }

    public List<Map<String, Object>> getTopGainers() {
        return cachedGainers;
    }
}