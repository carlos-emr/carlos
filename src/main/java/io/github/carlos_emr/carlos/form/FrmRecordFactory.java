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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.carlos_emr.carlos.utility.MiscUtils;

public class FrmRecordFactory {

    /**
     * Allowlist of permitted form names that may be loaded via reflection.
     * Each name here corresponds to a class {@code io.github.carlos_emr.carlos.form.Frm<name>Record}.
     * Only classes in this allowlist can be instantiated; any other value is rejected
     * to prevent unsafe reflection from user-controlled input.
     */
    private static final Set<String> ALLOWED_FORM_NAMES;

    static {
        Set<String> names = new HashSet<>();
        names.add("2MinWalk");
        names.add("AdfV2");
        names.add("Annual");
        names.add("AnnualV2");
        names.add("BCAR");
        names.add("BCAR2007");
        names.add("BCAR2012");
        names.add("BCAR2020");
        names.add("BCBirthSumMo2008");
        names.add("BCBrithSumMo");
        names.add("BCClientChartChecklist");
        names.add("BCHP");
        names.add("BCINR");
        names.add("BCNewBorn");
        names.add("BCNewBorn2008");
        names.add("CESD");
        names.add("Caregiver");
        names.add("Consultant");
        names.add("CostQuestionnaire");
        names.add("Counseling");
        names.add("CounsellorAssessment");
        names.add("DischargeSummary");
        names.add("Falls");
        names.add("GripStrength");
        names.add("Growth0_36");
        names.add("GrowthChart");
        names.add("HomeFalls");
        names.add("ImmunAllergy");
        names.add("IntakeInfo");
        names.add("InternetAccess");
        names.add("Invoice");
        names.add("LabReq");
        names.add("LabReq07");
        names.add("LabReq10");
        names.add("LateLifeFDIDisability");
        names.add("LateLifeFDIFunction");
        names.add("MMSE");
        names.add("MentalHealth");
        names.add("MentalHealthForm1");
        names.add("MentalHealthForm14");
        names.add("MentalHealthForm42");
        names.add("PalliativeCare");
        names.add("PeriMenopausal");
        names.add("Policy");
        names.add("PositionHazard");
        names.add("ReceptionAssessment");
        names.add("RhImmuneGlobulin");
        names.add("Rourke");
        names.add("Rourke2006");
        names.add("Rourke2009");
        names.add("Rourke2017");
        names.add("Rourke2020");
        names.add("SF36");
        names.add("SF36Caregiver");
        names.add("SatisfactionScale");
        names.add("SelfAdministered");
        names.add("SelfAssessment");
        names.add("SelfEfficacy");
        names.add("SelfManagement");
        names.add("TreatmentPref");
        names.add("chf");
        ALLOWED_FORM_NAMES = Collections.unmodifiableSet(names);
    }

    public FrmRecord factory(String which) {

        if (which == null || !ALLOWED_FORM_NAMES.contains(which)) {
            MiscUtils.getLogger().warn("FrmRecordFactory: form name '{}' is not on the allowlist — refusing to load", which);
            return null;
        }

        // The form name has been validated against the allowlist; it is safe to build the class name.
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
