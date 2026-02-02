//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.managers;

import java.text.SimpleDateFormat;
import java.util.List;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;


public interface PreferenceManager {
    public static final String SOCHX = "SocHistory";
    public static final String MEDHX = "MedHistory";
    public static final String CONCERNS = "Concerns";
    public static final String REMINDERS = "Reminders";
    public static final String CUSTOM_SUMMARY_ENABLE = "cpp.pref.enable";
    public static final String OLD_SOCIAL_HISTORY_POS = "cpp.social_hx.position";
    public static final String OLD_MEDICAL_HISTORY_POS = "cpp.medical_hx.position";
    public static final String OLD_ONGOING_CONCERNS_POS = "cpp.ongoing_concerns.position";
    public static final String OLD_REMINDERS_POS = "cpp.reminders.position";
    public static final String SOC_HX_POS = "summary.item.social_hx.position";
    public static final String MED_HX_POS = "summary.item.med_hx.position";
    public static final String ONGOING_POS = "summary.item.ongoing_concerns.position";
    public static final String REMINDERS_POS = "summary.item.reminders.position";
    public static final String SOC_HX_START_DATE = "cpp.social_hx.start_date";
    public static final String SOC_HX_RES_DATE = "cpp.social_hx.res_date";
    public static final String MED_HX_START_DATE = "cpp.med_hx.start_date";
    public static final String MED_HX_RES_DATE = "cpp.med_hx.res_date";
    public static final String MED_HX_TREATMENT = "cpp.med_hx.treatment";
    public static final String MED_HX_PROCEDURE_DATE = "cpp.med_hx.procedure_date";
    public static final String ONGOING_START_DATE = "cpp.ongoing_concerns.start_date";
    public static final String ONGOING_RES_DATE = "cpp.ongoing_concerns.res_date";
    public static final String ONGOING_PROBLEM_STATUS = "cpp.ongoing_concerns.problem_status";
    public static final String REMINDERS_START_DATE = "cpp.reminders.start_date";
    public static final String REMINDERS_RES_DATE = "cpp.reminders.res_date";
    public static final String PREVENTION_POS = "summary.item.prevention.position";
    public static final String FAM_HX_POS = "summary.item.famhx.position";
    public static final String RISK_FACTORS_POS = "summary.item.riskfactors.position";
    public static final String ALLERGIES_POS = "summary.item.allergies.position";
    public static final String MEDS_POS = "summary.item.meds.position";
    public static final String OTHER_MEDS_POS = "summary.item.othermeds.position";
    public static final String ASSESSMENTS_POS = "summary.item.assessments.position";
    public static final String INCOMING_POS = "summary.item.incoming.position";
    public static final String DS_SUPPORT_POS = "summary.item.dssupport.position";

    public boolean displaySummaryItem(LoggedInInfo loggedInInfo, String item);

    //TODO: look at appending the spaces
    public String getCppExtsItem(LoggedInInfo loggedInInfo, List<CaseManagementNoteExt> noteExtList, String issueCode);


    public boolean isCppItem(String issueCode);

    public boolean isCustomSummaryEnabled(LoggedInInfo loggedInInfo);

    public String getProviderPreference(LoggedInInfo loggedInInfo, String propertyName);

}
