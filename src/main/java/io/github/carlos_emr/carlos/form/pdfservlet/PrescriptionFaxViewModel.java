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
 */
public record PrescriptionFaxViewModel(boolean validFaxNumber, String pharmacyName, String faxNumber) {
}
