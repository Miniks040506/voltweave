package io.voltweave.dispatch.domain.enums;

public enum DispatchStatus {
    DRAFT,
    SCHEDULED,
    PREPARING,
    ACTIVE,
    REBALANCING,
    COMPLETING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED
}
