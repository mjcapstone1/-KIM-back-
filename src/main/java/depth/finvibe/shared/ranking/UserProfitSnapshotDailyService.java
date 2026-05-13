package depth.finvibe.shared.ranking;

import depth.finvibe.shared.persistence.investment.AssetEntity;
import depth.finvibe.shared.persistence.investment.AssetRepository;
import depth.finvibe.shared.persistence.investment.WalletEntity;
import depth.finvibe.shared.persistence.investment.WalletRepository;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import depth.finvibe.shared.persistence.ranking.UserProfitSnapshotDailyEntity;
import depth.finvibe.shared.persistence.ranking.UserProfitSnapshotDailyRepository;
import depth.finvibe.shared.persistence.user.UserEntity;
import depth.finvibe.shared.persistence.user.UserRepository;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfitSnapshotDailyService {
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final UserProfitSnapshotDailyRepository snapshotRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public UserProfitSnapshotDailyService(
            WalletRepository walletRepository,
            AssetRepository assetRepository,
            UserProfitSnapshotDailyRepository snapshotRepository,
            StockRepository stockRepository,
            UserRepository userRepository
    ) {
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.snapshotRepository = snapshotRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void refreshSnapshot(String userId) {
        WalletEntity wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return;
        }

        LocalDate today = LocalDate.now(TimeUtil.SEOUL);
        InvestmentReturnSummary summary = calculateReturnSummary(userId);
        long investedAmountKrw = summary.investedAmountKrw();
        long evaluationAmountKrw = summary.evaluationAmountKrw();
        long realizedPnlKrw = summary.realizedPnlKrw();
        long unrealizedPnlKrw = summary.unrealizedPnlKrw();
        long totalAssetKrw = wallet.getCashBalanceKrw() + evaluationAmountKrw;

        BigDecimal totalReturnRate = summary.totalReturnRate();

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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rankUsersByStockReturn(Collection<String> userIds) {
        Set<String> allowedUserIds = userIds == null
                ? Set.of()
                : userIds.stream()
                        .filter(userId -> userId != null && !userId.isBlank())
                        .collect(Collectors.toSet());

        List<UserEntity> users = userRepository.findAll().stream()
                .filter(UserEntity::isActive)
                .filter(user -> allowedUserIds.isEmpty() || allowedUserIds.contains(user.getUserId()))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserEntity user : users) {
            InvestmentReturnSummary summary = calculateReturnSummary(user.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", user.getUserId());
            row.put("nickname", user.getNickname());
            row.put("name", user.getName());
            row.put("investedAmountKrw", summary.investedAmountKrw());
            row.put("evaluationAmountKrw", summary.evaluationAmountKrw());
            row.put("realizedPnlKrw", summary.realizedPnlKrw());
            row.put("unrealizedPnlKrw", summary.unrealizedPnlKrw());
            row.put("totalPnlKrw", summary.totalPnlKrw());
            row.put("totalReturnRate", summary.totalReturnRate().doubleValue());
            row.put("returnRate", summary.totalReturnRate().doubleValue());
            row.put("stockReturnRate", summary.totalReturnRate().doubleValue());
            rows.add(row);
        }

        rows.sort(Comparator
                .comparingDouble((Map<String, Object> row) -> Maps.doubleVal(row, "totalReturnRate"))
                .reversed()
                .thenComparing(row -> Maps.str(row, "nickname")));

        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("rank", i + 1);
            rows.get(i).put("ranking", i + 1);
            rows.get(i).put("currentRanking", i + 1);
        }
        return rows;
    }

    private InvestmentReturnSummary calculateReturnSummary(String userId) {
        List<AssetEntity> assets = assetRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        Map<String, StockEntity> stocksById = stockRepository.findAllById(
                        assets.stream().map(AssetEntity::getStockId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(StockEntity::getStockId, stock -> stock));

        long investedAmountKrw = 0L;
        long evaluationAmountKrw = 0L;
        long realizedPnlKrw = 0L;

        for (AssetEntity asset : assets) {
            realizedPnlKrw += asset.getRealizedPnlKrw();
            if (asset.getQuantity().doubleValue() <= 0) {
                continue;
            }
            investedAmountKrw += asset.getInvestedAmountKrw();
            long currentPriceKrw = resolveCurrentPriceKrw(asset, stocksById.get(asset.getStockId()));
            evaluationAmountKrw += Math.round(asset.getQuantity().doubleValue() * currentPriceKrw);
        }

        long unrealizedPnlKrw = evaluationAmountKrw - investedAmountKrw;
        long totalPnlKrw = realizedPnlKrw + unrealizedPnlKrw;
        BigDecimal totalReturnRate = investedAmountKrw <= 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(totalPnlKrw)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(investedAmountKrw), 4, RoundingMode.HALF_UP);

        return new InvestmentReturnSummary(
                investedAmountKrw,
                evaluationAmountKrw,
                realizedPnlKrw,
                unrealizedPnlKrw,
                totalPnlKrw,
                totalReturnRate
        );
    }

    private long resolveCurrentPriceKrw(AssetEntity asset, StockEntity stock) {
        if (stock != null && stock.getLastPrice() != null && stock.getLastPrice().doubleValue() > 0) {
            return Math.round(stock.getLastPrice().doubleValue());
        }
        return Math.max(asset.getCurrentPriceKrw(), 0L);
    }

    private record InvestmentReturnSummary(
            long investedAmountKrw,
            long evaluationAmountKrw,
            long realizedPnlKrw,
            long unrealizedPnlKrw,
            long totalPnlKrw,
            BigDecimal totalReturnRate
    ) {}
}
