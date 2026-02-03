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

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;

@Service
public class MeasurementManagerImpl implements MeasurementManager {
    @Autowired
    private MeasurementDao measurementDao;

    @Autowired
    private MeasurementMapDao measurementMapDao;

    @Autowired
    private PatientConsentManager patientConsentManager;

    @Override
    public List<Measurement> getCreatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive,
                                                 int itemsToReturn) {
        List<Measurement> results = measurementDao.findByCreateDate(updatedAfterThisDateExclusive, itemsToReturn);

        LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.getCreatedAfterDate",
                "updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive);

        return (results);
    }

    @Override
    public Measurement getMeasurement(LoggedInInfo loggedInInfo, Integer id) {
        Measurement result = measurementDao.find(id);

        // --- log action ---
        if (result != null) {
            LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.getMeasurement", "id=" + id);
        }

        return (result);
    }

    @Override
    public List<Measurement> getMeasurementByType(LoggedInInfo loggedInInfo, Integer id, List<String> types) {
        List<Measurement> results = measurementDao.findByType(id, types);
        if (results.size() > 0) {
            LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.getMeasurementByType", "id=" + id);
        }
        return results;
    }

    @Override
    public List<Measurement> getMeasurementByDemographicIdAfter(LoggedInInfo loggedInInfo, Integer demographicId,
                                                                Date updateAfter) {
        List<Measurement> results = new ArrayList<Measurement>();
        ConsentType consentType = patientConsentManager.getProviderSpecificConsent(loggedInInfo);
        if (patientConsentManager.hasPatientConsented(demographicId, consentType)) {
            results = measurementDao.findByDemographicLastUpdateAfterDate(demographicId, updateAfter);
            if (results.size() > 0) {
                LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.getMeasurementByDemographicIdAfter",
                        "demographicId=" + demographicId + " updateAfter=" + updateAfter);
            }
        }
        return results;
    }

    @Override
    public List<Measurement> getLatestMeasurementsByDemographicIdObservedAfter(LoggedInInfo loggedInInfo, Integer demographicId, Date observedDate) {
        //If the consent type does not exist in the table assume this consent type is not being managed by the clinic, otherwise ensure patient has consented
        boolean hasConsent = patientConsentManager.hasProviderSpecificConsent(loggedInInfo) || patientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER) == null;
        if (!hasConsent) {
            return Collections.emptyList();
        }

        List<Measurement> results = measurementDao.findLatestByDemographicObservedAfterDate(demographicId, observedDate);
        if (results.size() > 0) {
            LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.getMeasurementByDemographicIdAfter", "demographicId=" + demographicId + " updateAfter=" + observedDate);
        }
        return results;
    }

    @Override
    public List<MeasurementMap> getMeasurementMaps() {
        // should be safe to get all as they're a defined set of loinic codes or human
        // entered entries
        List<MeasurementMap> results = measurementMapDao.getAllMaps();

        // not logging the read, this is not medicalData

        return (results);
    }

    @Override
    public Measurement addMeasurement(LoggedInInfo loggedInInfo, Measurement measurement) {
        measurementDao.persist(measurement);
        LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.addMeasurement", "id=" + measurement.getId());
        return (measurement);
    }

    /**
     * ProgramId is not available in oscar right now but the method signature is
     * correct for when it is available.
     */
    @Override
    public List<Measurement> getMeasurementsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo,
                                                                             Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive,
                                                                             int itemsToReturn) {
        List<Measurement> results = measurementDao.findByProviderDemographicLastUpdateDate(providerNo, demographicId,
                updatedAfterThisDateExclusive.getTime(), itemsToReturn);

        LogAction.addLogSynchronous(loggedInInfo, "MeasurementManager.getMeasurementsByProgramProviderDemographicDate",
                "programId=" + programId + ", providerNo=" + providerNo + ", demographicId=" + demographicId
                        + ", updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive.getTime());

        return (results);
    }

    @Override
    public String getDShtml(String groupName) {

        String groupId = null;
        String propKey = null;

        groupId = findGroupId(groupName);
        propKey = "mgroup.ds.html." + groupId;

        String dsHTML = null;

        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        Property p = propertyDao.checkByName(propKey);

        if (p != null) {
            dsHTML = p.getValue();
            return MeasurementFlowSheet.getDSHTMLStream(dsHTML);
        }

        return "";
    }

    @Override
    public boolean isProperty(String prop) {
        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        Property props = propertyDao.checkByName(prop);
        if (props != null) {
            return true;
        }
        return false;
    }

    @Override
    public String findGroupId(String groupName) {
        String id = null;
        MeasurementGroupStyleDao measurementGroupStyleDao = (MeasurementGroupStyleDao) SpringUtils
                .getBean(MeasurementGroupStyleDao.class);
        List<MeasurementGroupStyle> results = measurementGroupStyleDao.findByGroupName(groupName);

        if (results.size() > 0) {
            for (MeasurementGroupStyle result : results) {
                id = result.getId().toString();
            }
        }

        return id;
    }

    @Override
    public void addMeasurementGroupDS(String groupName, String dsHTML) {
        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        String id = findGroupId(groupName);
        boolean propertyExists = isProperty("mgroup.ds.html." + id);
        if (propertyExists) {
            Property p = propertyDao.checkByName("mgroup.ds.html." + id);
            p.setValue(dsHTML);
            propertyDao.merge(p);
        } else {
            Property x = new Property();
            x.setName("mgroup.ds.html." + id);
            x.setValue(dsHTML);
            propertyDao.persist(x);
        }
    }

    @Override
    public void removeMeasurementGroupDS(String propKey) {
        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        boolean propertyExists = isProperty(propKey);
        if (propertyExists) {
            Property p = propertyDao.checkByName(propKey);
            Integer value = p.getId();

            propertyDao.remove(value);
        }
    }

}
