package depth.finvibe.investment.modules.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class IndexService {

    private final TokenService tokenService;

    @Value("${finvibe.kis.base-url}")
    private String baseUrl;

    public IndexService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Map<String, Object> getIndex(String code) {

        String token = tokenService.getAccessToken();

        RestTemplate restTemplate = new RestTemplate();

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-index-price";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("appkey", tokenService.getAppKey());
        headers.set("appsecret", tokenService.getAppSecret());
        headers.set("tr_id", "FHPUP02100000"); // 지수 TR
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 👉 지수 코드
        // 0001: 코스피
        // 1001: 코스닥
        // 2001: KRX300
        String fullUrl = url + "?fid_cond_mrkt_div_code=U&fid_input_iscd=" + code;

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(fullUrl, HttpMethod.GET, entity, Map.class);

        return response.getBody();
    }
}