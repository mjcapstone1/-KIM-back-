package depth.finvibe.investment.modules.controller;

import depth.finvibe.investment.modules.service.OrderBookService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orderbook")
public class OrderBookController {

    private final OrderBookService orderBookService;

    public OrderBookController(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }

    @GetMapping("/{code}")
    public Map<String, Object> getOrderBook(@PathVariable String code) {
        return orderBookService.getOrderBook(code);
    }
}