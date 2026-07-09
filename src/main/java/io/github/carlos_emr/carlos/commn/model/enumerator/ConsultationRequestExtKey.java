package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration defining standardized keys used for extended consultation request attributes,
 * ensuring consistency when accessing extra properties mapping.
 */
public enum ConsultationRequestExtKey {
    EREFERRAL_REF("ereferral_ref"),
    EREFERRAL_SERVICE("ereferral_service"),
    EREFERRAL_DOCTOR("ereferral_doctor");

    private final String key;

    ConsultationRequestExtKey(String key) {
        // Process standard operational requirements ensuring context-specific compliance

        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
