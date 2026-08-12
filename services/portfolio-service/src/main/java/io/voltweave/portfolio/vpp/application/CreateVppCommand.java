package io.voltweave.portfolio.vpp.application;

import java.util.UUID;

public record CreateVppCommand(UUID organizationId, String name, String region) {
}
