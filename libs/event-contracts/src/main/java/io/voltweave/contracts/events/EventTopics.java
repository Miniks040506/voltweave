package io.voltweave.contracts.events;

import java.util.List;

public final class EventTopics {
    public static final String TELEMETRY_RAW_V1 = "vw.telemetry.raw.v1";
    public static final String TELEMETRY_NORMALIZED_V1 = "vw.telemetry.normalized.v1";
    public static final String PORTFOLIO_LIFECYCLE_V1 = "vw.portfolio.lifecycle.v1";
    public static final String COMMAND_LIFECYCLE_V1 = "vw.command.lifecycle.v1";
    public static final String DISPATCH_LIFECYCLE_V1 = "vw.dispatch.lifecycle.v1";
    public static final String SETTLEMENT_LIFECYCLE_V1 = "vw.settlement.lifecycle.v1";
    public static final String AUDIT_V1 = "vw.audit.v1";
    public static final String AUDIT_DLQ_V1 = "vw.audit.v1.dlq";

    public static final List<String> V1_TOPICS = List.of(
            TELEMETRY_RAW_V1,
            TELEMETRY_NORMALIZED_V1,
            PORTFOLIO_LIFECYCLE_V1,
            COMMAND_LIFECYCLE_V1,
            DISPATCH_LIFECYCLE_V1,
            SETTLEMENT_LIFECYCLE_V1,
            AUDIT_V1
    );

    private EventTopics() {
    }
}
