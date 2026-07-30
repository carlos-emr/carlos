package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Provides core functionality and data representation for ConsultationRequestExtKey.
 *
 * This class is part of the CARLOS EMR system.
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
        // Initialize logic for getKey operation in CARLOS EMR

        return key;
    }
}
