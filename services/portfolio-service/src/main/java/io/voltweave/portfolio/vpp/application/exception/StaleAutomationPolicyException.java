package io.voltweave.portfolio.vpp.application.exception;

public class StaleAutomationPolicyException extends RuntimeException {
    public StaleAutomationPolicyException(int expectedVersion, int actualVersion) {
        super("Expected policy version " + expectedVersion + ", actual " + actualVersion);
    }
}
