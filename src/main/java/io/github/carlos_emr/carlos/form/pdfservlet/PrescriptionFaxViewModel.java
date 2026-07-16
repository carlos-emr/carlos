/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

/**
 * Result of preparing an Rx PDF fax job.
 *
 * @param validFaxNumber whether the selected clinic fax line matched a configured fax provider
 * @param pharmacyName pharmacy display name used in the legacy status response
 * @param faxNumber sanitized pharmacy destination fax number used in the legacy status response
 * @param failureReason categorized failure reason when the fax job was not prepared
 */
public record PrescriptionFaxViewModel(
        boolean validFaxNumber,
        String pharmacyName,
        String faxNumber,
        FailureReason failureReason) {

    public enum FailureReason {
        INVALID_CLINIC_FAX,
        NO_MATCHING_CLINIC_FAX_CONFIG
    }

    public PrescriptionFaxViewModel(boolean validFaxNumber, String pharmacyName, String faxNumber) {
        this(validFaxNumber, pharmacyName, faxNumber, null);
    }

    public static PrescriptionFaxViewModel invalidClinicFax(String pharmacyName, String faxNumber) {
        return new PrescriptionFaxViewModel(false, pharmacyName, faxNumber, FailureReason.INVALID_CLINIC_FAX);
    }

    public static PrescriptionFaxViewModel noMatchingClinicFaxConfig(String pharmacyName, String faxNumber) {
        return new PrescriptionFaxViewModel(false, pharmacyName, faxNumber, FailureReason.NO_MATCHING_CLINIC_FAX_CONFIG);
    }
}
