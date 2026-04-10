package depth.finvibe.investment.modules.controller;

import depth.finvibe.investment.modules.service.StockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*") // 프론트 연결용 (React)
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    // 🔥 거래량 TOP
    @GetMapping("/top-volume")
    public List<Map<String, Object>> getTopVolumeStocks() {
        return stockService.getTopVolumeStocks();
    }

    // 🔥 급등 TOP
    @GetMapping("/top-gainers")
    public List<Map<String, Object>> getTopGainers() {
        return stockService.getTopGainers();
    }

    // 🔥 특정 종목 조회 (테스트용)
    @GetMapping("/{code}")
    public Map<String, Object> getStock(@PathVariable String code) {
        return stockService.getStockSimple(code);
    }
}