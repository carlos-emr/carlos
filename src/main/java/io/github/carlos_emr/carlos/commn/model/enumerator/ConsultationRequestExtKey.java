package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration ConsultationRequestExtKey defining specific constants used across the domain model.
 */
public enum ConsultationRequestExtKey {
    // Provides type-safe enum constants for ConsultationRequestExtKey

    EREFERRAL_REF("ereferral_ref"),
    EREFERRAL_SERVICE("ereferral_service"),
    EREFERRAL_DOCTOR("ereferral_doctor");

    private final String key;

    ConsultationRequestExtKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
