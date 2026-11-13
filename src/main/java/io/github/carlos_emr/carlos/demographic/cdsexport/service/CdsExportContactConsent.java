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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;

/**
 * The {@code consentToContact} flag on a patient's contact and pharmacy links. CDS 5.2 has no
 * element for it on {@code Contact} or {@code PreferredPharmacy}.
 *
 * <p>"1" is what CARLOS writes when nobody chooses otherwise: the {@code DemographicContact} and
 * {@code DemographicPharmacy} models default it to TRUE, {@code DemographicPharmacyDaoImpl}
 * persists that default for every pharmacy a patient is linked to, contact.jsp and procontact.jsp
 * list "Consent" first, manageHealthCareTeam.jsp posts 1 and {@code Contact2Action} starts a new
 * professional link at TRUE. Exporting the contact without it says nothing a receiver would not
 * already assume, so it is operational. "0" ("No Consent", an ordinary choice on the same screens)
 * is a deliberate instruction not to contact that person or pharmacy: dropping it would silently
 * lift a restriction, so it travels exactly in the native demographic NewCategory record
 * ({@value #REFUSAL_CARRIED_RULE}) beside the exported contact, never held and never dropped. Any
 * other spelling, which no screen writes, requires review. SQL NULL (the form's "Unknown") is
 * absent.
 */
final class CdsExportContactConsent {
    /** Rule of a consent refusal carried in the native demographic record. */
    static final String REFUSAL_CARRIED_RULE =
            "CONTACT_CONSENT_REFUSAL_CARRIED_IN_NATIVE_DEMOGRAPHIC_RECORD";

    private CdsExportContactConsent() {}

    static void assess(CdsExportNativeFieldAssessments state, String destination) {
        String value = state.raw("consentToContact");
        if (value == null || value.isEmpty())
            state.set("consentToContact", Outcome.ABSENT, "", "ABSENT_CONTACT_CONSENT");
        else if (value.equals("1"))
            state.operational("consentToContact", "DEFAULT_CONTACT_CONSENT_HAS_NO_CDS_CARRIER");
        else if (value.equals("0")) {
            var carried = CdsExportDemographicSupportingMapper.carried(value);
            state.set("consentToContact", carried.outcome(), carried.destination(), REFUSAL_CARRIED_RULE);
        }
        else
            state.review(
                    "consentToContact", destination, "UNKNOWN_CONTACT_CONSENT_REQUIRES_REVIEW");
    }
}
