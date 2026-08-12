package io.voltweave.telemetry.query.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.telemetry.query.application.model.DeviceTwin;
import io.voltweave.telemetry.query.application.model.TelemetryPoint;
import io.voltweave.telemetry.query.persistence.TelemetryQueryRepository;

@Service
public class TelemetryQueryService {
    private static final int MAX_LIMIT = 1_000;

    private final TelemetryQueryRepository repository;

    public TelemetryQueryService(TelemetryQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TelemetryPoint> siteHistory(
            UUID organizationId,
            UUID siteId,
            Instant from,
            Instant to,
            int limit
    ) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must precede to");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return repository.findSiteHistory(organizationId, siteId, from, to, limit);
    }

    @Transactional(readOnly = true)
    public List<DeviceTwin> siteTwins(UUID organizationId, UUID siteId) {
        return repository.findSiteTwins(organizationId, siteId);
    }

    @Transactional(readOnly = true)
    public Optional<DeviceTwin> deviceTwin(UUID organizationId, UUID deviceId) {
        return repository.findDeviceTwin(organizationId, deviceId);
    }
}
