package depth.finvibe.investment.modules.controller;

import org.springframework.web.bind.annotation.*;
import depth.finvibe.investment.modules.service.TokenService;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TokenService tokenService;

    public TestController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/token")
    public String getToken() {
        return tokenService.getAccessToken();
    }
}