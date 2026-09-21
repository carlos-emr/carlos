/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form;

/**
 * Assembles the Ontario requisition practitioner number that the lab requisition and mental
 * health forms render, post back and print.
 *
 * <p>The historical shape is {@code 0000-<billing number>-<specialty code>}, and every caller
 * used to concatenate the three parts unconditionally. A provider with no {@code ohip_no}
 * therefore produced {@code 0000--00}: a skeleton of separators around an empty billing number.
 *
 * <p>That literal is not merely cosmetic. {@code --} is the SQL line comment marker, so OWASP
 * CRS rule 942100 (libinjection) scores a form body carrying it as an injection attempt and the
 * reverse proxy in front of CARLOS rejects the save with HTTP 403 before the request reaches
 * Tomcat. That is the failure reported in issue #3724: the demo Ontario data ships a
 * {@code formLabReq07} row holding {@code 0000--00} precisely because this value was generated
 * and saved once, and every later save of that form was blocked.
 *
 * <p>A form that cannot name a billing number should print nothing rather than a skeleton, so an
 * absent or blank billing number yields an empty string here. Callers write the result straight
 * into the {@code practitionerNo} property, which every consuming JSP reads with an empty-string
 * default.
 *
 * @since 2026-09-18
 */
final class PractitionerNumber {

    /**
     * Fixed leading group of the requisition number. It is not a billing number in its own right;
     * the Ministry form simply prints four zeroes ahead of the practitioner's number.
     */
    private static final String PREFIX = "0000-";

    private PractitionerNumber() {
    }

    /**
     * Builds a requisition practitioner number from its parts, omitting any part that is unknown.
     *
     * @param billingNo the provider's OHIP billing number ({@code provider.ohip_no}); may be
     *                  {@code null} or blank when the provider record has none
     * @param specialtyCode the specialty code parsed out of the provider's comments XML; may be
     *                      {@code null} or blank
     * @return {@code 0000-<billingNo>-<specialtyCode>}; the same value without the trailing
     *         specialty segment when no specialty code is known; or an empty string when there is
     *         no billing number, so that no value containing an empty segment is ever emitted
     */
    static String ohipRequisition(String billingNo, String specialtyCode) {
        if (billingNo == null || billingNo.trim().isEmpty()) {
            return "";
        }
        String requisitionNumber = PREFIX + billingNo.trim();
        if (specialtyCode == null || specialtyCode.trim().isEmpty()) {
            return requisitionNumber;
        }
        return requisitionNumber + "-" + specialtyCode.trim();
    }
}
