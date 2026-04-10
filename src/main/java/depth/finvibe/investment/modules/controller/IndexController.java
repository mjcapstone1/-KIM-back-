package depth.finvibe.investment.modules.controller;

import depth.finvibe.investment.modules.service.IndexService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    // 코스피
    @GetMapping("/kospi")
    public Map<String, Object> kospi() {
        return indexService.getIndex("0001");
    }

    // 코스닥
    @GetMapping("/kosdaq")
    public Map<String, Object> kosdaq() {
        return indexService.getIndex("1001");
    }

    // KRX300
    @GetMapping("/krx300")
    public Map<String, Object> krx300() {
        return indexService.getIndex("2001");
    }
}