package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration of keys used to identify extended attribute fields on a consultation request, facilitating dynamic metadata storage without schema changes.
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
