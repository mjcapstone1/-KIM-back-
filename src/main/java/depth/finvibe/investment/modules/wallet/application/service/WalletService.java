package depth.finvibe.investment.modules.wallet.application.service;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.outbox.OutboxJdbcRepository;
import depth.finvibe.shared.persistence.investment.WalletEntity;
import depth.finvibe.shared.persistence.investment.WalletLedgerEntity;
import depth.finvibe.shared.persistence.investment.WalletLedgerRepository;
import depth.finvibe.shared.persistence.investment.WalletRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private static final int EXCHANGE_RATE = 1300;

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final OutboxJdbcRepository outboxJdbcRepository;

    public WalletService(
            WalletRepository walletRepository,
            WalletLedgerRepository walletLedgerRepository,
            OutboxJdbcRepository outboxJdbcRepository
    ) {
        this.walletRepository = walletRepository;
        this.walletLedgerRepository = walletLedgerRepository;
        this.outboxJdbcRepository = outboxJdbcRepository;
    }

    public Map<String, Object> getWalletSummary(String userId) {
        WalletEntity wallet = requireWallet(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", wallet.getCashBalanceKrw());
        result.put("reservedBalance", wallet.getReservedCashKrw());
        result.put("withdrawableBalance", wallet.getWithdrawableCashKrw());
        result.put("currency", "KRW");
        result.put("exchangeRate", EXCHANGE_RATE);
        result.put("availableUsd", Math.round((wallet.getCashBalanceKrw() * 100.0 / EXCHANGE_RATE)) / 100.0);
        return result;
    }

    @Transactional
    public Map<String, Object> chargeWallet(String userId, long amount) {
        WalletEntity wallet = requireWallet(userId);
        wallet.setCashBalanceKrw(wallet.getCashBalanceKrw() + amount);
        wallet.setWithdrawableCashKrw(wallet.getWithdrawableCashKrw() + amount);
        walletRepository.save(wallet);

        WalletLedgerEntity ledger = new WalletLedgerEntity();
        ledger.setWalletId(wallet.getWalletId());
        ledger.setUserId(userId);
        ledger.setEntryType("DEPOSIT");
        ledger.setDirection("IN");
        ledger.setAmountKrw(amount);
        ledger.setBalanceAfterKrw(wallet.getCashBalanceKrw());
        ledger.setReferenceType("MANUAL_CHARGE");
        ledger.setReferenceId("wallet-charge");
        ledger.setMemo("가상 투자금 충전");
        walletLedgerRepository.save(ledger);

        appendWalletChangedEvent(wallet, userId, "DEPOSIT", amount, "MANUAL_CHARGE", "wallet-charge", "가상 투자금 충전");
        return getWalletSummary(userId);
    }

    public WalletEntity requireWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다."));
    }

    @Transactional
    public void writeLedger(
            WalletEntity wallet,
            String userId,
            String entryType,
            String direction,
            long amountKrw,
            String referenceType,
            String referenceId,
            String memo
    ) {
        WalletLedgerEntity ledger = new WalletLedgerEntity();
        ledger.setWalletId(wallet.getWalletId());
        ledger.setUserId(userId);
        ledger.setEntryType(entryType);
        ledger.setDirection(direction);
        ledger.setAmountKrw(amountKrw);
        ledger.setBalanceAfterKrw(wallet.getCashBalanceKrw());
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        ledger.setMemo(memo);
        walletLedgerRepository.save(ledger);

        appendWalletChangedEvent(wallet, userId, entryType, amountKrw, referenceType, referenceId, memo);
    }

    @Transactional
    public void appendWalletChangedEvent(
            WalletEntity wallet,
            String userId,
            String reason,
            long amountKrw,
            String referenceType,
            String referenceId,
            String memo
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("walletId", wallet.getWalletId());
        payload.put("reason", reason);
        payload.put("amountKrw", amountKrw);
        payload.put("referenceType", referenceType);
        payload.put("referenceId", referenceId);
        payload.put("memo", memo);
        payload.put("cashBalanceKrw", wallet.getCashBalanceKrw());
        payload.put("reservedCashKrw", wallet.getReservedCashKrw());
        payload.put("withdrawableCashKrw", wallet.getWithdrawableCashKrw());

        outboxJdbcRepository.append(
                "WALLET",
                String.valueOf(wallet.getWalletId()),
                "finvibe.wallet.changed",
                userId,
                payload
        );
    }
}