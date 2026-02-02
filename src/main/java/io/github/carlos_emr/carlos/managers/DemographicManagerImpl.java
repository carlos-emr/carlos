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

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.caisi_integrator.ws.DemographicTransfer;
import io.github.carlos_emr.carlos.caisi_integrator.ws.DemographicWs;
import io.github.carlos_emr.carlos.caisi_integrator.ws.GetConsentTransfer;
import io.github.carlos_emr.carlos.commn.Gender;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Demographic.PatientStatus;
import io.github.carlos_emr.carlos.commn.model.enumerator.DemographicExtKey;
import io.github.carlos_emr.carlos.utility.DemographicContactCreator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.util.StringUtils;

import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Will provide access to demographic data, as well as closely related data such
 * as
 * extensions (DemographicExt), merge data, archive data, etc.
 * <p>
 * Future Use: Add privacy, sec, and consent profiles
 */
@Service
public class DemographicManagerImpl implements DemographicManager {

    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    private DemographicExtDao demographicExtDao;
    @Autowired
    private DemographicCustDao demographicCustDao;
    @Autowired
    private DemographicContactDao demographicContactDao;

    @Autowired
    private DemographicArchiveDao demographicArchiveDao;
    @Autowired
    private DemographicExtArchiveDao demographicExtArchiveDao;
    @Autowired
    private DemographicCustArchiveDao demographicCustArchiveDao;

    @Autowired
    private DemographicMergedDao demographicMergedDao;


    @Autowired
    private AdmissionDao admissionDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    AppManager appManager;

    @Autowired
    private ContactSpecialtyDao contactSpecialtyDao;

    @Autowired
    PatientConsentManager patientConsentManager;

    @Autowired
    ConsentDao consentDao;

    @Autowired
    ProgramManager2 programManager2;

    @Autowired
    ProviderManager2 providerManager;

    @Autowired
    AppointmentManager appointmentManager;

