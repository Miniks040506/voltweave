package io.voltweave.dispatch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.DispatchInput;
import io.voltweave.dispatch.application.model.CreateDispatchCommand;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.domain.enums.DispatchStatus;
import io.voltweave.dispatch.exception.IdempotencyConflictException;
import io.voltweave.dispatch.persistence.DispatchRepository;

@Service
public class DispatchApplicationService {
    private static final long INTERVAL_MINUTES = 15;

    private final DispatchRepository repository;
    private final IntelligenceDispatchClient intelligenceClient;
    private final Clock clock;

    @Autowired
    public DispatchApplicationService(
            DispatchRepository repository,
            IntelligenceDispatchClient intelligenceClient
    ) {
        this(repository, intelligenceClient, Clock.systemUTC());
    }

    DispatchApplicationService(
            DispatchRepository repository,
            IntelligenceDispatchClient intelligenceClient,
            Clock clock
    ) {
        this.repository = repository;
        this.intelligenceClient = intelligenceClient;
        this.clock = clock;
    }

    @Transactional
    public Dispatch create(CreateDispatchCommand command) {
        validate(command);
        String requestHash = requestHash(command);
        repository.lockIdempotency(command.organizationId(), command.idempotencyKey());
        var existing = repository.findIdempotency(
                command.organizationId(), command.idempotencyKey()
        );
        if (existing.isPresent()) {
            if (!existing.orElseThrow().requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key was reused with another request"
                );
            }
            return repository.find(command.organizationId(), existing.orElseThrow().resourceId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record references a missing dispatch"
                    ));
        }

        Instant endAt = command.scheduledStartAt().plus(command.duration());
        DispatchInput input = intelligenceClient.input(
                command.organizationId(), command.vppId(), command.optimizationPreviewId(),
                command.scheduledStartAt(), endAt
        );
        validateInput(command, input);
        Instant now = clock.instant();
        var dispatch = new Dispatch(
                UUID.randomUUID(), command.organizationId(), command.vppId(),
                input.optimizationPreviewId(), input.optimizationPreviewVersion(), command.type(),
                input.targetPowerKw(), input.requiredPowerKw(), input.plannedPowerKw(),
                command.scheduledStartAt(), endAt, DispatchStatus.SCHEDULED,
                command.createdBy(), now, 0,
                new Dispatch.Baseline(
                        input.forecastId(), input.forecastVersion(), input.forecastModelName(),
                        input.forecastModelVersion(), input.forecastValidUntil(), now,
                        input.baselinePoints().stream().map(point -> new Dispatch.BaselinePoint(
                                point.forecastAt(), point.baselineGridImportKw()
                        )).toList()
                ),
                input.allocations().stream().map(allocation -> new Dispatch.Allocation(
                        allocation.siteId(), allocation.deviceId(), allocation.deviceType(),
                        allocation.availablePowerKw(), allocation.allocatedPowerKw(), expectedEnergy(
                                allocation.allocatedPowerKw(), command.duration()
                        ), allocation.score()
                )).toList()
        );
        repository.insert(dispatch, command.idempotencyKey(), requestHash);
        return dispatch;
    }

    @Transactional(readOnly = true)
    public Optional<Dispatch> find(UUID organizationId, UUID dispatchId) {
        return repository.find(organizationId, dispatchId);
    }

    @Transactional(readOnly = true)
    public Optional<Dispatch> find(UUID dispatchId) {
        return repository.findById(dispatchId);
    }

    @Transactional(readOnly = true)
    public List<Dispatch> findAll(UUID organizationId, UUID vppId) {
        return repository.findAll(organizationId, vppId);
    }

    private void validate(CreateDispatchCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.organizationId(), "organizationId is required");
        Objects.requireNonNull(command.vppId(), "vppId is required");
        Objects.requireNonNull(command.optimizationPreviewId(), "optimizationPreviewId is required");
        if (!"REDUCE_DEMAND".equals(command.type())) {
            throw new IllegalArgumentException("Only REDUCE_DEMAND is supported in V1");
        }
        if (command.scheduledStartAt() == null
                || command.scheduledStartAt().isBefore(clock.instant())
                || command.scheduledStartAt().getNano() != 0
                || command.scheduledStartAt().getEpochSecond() % (INTERVAL_MINUTES * 60) != 0) {
            throw new IllegalArgumentException("scheduledStartAt must be a future 15-minute boundary");
        }
        long seconds = command.duration() == null ? 0 : command.duration().toSeconds();
        if (seconds < INTERVAL_MINUTES * 60 || seconds > 86_400
                || seconds % (INTERVAL_MINUTES * 60) != 0) {
            throw new IllegalArgumentException("duration must be 15..1440 minutes in 15-minute steps");
        }
        if (command.createdBy() == null || command.createdBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 100) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..100 characters");
        }
    }

    private static void validateInput(CreateDispatchCommand command, DispatchInput input) {
        if (!command.organizationId().equals(input.organizationId())
                || !command.vppId().equals(input.vppId())
                || !command.optimizationPreviewId().equals(input.optimizationPreviewId())
                || command.duration().toSeconds() != input.dispatchDurationSeconds()) {
            throw new IllegalStateException("Intelligence returned mismatched dispatch input");
        }
        if (!input.feasible() || input.plannedPowerKw().compareTo(input.requiredPowerKw()) < 0
                || input.allocations().isEmpty() || input.baselinePoints().isEmpty()) {
            throw new IllegalStateException("Optimization preview is not dispatchable");
        }
    }

    private static BigDecimal expectedEnergy(BigDecimal powerKw, Duration duration) {
        return powerKw.multiply(BigDecimal.valueOf(duration.toSeconds()))
                .divide(BigDecimal.valueOf(3_600), 3, RoundingMode.HALF_UP);
    }

    private static String requestHash(CreateDispatchCommand command) {
        String canonical = String.join("|",
                command.vppId().toString(), command.optimizationPreviewId().toString(),
                command.type(), command.scheduledStartAt().toString(), command.duration().toString()
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
