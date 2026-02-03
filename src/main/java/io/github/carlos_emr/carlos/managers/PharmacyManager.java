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
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * Will provide access to pharmacy data.
 * <p>
 * Future Use: Add privacy, sec, and consent profiles
 */
@Service
public class PharmacyManager {

    //private Logger logger=MiscUtils.getLogger();

    @Autowired
    private DemographicPharmacyDao demographicPharmacyDao;
    @Autowired
    private PharmacyInfoDao pharmacyInfoDao;

    public List<DemographicPharmacy> getPharmacies(LoggedInInfo loggedInInfo, Integer demographicId) {
        List<DemographicPharmacy> result = demographicPharmacyDao.findAllByDemographicId(demographicId);

        if (result != null) {
            LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.getPharmacies", "demographicNo=" + demographicId);
            result = addDetails(loggedInInfo, result);
        }

        return result;
    }

    public DemographicPharmacy getDemographicPharmacy(LoggedInInfo loggedInInfo, Integer demographicPharmacyId) {
        DemographicPharmacy demographicPharmacy = demographicPharmacyDao.find(demographicPharmacyId);

        if (demographicPharmacy != null) {
            LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.getPharmacy", "demographicPharmacyId=" + demographicPharmacyId);
            demographicPharmacy = addDetails(loggedInInfo, demographicPharmacy);
        }
        return demographicPharmacy;
    }

    public DemographicPharmacy addPharmacy(LoggedInInfo loggedInInfo, Integer demographicId, Integer pharmacyId, Integer preferredOrder) {
        DemographicPharmacy result = demographicPharmacyDao.addPharmacyToDemographic(pharmacyId, demographicId, preferredOrder);

        if (result != null) {
            //--- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.addPharmacy", "demographicNo=" + demographicId + ",pharmacyId=" + pharmacyId);
        }

        return result;
    }

    public void removePharmacy(LoggedInInfo loggedInInfo, Integer demographicId, Integer pharmacyId) {
        DemographicPharmacy pharmacy = demographicPharmacyDao.find(pharmacyId);
        if (pharmacy == null) {
            throw new IllegalArgumentException("Unable to locate pharmacy association with id " + pharmacyId);
        }

        if (pharmacy.getDemographicNo() != demographicId) {
            throw new IllegalArgumentException("Pharmacy association with id " + pharmacyId + " does't belong to demographic record with ID " + demographicId);
        }

        pharmacy.setStatus("0");
        demographicPharmacyDao.saveEntity(pharmacy);

        //--- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.removePharmacy", "demographicNo=" + demographicId + ",pharmacyId=" + pharmacyId);
    }

    public PharmacyInfo getPharmacy(LoggedInInfo loggedInInfo, Integer pharmacyId) {
        return pharmacyInfoDao.find(pharmacyId);
    }

    public void setDoNotContact(LoggedInInfo loggedInInfo, Integer pharmacyId, boolean doNotContact) {
        DemographicPharmacy pharmacy = demographicPharmacyDao.find(pharmacyId);
        pharmacy.setConsentToContact(doNotContact);
        demographicPharmacyDao.saveEntity(pharmacy);
        LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.setDoNotContact", "demographicNo=" + pharmacy.getDemographicNo() + ",pharmacyId=" + pharmacyId);
    }

    public Integer savePharmacyInfo(LoggedInInfo loggedInInfo, PharmacyInfo pharmacyInfo) {

        // not sure what the server is set to pass.
        if (pharmacyInfo.getId() == 0) {
            pharmacyInfo.setId(null);
        }

        if (pharmacyInfo.getId() == null) {
            pharmacyInfoDao.persist(pharmacyInfo);
            LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.createPharmacy", "Added New Pharmacy Contact to PharmacyInfo");

        } else {
            pharmacyInfoDao.merge(pharmacyInfo);
            LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.createPharmacy", "Saved Edited Pharmacy Contact");
        }

        return pharmacyInfo.getId();
    }

    private final List<DemographicPharmacy> addDetails(LoggedInInfo loggedInInfo, List<DemographicPharmacy> demographicPharmacyList) {
        for (DemographicPharmacy demographicPharmacy : demographicPharmacyList) {
            demographicPharmacy = addDetails(loggedInInfo, demographicPharmacy);
        }
        return demographicPharmacyList;
    }

    private final DemographicPharmacy addDetails(LoggedInInfo loggedInInfo, DemographicPharmacy demographicPharmacy) {
        // would rather have this done in the JPA as a join, but...
        LogAction.addLogSynchronous(loggedInInfo, "PharmacyManager.addDetails", "pharmacyId=" + demographicPharmacy.getPharmacyId());
        PharmacyInfo pharmacyInfo = pharmacyInfoDao.getPharmacy(demographicPharmacy.getPharmacyId());
        demographicPharmacy.setDetails(pharmacyInfo);
        return demographicPharmacy;
    }

}
