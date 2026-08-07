/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFavouriteDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFavourite;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.SxmlMisc;

/**
 * Lookup-and-mutation service used by ON billing actions and assemblers.
 * Mostly read-side helpers (provider lists, provider OHIP/name maps,
 * appointment-status lookup, current billing-demographic-id resolution) plus one mutation
 * ({@link #updateApptStatus}). Sits in {@code service/} because the
 * mutation makes it side-effect-bearing per the package-info contract;
 * the reads stay co-located so the entire surface is one file.
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingOnLookupService {

    private static final Logger _logger = MiscUtils.getLogger();
    private static final String KEY_TYPE_TO_SEARCH = "billing.billingOnFavourite.msgTypeToSearch";
    private static final String KEY_NOT_SAVE_SEARCH_FIRST = "billing.billingOnFavourite.msgNotSaveSearchFirst";
    private static final String KEY_NOT_SAVE_NAME_CHANGED = "billing.billingOnFavourite.msgNotSaveNameChanged";
    private static final String KEY_UPDATED = "billing.billingOnFavourite.msgUpdated";
    private static final String KEY_NOT_UPDATED = "billing.billingOnFavourite.msgNotUpdated";
    private static final String KEY_ADDED = "billing.billingOnFavourite.msgAdded";
    private static final String KEY_NOT_ADDED = "billing.billingOnFavourite.msgNotAdded";
    private static final String KEY_NOTHING_TO_DELETE = "billing.billingOnFavourite.msgNothingToDelete";
    private static final String KEY_DELETED = "billing.billingOnFavourite.msgDeleted";
    private static final String KEY_NOT_DELETED = "billing.billingOnFavourite.msgNotDeleted";
    private static final String LEVEL_INFO = "info";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_WARNING = "warning";
    private static final String LEVEL_DANGER = "danger";

    private final OscarAppointmentDao appointmentDao;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    private final ClinicLocationDao clinicLocationDao;
    private final ProviderDao providerDao;
    private final BillingPaymentTypeDao billingPaymentTypeDao;
    private final BillingONFavouriteDao billingONFavouriteDao;
    private final DemographicManager demographicManager;
    private final BillingONFilenameDao billingONFilenameDao;
    private final ProviderSiteDao providerSiteDao;

    /** Test-friendly constructor — package-private, takes DAO mocks directly. */
    BillingOnLookupService(OscarAppointmentDao appointmentDao, ProfessionalSpecialistDao professionalSpecialistDao, ClinicLocationDao clinicLocationDao, ProviderDao providerDao, BillingPaymentTypeDao billingPaymentTypeDao, BillingONFavouriteDao billingONFavouriteDao, DemographicManager demographicManager, BillingONFilenameDao billingONFilenameDao, ProviderSiteDao providerSiteDao) {
        this.appointmentDao = appointmentDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.clinicLocationDao = clinicLocationDao;
        this.providerDao = providerDao;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
        this.billingONFavouriteDao = billingONFavouriteDao;
        this.demographicManager = demographicManager;
        this.billingONFilenameDao = billingONFilenameDao;
        this.providerSiteDao = providerSiteDao;
    }

    /**

     * Returns cur team provider str.

     *

     * @param provider_no String

     * @return List<String>

     */

    public List<ProviderDropdownEntry> getCurTeamProviderStr(String provider_no) {
        List<ProviderDropdownEntry> retval = new ArrayList<>();
        List<Provider> ps = providerDao.getCurrentTeamProviders(provider_no);
        for (Provider p : ps) {
            retval.add(new ProviderDropdownEntry(
                    p.getProviderNo(),
                    p.getLastName(),
                    p.getFirstName(),
                    p.getOhipNo(),
                    getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000"),
                    getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00")));
        }
        return retval;
    }

    /**

     * Returns cur site provider str.

     *

     * @param provider_no String

     * @return List<String>

     */

    public List<ProviderDropdownEntry> getCurSiteProviderStr(String provider_no) {
        List<ProviderDropdownEntry> retval = new ArrayList<>();

        List<ProviderSite> sites = providerSiteDao.findByProviderNo(provider_no);
        List<Integer> siteIds = new ArrayList<Integer>();
        for (ProviderSite site : sites) {
            siteIds.add(site.getId().getSiteId());
        }

        try {
            for (Provider p : providerSiteDao.findActiveProvidersWithSites(provider_no)) {
                retval.add(new ProviderDropdownEntry(
                        p.getProviderNo(),
                        p.getLastName(),
                        p.getFirstName(),
                        p.getOhipNo(),
                        getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000"),
                        getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00")));
            }
        } catch (RuntimeException e) {
            // Rethrow so the JSP gets a real error rather than a silently-
            // truncated dropdown the user picks from. A blank or partial
            // dropdown blocks selection AND yields wrong-subset picks; both
            // are worse than the global Struts exception mapping rendering
            // an explicit "lookup failed" page.
            _logger.error("Failed to load provider dropdown list (returning {} entries before failure); rethrowing",
                    retval.size(), e);
            throw e;
        }

        return retval;
    }

    /**

     * Returns cur provider str.

     * @return List<String>

     */

    public List<ProviderDropdownEntry> getCurProviderStr() {
        List<ProviderDropdownEntry> retval = new ArrayList<>();
        List<Provider> ps = providerDao.getBillableProviders();
        for (Provider p : ps) {
            retval.add(new ProviderDropdownEntry(
                    p.getProviderNo(),
                    p.getLastName(),
                    p.getFirstName(),
                    p.getOhipNo(),
                    getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000"),
                    getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00")));
        }
        return retval;
    }

    private String getXMLStringWithDefault(String xmlStr, String xmlName, String strDefault) {
        String retval = SxmlMisc.getXmlContent(xmlStr, "<" + xmlName + ">", "</" + xmlName + ">");
        retval = retval == null || "".equals(retval) ? strDefault : retval;
        return retval;
    }

    /**

     * Returns prop provider o h i p.

     * @return Properties

     */

    public Properties getPropProviderOHIP() {
        Properties retval = new Properties();
        List<Provider> ps = providerDao.getBillableProviders();

        String proid = "";
        String proOHIP = "";

        for (Provider p : ps) {
            proid = p.getProviderNo();
            proOHIP = p.getOhipNo();
            retval.setProperty(proid, proOHIP);
        }

        return retval;
    }

    /**

     * Returns prop provider name.

     * @return Properties

     */

    public Properties getPropProviderName() {
        Properties retval = new Properties();

        List<Provider> ps = providerDao.getProviders();
        String proid = "";
        String proName = "";
        for (Provider p : ps) {
            proid = p.getProviderNo();
            proName = p.getLastName() + "," + p.getFirstName();
            retval.setProperty(proid, proName);
        }

        return retval;
    }

    /**

     * Returns provider obj.

     *

     * @param providerNo String

     * @return BillingProviderDto

     */

    public BillingProviderDto getProviderObj(String providerNo) {
        BillingProviderDto pObj = null;

        List<Provider> ps = new ArrayList<Provider>();
        if (providerNo.equals("all")) {
            ps = providerDao.getActiveProviders();
        } else {
            Provider p = providerDao.getProvider(providerNo);
            if (p.getStatus().equals("1"))
                ps.add(p);
        }
        String specialty_code;
        String billinggroup_no;

        for (Provider p : ps) {
            pObj = new BillingProviderDto();
            billinggroup_no = getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000");
            specialty_code = getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00");
            pObj.setProviderNo(p.getProviderNo());
            pObj.setLastName(p.getLastName());
            pObj.setFirstName(p.getFirstName());
            pObj.setOhipNo(p.getOhipNo());
            pObj.setRmaNo(p.getRmaNo());
            pObj.setSpecialtyCode(specialty_code);
            pObj.setBillingGroupNo(billinggroup_no);
        }

        return pObj;
    }

    /**

     * Returns provider obj list.

     *

     * @param providerNo String

     * @return List<BillingProviderDto>

     */

    public List<BillingProviderDto> getProviderObjList(String providerNo) {
        BillingProviderDto pObj = null;
        List<BillingProviderDto> res = new ArrayList<BillingProviderDto>();

        List<Provider> ps = new ArrayList<Provider>();
        if (providerNo.equals("all")) {
            ps = providerDao.getActiveProviders();
        } else {
            Provider p = providerDao.getProvider(providerNo);
            if (p.getStatus().equals("1"))
                ps.add(p);
        }
        String specialty_code;
        String billinggroup_no;
        for (Provider p : ps) {
            pObj = new BillingProviderDto();
            billinggroup_no = getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000");
            specialty_code = getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00");
            pObj.setProviderNo(p.getProviderNo());
            pObj.setLastName(p.getLastName());
            pObj.setFirstName(p.getFirstName());
            pObj.setOhipNo(p.getOhipNo());
            pObj.setRmaNo(p.getRmaNo());
            pObj.setSpecialtyCode(specialty_code);
            pObj.setBillingGroupNo(billinggroup_no);
            res.add(pObj);
        }

        return res;
    }

    /**

     * Returns provider.

     *

     * @param diskId String

     * @return List<BillingProviderDto>

     */

    public List<BillingProviderDto> getProvider(String diskId) {
        List<BillingProviderDto> retval = new ArrayList<BillingProviderDto>();
        String providerNo = null;

        List<BillingONFilename> fs = billingONFilenameDao.findByDiskId(Integer.parseInt(diskId));
        for (BillingONFilename f : fs) {
            providerNo = f.getProviderNo();

            Provider p = providerDao.getProvider(providerNo);
            if (p != null && p.getStatus().equals("1") && p.getOhipNo().length() > 0) {
                String specialty_code;
                String billinggroup_no;
                billinggroup_no = getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000");
                specialty_code = getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00");

                BillingProviderDto pObj = new BillingProviderDto();
                pObj.setProviderNo(p.getProviderNo());
                pObj.setLastName(p.getLastName());
                pObj.setFirstName(p.getFirstName());
                pObj.setOhipNo(p.getOhipNo());
                pObj.setSpecialtyCode(specialty_code);
                pObj.setBillingGroupNo(billinggroup_no);
                retval.add(pObj);
            }
        }
        return retval;
    }

    /**

     * Returns cur solo provider.

     * @return List<BillingProviderDto>

     */

    public List<BillingProviderDto> getCurSoloProvider() {
        List<BillingProviderDto> retval = new ArrayList<BillingProviderDto>();
        String specialty_code;
        String billinggroup_no;

        List<Provider> ps = providerDao.getBillableProviders();
        for (Provider p : ps) {
            billinggroup_no = getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000");
            specialty_code = getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00");
            if (!"0000".equals(billinggroup_no))
                continue;
            BillingProviderDto pObj = new BillingProviderDto();
            pObj.setProviderNo(p.getProviderNo());
            pObj.setLastName(p.getLastName());
            pObj.setFirstName(p.getFirstName());
            pObj.setOhipNo(p.getOhipNo());
            pObj.setSpecialtyCode(specialty_code);
            pObj.setBillingGroupNo(billinggroup_no);
            retval.add(pObj);
        }

        return retval;
    }

    /**

     * Returns cur grp provider.

     * @return List<BillingProviderDto>

     */

    public List<BillingProviderDto> getCurGrpProvider() {
        List<BillingProviderDto> retval = new ArrayList<BillingProviderDto>();
        String specialty_code;
        String billinggroup_no;

        List<Provider> ps = providerDao.getBillableProviders();
        for (Provider p : ps) {
            billinggroup_no = getXMLStringWithDefault(p.getComments(), "xml_p_billinggroup_no", "0000");
            specialty_code = getXMLStringWithDefault(p.getComments(), "xml_p_specialty_code", "00");
            if ("0000".equals(billinggroup_no))
                continue;
            BillingProviderDto pObj = new BillingProviderDto();
            pObj.setProviderNo(p.getProviderNo());
            pObj.setLastName(p.getLastName());
            pObj.setFirstName(p.getFirstName());
            pObj.setOhipNo(p.getOhipNo());
            pObj.setSpecialtyCode(specialty_code);
            pObj.setBillingGroupNo(billinggroup_no);
            retval.add(pObj);
        }

        return retval;
    }

    /**

     * Updates appt status.

     *

     * @param apptNo String

     * @param status String

     * @param userNo String

     * @return boolean

     */

    @org.springframework.transaction.annotation.Transactional
    public boolean updateApptStatus(String apptNo, String status, String userNo) {
        // Method-level @Transactional overrides the class-level
        // readOnly=true. Without this override the merge below silently
        // does not flush at commit (Hibernate sets flush mode MANUAL on
        // readOnly transactions), losing the update.
        Appointment appt = appointmentDao.find(Integer.valueOf(apptNo));
        if (appt != null) {
            appt.setStatus(status);
            appt.setLastUpdateUser(userNo);
            appt.setUpdateDateTime(new Date());
            appointmentDao.merge(appt);
            return true;
        }
        _logger.warn("BillingOnLookupService.updateApptStatus: appointment {} not found; status {} not applied", // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                LogSafe.sanitize(apptNo), LogSafe.sanitize(status));
        return false;
    }

    /**

     * Returns appt status.

     *

     * @param apptNo String

     * @return String

     */

    public String getApptStatus(String apptNo) {
        String retval = "T";

        Appointment appt = appointmentDao.find(Integer.valueOf(apptNo));
        if (appt != null) {
            retval = appt.getStatus();
        }

        return retval;
    }

    /**

     * Returns patient cur billing demographic.

     *

     * @param loggedInInfo LoggedInInfo

     * @param demoNo String

     * @return List<String>

     */

    public List<String> getPatientCurBillingDemographic(LoggedInInfo loggedInInfo, String demoNo) {
        List<String> retval = null;
        Demographic d = demographicManager.getDemographic(loggedInInfo, demoNo);
        if (d != null) {
            retval = new ArrayList<String>();
            retval.add(d.getLastName());
            retval.add(d.getFirstName());
            retval.add(d.getYearOfBirth() + d.getMonthOfBirth() + d.getDateOfBirth());
            retval.add(d.getHin() == null ? "" : d.getHin());
            retval.add(d.getVer() == null ? "" : d.getVer());
            retval.add(d.getHcType() == null ? "" : d.getHcType());
            retval.add(d.getSex().startsWith("F") ? "2" : "1");
            retval.add(d.getFamilyDoctor() == null ? "" : d.getFamilyDoctor());
            retval.add(d.getProviderNo() == null ? "" : d.getProviderNo());
            retval.add(d.getRosterStatus() == null ? "" : d.getRosterStatus());
        }
        return retval;
    }

    /**

     * Returns refer doc spet.

     *

     * @param billingNo String

     * @return String

     */

    public String getReferDocSpet(String billingNo) {
        String retval = null;
        ProfessionalSpecialist specialist = professionalSpecialistDao.getByReferralNo(billingNo);
        if (specialist != null) {
            return specialist.getSpecialtyType();
        }

        return retval;
    }

    /**

     * Returns patient cur billing demo.

     *

     * @param loggedInInfo LoggedInInfo

     * @param demoNo String

     * @return List<String>

     */

    public List<String> getPatientCurBillingDemo(LoggedInInfo loggedInInfo, String demoNo) {
        List<String> retval = null;
        Demographic d = demographicManager.getDemographic(loggedInInfo, demoNo);
        if (d != null) {
            retval = new ArrayList<String>();
            retval.add(d.getLastName());
            retval.add(d.getFirstName());
            retval.add(d.getYearOfBirth() + d.getMonthOfBirth() + d.getDateOfBirth());
            retval.add(d.getHin());
            retval.add(d.getVer());
            retval.add(d.getHcType());
            retval.add(d.getSex().startsWith("F") ? "2" : "1");
            retval.add(d.getFamilyDoctor() == null ? "" : d.getFamilyDoctor());
            retval.add(d.getProviderNo() == null ? "" : d.getProviderNo());
        }
        return retval;
    }

    // name : code|dx|
    /**
     * Returns billing favourite list.
     * @return List<String>
     */
    public List<String> getBillingFavouriteList() {
        List<String> retval = new ArrayList<String>();
        List<BillingONFavourite> bs = billingONFavouriteDao.findCurrent();
        Collections.sort(bs, BillingONFavourite.NAME_COMPARATOR);
        for (BillingONFavourite b : bs) {
            retval.add(b.getName());
            retval.add(b.getServiceDx());
        }
        return retval;
    }

    /**

     * Returns billing favourite one.

     *

     * @param name String

     * @return List<String>

     */

    public List<String> getBillingFavouriteOne(String name) {
        List<String> retval = new ArrayList<String>();
        List<BillingONFavourite> bs = billingONFavouriteDao.findByName(name);
        for (BillingONFavourite b : bs) {
            if (b.getDeleted() == 1)
                continue;
            retval.add(b.getName());
            retval.add(b.getServiceDx());
        }
        return retval;
    }

    /**

     * Persists billing favourite list.

     *

     * @param name String

     * @param list String

     * @param providerNo String

     * @return int

     */

    @org.springframework.transaction.annotation.Transactional
    public int addBillingFavouriteList(String name, String list, String providerNo) {
        // Method-level @Transactional overrides the class-level readOnly=true
        // — without it the persist below silently no-ops (Hibernate flush
        // mode MANUAL on read-only transactions drops the insert).
        BillingONFavourite b = new BillingONFavourite();
        b.setName(name);
        b.setServiceDx(list);
        b.setProviderNo(providerNo);
        b.setTimestamp(new Date());
        b.setDeleted(0);
        billingONFavouriteDao.persist(b);

        return b.getId();
    }

    //	 @ OSCARSERVICE
    /**
     * delBillingFavouriteList.
     *
     * @param name String
     * @param providerNo String
     * @return boolean
     */
    @org.springframework.transaction.annotation.Transactional
    public boolean delBillingFavouriteList(String name, String providerNo) {
        // Method-level @Transactional overrides the class-level readOnly=true
        // — without it the merge below silently no-ops (Hibernate flush
        // mode MANUAL on read-only transactions drops the soft-delete).
        List<BillingONFavourite> bs = billingONFavouriteDao.findByNameAndProviderNo(name, providerNo);
        for (BillingONFavourite b : bs) {
            b.setDeleted(1);
            billingONFavouriteDao.merge(b);
        }
        return true;
    }
    // @ OSCARSERVICE

    /**

     * Updates billing favourite list.

     *

     * @param name String

     * @param list String

     * @param providerNo String

     * @return boolean

     */

    @org.springframework.transaction.annotation.Transactional
    public boolean updateBillingFavouriteList(String name, String list, String providerNo) {
        // Method-level @Transactional overrides the class-level readOnly=true.
        List<BillingONFavourite> bs = billingONFavouriteDao.findByName(name);
        for (BillingONFavourite b : bs) {
            b.setServiceDx(list);
            b.setProviderNo(providerNo);
            billingONFavouriteDao.merge(b);
        }
        return true;
    }

    /**
     * Applies a favourite-list add, edit, or delete submission and returns the
     * view state the action should pass to the read-only assembler.
     */
    @org.springframework.transaction.annotation.Transactional
    public FavouriteMutationResult saveOrDeleteFavourite(FavouriteMutationRequest request) {
        String submit = request.submit();
        if ("Save".equals(submit)) {
            return handleFavouriteSave(request);
        }
        if ("Search".equals(submit) && "Delete".equals(nullToEmpty(request.action()))) {
            return handleFavouriteDelete(request);
        }
        return new FavouriteMutationResult(KEY_TYPE_TO_SEARCH, null, LEVEL_INFO, "search", Map.of());
    }

    private FavouriteMutationResult handleFavouriteSave(FavouriteMutationRequest request) {
        String actionParam = nullToEmpty(request.action());
        if (actionParam.startsWith("edit")) {
            return processFavouriteEdit(request, actionParam);
        }
        if (actionParam.startsWith("add")) {
            return processFavouriteAdd(request, actionParam);
        }
        return new FavouriteMutationResult(KEY_NOT_SAVE_SEARCH_FIRST, null, LEVEL_WARNING, "search", Map.of());
    }

    private FavouriteMutationResult processFavouriteEdit(FavouriteMutationRequest request, String actionParam) {
        String name = nullToEmpty(request.name());
        Map<String, String> formFields = new LinkedHashMap<>();
        if (!name.equals(actionParam.substring("edit".length()))) {
            formFields.put("name", name);
            return new FavouriteMutationResult(name.isEmpty() ? KEY_NOT_SAVE_SEARCH_FIRST : KEY_NOT_SAVE_NAME_CHANGED, name.isEmpty() ? null : name, LEVEL_WARNING, "search", formFields);
        }
        String list = buildFavouriteServiceList(request, false);
        boolean ok = updateBillingFavouriteList(name, list, request.providerNo());
        formFields.put("name", name);
        if (ok) {
            return new FavouriteMutationResult(KEY_UPDATED, name, LEVEL_SUCCESS, "search", formFields);
        }
        captureFavouritePersistFields(request, formFields);
        return new FavouriteMutationResult(KEY_NOT_UPDATED, name, LEVEL_DANGER, "edit" + name, formFields);
    }

    private FavouriteMutationResult processFavouriteAdd(FavouriteMutationRequest request, String actionParam) {
        String name = nullToEmpty(request.name());
        Map<String, String> formFields = new LinkedHashMap<>();
        if (name.isEmpty()) {
            formFields.put("name", name);
            return new FavouriteMutationResult(KEY_NOT_SAVE_SEARCH_FIRST, null, LEVEL_WARNING, "search", formFields);
        }
        if (!name.equals(actionParam.substring("add".length()))) {
            formFields.put("name", name);
            return new FavouriteMutationResult(KEY_NOT_SAVE_NAME_CHANGED, name, LEVEL_WARNING, "search", formFields);
        }
        String list = buildFavouriteServiceList(request, true);
        int rc = addBillingFavouriteList(name, list, request.providerNo());
        formFields.put("name", name);
        if (rc > 0) {
            return new FavouriteMutationResult(KEY_ADDED, name, LEVEL_SUCCESS, "search", formFields);
        }
        captureFavouritePersistFields(request, formFields);
        return new FavouriteMutationResult(KEY_NOT_ADDED, name, LEVEL_DANGER, "add" + name, formFields);
    }

    private FavouriteMutationResult handleFavouriteDelete(FavouriteMutationRequest request) {
        String name = nullToEmpty(request.name());
        if (name.isEmpty()) {
            return new FavouriteMutationResult(KEY_NOTHING_TO_DELETE, null, LEVEL_WARNING, "search", Map.of());
        }
        boolean ok = delBillingFavouriteList(name, request.providerNo());
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("name", name);
        if (ok) {
            return new FavouriteMutationResult(KEY_DELETED, name, LEVEL_SUCCESS, "search", formFields);
        }
        return new FavouriteMutationResult(KEY_NOT_DELETED, name, LEVEL_DANGER, "edit" + name, formFields);
    }

    private String buildFavouriteServiceList(FavouriteMutationRequest request, boolean padAtPrefix) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < BillingOnConstants.FIELD_SERVICE_NUM; i++) {
            String code = nullToEmpty(request.field("serviceCode" + i));
            if (code.length() == 5) {
                String unitParam = nullToEmpty(request.field("serviceUnit" + i));
                String unit = unitParam.isEmpty() ? "1" : unitParam;
                String atParam = nullToEmpty(request.field("serviceAt" + i));
                String at = atParam.isEmpty() ? "1" : atParam;
                if (padAtPrefix && at.length() == 3) {
                    if (at.startsWith(".")) {
                        at = "0" + at;
                    } else {
                        at = at + "0";
                    }
                }
                list.append(code).append('|').append(unit).append('|').append(at).append('|');
            }
        }
        String dx = nullToEmpty(request.field("dx"));
        if (dx.length() == 3) {
            list.append(dx).append('|');
            String dx1 = nullToEmpty(request.field("dx1"));
            if (dx1.length() == 3) {
                list.append(dx1).append('|');
                String dx2 = nullToEmpty(request.field("dx2"));
                if (dx2.length() == 3) {
                    list.append(dx2).append('|');
                }
            }
        }
        return list.toString();
    }

    private void captureFavouritePersistFields(FavouriteMutationRequest request, Map<String, String> formFields) {
        for (int i = 0; i < BillingOnConstants.FIELD_SERVICE_NUM; i++) {
            putIfPresent(formFields, "serviceCode" + i, request.field("serviceCode" + i));
            putIfPresent(formFields, "serviceUnit" + i, request.field("serviceUnit" + i));
            putIfPresent(formFields, "serviceAt" + i, request.field("serviceAt" + i));
        }
        putIfPresent(formFields, "dx", request.field("dx"));
        putIfPresent(formFields, "dx1", request.field("dx1"));
        putIfPresent(formFields, "dx2", request.field("dx2"));
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Immutable favourite mutation command built by the action from request
     * parameters.
     */
    public record FavouriteMutationRequest(String submit, String action, String name,
                                           String providerNo, Map<String, String> fields) {
        public FavouriteMutationRequest {
            fields = fields == null ? Map.of() : new LinkedHashMap<>(fields);
        }

        public String field(String name) {
            return fields.get(name);
        }
    }

    /** Result of applying a favourite mutation before rendering the page. */
    public record FavouriteMutationResult(String messageKey, String messageName, String messageLevel,
                                          String action, Map<String, String> formFields) {
        public FavouriteMutationResult {
            formFields = formFields == null ? Map.of() : new LinkedHashMap<>(formFields);
        }
    }

    /**

     * Returns payment type.

     * @return List<String>

     */

    public List<String> getPaymentType() {
        List<String> retval = new ArrayList<String>();
        List<BillingPaymentType> bs = billingPaymentTypeDao.findAll();
        for (BillingPaymentType b : bs) {
            retval.add("" + b.getId());
            retval.add(b.getPaymentType());
        }

        return retval;
    }

    /**

     * Returns facilty num.

     * @return List<String>

     */

    public List<String> getFacilty_num() {
        List<String> retval = new ArrayList<String>();
        List<ClinicLocation> clinicLocations = clinicLocationDao.findByClinicNo(1);
        for (ClinicLocation clinicLocation : clinicLocations) {
            retval.add(clinicLocation.getClinicLocationNo());
            retval.add(clinicLocation.getClinicLocationName());
        }

        return retval;
    }

}
