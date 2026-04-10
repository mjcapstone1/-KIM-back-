package depth.finvibe.investment.modules.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OrderBookService {

    private final TokenService tokenService;

    @Value("${finvibe.kis.base-url}")
    private String baseUrl;

    public OrderBookService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Map<String, Object> getOrderBook(String code) {

        String token = tokenService.getAccessToken();

        RestTemplate restTemplate = new RestTemplate();

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("appkey", tokenService.getAppKey());
        headers.set("appsecret", tokenService.getAppSecret());
        headers.set("tr_id", "FHKST01010200"); // 호가 TR

        headers.setContentType(MediaType.APPLICATION_JSON);

        // 쿼리 파라미터
        String fullUrl = url + "?fid_cond_mrkt_div_code=J&fid_input_iscd=" + code;

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(fullUrl, HttpMethod.GET, entity, Map.class);

        return response.getBody();
    }
}