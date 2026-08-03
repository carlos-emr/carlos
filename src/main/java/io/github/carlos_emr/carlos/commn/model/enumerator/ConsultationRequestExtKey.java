package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Composite key identifier for external consultation request records,
 * joining external provider identifiers with internal referral IDs.
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
