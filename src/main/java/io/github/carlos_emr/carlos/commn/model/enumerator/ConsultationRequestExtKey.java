package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumerates key identifiers used to track extended properties on consultation requests.
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