    /**
	 *  Get the patient demographic profile.
	 *  This particular method also sets the Demographic.MRP and Demographic.nextAppointment
	 *  properties.
	 * @param loggedInInfo
	 * @param demographicId
	 * @return
	 * @throws PatientDirectiveException
	 */
     @Override
     public Demographic getDemographic(LoggedInInfo loggedInInfo, Integer demographicId) throws PatientDirectiveException {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ, (demographicId != null) ? demographicId : null); 
        Demographic demographic = demographicDao.getDemographicById(demographicId); 
        if(demographic != null) {
			this.getMRP(loggedInInfo, demographic);
			this.getNextAppointmentDate(loggedInInfo, demographic);
		}
		return demographic;
     }

    /**
	 *  Get the patient demographic profile.
	 *  This particular method also sets the Demographic.MRP and Demographic.nextAppointment
	 *  properties.
	 * @param loggedInInfo
	 * @param demographicNo
	 * @return
	 * @throws PatientDirectiveException
	 */
    @Override
    public Demographic getDemographic(LoggedInInfo loggedInInfo, String demographicNo) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        Integer demographicId = null;
        try {
            demographicId = Integer.parseInt(demographicNo);
        } catch (NumberFormatException e) {
            return null;
        }
        return getDemographic(loggedInInfo, demographicId);
    }

    @Override
    public Demographic getDemographicWithExt(LoggedInInfo loggedInInfo, Integer demographicId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        Demographic result = getDemographic(loggedInInfo, demographicId);
        if (result != null) {
            List<DemographicExt> demoExts = getDemographicExts(loggedInInfo, demographicId);
            if (demoExts != null && !demoExts.isEmpty()) {
                DemographicExt[] demoExtArray = demoExts.toArray(new DemographicExt[demoExts.size()]);
                result.setExtras(demoExtArray);
            }
        }
        return result;
    }

    @Override
    public String getDemographicFormattedName(LoggedInInfo loggedInInfo, Integer demographicId) {
        Demographic result = getDemographic(loggedInInfo, demographicId);
        String name = null;
        if (result != null) {
            name = result.getLastName() + ", " + result.getFirstName();
        }

        return (name);
    }

    @Override
    public String getDemographicEmail(LoggedInInfo loggedInInfo, Integer demographicId) {
        Demographic result = getDemographic(loggedInInfo, demographicId);
        String email = null;
        if (result != null) {
            email = result.getEmail();
        }
        return (email);
    }


    @Override
    public List<Demographic> searchDemographicByName(LoggedInInfo loggedInInfo, String searchString, int startIndex,
                                                     int itemsToReturn) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<Demographic> results = demographicDao.searchDemographicByNameString(searchString, startIndex,
                itemsToReturn);

        if (logger.isDebugEnabled()) {
            logger.debug("searchDemographicByName, searchString=" + searchString + ", result.size=" + results.size());
        }

        // --- log action ---
        for (Demographic demographic : results) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.searchDemographicByName result",
                    "demographicId=" + demographic.getDemographicNo());
        }

        return (results);
    }

    @Override
    public List<Demographic> getActiveDemographicAfter(LoggedInInfo loggedInInfo, Date afterDateExclusive) {
        // lastDate format: yyyy-MM-dd HH:mm:ss
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<Demographic> results = demographicDao.getActiveDemographicAfter(afterDateExclusive);

        // --- log action ---
        if (results != null) {
            for (Demographic item : results) {
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getActiveDemographicAfter(date)",
                        "id=" + item.getId());
            }
        }

        return results;
    }

    @Override
    public List<DemographicExt> getDemographicExts(LoggedInInfo loggedInInfo, Integer id) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<DemographicExt> result = null;

        result = demographicExtDao.getDemographicExtByDemographicNo(id);

        // --- log action ---
        if (result != null) {
            for (DemographicExt item : result) {
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicExts",
                        "id=" + item.getId() + "(" + id.toString() + ")");
            }
        }

        return result;
    }

    @Override
    public DemographicExt getDemographicExt(LoggedInInfo loggedInInfo, Integer demographicNo,
                                            DemographicExt.DemographicProperty key) {
        return getDemographicExt(loggedInInfo, demographicNo, key.name());
    }

    @Override
    public DemographicExt getDemographicExt(LoggedInInfo loggedInInfo, Integer demographicNo, String key) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        DemographicExt result = null;
        result = demographicExtDao.getDemographicExt(demographicNo, key);

        // --- log action ---
        if (result != null) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicExt(demographicNo, key)",
                    "id=" + result.getId() + "(" + demographicNo + ")");
        }
        return result;
    }

    @Override
    public DemographicCust getDemographicCust(LoggedInInfo loggedInInfo, Integer id) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        DemographicCust result = null;
        result = demographicCustDao.find(id);
        return result;
    }

    @Override
    public void createUpdateDemographicCust(LoggedInInfo loggedInInfo, DemographicCust demoCust) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        if (demoCust != null) {
            // Archive previous demoCust
            DemographicCust prevCust = demographicCustDao.find(demoCust.getId());
            if (prevCust != null) {
                if (!(StringUtils.nullSafeEquals(prevCust.getAlert(), demoCust.getAlert())
                        && StringUtils.nullSafeEquals(prevCust.getMidwife(), demoCust.getMidwife())
                        && StringUtils.nullSafeEquals(prevCust.getNurse(), demoCust.getNurse())
                        && StringUtils.nullSafeEquals(prevCust.getResident(), demoCust.getResident())
                        && StringUtils.nullSafeEquals(prevCust.getNotes(), demoCust.getNotes()))) {
                    demographicCustArchiveDao.archiveDemographicCust(prevCust);
                }
            }

            demographicCustDao.merge(demoCust);
        }

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.createUpdateDemographicCust",
                "id=" + demoCust.getId());
    }

    @Override
    public List<DemographicContact> getDemographicContacts(LoggedInInfo loggedInInfo, Integer id, String category) {
        List<DemographicContact> result = null;

        result = demographicContactDao.findByDemographicNoAndCategory(id, category);

        // --- log action ---
        if (result != null) {
            for (DemographicContact item : result) {
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicContacts",
                        "id=" + item.getId() + "(" + id.toString() + ")");
            }
        }
        return result;
    }

    @Override
    public List<DemographicContact> getDemographicContacts(LoggedInInfo loggedInInfo, Integer id) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<DemographicContact> result = null;
        result = demographicContactDao.findActiveByDemographicNo(id);

        // --- log action ---
        if (result != null) {
            for (DemographicContact item : result) {
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicContacts",
                        "id=" + item.getId() + "(" + id.toString() + ")");
            }
        }
        return result;
    }

    /**
     * Returns a list of all the internal providers assigned to this demographic.
     */
    @Override
    public List<Provider> getDemographicMostResponsibleProviders(LoggedInInfo loggedInInfo, int demographicNo) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<DemographicContact> demographicContacts = demographicContactDao
                .findAllByDemographicNoAndCategoryAndType(demographicNo, "professional", 0);
        ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);
        List<Provider> providerList = null;

        for (DemographicContact demographicContact : demographicContacts) {
            Provider provider = providerManager.getProvider(loggedInInfo, demographicContact.getContactId());
            if (providerList == null) {
                providerList = new ArrayList<Provider>();
            }
            if (provider != null) {
                providerList.add(provider);
            }
        }

        if (providerList == null) {
            providerList = Collections.emptyList();
        }

        return providerList;
    }

    @Override
    public List<Demographic> getDemographicsNameRangeByProvider(LoggedInInfo loggedInInfo, Provider provider,
                                                                String regex) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        if (provider == null || provider.getProviderNo() == null || regex == null) {
            return new ArrayList<>(); // Return an empty list if providers or regex is null
        }

        List<Demographic> demographicList = demographicDao.getDemographicByProvider(provider.getProviderNo());
        /*
         * A reluctant method to sort the results due to the lack of REGEX functions
         * in the outdated Demographic Hibernate Template.
         * Upgrading DemographicDao to JPA is not tenable at this time.
         * However, it needs to be done eventually.
         */
        List<Demographic> demographicFilterList = new ArrayList<>();
        if (demographicList != null && !demographicList.isEmpty()) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher;
            for (Demographic demographic : demographicList) {
                if (demographic.getLastName() != null) {
                    matcher = pattern.matcher(demographic.getLastName().toUpperCase());
                    if (matcher.find()) {
                        demographicFilterList.add(demographic);
                    }
                }
            }
        }
        return demographicFilterList;
    }

    @Override
    public List<Demographic> getDemographicsByProvider(LoggedInInfo loggedInInfo, Provider provider) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicDao.getDemographicByProvider(provider.getProviderNo(), true);
    }

    @Override
    public void createDemographic(LoggedInInfo loggedInInfo, Demographic demographic, Integer admissionProgramId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        try {
            demographic.getBirthDay();
        } catch (Exception e) {
            throw new IllegalArgumentException("Birth date was specified for " + demographic.getFullName() + ": "
                    + demographic.getBirthDayAsString());
        }

        demographic.setPatientStatus(PatientStatus.AC.name());
        demographic.setFamilyDoctor("<rdohip></rdohip><rd></rd>");
        demographic.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        demographicDao.save(demographic);

        Admission admission = new Admission();
        admission.setClientId(demographic.getDemographicNo());
        admission.setProgramId(admissionProgramId);
        admission.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        admission.setAdmissionDate(new Date());
        admission.setAdmissionStatus(Admission.STATUS_CURRENT);
        admission.setAdmissionNotes("");

        admissionDao.saveAdmission(admission);

        if (demographic.getExtras() != null) {
            for (DemographicExt ext : demographic.getExtras()) {
                createExtension(loggedInInfo, ext);
            }
        }

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.createDemographic",
                "new id is =" + demographic.getDemographicNo());

    }

    @Override
    public void updateDemographic(LoggedInInfo loggedInInfo, Demographic demographic) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.UPDATE);
        try {
            demographic.getBirthDay();
        } catch (Exception e) {
            throw new IllegalArgumentException("Birth date was specified for " + demographic.getFullName() + ": "
                    + demographic.getBirthDayAsString());
        }

        // Archive previous demo
        Demographic prevDemo = demographicDao.getDemographicById(demographic.getDemographicNo());
        demographicArchiveDao.archiveRecord(prevDemo);

        // retain merge info
        demographic.setSubRecord(prevDemo.getSubRecord());

        // save current demo
        demographic.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        demographicDao.save(demographic);

        if (demographic.getExtras() != null) {
            for (DemographicExt ext : demographic.getExtras()) {
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.updateDemographic ext",
                        "id=" + ext.getId() + "(" + ext.toString() + ")");
                updateExtension(loggedInInfo, ext);
            }
        }

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.updateDemographic",
                "demographicNo=" + demographic.getDemographicNo());

    }

    /**
     * Exact match is by firstname, lastname, gender, dob, and hin. Return value is
     * null if more than 1 exact match is returned.
     *
     * @param loggedInInfo
     * @param demographic
     * @return
     */
    @Override
    public Demographic findExactMatchToDemographic(LoggedInInfo loggedInInfo, Demographic demographic) {
        Calendar dateOfBirth = Calendar.getInstance();
        dateOfBirth.set(Integer.parseInt(demographic.getYearOfBirth()), Integer.parseInt(demographic.getMonthOfBirth()),
                Integer.parseInt(demographic.getDateOfBirth()));
        List<Demographic> exactmatch = searchDemographicsByAttributes(
                loggedInInfo,
                demographic.getHin(),
                demographic.getFirstName(),
                demographic.getLastName(),
                Gender.valueOf(demographic.getSex()),
                dateOfBirth,
                null,
                null,
                null,
                null,
                null,
                0,
                2);

        if (exactmatch != null && exactmatch.size() == 1) {
            return exactmatch.get(0);
        }

        return null;
    }

    /**
     * fuzzy match is by lastname and dob
     *
     * @param loggedInInfo
     * @param demographic
     * @return
     */
    @Override
    public List<Demographic> findFuzzyMatchToDemographic(LoggedInInfo loggedInInfo, Demographic demographic) {
        return getDemographicWithLastFirstDOB(loggedInInfo, demographic.getLastName(), demographic.getFirstName(),
                demographic.getYearOfBirth(), demographic.getMonthOfBirth(), demographic.getDateOfBirth());
    }

    @Override
    public void addDemographic(LoggedInInfo loggedInInfo, Demographic demographic) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        try {
            demographic.getBirthDay();
        } catch (Exception e) {
            throw new IllegalArgumentException("Birth date was specified for " + demographic.getFullName() + ": "
                    + demographic.getBirthDayAsString());
        }

        // save current demo
        demographic.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        demographicDao.save(demographic);

        if (demographic.getExtras() != null) {
            for (DemographicExt ext : demographic.getExtras()) {
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.addDemographic ext",
                        "id=" + ext.getId() + "(" + ext.toString() + ")");
                updateExtension(loggedInInfo, ext);
            }
        }

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.addDemographic",
                "demographicNo=" + demographic.getDemographicNo());

    }

    @Override
    public void createExtension(LoggedInInfo loggedInInfo, DemographicExt ext) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        demographicExtDao.saveEntity(ext);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.createExtension", "id=" + ext.getId());
    }

    @Override
    public void updateExtension(LoggedInInfo loggedInInfo, DemographicExt ext) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.UPDATE);
        archiveExtension(ext);
        demographicExtDao.saveEntity(ext);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.updateExtension", "id=" + ext.getId());
    }

    @Override
    public void archiveExtension(DemographicExt ext) {
        // TODO: this needs a loggedInInfo
        if (ext != null && ext.getId() != null) {
            DemographicExt prevExt = demographicExtDao.find(ext.getId());
            if (!(ext.getKey().equals(prevExt.getKey()) && Objects.equals(ext.getValue(), prevExt.getValue()))) {
                demographicExtArchiveDao.archiveDemographicExt(prevExt);
            }
        }
    }

    @Override
    public void createUpdateDemographicContact(LoggedInInfo loggedInInfo, DemographicContact demoContact) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

        demographicContactDao.merge(demoContact);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.createUpdateDemographicContact",
                "id=" + demoContact.getId());
    }

    @Override
    public void deleteDemographic(LoggedInInfo loggedInInfo, Demographic demographic) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

        demographicArchiveDao.archiveRecord(demographic);
        demographic.setPatientStatus(Demographic.PatientStatus.DE.name());
        demographic.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        demographicDao.save(demographic);

        for (DemographicExt ext : getDemographicExts(loggedInInfo, demographic.getDemographicNo())) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.deleteDemographic ext",
                    "id=" + ext.getId() + "(" + ext.toString() + ")");
            deleteExtension(loggedInInfo, ext);
        }

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.deleteDemographic",
                "demographicNo=" + demographic.getDemographicNo());
    }

    @Override
    public void deleteExtension(LoggedInInfo loggedInInfo, DemographicExt ext) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        archiveExtension(ext);
        demographicExtDao.removeDemographicExt(ext.getId());

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.removeDemographicExt", "id=" + ext.getId());
    }

    @Override
    public void mergeDemographics(LoggedInInfo loggedInInfo, Integer parentId, List<Integer> children) {
        for (Integer child : children) {
            DemographicMerged dm = new DemographicMerged();
            dm.setDemographicNo(child);
            dm.setMergedTo(parentId);
            demographicMergedDao.persist(dm);

            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.mergeDemographics", "id=" + dm.getId());
        }

    }

    @Override
    public void unmergeDemographics(LoggedInInfo loggedInInfo, Integer parentId, List<Integer> children) {
        for (Integer childId : children) {
            List<DemographicMerged> dms = demographicMergedDao.findByParentAndChildIds(parentId, childId);
            if (dms.isEmpty()) {
                throw new IllegalArgumentException(
                        "Unable to find merge record for parent " + parentId + " and child " + childId);
            }
            for (DemographicMerged dm : demographicMergedDao.findByParentAndChildIds(parentId, childId)) {
                dm.setDeleted(1);
                demographicMergedDao.merge(dm);

                // --- log action ---
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.unmergeDemographics", "id=" + dm.getId());
            }
        }
    }

    @Override
    public Long getActiveDemographicCount(LoggedInInfo loggedInInfo) {
        Long count = demographicDao.getActiveDemographicCount();

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getActiveDemographicCount", "");

        return count;
    }

    @Override
    public List<Demographic> getActiveDemographics(LoggedInInfo loggedInInfo, int offset, int limit) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<Demographic> result = demographicDao.getActiveDemographics(offset, limit);

        if (result != null) {
            for (Demographic d : result) {
                // --- log action ---
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getActiveDemographics result",
                        "demographicNo=" + d.getDemographicNo());
            }
        }

        return result;
    }

    /**
     * Gets all merged demographic for the specified parent record ID
     *
     * @param parentId ID of the parent demographic record
     * @return Returns all merged demographic records for the specified parent id.
     */
    @Override
    public List<DemographicMerged> getMergedDemographics(LoggedInInfo loggedInInfo, Integer parentId) {
        List<DemographicMerged> result = demographicMergedDao.findCurrentByMergedTo(parentId);

        if (result != null) {
            for (DemographicMerged d : result) {
                // --- log action ---
                LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getMergedDemogrpaphics result",
                        "demographicNo=" + d.getDemographicNo());
            }

        }

        return result;
    }





    /**
     * @deprecated there should be a generic call for getDemographicExt(Integer
     * demoId, String key) instead. Then the caller should assemble what
     * it needs from the demographic and ext call itself.
     */
    @Deprecated
    @Override
    public String getDemographicWorkPhoneAndExtension(LoggedInInfo loggedInInfo, Integer demographicNo) {

        Demographic result = demographicDao.getDemographicById(demographicNo);
        String workPhone = result.getPhone2();
        if (workPhone != null && workPhone.length() > 0) {
            String value = demographicExtDao.getValueForDemoKey(demographicNo, "wPhoneExt");
            if (value != null && value.length() > 0) {
                workPhone += "x" + value;
            }
        }

        // --- log action ---
        if (result != null) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicWorkPhoneAndExtension",
                    "demographicId=" + result.getDemographicNo() + "result=" + workPhone);
        }

        return (workPhone);
    }

    /**
     * @see DemographicDao for parameter details
     */
    @Override
    public List<Demographic> searchDemographicsByAttributes(LoggedInInfo loggedInInfo, String hin, String firstName,
                                                            String lastName, Gender gender, Calendar dateOfBirth, String city, String province, String phone,
                                                            String email, String alias, int startIndex, int itemsToReturn) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<Demographic> results = demographicDao.findByAttributes(hin, firstName, lastName, gender, dateOfBirth, city,
                province, phone, email, alias, startIndex, itemsToReturn);

        // log all items read
        for (Demographic d : results) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.searchDemographicsByAttributes result",
                    "demographicNo=" + d.getDemographicNo());
        }

        return (results);
    }

    @Override
    public List<String> getPatientStatusList() {
        return demographicDao.search_ptstatus();
    }

    @Override
    public List<String> getRosterStatusList() {
        return demographicDao.getRosterStatuses();
    }

    @Override
    public List<DemographicSearchResult> searchPatients(LoggedInInfo loggedInInfo,
                                                        DemographicSearchRequest searchRequest, int startIndex, int itemsToReturn) {
        List<DemographicSearchResult> results = demographicDao.searchPatients(loggedInInfo, searchRequest, startIndex,
                itemsToReturn);

        for (DemographicSearchResult demographic : results) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.searchPatients result",
                    "demographicId=" + demographic.getDemographicNo());
        }

        return results;
    }

    @Override
    public int searchPatientsCount(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest) {
        return demographicDao.searchPatientCount(loggedInInfo, searchRequest);
    }

    /**
     * @programId can be null for all/any program
     */
    @Override
    public List<Integer> getAdmittedDemographicIdsByProgramAndProvider(LoggedInInfo loggedInInfo, Integer programId,
                                                                       String providerNo) {
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));

        List<Integer> demographicIds = admissionDao.getAdmittedDemographicIdByProgramAndProvider(programId, providerNo);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getAdmittedDemographicIdsByProgramAndProvider",
                "programId=" + programId + ", providerNo=" + providerNo);

        return (demographicIds);
    }


    @Override
    public List<Demographic> getDemographics(LoggedInInfo loggedInInfo, List<Integer> demographicIds) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));

        List<Demographic> demographics = demographicDao.getDemographics(demographicIds);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographics",
                "demographicIds=" + demographicIds);

        return (demographics);
    }

    @Override
    public List<Demographic> searchDemographic(LoggedInInfo loggedInInfo, String searchStr) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));

        List<Demographic> demographics = demographicDao.searchDemographic(searchStr);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.searchDemographic", "searchStr=" + searchStr);

        return (demographics);
    }

    @Override
    public List<Demographic> getActiveDemosByHealthCardNo(LoggedInInfo loggedInInfo, String hcn, String hcnType) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));

        List<Demographic> demographics = demographicDao.getActiveDemosByHealthCardNo(hcn, hcnType);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getActiveDemosByHealthCardNo",
                "hcn=" + hcn + ",hcnType=" + hcnType);

        return (demographics);
    }

    @Override
    public List<Integer> getMergedDemographicIds(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));

        List<Integer> ids = demographicDao.getMergedDemographics(demographicNo);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getMergedDemographics",
                "demographicNo=" + demographicNo);

        return ids;
    }

    @Override
    public List<Demographic> getDemosByChartNo(LoggedInInfo loggedInInfo, String chartNo) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));

        List<Demographic> demographics = demographicDao.getClientsByChartNo(chartNo);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getActiveDemosByChartNo", "chartNo=" + chartNo);

        return (demographics);
    }

    @Override
    public List<Demographic> searchByHealthCard(LoggedInInfo loggedInInfo, String hin) {
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<Demographic> demographics = demographicDao.searchByHealthCard(hin);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.searchByHealthCard", "hin=" + hin);

        return (demographics);
    }

    @Override
    public Demographic getDemographicByNamePhoneEmail(LoggedInInfo loggedInInfo, String firstName, String lastName,
                                                      String hPhone, String wPhone, String email) {
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        Demographic demographic = demographicDao.getDemographicByNamePhoneEmail(firstName, lastName, hPhone, wPhone,
                email);

        if (demographic != null) {
            LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicByNamePhoneEmail",
                    "id found=" + demographic.getDemographicNo());
        }

        return (demographic);
    }

    @Override
    public List<Demographic> getDemographicWithLastFirstDOB(LoggedInInfo loggedInInfo, String lastname,
                                                            String firstname, String year_of_birth, String month_of_birth, String date_of_birth) {
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        List<Demographic> results = demographicDao.getDemographicWithLastFirstDOB(lastname, firstname, year_of_birth,
                month_of_birth, date_of_birth);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getDemographicWithLastFirstDOB", "");

        return (results);
    }

    @Override
    public List<Integer> getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(
            LoggedInInfo loggedInInfo,
            final String midwifeNumber,
            final String lastNameRegex) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicExtDao.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                DemographicExtKey.MIDWIFE,
                midwifeNumber,
                lastNameRegex);
    }

    @Override
    public List<Integer> getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(
            LoggedInInfo loggedInInfo,
            final String nurseNumber,
            final String lastNameRegex) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicExtDao.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                DemographicExtKey.NURSE,
                nurseNumber,
                lastNameRegex);
    }

    @Override
    public List<Integer> getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(
            LoggedInInfo loggedInInfo,
            final String residentNumber,
            final String lastNameRegex) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicExtDao.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                DemographicExtKey.RESIDENT,
                residentNumber,
                lastNameRegex);
    }

    @Override
    public List<DemographicExt> getMultipleMidwifeForDemographicNumbersByProviderNumber(
            LoggedInInfo loggedInInfo,
            final Collection<Integer> demographicNumbers,
            final String midwifeNumber) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicExtDao.getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
                DemographicExtKey.MIDWIFE,
                demographicNumbers,
                midwifeNumber);
    }

    @Override
    public List<DemographicExt> getMultipleNurseForDemographicNumbersByProviderNumber(
            LoggedInInfo loggedInInfo,
            final Collection<Integer> demographicNumbers,
            final String nurseNumber) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicExtDao.getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
                DemographicExtKey.NURSE,
                demographicNumbers,
                nurseNumber);
    }

    @Override
    public List<DemographicExt> getMultipleResidentForDemographicNumbersByProviderNumber(
            LoggedInInfo loggedInInfo,
            final Collection<Integer> demographicNumbers,
            final String residentNumber) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        return demographicExtDao.getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
                DemographicExtKey.RESIDENT,
                demographicNumbers,
                residentNumber);
    }

    /**
     * Fetch the remote demographic file from the Integrator.
     * <p>
     * THIS IS AN INTEGRATOR FUNCTION ONLY. INTEGRATOR MUST BE ENABLED.
     *
     * @param loggedInInfo
     * @param remoteFacilityId
     * @param remoteDemographicId
     * @return
     */
    @Override
    public Demographic getRemoteDemographic(LoggedInInfo loggedInInfo, int remoteFacilityId, int remoteDemographicId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        Demographic demographic = null;
        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            try {
                demographic = CaisiIntegratorManager.makeUnpersistedDemographicObjectFromRemoteEntry(loggedInInfo,
                        loggedInInfo.getCurrentFacility(), remoteFacilityId, remoteDemographicId);
            } catch (MalformedURLException e) {
                logger.error("Error while importing patient file " + remoteDemographicId + " from facility "
                        + remoteFacilityId, e);
            }
            LogAction.addLog(loggedInInfo, "DemographicManager.getRemoteDemographic", null, null,
                    "" + remoteDemographicId, null);
        }
        return demographic;
    }

    /**
     * Copies the given remotely Integrated demographic file into this local
     * facility.
     * <p>
     * THIS IS AN INTEGRATOR FUNCTION ONLY. INTEGRATOR MUST BE ENABLED.
     *
     * @param loggedInInfo
     * @param remoteFacilityId
     * @param remoteDemographicId
     * @return
     */
    @Override
    public Demographic copyRemoteDemographic(LoggedInInfo loggedInInfo, Demographic remoteDemographic,
                                             int remoteFacilityId, int remoteDemographicId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

        remoteDemographic.setDemographicNo(null);

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            try {
                // find the program id
                ProgramProvider programProvider = programManager2.getCurrentProgramInDomain(loggedInInfo);
                createDemographic(loggedInInfo, remoteDemographic, (int) (long) programProvider.getProgramId());

                // get the remote patient consent status
                GetConsentTransfer consentTransfer = CaisiIntegratorManager.getConsentState(loggedInInfo,
                        loggedInInfo.getCurrentFacility(), remoteFacilityId, remoteDemographicId);
                updatePatientConsent(loggedInInfo, remoteDemographic.getDemographicNo(),
                        UserProperty.INTEGRATOR_PATIENT_CONSENT,
                        "ALL".equals(consentTransfer.getConsentState().value()));
            } catch (MalformedURLException e) {
                logger.error("Error while importing patient file " + remoteDemographicId + " from facility "
                        + remoteFacilityId, e);
            }

            LogAction.addLog(loggedInInfo, "DemographicManager.copyRemoteDemographic", null, null,
                    "" + remoteDemographicId, null);
        }
        return remoteDemographic;
    }

    /**
     * Update a patient's consent status.
     *
     * @param loggedInInfo
     * @param demographic_no
     * @param consentType
     * @param consented
     */
    @Override
    public void updatePatientConsent(LoggedInInfo loggedInInfo, int demographic_no, String consentType,
                                     boolean consented) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

        ConsentType patientConsentType = patientConsentManager.getConsentType(consentType);
        patientConsentManager.setConsent(loggedInInfo, demographic_no, patientConsentType.getId(), consented);
    }

    @Override
    public List<DemographicContact> findSDMByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo) {
        if (loggedInInfo == null)
            throw (new SecurityException("user not logged in?"));
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);
        List<DemographicContact> results = demographicContactDao.findSDMByDemographicNo(demographicNo);

        LogAction.addLog(loggedInInfo, "DemographicManager.findSDMByDemographicNo", null, null, "" + demographicNo,
                null);

        return results;
    }

    /**
     * Checks to see if this patient has consented to the given consent type
     */
    @Override
    public boolean isPatientConsented(LoggedInInfo loggedInInfo, int demographic_no, String consentType) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ);

        ConsentType patientConsentType = patientConsentManager.getConsentType(consentType);
        return patientConsentManager.hasPatientConsented(demographic_no, patientConsentType);
    }

    /**
     * Link the given demographic numbers with in the given remote facility.
     * <p>
     * THIS IS AN INTEGRATOR FUNCTION ONLY. INTEGRATOR MUST BE ENABLED.
     */
    @Override
    public boolean linkDemographicToRemoteDemographic(LoggedInInfo loggedInInfo, int demographicNo,
                                                      int remoteFacilityId, int remoteDemographicNo) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.WRITE);

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            try {
                CaisiIntegratorManager.linkIntegratedDemographicFiles(loggedInInfo, demographicNo, remoteFacilityId,
                        remoteDemographicNo);
                String providerNo = loggedInInfo.getLoggedInProviderNo();

                MiscUtils.getLogger()
                        .info("LINK DEMOGRAPHIC #### ProviderNo :" + providerNo + " ,demo No :" + remoteDemographicNo
                                + " , remoteFacilityId :" + remoteFacilityId + " ,remoteDemographicId "
                                + remoteDemographicNo + " orig demo " + demographicNo);

                LogAction.addLog(loggedInInfo, "DemographicManager.linkDemographicToRemoteDemographic", null, null,
                        "" + demographicNo, null);

                return true;
            } catch (MalformedURLException e) {
                logger.error("Failure to link local demographic number " + demographicNo + " with demographicNo "
                        + remoteDemographicNo + " from facility " + remoteFacilityId, e);
            }

        }
        return false;
    }

    /**
     * Fetch all the the demographic ids linked by the Integrator to the given local
     * demographic number
     * and given remote facility id.
     * <p>
     * THIS IS AN INTEGRATOR FUNCTION ONLY. INTEGRATOR MUST BE ENABLED.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @param sourceFacilityId where the linked demographic should exist
     * @return
     */
    @Override
    public List<Integer> getLinkedDemographicIds(LoggedInInfo loggedInInfo, int demographicNo, int sourceFacilityId) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ, demographicNo);

        ArrayList<Integer> remoteDemographicNumbers = new ArrayList<Integer>();
        List<DemographicTransfer> demographicTransferList = getLinkedDemographics(loggedInInfo, demographicNo);

        /*
         * Add the demographic number to the array if the demographic file is
         * from the target facility (sourceFacilityId)
         */
        for (DemographicTransfer demographicTransfer : demographicTransferList) {
            if (demographicTransfer.getIntegratorFacilityId() == sourceFacilityId) {
                remoteDemographicNumbers.add(demographicTransfer.getCaisiDemographicId());
            }
        }

        LogAction.addLog(loggedInInfo, "DemographicManager.getLinkedDemographicIds", null, null, "" + demographicNo,
                null);

        return remoteDemographicNumbers;
    }

    /**
     * Fetch all the demographic files from all facilities linked by the Integrator
     * to the given local demographic number
     * Excludes the demographic file located in this facility
     * <p>
     * THIS IS AN INTEGRATOR FUNCTION ONLY. INTEGRATOR MUST BE ENABLED.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    @Override
    public List<DemographicTransfer> getLinkedDemographics(LoggedInInfo loggedInInfo, int demographicNo) {
        checkPrivilege(loggedInInfo, SecurityInfoManager.READ, demographicNo);

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            /*
             * Fetch all demographic files that are linked to this local demographic number.
             * Excludes all results for this facility.
             */
            try {
                DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo,
                        loggedInInfo.getCurrentFacility());
                return demographicWs.getLinkedDemographicsByDemographicId(demographicNo);
            } catch (MalformedURLException e) {
                MiscUtils.getLogger().error("Integrator connection failed ", e);
            }
        }

        LogAction.addLog(loggedInInfo, "DemographicManager.getLinkedDemographics(LoggedInInfo", null, null,
                "" + demographicNo, null);

        return Collections.emptyList();
    }

    @Override
    public void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", privilege, null)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }
    }

    @Override
    public void checkPrivilege(LoggedInInfo loggedInInfo, String privilege, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", privilege, demographicNo)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }
    }

    /**
     * Returns a list of type DemographicContacts that are associated to this
     * demographic number.
     * DemographicContact.details returns a Contact object that contains the
     * detailed info of each contact.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return DemographicContacts
     */
    @Override
    public List<DemographicContact> getHealthCareTeam(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (demographicNo == null) {
            return null;
        }

        // only professional contacts here.
        List<DemographicContact> demographicContacts = getDemographicContacts(loggedInInfo, demographicNo,
                DemographicContact.CATEGORY_PROFESSIONAL);
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getHealthCareTeam", demographicNo + "");

        return DemographicContactCreator.addContactDetailsToDemographicContact(demographicContacts);
    }

    @Override
    public List<Object[]> getArchiveMeta(LoggedInInfo loggedInInfo, Integer demographicNo) {

        List<Object[]> archiveMeta = demographicArchiveDao.findMetaByDemographicNo(demographicNo);

        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getArchiveMeta", "" + demographicNo);

        return archiveMeta;
    }

    /**
     * Find the providers designated as the primary or most responsible practitioner
     * for this patient.
     * If the MRP is not indicated this method will return the first available
     * internal providers.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return DemographicContact
     */
    @Override
    public DemographicContact getMostResponsibleProviderFromHealthCareTeam(LoggedInInfo loggedInInfo,
                                                                           Integer demographicNo) {

        if (demographicNo == null) {
            return null;
        }

        DemographicContact mrp = null;
        List<DemographicContact> demographicContacts = getHealthCareTeam(loggedInInfo, demographicNo);

        for (DemographicContact demographicContact : demographicContacts) {
            if (demographicContact.isMrp()) {
                mrp = demographicContact;
            }
        }

        // can be removed if annoying. This is a back up for when the MRP is
        // not indicated.
        if (mrp == null) {
            for (DemographicContact demographicContact : demographicContacts) {
                if (demographicContact.getType() == 0) {
                    mrp = demographicContact;
                }
            }
        }

        return DemographicContactCreator.addContactDetailsToDemographicContact(mrp);
    }

    /**
     * Get Health Care Team Member by a specific role.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @param role          (can be an numeric string or alpha string)
     * @return
     */
    @Override
    public DemographicContact getHealthCareMemberbyRole(LoggedInInfo loggedInInfo, Integer demographicNo, String role) {
        if (demographicNo == null) {
            return null;
        }

        if (role == null) {
            role = "";
        }

        ContactSpecialty contactSpecialty = null;
        String roleId = "";
        DemographicContact contact = null;
        String contactRole = "";

        if (StringUtils.isNumeric(role)) {
            contactSpecialty = contactSpecialtyDao.find(Integer.parseInt(role));
            if (contactSpecialty != null) {
                role = contactSpecialty.getSpecialty();
                roleId = role;
            }
        } else {
            contactSpecialty = contactSpecialtyDao.findBySpecialty(role.trim());
            if (contactSpecialty != null) {
                roleId = contactSpecialty.getId() + "";
            }
        }

        List<DemographicContact> demographicContacts = getHealthCareTeam(loggedInInfo, demographicNo);

        for (DemographicContact demographicContact : demographicContacts) {
            contactRole = demographicContact.getRole();
            if (role.equalsIgnoreCase(contactRole) || roleId.equalsIgnoreCase(contactRole)) {
                contact = demographicContact;
            }
        }

        return DemographicContactCreator.addContactDetailsToDemographicContact(contact);
    }

    @Override
    public DemographicContact getHealthCareMemberbyId(LoggedInInfo loggedInInfo, Integer demographicContactId) {
        if (demographicContactId == null) {
            return null;
        }

        DemographicContact contact = demographicContactDao.find(demographicContactId);
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getHealthCareMemberbyId",
                demographicContactId + "");
        MiscUtils.getLogger().debug("Health Care Contact found." + contact);
        return DemographicContactCreator.addContactDetailsToDemographicContact(contact);
    }

    /**
     * Returns all of the demographics personal contacts.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    @Override
    public List<DemographicContact> getPersonalEmergencyContacts(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (demographicNo == null) {
            return null;
        }

        List<DemographicContact> demographicContacts = getDemographicContacts(loggedInInfo, demographicNo,
                DemographicContact.CATEGORY_PERSONAL);

        return DemographicContactCreator.addContactDetailsToDemographicContact(demographicContacts);
    }

    /**
     * Get personal contact info with the contact id.
     *
     * @param loggedInInfo
     * @return
     */
    @Override
    public DemographicContact getPersonalEmergencyContactById(LoggedInInfo loggedInInfo, Integer demographicContactId) {

        if (demographicContactId == null) {
            return null;
        }

        DemographicContact contact = demographicContactDao.find(demographicContactId);
        LogAction.addLogSynchronous(loggedInInfo, "DemographicManager.getPersonalEmergencyContactById",
                demographicContactId + "");
        return DemographicContactCreator.addContactDetailsToDemographicContact(contact);
    }

    /**
     * These are contacts which are marked as emergency contacts.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    @Override
    public List<DemographicContact> getEmergencyContacts(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (demographicNo == null) {
            return null;
        }

        List<DemographicContact> demographicContacts = getPersonalEmergencyContacts(loggedInInfo, demographicNo);
        List<DemographicContact> emergencyContacts = new ArrayList<DemographicContact>();
        for (DemographicContact demographicContact : demographicContacts) {
            if (Boolean.parseBoolean(demographicContact.getEc())) {
                emergencyContacts
                        .add(DemographicContactCreator.addContactDetailsToDemographicContact(demographicContact));
            }
        }
        return emergencyContacts;
    }

    @Override
    public Provider getMRP(LoggedInInfo loggedInInfo, Integer demographicNo) {
        return getDemographic(loggedInInfo, demographicNo).getMrp();
    }

    @Override
    public Provider getMRP(LoggedInInfo loggedInInfo, Demographic demographic) {
        String providerNo = demographic.getProviderNo();
        Provider mrp = null;
        if (providerNo != null && !providerNo.isEmpty()) {
            mrp = providerManager.getProvider(loggedInInfo, providerNo);
        }

        if (mrp == null) {
            DemographicContact demographicContact = getMostResponsibleProviderFromHealthCareTeam(loggedInInfo, demographic.getDemographicNo());
            String contactId = null;
            if (demographicContact != null && DemographicContact.TYPE_PROVIDER == demographicContact.getType()) {
                contactId = demographicContact.getContactId();
            }
            if (contactId != null && !contactId.isEmpty()) {
                mrp = providerManager.getProvider(loggedInInfo, contactId);
            }
        }
        demographic.setMrp(mrp);
        return mrp;
    }

    @Override
    public String getNextAppointmentDate(LoggedInInfo loggedInInfo, Integer demographicNo) {
        return appointmentManager.getNextAppointmentDate(demographicNo);
    }

    @Override
    public String getNextAppointmentDate(LoggedInInfo loggedInInfo, Demographic demographic) {
        String appointmentString = getNextAppointmentDate(loggedInInfo, demographic.getDemographicNo());
        demographic.setNextAppointment(appointmentString);
        return appointmentString;
    }

}
