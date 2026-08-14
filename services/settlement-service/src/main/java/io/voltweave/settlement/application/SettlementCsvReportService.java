package io.voltweave.settlement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.settlement.application.model.Settlement;
import io.voltweave.settlement.persistence.RewardLedgerRepository;

@Service
public class SettlementCsvReportService {
    private final RewardLedgerRepository rewardRepository;

    public SettlementCsvReportService(RewardLedgerRepository rewardRepository) {
        this.rewardRepository = rewardRepository;
    }

    @Transactional(readOnly = true)
    public String create(Settlement settlement) {
        var energyBySite = new LinkedHashMap<UUID, Energy>();
        settlement.lines().forEach(line -> energyBySite.merge(
                line.siteId(), new Energy(line.expectedEnergyKwh(), line.deliveredEnergyKwh()),
                Energy::add
        ));
        var rewardBySite = new LinkedHashMap<UUID, BigDecimal>();
        rewardRepository.findBySettlementId(settlement.id()).forEach(entry ->
                rewardBySite.merge(entry.participantId(), entry.amount(), BigDecimal::add)
        );

        var csv = new StringBuilder("dispatch_id,settlement_id,site_id,")
                .append("expected_energy_kwh,delivered_energy_kwh,")
                .append("achievement_percent,reward_amount,currency\n");
        energyBySite.forEach((siteId, energy) -> csv
                .append(settlement.dispatchId()).append(',')
                .append(settlement.id()).append(',')
                .append(siteId).append(',')
                .append(energy.expected().toPlainString()).append(',')
                .append(energy.delivered().toPlainString()).append(',')
                .append(percent(energy)).append(',')
                .append(rewardBySite.getOrDefault(siteId, BigDecimal.ZERO)
                        .setScale(4, RoundingMode.HALF_UP).toPlainString())
                .append(",VWC\n"));
        return csv.toString();
    }

    private static String percent(Energy energy) {
        if (energy.expected().signum() == 0) {
            return "0.000";
        }
        return energy.delivered().multiply(BigDecimal.valueOf(100))
                .divide(energy.expected(), 3, RoundingMode.HALF_UP).toPlainString();
    }

    private record Energy(BigDecimal expected, BigDecimal delivered) {
        private Energy add(Energy other) {
            return new Energy(expected.add(other.expected), delivered.add(other.delivered));
        }
    }
}
