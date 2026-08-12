package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Domain model representing ConsultationRequestExtKey data structures within the CARLOS EMR system, including state and relationships.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
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
        // Internal logic boundary for ConsultationRequestExtKey state management
        return key;
    }
}
