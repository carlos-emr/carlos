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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingDemographicSummary;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingONFormViewModel;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.SxmlMisc;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingDateOfBirths;
import static io.github.carlos_emr.carlos.billings.ca.on.support.BillingDateOfBirths.calculateAge;

/**
 * Composer that loads patient context onto the billing form view model:
 * demographic fields, age, roster status, referral doctor, and patient
 * validation messages (HIN / DOB / referral-no warnings).
 *
 * <p>Returns a {@link LoadedDemographic} carrying the derived state
 * (rosterStatus, demoHcType, demoDob, family-doctor blob) that
 * downstream composers — bill-form resolver and service-code grid —
 * need without re-loading the entity.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingONFormDemographicLoader {

    private final DemographicManager demographicManager;
    private final ProfessionalSpecialistDao professionalSpecialistDao;

    public BillingONFormDemographicLoader(DemographicManager demographicManager,
                                   ProfessionalSpecialistDao professionalSpecialistDao) {
        this.demographicManager = demographicManager;
        this.professionalSpecialistDao = professionalSpecialistDao;
    }

    /**
     * Loaded demographic + derived state for downstream use. Immutable
     * carrier — composers that need {@code rosterStatus} or {@code demo}
     * read from this rather than reloading.
     */
    record LoadedDemographic(
            Demographic demo,
            String demoHin,
            String demoHcType,
            String demoDob,
            String rosterStatus) {
    }

    /**
     * Loads the demographic + populates every patient-context field on
     * the builder (last/first/dob/hin/sex/hcType/familyDoctor/roster +
     * derived age). Also resolves the referral doctor from the
     * family_doctor XML blob and populates the referral fields, and
     * builds the warning/error/errorFlag strings.
     */
    LoadedDemographic load(BillingONFormViewModel.Builder b,
                           LoggedInInfo loggedInInfo,
                           String demoNo) {
        Demographic demo = demographicManager.getDemographic(loggedInInfo, demoNo);

        // Canonical demographic projection (HC type defaulting, DOB padding,
        // raw passthrough). Form normalizes sex to "1"/"2" below ONLY when
        // a demographic was loaded — preserving the legacy "demoSex stays
        // empty when demo is null" contract.
        BillingDemographicSummary summary = BillingDemographicSummary.fromDemographic(demo);
        String demoSex = "";
        String familyDoctor = "";
        String assgProviderNo = "";
        String rosterStatus = "";
        if (demo != null) {
            demoSex = demo.getSex() != null && demo.getSex().startsWith("F") ? "2" : "1";
            familyDoctor = nullToEmpty(demo.getFamilyDoctor());
            assgProviderNo = nullToEmpty(demo.getProviderNo());
            rosterStatus = nullToEmpty(demo.getRosterStatus());
        }

        BillingDateOfBirths.AgeResult ageResult = calculateAge(summary.dob());
        // Flat setters accumulate into the builder's fields; the build()
        // step composes them into a {@link BillingDemographicSummary} record
        // unless the orchestrator (or a later composer) calls .demographic()
        // explicitly. Keeping setters flat here means peek* helpers stay
        // consistent across composers that haven't migrated.
        b.demoLast(summary.lastName())
                .demoFirst(summary.firstName())
                .demoHin(summary.hin())
                .demoVer(summary.ver())
                .demoDob(summary.dob())
                .demoDobYear(summary.dobYy())
                .demoDobMonth(summary.dobMm())
                .demoDobDay(summary.dobDd())
                .demoHcType(summary.hcType())
                .demoSex(demoSex)
                .familyDoctor(familyDoctor)
                .assgProviderNo(assgProviderNo)
                .rosterStatus(rosterStatus)
                .age(ageResult.age())
                .demoDobInvalid(ageResult.invalid());

        // Referral doctor extraction from the family_doctor XML blob.
        String rDoctorOhip = resolveReferralDoctor(b, familyDoctor);

        // Validation messages — pre-formatted HTML, same as the legacy scriptlet.
        populateValidationMessages(b, summary.hin(), rDoctorOhip, summary.dob());

        return new LoadedDemographic(demo, summary.hin(), summary.hcType(), summary.dob(), rosterStatus);
    }

    /**
     * Resolves the referral doctor + specialty from the {@code family_doctor}
     * XML blob, populates the corresponding builder fields, and returns the
     * referral OHIP number for downstream use (validation messenger).
     */
    private String resolveReferralDoctor(BillingONFormViewModel.Builder b, String familyDoctor) {
        String rDoctor;
        String rDoctorOhip;
        String referralSpecialty = "";
        if (familyDoctor.isEmpty()) {
            rDoctor = "N/A";
            rDoctorOhip = "000000";
        } else {
            rDoctor = firstNonNull(SxmlMisc.getXmlContent(familyDoctor, "rd"), "");
            rDoctorOhip = firstNonNull(SxmlMisc.getXmlContent(familyDoctor, "rdohip"), "");
            ProfessionalSpecialist specialist = professionalSpecialistDao.getByReferralNo(rDoctorOhip);
            if (specialist != null) {
                rDoctor = specialist.getLastName() + "," + specialist.getFirstName();
                referralSpecialty = firstNonNull(specialist.getSpecialtyType(), "");
            }
        }
        b.referralDoctor(rDoctor)
                .referralDoctorOhip(rDoctorOhip)
                .referralSpecialty(referralSpecialty);
        return rDoctorOhip;
    }

    /**
     * Builds the patient-validation banners (HIN / referral-no / DOB) and
     * the {@code errorFlag} based on the loaded demographic's state.
     */
    private void populateValidationMessages(BillingONFormViewModel.Builder b,
                                            String demoHin,
                                            String rDoctorOhip,
                                            String demoDob) {
        StringBuilder warning = new StringBuilder();
        StringBuilder error = new StringBuilder();
        String errorFlag = "";
        if (demoHin != null && demoHin.isEmpty()) {
            warning.append("<b><div class='alert alert-danger'>Warning: The patient does not have a valid HIN. </div></b>");
        }
        if (rDoctorOhip != null && !rDoctorOhip.isEmpty() && rDoctorOhip.length() != 6) {
            warning.append("<div class='alert alert error'>Warning: the referral doctor's no is wrong. </div>");
        }
        if (demoDob == null || demoDob.isEmpty() || demoDob.length() != 8) {
            errorFlag = "1";
            error.append("<b><div class='alert alert error'>Error: The patient does not have a valid DOB. </div></b>");
        }
        b.warningMsg(warning.toString())
                .errorMsg(error.toString())
                .errorFlag(errorFlag);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
