package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration of keys used in Consultation Request extensions.
 * These keys define specific metadata fields or additional attributes attached to consultation requests.
 *
 * @since 2026-07-09
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
        // Return the string key used in extension maps
        return key;
    }
}
