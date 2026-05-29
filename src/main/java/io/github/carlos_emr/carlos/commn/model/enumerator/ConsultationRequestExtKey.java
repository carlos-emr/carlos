package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration defining the allowable states and type categories for ConsultationRequestExtKey within the system.
 */
public enum ConsultationRequestExtKey {
    EREFERRAL_REF("ereferral_ref"),
    EREFERRAL_SERVICE("ereferral_service"),
    EREFERRAL_DOCTOR("ereferral_doctor");

    private final String key;

    ConsultationRequestExtKey(String key) {
        this.key = key;
    }


    // Getkey is exposed here to satisfy the external component interface contract without exposing internal state.
    public String getKey() {
        return key;
    }
}
