package depth.finvibe.investment.modules.wallet.api.external;

import depth.finvibe.investment.modules.wallet.application.service.WalletService;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalletController {
    private final WalletService walletService;
    private final AuthService authService;

    public WalletController(WalletService walletService, AuthService authService) {
        this.walletService = walletService;
        this.authService = authService;
    }

    @GetMapping("/api/v1/simulator/wallet")
    public Object wallet(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return walletService.getWalletSummary(currentUser.userId());
    }

    @PostMapping("/api/v1/simulator/wallet/charge")
    public Object charge(@RequestHeader(name = "Authorization", required = false) String authorization,
                         @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        int amount = Maps.intVal(request, "amount");
        if (amount <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "amount는 1 이상이어야 합니다.");
        }
        return Maps.of(
                "message", "가상 투자금이 충전되었습니다.",
                "wallet", walletService.chargeWallet(currentUser.userId(), amount)
        );
    }

    @GetMapping("/wallets/balance")
    public Object walletAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return walletService.getWalletSummary(currentUser.userId());
    }
}
