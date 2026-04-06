/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.util.Set;

public class FrmRecordFactory {

    /**
     * Whitelist of allowed form class name suffixes (the {@code which} parameter).
     *
     * <p>The full class name is resolved as:
     * {@code io.github.carlos_emr.carlos.form.Frm<which>Record}.
     * Add the bare suffix (without the {@code Frm} prefix and {@code Record} suffix)
     * here whenever a new {@code Frm*Record} implementation is introduced.</p>
     */
    public static final Set<String> ALLOWED_FORM_CLASSES = Set.of(
            "2MinWalk",
            "AdfV2",
            "Annual",
            "AnnualV2",
            "BCAR",
            "BCAR2007",
            "BCAR2012",
            "BCAR2020",
            "BCBirthSumMo2008",
            "BCBrithSumMo",
            "BCClientChartChecklist",
            "BCHP",
            "BCINR",
            "BCNewBorn",
            "BCNewBorn2008",
            "Caregiver",
            "CESD",
            "chf",
            "Consultant",
            "CostQuestionnaire",
            "Counseling",
            "CounsellorAssessment",
            "DischargeSummary",
            "Falls",
            "GripStrength",
            "Growth0_36",
            "GrowthChart",
            "HomeFalls",
            "ImmunAllergy",
            "IntakeInfo",
            "InternetAccess",
            "Invoice",
            "LabReq",
            "LabReq07",
            "LabReq10",
            "LateLifeFDIDisability",
            "LateLifeFDIFunction",
            "MentalHealth",
            "MentalHealthForm1",
            "MentalHealthForm14",
            "MentalHealthForm42",
            "MMSE",
            "PalliativeCare",
            "PeriMenopausal",
            "Policy",
            "PositionHazard",
            "ReceptionAssessment",
            "RhImmuneGlobulin",
            "Rourke",
            "Rourke2006",
            "Rourke2009",
            "Rourke2017",
            "Rourke2020",
            "SF36",
            "SF36Caregiver",
            "SatisfactionScale",
            "SelfAdministered",
            "SelfAssessment",
            "SelfEfficacy",
            "SelfManagement",
            "TreatmentPref"
    );

    /**
     * Instantiates the {@link FrmRecord} implementation identified by {@code which}.
     *
     * <p>{@code which} is validated against {@link #ALLOWED_FORM_CLASSES} before
     * any reflective instantiation takes place, preventing user-controlled class
     * loading (CWE-470 / CodeQL {@code java/Reflection}).</p>
     *
     * @param which the bare form class suffix (e.g. {@code "LabReq07"})
     * @return a new instance of the corresponding {@code Frm<which>Record} class,
     *         or {@code null} if instantiation fails after whitelist validation
     * @throws SecurityException if {@code which} is {@code null} or not in
     *                           {@link #ALLOWED_FORM_CLASSES}
     */
    public FrmRecord factory(String which) {

        if (which == null || !ALLOWED_FORM_CLASSES.contains(which)) {
            throw new SecurityException("Invalid form class: " + which);
        }

        // Build the full class name from the form name (the 'which' parameter).
        String fullName = "io.github.carlos_emr.carlos.form.Frm" + which + "Record"; // keyword - form_name get reference to the class
        FrmRecord myClass = null;

        try {
            Class classDefinition = Class.forName(fullName);
            myClass = (FrmRecord) classDefinition.newInstance();
        } catch (InstantiationException e) {
            MiscUtils.getLogger().debug("debug", e);
        } catch (IllegalAccessException e) {
            MiscUtils.getLogger().debug("debug", e);
        } catch (ClassNotFoundException e) {
            MiscUtils.getLogger().debug("debug", e);
        }

        return myClass;
    }
}
