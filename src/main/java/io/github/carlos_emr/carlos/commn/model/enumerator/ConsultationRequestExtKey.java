package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration of extended keys for consultation requests.
 * <p>
 * Defines the standard keys used for storing and retrieving extended metadata
 * associated with consultation requests in CARLOS EMR.
 * </p>
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
    // Ensure extended keys match the expected JSON payload fields from the external referral API.
        return key;
    }
}
