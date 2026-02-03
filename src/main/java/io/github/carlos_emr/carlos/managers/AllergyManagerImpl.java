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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.log.LogAction;

@Service
public class AllergyManagerImpl implements AllergyManager {
    @Autowired
    private AllergyDao allergyDao;

    @Autowired
    private PatientConsentManager patientConsentManager;

    @Override
    public Allergy getAllergy(LoggedInInfo loggedInInfo, Integer id) {
        Allergy result = allergyDao.find(id);

        // --- log action ---
        if (result != null) {
            LogAction.addLogSynchronous(loggedInInfo, "AllergyManager.getAllergy", "id=" + id);
        }

        return (result);
    }

    @Override
    public List<Allergy> getActiveAllergies(LoggedInInfo loggedInInfo, Integer demographicNo) {
        List<Allergy> results = allergyDao.findActiveAllergiesOrderByDescription(demographicNo);

        // --- log action ---
        if (results != null && results.size() > 0) {
            LogAction.addLogSynchronous(loggedInInfo, "AllergyManager.getActiveAllergies",
                    "demographicNo=" + demographicNo);
        }

        return (results);
    }

    @Override
    public List<Allergy> getUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateInclusive,
                                             int itemsToReturn) {
        List<Allergy> results = allergyDao.findByUpdateDate(updatedAfterThisDateInclusive, itemsToReturn);
        patientConsentManager.filterProviderSpecificConsent(loggedInInfo, results);
        LogAction.addLogSynchronous(loggedInInfo, "AllergyManager.getUpdatedAfterDate",
                "updatedAfterThisDateInclusive=" + updatedAfterThisDateInclusive);

        return (results);
    }

    @Override
    public List<Allergy> getByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId,
                                                            Date updatedAfterThisDate) {
        List<Allergy> results = new ArrayList<Allergy>();
        ConsentType consentType = patientConsentManager.getProviderSpecificConsent(loggedInInfo);
        if (patientConsentManager.hasPatientConsented(demographicId, consentType)) {
            results = allergyDao.findByDemographicIdUpdatedAfterDate(demographicId, updatedAfterThisDate);
            LogAction.addLogSynchronous(loggedInInfo, "AllergyManager.getByDemographicIdUpdatedAfterDate",
                    "demographicId=" + demographicId + " updatedAfterThisDate=" + updatedAfterThisDate);
        }
        return (results);
    }

    /**
     * At this time, not all criteria maybe available in oscar but the method
     * signature is what is "should" be
     * and hopefully can be refactored as data becomes available.
     */
    @Override
    public List<Allergy> getAllergiesByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId,
                                                                      String providerNo, Integer demographicId, Calendar updatedAfterThisDateInclusive, int itemsToReturn) {
        List<Allergy> results = allergyDao.findByProviderDemographicLastUpdateDate(providerNo, demographicId,
                updatedAfterThisDateInclusive.getTime(), itemsToReturn);

        LogAction.addLogSynchronous(loggedInInfo, "AllergyManager.getUpdatedAfterDate",
                "programId=" + programId + ", providerNo=" + providerNo + ", demographicId=" + demographicId
                        + ", updatedAfterThisDateInclusive=" + updatedAfterThisDateInclusive.getTime());

        return (results);
    }
}
