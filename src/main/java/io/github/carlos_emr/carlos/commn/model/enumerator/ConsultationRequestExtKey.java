package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration defining the specific constants for ConsultationRequestExtKey within the CARLOS system.
 * These values represent strictly allowed options for ConsultationRequestExtKey in the domain model.
 */

public enum ConsultationRequestExtKey {
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
