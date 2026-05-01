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
package io.github.carlos_emr.carlos.billings.ca.on.command;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed input for preparing an ON billing correction review.
 */
public record BillingCorrectionValidationCommand(String diagnosticDetail,
                                                 String referralDoctor,
                                                 String rosterStatus,
                                                 boolean manualReview,
                                                 String referralDoctorOhip,
                                                 boolean referralChecked,
                                                 String hcType,
                                                 String hcSex,
                                                 String specialty,
                                                 Map<String, String> xmlParameters,
                                                 List<BillingCorrectionLineCommand> serviceLines,
                                                 String billingNo,
                                                 String hin,
                                                 LocalDate dob,
                                                 String visitType,
                                                 LocalDate visitDate,
                                                 String status,
                                                 String clinicRefCode,
                                                 String providerNo,
                                                 LocalDate billingDate,
                                                 LocalDate updateDate,
                                                 String demoName,
                                                 String demoAddress,
                                                 String demoProvince,
                                                 String demoCity,
                                                 String demoPostal,
                                                 String demoSex) {
    public BillingCorrectionValidationCommand(String diagnosticDetail,
                                              String referralDoctor,
                                              String rosterStatus,
                                              boolean manualReview,
                                              String referralDoctorOhip,
                                              boolean referralChecked,
                                              String hcType,
                                              String hcSex,
                                              String specialty,
                                              Map<String, String> xmlParameters,
                                              List<BillingCorrectionLineCommand> serviceLines,
                                              String billingNo,
                                              String hin,
                                              String dob,
                                              String visitType,
                                              String visitDate,
                                              String status,
                                              String clinicRefCode,
                                              String providerNo,
                                              String billingDate,
                                              String updateDate,
                                              String demoName,
                                              String demoAddress,
                                              String demoProvince,
                                              String demoCity,
                                              String demoPostal,
                                              String demoSex) {
        this(diagnosticDetail,
                referralDoctor,
                rosterStatus,
                manualReview,
                referralDoctorOhip,
                referralChecked,
                hcType,
                hcSex,
                specialty,
                xmlParameters,
                serviceLines,
                billingNo,
                hin,
                Commands.isoDate(dob, "dob"),
                visitType,
                Commands.isoDate(visitDate, "visitDate"),
                status,
                clinicRefCode,
                providerNo,
                Commands.isoDate(billingDate, "billingDate"),
                Commands.isoDate(updateDate, "updateDate"),
                demoName,
                demoAddress,
                demoProvince,
                demoCity,
                demoPostal,
                demoSex);
    }

    public BillingCorrectionValidationCommand {
        diagnosticDetail = Commands.nullToEmpty(diagnosticDetail);
        referralDoctor = Commands.nullToEmpty(referralDoctor);
        rosterStatus = Commands.nullToEmpty(rosterStatus);
        referralDoctorOhip = Commands.nullToEmpty(referralDoctorOhip);
        hcType = Commands.nullToEmpty(hcType);
        hcSex = Commands.nullToEmpty(hcSex);
        specialty = Commands.nullToEmpty(specialty);
        xmlParameters = xmlParameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(xmlParameters));
        serviceLines = serviceLines == null ? List.of() : List.copyOf(serviceLines);
        billingNo = Commands.nullToEmpty(billingNo);
        hin = Commands.nullToEmpty(hin);
        visitType = Commands.nullToEmpty(visitType);
        status = Commands.nullToEmpty(status);
        clinicRefCode = Commands.nullToEmpty(clinicRefCode);
        providerNo = Commands.nullToEmpty(providerNo);
        demoName = Commands.nullToEmpty(demoName);
        demoAddress = Commands.nullToEmpty(demoAddress);
        demoProvince = Commands.nullToEmpty(demoProvince);
        demoCity = Commands.nullToEmpty(demoCity);
        demoPostal = Commands.nullToEmpty(demoPostal);
        demoSex = Commands.nullToEmpty(demoSex);
    }

    public String dobText() {
        return Commands.isoText(dob);
    }

    public String visitDateText() {
        return Commands.isoText(visitDate);
    }

    public String billingDateText() {
        return Commands.isoText(billingDate);
    }

    public String updateDateText() {
        return Commands.isoText(updateDate);
    }
}
