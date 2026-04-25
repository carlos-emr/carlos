/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.SxmlMisc;

import static io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONFormDataAssembler.calculateAge;

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
final class BillingONFormDemographicLoader {

    private final DemographicManager demographicManager;
    private final ProfessionalSpecialistDao professionalSpecialistDao;

    BillingONFormDemographicLoader(DemographicManager demographicManager,
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

        String demoLast = "";
        String demoFirst = "";
        String demoHin = "";
        String demoVer = "";
        String demoDob = "";
        String demoHcType = "";
        String demoSex = "";
        String familyDoctor = "";
        String assgProviderNo = "";
        String rosterStatus = "";
        if (demo != null) {
            demoLast = nullToEmpty(demo.getLastName());
            demoFirst = nullToEmpty(demo.getFirstName());
            // Zero-pad month/day so the YYYYMMDD substring slices below land
            // at fixed positions. Without padding, a single-digit month (e.g.
            // "4") makes demoDob 6-7 chars, substring(4, 6) becomes "47", and
            // calculateAge() returns 0.
            demoDob = nullToEmpty(demo.getYearOfBirth())
                    + padTwo(nullToEmpty(demo.getMonthOfBirth()))
                    + padTwo(nullToEmpty(demo.getDateOfBirth()));
            demoHin = nullToEmpty(demo.getHin());
            demoVer = nullToEmpty(demo.getVer());
            demoHcType = nullToEmpty(demo.getHcType());
            demoSex = demo.getSex() != null && demo.getSex().startsWith("F") ? "2" : "1";
            familyDoctor = nullToEmpty(demo.getFamilyDoctor());
            assgProviderNo = nullToEmpty(demo.getProviderNo());
            rosterStatus = nullToEmpty(demo.getRosterStatus());
        }

        // HC type normalization: scriptlet coerced missing/short values to "ON".
        if (demoHcType == null || demoHcType.length() < 2) {
            demoHcType = "ON";
        } else {
            demoHcType = demoHcType.substring(0, 2).toUpperCase();
        }

        BillingONFormDataAssembler.AgeResult ageResult = calculateAge(demoDob);
        b.demoLast(demoLast)
                .demoFirst(demoFirst)
                .demoHin(demoHin)
                .demoVer(demoVer)
                .demoDob(demoDob)
                .demoDobYear(demoDob.length() >= 4 ? demoDob.substring(0, 4) : "")
                .demoDobMonth(demoDob.length() >= 6 ? demoDob.substring(4, 6) : "")
                .demoDobDay(demoDob.length() >= 8 ? demoDob.substring(6, 8) : "")
                .demoHcType(demoHcType)
                .demoSex(demoSex)
                .familyDoctor(familyDoctor)
                .assgProviderNo(assgProviderNo)
                .rosterStatus(rosterStatus)
                .age(ageResult.age())
                .demoDobInvalid(ageResult.invalid());

        // Referral doctor extraction from the family_doctor XML blob.
        String rDoctorOhip = resolveReferralDoctor(b, familyDoctor);

        // Validation messages — pre-formatted HTML, same as the legacy scriptlet.
        populateValidationMessages(b, demoHin, rDoctorOhip, demoDob);

        return new LoadedDemographic(demo, demoHin, demoHcType, demoDob, rosterStatus);
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

    private static String padTwo(String value) {
        return value != null && value.length() == 1 ? "0" + value : nullToEmpty(value);
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
