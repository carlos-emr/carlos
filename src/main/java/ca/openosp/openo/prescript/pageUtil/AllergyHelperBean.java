//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.prescript.pageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicAllergy;
import ca.openosp.openo.commn.dao.PartialDateDao;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.PartialDate;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.prescript.data.RxPatientData;
import ca.openosp.openo.prescript.data.RxPatientData.Patient;
import ca.openosp.openo.util.DateUtils;

public final class AllergyHelperBean {
    private static Logger logger = MiscUtils.getLogger();
    private static final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

    public static List<AllergyDisplay> getAllergiesToDisplay(LoggedInInfo loggedInInfo, Integer demographicId, Locale locale) {
        ArrayList<AllergyDisplay> results = new ArrayList<AllergyDisplay>();

        addLocalAllergies(loggedInInfo, demographicId, results, locale);

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            addIntegratorAllergies(loggedInInfo, demographicId, results, locale);
        }

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

    private static void addIntegratorAllergies(LoggedInInfo loggedInInfo, Integer demographicId, ArrayList<AllergyDisplay> results, Locale locale) {
        try {
            List<CachedDemographicAllergy> remoteAllergies = null;
            try {
                if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                    remoteAllergies = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility()).getLinkedCachedDemographicAllergies(demographicId);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Unexpected error.", e);
                CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
            }

            if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                remoteAllergies = IntegratorFallBackManager.getRemoteAllergies(loggedInInfo, demographicId);
            }


            for (CachedDemographicAllergy remoteAllergy : remoteAllergies) {
                AllergyDisplay allergyDisplay = new AllergyDisplay();

                allergyDisplay.setRemoteFacilityId(remoteAllergy.getFacilityIdIntegerCompositePk().getIntegratorFacilityId());
                allergyDisplay.setId(remoteAllergy.getFacilityIdIntegerCompositePk().getCaisiItemId());

                allergyDisplay.setDescription(remoteAllergy.getDescription());
                allergyDisplay.setEntryDate(DateUtils.formatDate(remoteAllergy.getEntryDate(), locale));
                allergyDisplay.setOnSetCode(remoteAllergy.getOnSetCode());
                allergyDisplay.setReaction(remoteAllergy.getReaction());
                allergyDisplay.setSeverityCode(remoteAllergy.getSeverityCode());
                allergyDisplay.setStartDate(DateUtils.formatDate(remoteAllergy.getStartDate(), locale));
                allergyDisplay.setTypeCode(remoteAllergy.getTypeCode());

                results.add(allergyDisplay);
            }
        } catch (Exception e) {
            logger.error("error getting remote allergies", e);
        }
    }
}
