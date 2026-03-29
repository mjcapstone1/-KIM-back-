package depth.finvibe.investment.modules.wallet.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalletController {
    private final AppState state;

    public WalletController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/simulator/wallet")
    public Object wallet() {
        return state.getWalletSummary();
    }

    @PostMapping("/api/v1/simulator/wallet/charge")
    public Object charge(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        int amount = Maps.intVal(request, "amount");
        if (amount <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "amount는 1 이상이어야 합니다.");
        }
        return Maps.of(
                "message", "가상 투자금이 충전되었습니다.",
                "wallet", state.chargeWallet(amount)
        );
    }

    @GetMapping("/wallets/balance")
    public Object walletAlias() {
        return state.getWalletSummary();
    }
}
