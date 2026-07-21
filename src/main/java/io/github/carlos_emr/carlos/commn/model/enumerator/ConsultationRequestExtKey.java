package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration of extended keys for consultation requests.
 * Provides standardized identifiers for accessing or storing additional
 * metadata and dynamic attributes associated with a consultation request.
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
