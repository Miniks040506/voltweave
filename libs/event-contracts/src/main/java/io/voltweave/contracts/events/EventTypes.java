package io.voltweave.contracts.events;

public final class EventTypes {
    public static final String TELEMETRY_RAW_RECEIVED = "TelemetryRawReceived";
    public static final String TELEMETRY_NORMALIZED = "TelemetryNormalized";
    public static final String ORGANIZATION_MEMBER_ADDED = "OrganizationMemberAdded";
    public static final String DEVICE_PROVISION_REQUESTED = "DeviceProvisionRequested";
    public static final String DEVICE_CREDENTIAL_REVOKED = "DeviceCredentialRevoked";
    public static final String SITE_PREFERENCE_UPDATED = "SitePreferenceUpdated";
    public static final String VPP_SITE_ADDED = "VppSiteAdded";
    public static final String VPP_SITE_REMOVED = "VppSiteRemoved";
    public static final String VPP_AUTOMATION_POLICY_UPDATED =
            "VppAutomationPolicyUpdated";
    public static final String AUDIT_RECORDED = "AuditRecorded";

    private EventTypes() {
    }
}
