package io.voltweave.contracts.events.command.v1;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CommandPayloadV1Tests {
    private static final Instant START = Instant.parse("2026-08-13T05:00:00Z");

    @Test
    void rejectsInvalidCommandInterval() {
        assertThatThrownBy(() -> new CommandRequestedPayloadV1(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "SET_POWER", BigDecimal.ONE, START, START, START, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("validFrom must precede expiresAt");
    }

    @Test
    void rejectsAcknowledgementDeadlineOutsideCommandInterval() {
        assertThatThrownBy(() -> new CommandRequestedPayloadV1(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "SET_POWER", BigDecimal.ONE, START, START.minusSeconds(1),
                START.plusSeconds(60), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("acknowledgementDeadlineAt must be within the command interval");
    }

    @Test
    void rejectsIncompleteAcknowledgement() {
        assertThatThrownBy(() -> new CommandAcknowledgedPayloadV1(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "REJECTED", BigDecimal.ZERO, null, START
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected acknowledgement requires a reason");
    }
}
