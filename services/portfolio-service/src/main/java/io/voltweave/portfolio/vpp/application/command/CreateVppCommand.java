package io.voltweave.portfolio.vpp.application.command;

import java.util.UUID;

public record CreateVppCommand(UUID organizationId, String name, String region) {
}
