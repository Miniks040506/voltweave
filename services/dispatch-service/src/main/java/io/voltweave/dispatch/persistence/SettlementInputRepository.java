package io.voltweave.dispatch.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.application.model.SettlementInput;

@Repository
public class SettlementInputRepository {
    private final JdbcClient jdbcClient;

    public SettlementInputRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SettlementInput.Participant> participants(UUID dispatchId) {
        return jdbcClient.sql("""
                WITH participants AS (
                    SELECT site_id, device_id, device_type,
                           allocated_power_kw, expected_energy_kwh
                    FROM dispatch_allocations WHERE dispatch_id = :dispatchId
                    UNION ALL
                    SELECT site_id, device_id, device_type,
                           allocated_power_kw, expected_energy_kwh
                    FROM dispatch_replacement_allocations WHERE dispatch_id = :dispatchId
                )
                SELECT p.*, COALESCE(performance.cumulative_delivered_energy_kwh, 0)
                           AS delivered_energy_kwh
                FROM participants p
                LEFT JOIN LATERAL (
                    SELECT cumulative_delivered_energy_kwh
                    FROM dispatch_performance_points
                    WHERE dispatch_id = :dispatchId AND device_id = p.device_id
                    ORDER BY observed_at DESC LIMIT 1
                ) performance ON true
                ORDER BY p.site_id, p.device_id
                """)
                .param("dispatchId", dispatchId)
                .query((row, rowNumber) -> new SettlementInput.Participant(
                        row.getObject("site_id", UUID.class),
                        row.getObject("device_id", UUID.class), row.getString("device_type"),
                        row.getBigDecimal("allocated_power_kw"),
                        row.getBigDecimal("expected_energy_kwh"),
                        row.getBigDecimal("delivered_energy_kwh")
                )).list();
    }
}
