package io.voltweave.intelligence.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class WeightedAllocator {

    private static final double EPSILON = 1e-9;

    private WeightedAllocator() {
    }

    public static AllocationPlan allocate(
            List<CandidateResource> candidates,
            double targetPowerKw,
            double reserveMarginPercent,
            Weights weights) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(weights, "weights must not be null");
        requirePositive("targetPowerKw", targetPowerKw);
        requireNonNegative("reserveMarginPercent", reserveMarginPercent);

        double requiredPowerKw = targetPowerKw * (1.0 + reserveMarginPercent / 100.0);
        if (!Double.isFinite(requiredPowerKw)) {
            throw new IllegalArgumentException("target plus reserve margin must be finite");
        }

        var deviceIds = new HashSet<String>();
        for (var candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate must not be null");
            if (!deviceIds.add(candidate.deviceId())) {
                throw new IllegalArgumentException("duplicate deviceId: " + candidate.deviceId());
            }
        }

        var rankedCandidates = candidates.stream()
                .filter(CandidateResource::eligible)
                .filter(candidate -> candidate.availablePowerKw() > 0.0)
                .sorted(Comparator
                        .comparingDouble(weights::score)
                        .reversed()
                        .thenComparing(CandidateResource::deviceId))
                .toList();

        var allocations = new ArrayList<Allocation>();
        double plannedPowerKw = 0.0;

        for (var candidate : rankedCandidates) {
            double remainingPowerKw = requiredPowerKw - plannedPowerKw;
            if (remainingPowerKw <= EPSILON) {
                break;
            }

            double allocatedPowerKw = Math.min(candidate.availablePowerKw(), remainingPowerKw);
            allocations.add(new Allocation(
                    candidate.deviceId(),
                    allocatedPowerKw,
                    weights.score(candidate)));
            plannedPowerKw += allocatedPowerKw;
        }

        return new AllocationPlan(
                targetPowerKw,
                requiredPowerKw,
                plannedPowerKw,
                plannedPowerKw + EPSILON >= requiredPowerKw,
                allocations);
    }

    public record CandidateResource(
            String deviceId,
            double availablePowerKw,
            double reliability,
            double availableSoc,
            double responseSpeed,
            double lowDegradationCost,
            double customerPreference,
            boolean eligible) {

        public CandidateResource {
            if (deviceId == null || deviceId.isBlank()) {
                throw new IllegalArgumentException("deviceId must not be blank");
            }
            requireNonNegative("availablePowerKw", availablePowerKw);
            requireFactor("reliability", reliability);
            requireFactor("availableSoc", availableSoc);
            requireFactor("responseSpeed", responseSpeed);
            requireFactor("lowDegradationCost", lowDegradationCost);
            requireFactor("customerPreference", customerPreference);
        }
    }

    public record Weights(
            double reliability,
            double availableSoc,
            double responseSpeed,
            double lowDegradationCost,
            double customerPreference) {

        public static final Weights V1 = new Weights(0.30, 0.25, 0.20, 0.15, 0.10);

        public Weights {
            requireFactor("reliabilityWeight", reliability);
            requireFactor("availableSocWeight", availableSoc);
            requireFactor("responseSpeedWeight", responseSpeed);
            requireFactor("lowDegradationCostWeight", lowDegradationCost);
            requireFactor("customerPreferenceWeight", customerPreference);

            double sum = reliability
                    + availableSoc
                    + responseSpeed
                    + lowDegradationCost
                    + customerPreference;
            if (Math.abs(sum - 1.0) > EPSILON) {
                throw new IllegalArgumentException("weights must sum to 1");
            }
        }

        public double score(CandidateResource candidate) {
            return reliability * candidate.reliability()
                    + availableSoc * candidate.availableSoc()
                    + responseSpeed * candidate.responseSpeed()
                    + lowDegradationCost * candidate.lowDegradationCost()
                    + customerPreference * candidate.customerPreference();
        }
    }

    public record Allocation(String deviceId, double powerKw, double score) {
    }

    public record AllocationPlan(
            double targetPowerKw,
            double requiredPowerKw,
            double plannedPowerKw,
            boolean feasible,
            List<Allocation> allocations) {

        public AllocationPlan {
            allocations = List.copyOf(allocations);
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireFactor(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
