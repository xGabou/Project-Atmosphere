package net.Gabou.projectatmosphere.telemetry.verification;

public enum VerificationStatus {
    OK,
    WARNING,
    ERROR,
    MISSING;

    public String label() {
        return name();
    }
}
