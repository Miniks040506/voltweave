package io.voltweave.intelligence.forecast.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.intelligence.forecast.application.model.Forecast;
import io.voltweave.intelligence.forecast.application.model.ForecastPoint;
import io.voltweave.intelligence.forecast.domain.entities.TrainingSample;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;

@Repository
public class ForecastRepository {
    private final JdbcClient jdbcClient;

    public ForecastRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<TrainingSample> trainingSamples(
            UUID organizationId,
            UUID vppId,
            Instant target,
            Instant from,
            Instant to
    ) {
        return jdbcClient.sql("""
                WITH site_intervals AS (
                    SELECT observation.site_id,
                           date_trunc('day', observation.observed_at) AS sample_day,
                           observation.energy_type,
                           avg(observation.power_kw) AS power_kw
                    FROM energy_observations observation
                    JOIN vpp_site_projection member
                      ON member.site_id = observation.site_id
                     AND member.vpp_organization_id = :organizationId
                     AND member.vpp_id = :vppId
                     AND member.active
                    WHERE observation.observed_at >= :from
                      AND observation.observed_at < :to
                      AND extract(hour FROM observation.observed_at AT TIME ZONE 'UTC')
                          = extract(hour FROM CAST(:target AS timestamptz) AT TIME ZONE 'UTC')
                      AND floor(extract(minute FROM observation.observed_at AT TIME ZONE 'UTC') / 15)
                          = floor(extract(minute FROM CAST(:target AS timestamptz) AT TIME ZONE 'UTC') / 15)
                    GROUP BY observation.site_id, sample_day, observation.energy_type
                ), daily AS (
                    SELECT sample_day,
                           sum(power_kw) FILTER (WHERE energy_type = 'GRID_IMPORT') AS load_kw,
                           coalesce(sum(power_kw) FILTER (
                               WHERE energy_type = 'SOLAR_GENERATION'
                           ), 0) AS solar_kw
                    FROM site_intervals
                    GROUP BY sample_day
                )
                SELECT sample_day, load_kw, solar_kw
                FROM daily
                WHERE load_kw IS NOT NULL
                ORDER BY sample_day
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("target", timestamp(target))
                .param("from", timestamp(from))
                .param("to", timestamp(to))
                .query((resultSet, rowNumber) -> new TrainingSample(
                        resultSet.getTimestamp("sample_day").toInstant(),
                        resultSet.getBigDecimal("load_kw"),
                        resultSet.getBigDecimal("solar_kw")
                ))
                .list();
    }

    public long nextVersion(UUID organizationId, UUID vppId, Instant now) {
        return jdbcClient.sql("""
                INSERT INTO forecast_versions (
                    vpp_organization_id, vpp_id, last_version, updated_at
                ) VALUES (:organizationId, :vppId, 1, :now)
                ON CONFLICT (vpp_organization_id, vpp_id) DO UPDATE
                SET last_version = forecast_versions.last_version + 1,
                    updated_at = EXCLUDED.updated_at
                RETURNING last_version
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("now", timestamp(now))
                .query(Long.class)
                .single();
    }

    public void insert(Forecast forecast) {
        jdbcClient.sql("""
                INSERT INTO forecasts (
                    id, vpp_organization_id, vpp_id, version, horizon,
                    model_name, model_version, generated_at, training_from,
                    training_to, target_start, target_end, valid_until
                ) VALUES (
                    :id, :organizationId, :vppId, :version, :horizon,
                    :modelName, :modelVersion, :generatedAt, :trainingFrom,
                    :trainingTo, :targetStart, :targetEnd, :validUntil
                )
                """)
                .param("id", forecast.id())
                .param("organizationId", forecast.organizationId())
                .param("vppId", forecast.vppId())
                .param("version", forecast.version())
                .param("horizon", forecast.horizon().name())
                .param("modelName", forecast.modelName())
                .param("modelVersion", forecast.modelVersion())
                .param("generatedAt", timestamp(forecast.generatedAt()))
                .param("trainingFrom", timestamp(forecast.trainingFrom()))
                .param("trainingTo", timestamp(forecast.trainingTo()))
                .param("targetStart", timestamp(forecast.targetStart()))
                .param("targetEnd", timestamp(forecast.targetEnd()))
                .param("validUntil", timestamp(forecast.validUntil()))
                .update();

        for (var point : forecast.points()) {
            jdbcClient.sql("""
                    INSERT INTO forecast_points (
                        vpp_organization_id, forecast_id, forecast_at,
                        baseline_grid_import_kw, solar_generation_kw
                    ) VALUES (
                        :organizationId, :forecastId, :forecastAt, :baseline, :solar
                    )
                    """)
                    .param("organizationId", forecast.organizationId())
                    .param("forecastId", forecast.id())
                    .param("forecastAt", timestamp(point.forecastAt()))
                    .param("baseline", point.baselineGridImportKw())
                    .param("solar", point.solarGenerationKw())
                    .update();
        }
    }

    public Optional<Forecast> latest(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT * FROM forecasts
                WHERE vpp_organization_id = :organizationId AND vpp_id = :vppId
                ORDER BY version DESC LIMIT 1
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query((row, rowNumber) -> new ForecastHeader(
                        row.getObject("id", UUID.class),
                        row.getObject("vpp_organization_id", UUID.class),
                        row.getObject("vpp_id", UUID.class), row.getLong("version"),
                        ForecastHorizon.valueOf(row.getString("horizon")),
                        row.getString("model_name"), row.getString("model_version"),
                        row.getTimestamp("generated_at").toInstant(),
                        row.getTimestamp("training_from").toInstant(),
                        row.getTimestamp("training_to").toInstant(),
                        row.getTimestamp("target_start").toInstant(),
                        row.getTimestamp("target_end").toInstant(),
                        row.getTimestamp("valid_until").toInstant()
                ))
                .optional()
                .map(this::withPoints);
    }

    private Forecast withPoints(ForecastHeader header) {
        var points = jdbcClient.sql("""
                SELECT forecast_at, baseline_grid_import_kw, solar_generation_kw
                FROM forecast_points
                WHERE vpp_organization_id = :organizationId AND forecast_id = :forecastId
                ORDER BY forecast_at
                """)
                .param("organizationId", header.organizationId())
                .param("forecastId", header.id())
                .query((resultSet, rowNumber) -> new ForecastPoint(
                        resultSet.getTimestamp("forecast_at").toInstant(),
                        resultSet.getBigDecimal("baseline_grid_import_kw"),
                        resultSet.getBigDecimal("solar_generation_kw")
                )).list();
        return new Forecast(
                header.id(), header.organizationId(), header.vppId(), header.version(),
                header.horizon(), header.modelName(), header.modelVersion(),
                header.generatedAt(), header.trainingFrom(), header.trainingTo(),
                header.targetStart(), header.targetEnd(), header.validUntil(), points
        );
    }

    private record ForecastHeader(
            UUID id, UUID organizationId, UUID vppId, long version,
            ForecastHorizon horizon, String modelName, String modelVersion,
            Instant generatedAt, Instant trainingFrom, Instant trainingTo,
            Instant targetStart, Instant targetEnd, Instant validUntil
    ) {
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
