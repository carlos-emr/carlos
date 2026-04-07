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

import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FrmRecordFactory {

    /**
     * Whitelist of permitted form class name suffixes (the segment between "Frm" and "Record").
     *
     * <p>Only names present in this set may be instantiated via reflection. Any user-supplied
     * {@code form_class} value that is not on this list is rejected before {@code Class.forName()}
     * is ever called, preventing unsafe reflective class instantiation (CWE-470).</p>
     *
     * <p>When a new {@code Frm*Record} class is added to the codebase, its name suffix must
     * also be added here.</p>
     */
    public static final Set<String> ALLOWED_FORM_CLASSES;

    static {
        Set<String> allowed = new HashSet<>();
        allowed.add("2MinWalk");
        allowed.add("AdfV2");
        allowed.add("Annual");
        allowed.add("AnnualV2");
        allowed.add("BCAR");
        allowed.add("BCAR2007");
        allowed.add("BCAR2012");
        allowed.add("BCAR2020");
        allowed.add("BCBirthSumMo2008");
        allowed.add("BCBrithSumMo");
        allowed.add("BCClientChartChecklist");
        allowed.add("BCHP");
        allowed.add("BCINR");
        allowed.add("BCNewBorn");
        allowed.add("BCNewBorn2008");
        allowed.add("CESD");
        allowed.add("Caregiver");
        allowed.add("Consultant");
        allowed.add("CostQuestionnaire");
        allowed.add("Counseling");
        allowed.add("CounsellorAssessment");
        allowed.add("DischargeSummary");
        allowed.add("Falls");
        allowed.add("GripStrength");
        allowed.add("Growth0_36");
        allowed.add("GrowthChart");
        allowed.add("HomeFalls");
        allowed.add("ImmunAllergy");
        allowed.add("IntakeInfo");
        allowed.add("InternetAccess");
        allowed.add("Invoice");
        allowed.add("LabReq");
        allowed.add("LabReq07");
        allowed.add("LabReq10");
        allowed.add("LateLifeFDIDisability");
        allowed.add("LateLifeFDIFunction");
        allowed.add("MMSE");
        allowed.add("MentalHealth");
        allowed.add("MentalHealthForm1");
        allowed.add("MentalHealthForm14");
        allowed.add("MentalHealthForm42");
        allowed.add("PalliativeCare");
        allowed.add("PeriMenopausal");
        allowed.add("Policy");
        allowed.add("PositionHazard");
        allowed.add("ReceptionAssessment");
        allowed.add("RhImmuneGlobulin");
        allowed.add("Rourke");
        allowed.add("Rourke2006");
        allowed.add("Rourke2009");
        allowed.add("Rourke2017");
        allowed.add("Rourke2020");
        allowed.add("SF36");
        allowed.add("SF36Caregiver");
        allowed.add("SatisfactionScale");
        allowed.add("SelfAdministered");
        allowed.add("SelfAssessment");
        allowed.add("SelfEfficacy");
        allowed.add("SelfManagement");
        allowed.add("TreatmentPref");
        allowed.add("chf");
        ALLOWED_FORM_CLASSES = Collections.unmodifiableSet(allowed);
    }

    /**
     * Instantiates the {@link FrmRecord} subclass identified by {@code which}.
     *
     * <p>{@code which} must be a member of {@link #ALLOWED_FORM_CLASSES}; any other value
     * (including {@code null}) is rejected and {@code null} is returned. This prevents
     * user-controlled input from being used for arbitrary reflective class instantiation.</p>
     *
     * @param which the form class name suffix (the part between "Frm" and "Record")
     * @return the instantiated {@link FrmRecord}, or {@code null} if {@code which} is not
     *         on the whitelist or if instantiation fails
     */
    @SuppressWarnings({"rawtypes", "deprecation"})
    public FrmRecord factory(String which) {

        if (which == null || !ALLOWED_FORM_CLASSES.contains(which)) {
            MiscUtils.getLogger().warn("Rejected disallowed form class name: {}", LogSanitizer.sanitize(which));
            return null;
        }

        // Build the full class name from the whitelisted form name.
        String fullName = "io.github.carlos_emr.carlos.form.Frm" + which + "Record";
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
