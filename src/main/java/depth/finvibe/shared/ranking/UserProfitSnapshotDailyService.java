package depth.finvibe.shared.ranking;

import depth.finvibe.shared.persistence.investment.AssetEntity;
import depth.finvibe.shared.persistence.investment.AssetRepository;
import depth.finvibe.shared.persistence.investment.WalletEntity;
import depth.finvibe.shared.persistence.investment.WalletRepository;
import depth.finvibe.shared.persistence.ranking.UserProfitSnapshotDailyEntity;
import depth.finvibe.shared.persistence.ranking.UserProfitSnapshotDailyRepository;
import depth.finvibe.shared.util.TimeUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfitSnapshotDailyService {
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final UserProfitSnapshotDailyRepository snapshotRepository;

    public UserProfitSnapshotDailyService(
            WalletRepository walletRepository,
            AssetRepository assetRepository,
            UserProfitSnapshotDailyRepository snapshotRepository
    ) {
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public void refreshSnapshot(String userId) {
        WalletEntity wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return;
        }

        LocalDate today = LocalDate.now(TimeUtil.SEOUL);
        List<AssetEntity> assets = assetRepository.findAllByUserIdOrderByCreatedAtAsc(userId);

        long investedAmountKrw = assets.stream()
                .mapToLong(AssetEntity::getInvestedAmountKrw)
                .sum();

        long evaluationAmountKrw = assets.stream()
                .mapToLong(asset -> Math.round(asset.getQuantity().doubleValue() * asset.getCurrentPriceKrw()))
                .sum();

        long realizedPnlKrw = assets.stream()
                .mapToLong(AssetEntity::getRealizedPnlKrw)
                .sum();

        long unrealizedPnlKrw = evaluationAmountKrw - investedAmountKrw;
        long totalAssetKrw = wallet.getCashBalanceKrw() + evaluationAmountKrw;

        BigDecimal totalReturnRate = investedAmountKrw <= 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(realizedPnlKrw + unrealizedPnlKrw)
                  .multiply(BigDecimal.valueOf(100))
                  .divide(BigDecimal.valueOf(investedAmountKrw), 4, RoundingMode.HALF_UP);

        BigDecimal dailyReturnRate = snapshotRepository
                .findTopByUserIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(userId, today)
                .map(previous -> {
                    if (previous.getTotalAssetKrw() <= 0) {
                        return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                    }
                    return BigDecimal.valueOf(totalAssetKrw - previous.getTotalAssetKrw())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(previous.getTotalAssetKrw()), 4, RoundingMode.HALF_UP);
                })
                .orElse(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));

        UserProfitSnapshotDailyEntity snapshot = snapshotRepository
                .findByUserIdAndSnapshotDate(userId, today)
                .orElseGet(UserProfitSnapshotDailyEntity::new);

        snapshot.setUserId(userId);
        snapshot.setSnapshotDate(today);
        snapshot.setCashBalanceKrw(wallet.getCashBalanceKrw());
        snapshot.setReservedCashKrw(wallet.getReservedCashKrw());
        snapshot.setInvestedAmountKrw(investedAmountKrw);
        snapshot.setEvaluationAmountKrw(evaluationAmountKrw);
        snapshot.setRealizedPnlKrw(realizedPnlKrw);
        snapshot.setUnrealizedPnlKrw(unrealizedPnlKrw);
        snapshot.setTotalAssetKrw(totalAssetKrw);
        snapshot.setDailyReturnRate(dailyReturnRate);
        snapshot.setTotalReturnRate(totalReturnRate);

        snapshotRepository.save(snapshot);
    }
}