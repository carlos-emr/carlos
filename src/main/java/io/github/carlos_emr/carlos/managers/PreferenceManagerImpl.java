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
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class PreferenceManagerImpl implements PreferenceManager {

    @Autowired
    private ProviderManager2 providerManager;


    //NEW - summary.item.custom.display



    /*
     *
     * if .position dosne't exist = on
     * if .position exists and not equal "" = on
     * ignore the R* position eg: R1I1 R1I2 R2I1 R2I2
     *
     * so setting position in old ui will have not effect on the new ui
     * and when setting position in new ui it will have no effect on the old ui
     *
     *
     */


    public boolean displaySummaryItem(LoggedInInfo loggedInInfo, String item) {
        if (isCustomSummaryEnabled(loggedInInfo)) {
            List<Property> results = providerManager.getProviderProperties(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), item);

            if (results.size() > 0) {
                String value = null;

                for (Property result : results) {
                    value = result.getValue();
                }

                if (value.isEmpty() || value.equals("off")) {
                    return false;
                }
            } else {
                //check if the old cpp position property exist
                return isOldCppPosition(loggedInInfo, item);
            }
        }

        return true;
    }

    private boolean isOldCppPosition(LoggedInInfo loggedInInfo, String property) {
        if (property.equals(SOC_HX_POS)) {
            return displaySummaryItem(loggedInInfo, OLD_SOCIAL_HISTORY_POS);
        } else if (property.equals(MED_HX_POS)) {
            return displaySummaryItem(loggedInInfo, OLD_MEDICAL_HISTORY_POS);
        } else if (property.equals(ONGOING_POS)) {
            return displaySummaryItem(loggedInInfo, OLD_ONGOING_CONCERNS_POS);
        } else if (property.equals(REMINDERS_POS)) {
            return displaySummaryItem(loggedInInfo, OLD_REMINDERS_POS);
        }

        return true;
    }

    //TODO: look at appending the spaces
    public String getCppExtsItem(LoggedInInfo loggedInInfo, List<CaseManagementNoteExt> noteExtList, String issueCode) {

        StringBuilder sb = new StringBuilder();

        if (issueCode.equals(CONCERNS)) {
            if (isCustomCppItemOn(loggedInInfo, ONGOING_START_DATE)) {
                sb.append("Start Date:" + getNoteExt("Start Date", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, ONGOING_RES_DATE)) {
                sb.append(" Resolution Date:" + getNoteExt("Resolution Date", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, ONGOING_PROBLEM_STATUS)) {
                sb.append(" Problem Status:" + getNoteExt("Problem Status", noteExtList));
            }

        } else if (issueCode.equals(MEDHX)) {
            if (isCustomCppItemOn(loggedInInfo, MED_HX_START_DATE)) {
                sb.append("Start Date:" + getNoteExt("Start Date", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, MED_HX_RES_DATE)) {
                sb.append(" Resolution Date:" + getNoteExt("Resolution Date", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, MED_HX_TREATMENT)) {
                sb.append(" Treatment:" + getNoteExt("Treatment", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, MED_HX_PROCEDURE_DATE)) {
                sb.append(" Procedure Date:" + getNoteExt("Procedure Date", noteExtList));
            }

        } else if (issueCode.equals(SOCHX)) {
            if (isCustomCppItemOn(loggedInInfo, SOC_HX_START_DATE)) {
                sb.append("Start Date:" + getNoteExt("Start Date", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, SOC_HX_RES_DATE)) {
                sb.append(" Resolution Date:" + getNoteExt("Resolution Date", noteExtList));
            }

        } else if (issueCode.equals(REMINDERS)) {
            if (isCustomCppItemOn(loggedInInfo, REMINDERS_START_DATE)) {
                sb.append("Start Date:" + getNoteExt("Start Date", noteExtList));
            }

            if (isCustomCppItemOn(loggedInInfo, REMINDERS_RES_DATE)) {
                sb.append(" Resolution Date:" + getNoteExt("Resolution Date", noteExtList));
            }
        }

        if (sb.length() > 0) {
            sb.insert(0, " (");
            sb.append(")");
        }

        return sb.toString();
    }

    static String getNoteExt(String key, List<CaseManagementNoteExt> lcme) {
        for (CaseManagementNoteExt cme : lcme) {
            if (cme.getKeyVal().equals(key)) {
                String val = null;

                if (key.contains(" Date")) {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                    val = formatter.format(cme.getDateValue());
                } else {
                    val = cme.getValue();
                }
                return val;
            }
        }
        return "";
    }


    public boolean isCppItem(String issueCode) {
        if (issueCode.equals(SOCHX) || issueCode.equals(MEDHX) || issueCode.equals(CONCERNS) || issueCode.equals(REMINDERS)) {
            return true;
        }
        return false;
    }


    public boolean isCustomSummaryEnabled(LoggedInInfo loggedInInfo) {
        List<Property> results = providerManager.getProviderProperties(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), CUSTOM_SUMMARY_ENABLE);

        if (results.size() > 0) {
            String value = null;

            for (Property result : results) {
                value = result.getValue();
            }

            if (value.equals("on")) {
                return true;
            }
        }

        return false;
    }

    private boolean isCustomCppItemOn(LoggedInInfo loggedInInfo, String propertyName) {
        List<Property> results = providerManager.getProviderProperties(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), propertyName);

        if (results.size() > 0) {
            String value = null;

            for (Property result : results) {
                value = result.getValue();
            }

            if (value.equals("on")) {
                return true;
            }
        }
        return false;
    }

    public String getProviderPreference(LoggedInInfo loggedInInfo, String propertyName) {
        List<Property> results = providerManager.getProviderProperties(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), propertyName);

        String value = null;

        if (results.size() > 0) {

            for (Property result : results) {
                value = result.getValue();
            }

            return value;
        }

        return value;
    }

}