package io.voltweave.portfolio.device.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.portfolio.v1.PortfolioChangeTypeV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioResourceTypeV1;
import io.voltweave.portfolio.audit.application.AuditService;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;
import io.voltweave.portfolio.device.application.model.DeviceProfile;
import io.voltweave.portfolio.device.application.exception.DeviceNotFoundException;
import io.voltweave.portfolio.device.application.exception.DeviceProvisioningConflictException;
import io.voltweave.portfolio.device.application.exception.IdempotencyKeyConflictException;
import io.voltweave.portfolio.device.domain.entities.DeviceProvisioningRequest;
import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.persistence.ApiIdempotencyRepository;
import io.voltweave.portfolio.device.persistence.ApiIdempotencyRepository.Entry;
import io.voltweave.portfolio.device.persistence.DeviceProvisioningRepository;
import io.voltweave.portfolio.device.persistence.DeviceRepository;
import io.voltweave.portfolio.messaging.application.PortfolioEventService;

@Service
public class DeviceProvisioningApplicationService {
    private static final String OPERATION = "PROVISION_DEVICE";

    private final DeviceRepository deviceRepository;
    private final DeviceProvisioningRepository provisioningRepository;
    private final ApiIdempotencyRepository idempotencyRepository;
    private final AuditService auditService;
    private final PortfolioEventService eventService;

    public DeviceProvisioningApplicationService(
            DeviceRepository deviceRepository,
            DeviceProvisioningRepository provisioningRepository,
            ApiIdempotencyRepository idempotencyRepository,
            AuditService auditService,
            PortfolioEventService eventService
    ) {
        this.deviceRepository = deviceRepository;
        this.provisioningRepository = provisioningRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditService = auditService;
        this.eventService = eventService;
    }

    @Transactional
    public DeviceProvisioningRequest provision(
            UUID deviceId,
            String idempotencyKey,
            String subjectId
    ) {
        var key = normalizeKey(idempotencyKey);
        var device = deviceRepository.findByIdForSubject(deviceId, subjectId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        var now = Instant.now();
        var request = DeviceProvisioningRequest.pending(device, now);
        var requestHash = sha256(OPERATION + ":" + deviceId);
        var entry = new Entry(
                device.organizationId(), OPERATION, key, requestHash, request.id(), now
        );

        if (!idempotencyRepository.insert(entry)) {
            var existing = idempotencyRepository.find(device.organizationId(), OPERATION, key)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record disappeared during provisioning"
                    ));
            if (!existing.requestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException();
            }
            return provisioningRepository.findById(
                    device.organizationId(), existing.resourceId()
            ).filter(saved -> saved.deviceId().equals(deviceId)).orElseThrow(() ->
                    new IllegalStateException("Provisioning request is missing")
            );
        }

        if (device.status() != DeviceLifecycleStatus.REGISTERED) {
            throw new DeviceProvisioningConflictException(deviceId);
        }
        deviceRepository.update(device.beginProvisioning(now));
        provisioningRepository.insert(request);
        var audit = auditService.recordUserAction(
                device.organizationId(), subjectId, AuditAction.DEVICE_PROVISION_REQUESTED,
                AuditResourceType.DEVICE, device.id()
        );
        eventService.record(
                device.organizationId(), EventTypes.DEVICE_PROVISION_REQUESTED, device.id(),
                new PortfolioLifecyclePayloadV1(
                        device.id(), PortfolioResourceTypeV1.DEVICE,
                        PortfolioChangeTypeV1.PROVISION_REQUESTED, null
                ), audit.correlationId()
        );
        return request;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        var normalized = key.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key exceeds 128 characters");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
