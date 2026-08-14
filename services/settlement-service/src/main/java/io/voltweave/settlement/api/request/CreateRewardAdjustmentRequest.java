package io.voltweave.settlement.api.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRewardAdjustmentRequest(
        @NotNull UUID siteId,
        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Size(max = 500) String reason
) {
}
