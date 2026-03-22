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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData.Patient;
import io.github.carlos_emr.carlos.util.DateUtils;

/**
 * Helper bean that assembles allergy display data for a given patient.
 * <p>
 * Retrieves active allergies from the patient record and converts them into
 * {@link AllergyDisplay} objects with formatted dates using partial date support.
 * Used by JSP views to render the allergy list with locale-appropriate formatting.
 *
 * @since 2026-03-17
 */
public final class AllergyHelperBean {
    private static final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

    public static List<AllergyDisplay> getAllergiesToDisplay(LoggedInInfo loggedInInfo, Integer demographicId, Locale locale) {
        ArrayList<AllergyDisplay> results = new ArrayList<AllergyDisplay>();

        addLocalAllergies(loggedInInfo, demographicId, results, locale);

        return (results);
    }

    private static void addLocalAllergies(LoggedInInfo loggedInInfo, Integer demographicId, ArrayList<AllergyDisplay> results, Locale locale) {
        Patient pt = RxPatientData.getPatient(loggedInInfo, demographicId);
        if (pt == null) {
            return;
        }
        Allergy[] allergies = pt.getActiveAllergies();

        if (allergies == null) return;

        for (Allergy allergy : allergies) {
            AllergyDisplay allergyDisplay = new AllergyDisplay();

            allergyDisplay.setId(allergy.getAllergyId());

            allergyDisplay.setDescription(allergy.getDescription());
            allergyDisplay.setOnSetCode(allergy.getOnsetOfReaction());
            allergyDisplay.setReaction(allergy.getReaction());
            allergyDisplay.setSeverityCode(allergy.getSeverityOfReaction());
            allergyDisplay.setTypeCode(allergy.getTypeCode());
            allergyDisplay.setArchived(allergy.getArchived() ? "1" : "0");

            String entryDate = partialDateDao.getDatePartial(allergy.getEntryDate(), PartialDate.ALLERGIES, allergy.getAllergyId(), PartialDate.ALLERGIES_ENTRYDATE);
            String startDate = partialDateDao.getDatePartial(allergy.getStartDate(), PartialDate.ALLERGIES, allergy.getAllergyId(), PartialDate.ALLERGIES_STARTDATE);
            String lastUpdateDate = DateUtils.formatDate(allergy.getLastUpdateDate(), locale);
            allergyDisplay.setEntryDate(entryDate);
            allergyDisplay.setStartDate(startDate);
            allergyDisplay.setLastUpdateDate(lastUpdateDate);

            results.add(allergyDisplay);
        }
    }
}
