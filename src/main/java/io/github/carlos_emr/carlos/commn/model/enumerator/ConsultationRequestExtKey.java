package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration defining the extended property keys applicable to consultation requests.
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
    // Retrieves the raw string key used for mapping consultation request extensions.
        return key;
    }
}
