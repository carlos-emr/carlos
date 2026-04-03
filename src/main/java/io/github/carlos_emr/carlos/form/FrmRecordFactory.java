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
import org.owasp.encoder.Encode;

import java.util.Map;

public class FrmRecordFactory {

    /**
     * Allowlist of permitted form record types. Maps the short form name (the
     * {@code which} parameter) to its concrete {@link FrmRecord} class.  Only
     * keys present in this map can be instantiated; any other value is rejected
     * to prevent reflective class-loading from user-controlled input
     * (CWE-470 / SonarCloud javasecurity:S6173).
     */
    private static final Map<String, Class<? extends FrmRecord>> FORM_REGISTRY = Map.ofEntries(
            Map.entry("2MinWalk", Frm2MinWalkRecord.class),
            Map.entry("AdfV2", FrmAdfV2Record.class),
            Map.entry("Annual", FrmAnnualRecord.class),
            Map.entry("AnnualV2", FrmAnnualV2Record.class),
            Map.entry("BCAR2007", FrmBCAR2007Record.class),
            Map.entry("BCAR2012", FrmBCAR2012Record.class),
            Map.entry("BCAR2020", FrmBCAR2020Record.class),
            Map.entry("BCAR", FrmBCARRecord.class),
            Map.entry("BCBirthSumMo2008", FrmBCBirthSumMo2008Record.class),
            Map.entry("BCBrithSumMo", FrmBCBrithSumMoRecord.class),
            Map.entry("BCClientChartChecklist", FrmBCClientChartChecklistRecord.class),
            Map.entry("BCHP", FrmBCHPRecord.class),
            Map.entry("BCINR", FrmBCINRRecord.class),
            Map.entry("BCNewBorn2008", FrmBCNewBorn2008Record.class),
            Map.entry("BCNewBorn", FrmBCNewBornRecord.class),
            Map.entry("CESD", FrmCESDRecord.class),
            Map.entry("Caregiver", FrmCaregiverRecord.class),
            Map.entry("Consultant", FrmConsultantRecord.class),
            Map.entry("CostQuestionnaire", FrmCostQuestionnaireRecord.class),
            Map.entry("Counseling", FrmCounselingRecord.class),
            Map.entry("CounsellorAssessment", FrmCounsellorAssessmentRecord.class),
            Map.entry("DischargeSummary", FrmDischargeSummaryRecord.class),
            Map.entry("Falls", FrmFallsRecord.class),
            Map.entry("GripStrength", FrmGripStrengthRecord.class),
            Map.entry("Growth0_36", FrmGrowth0_36Record.class),
            Map.entry("GrowthChart", FrmGrowthChartRecord.class),
            Map.entry("HomeFalls", FrmHomeFallsRecord.class),
            Map.entry("ImmunAllergy", FrmImmunAllergyRecord.class),
            Map.entry("IntakeInfo", FrmIntakeInfoRecord.class),
            Map.entry("InternetAccess", FrmInternetAccessRecord.class),
            Map.entry("Invoice", FrmInvoiceRecord.class),
            Map.entry("LabReq07", FrmLabReq07Record.class),
            Map.entry("LabReq10", FrmLabReq10Record.class),
            Map.entry("LabReq", FrmLabReqRecord.class),
            Map.entry("LateLifeFDIDisability", FrmLateLifeFDIDisabilityRecord.class),
            Map.entry("LateLifeFDIFunction", FrmLateLifeFDIFunctionRecord.class),
            Map.entry("MMSE", FrmMMSERecord.class),
            Map.entry("MentalHealthForm14", FrmMentalHealthForm14Record.class),
            Map.entry("MentalHealthForm1", FrmMentalHealthForm1Record.class),
            Map.entry("MentalHealthForm42", FrmMentalHealthForm42Record.class),
            Map.entry("MentalHealth", FrmMentalHealthRecord.class),
            Map.entry("PalliativeCare", FrmPalliativeCareRecord.class),
            Map.entry("PeriMenopausal", FrmPeriMenopausalRecord.class),
            Map.entry("Policy", FrmPolicyRecord.class),
            Map.entry("PositionHazard", FrmPositionHazardRecord.class),
            Map.entry("ReceptionAssessment", FrmReceptionAssessmentRecord.class),
            Map.entry("RhImmuneGlobulin", FrmRhImmuneGlobulinRecord.class),
            Map.entry("Rourke2006", FrmRourke2006Record.class),
            Map.entry("Rourke2009", FrmRourke2009Record.class),
            Map.entry("Rourke2017", FrmRourke2017Record.class),
            Map.entry("Rourke2020", FrmRourke2020Record.class),
            Map.entry("Rourke", FrmRourkeRecord.class),
            Map.entry("SF36Caregiver", FrmSF36CaregiverRecord.class),
            Map.entry("SF36", FrmSF36Record.class),
            Map.entry("SatisfactionScale", FrmSatisfactionScaleRecord.class),
            Map.entry("SelfAdministered", FrmSelfAdministeredRecord.class),
            Map.entry("SelfAssessment", FrmSelfAssessmentRecord.class),
            Map.entry("SelfEfficacy", FrmSelfEfficacyRecord.class),
            Map.entry("SelfManagement", FrmSelfManagementRecord.class),
            Map.entry("TreatmentPref", FrmTreatmentPrefRecord.class),
            Map.entry("chf", FrmchfRecord.class)
    );

    /**
     * Instantiates the {@link FrmRecord} subclass corresponding to the given
     * short form name.  Only names present in the static {@link #FORM_REGISTRY}
     * allowlist are accepted; unknown names return {@code null}.
     *
     * @param which short form name (e.g. {@code "Rourke2020"}, {@code "BCAR"})
     * @return a new {@link FrmRecord} instance, or {@code null} if the name is
     *         not in the allowlist or instantiation fails
     */
    public FrmRecord factory(String which) {
        if (which == null) {
            return null;
        }

        Class<? extends FrmRecord> clazz = FORM_REGISTRY.get(which);

        if (clazz == null) {
            MiscUtils.getLogger().debug("FrmRecordFactory: unknown or disallowed form type requested: {}",
                    Encode.forJava(which));
            return null;
        }

        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            MiscUtils.getLogger().debug("FrmRecordFactory: failed to instantiate form record", e);
            return null;
        }
    }
}
