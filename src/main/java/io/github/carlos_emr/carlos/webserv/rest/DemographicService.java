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
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.DemographicExt.DemographicProperty;
import io.github.carlos_emr.carlos.commn.model.enumerator.CppCode;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.NoteManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.webserv.rest.conversion.AllergyConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DemographicContactFewConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DemographicConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.MeasurementConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ProfessionalSpecialistConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ProviderConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.WaitingListNameConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicContactFewTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SEARCHMODE;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SORTDIR;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SORTMODE;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProfessionalSpecialistTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProviderTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.StatusValueTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.WaitingListNameTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;


/**
 * Defines a service contract for main operations on demographic.
 */
@Path("/demographics")
@Component("demographicService")
@Consumes(MediaType.APPLICATION_JSON)
// XML stays first so a request without an explicit Accept keeps the representation the
// XML-only AbstractServiceImpl contract gave legacy callers; JSON is negotiated, not default.
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class DemographicService extends AbstractServiceImpl {

    private enum IncludeType {
        ALLERGIES("allergies"),
        MEASUREMENTS("measurements"),
        NOTES("notes"),
        MEDICATIONS("medications"),
        CONTACTS("contacts");

        private final String value;

        IncludeType(String value) {
            this.value = value;
        }

    public String getValue() {
            return value;
        }
    }

    public enum MeasurementType {
        HEIGHT("ht"),
        WEIGHT("wt");

        private final String abbreviation;

        MeasurementType(String abbreviation) {
            this.abbreviation = abbreviation;
        }

        public String getAbbreviation() {
            return abbreviation;
        }
    }


    @Autowired
    private DemographicManager demographicManager;

    @Autowired
    private ContactDao contactDao;

    @Autowired
    private AllergyManager allergyManager;

    @Autowired
    private MeasurementManager measurementManager;

    @Autowired
    private WaitingListDao waitingListDao;

    @Autowired
    private WaitingListNameDao waitingListNameDao;

    @Autowired
    private NoteManager noteManager;

    @Autowired
    private ProviderDao providerDao;

    @Autowired
    private RxManager rxManager;

    @Autowired
    private SecUserRoleDao secUserRoleDao;

    @Autowired
    private ProfessionalSpecialistDao specialistDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;


    private DemographicConverter demoConverter = new DemographicConverter();
    private DemographicContactFewConverter demoContactFewConverter = new DemographicContactFewConverter();
    private WaitingListNameConverter waitingListNameConverter = new WaitingListNameConverter();
    private ProviderConverter providerConverter = new ProviderConverter();
    private ProfessionalSpecialistConverter specialistConverter = new ProfessionalSpecialistConverter();

    /** Object-level privilege check — no patient-specific restriction. */
    private LoggedInInfo requireDemographicPrivilege(String action) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (loggedInInfo == null) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).entity("Unauthorized").build());
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", action, null)) {
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).entity("Access Denied").build());
        }
        return loggedInInfo;
    }

    /** Record-level privilege check — enforces patient-specific access control. */
    private LoggedInInfo requireDemographicPrivilege(String action, Integer demographicNo) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (loggedInInfo == null) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).entity("Unauthorized").build());
        }
        if (demographicNo == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity("Missing demographic identifier").build());
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", action, demographicNo)) {
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).entity("Access Denied").build());
        }
        return loggedInInfo;
    }


    /**
     * Finds all demographics.
     * <p/>
     * In case limit or offset parameters are set to null or zero, the entire result set is returned.
     *
     * @param offset First record in the entire result set to be returned
     * @param limit  Maximum total number of records that should be returned
     * @return Returns all demographics.
     */
    @GET
    public OscarSearchResponse<DemographicTo1> getAllDemographics(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("r");
        OscarSearchResponse<DemographicTo1> result = new OscarSearchResponse<DemographicTo1>();

        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 0;
        }

        result.setLimit(limit);
        result.setOffset(offset);
        result.setTotal(demographicManager.getActiveDemographicCount(loggedInInfo).intValue());

        for (Demographic demo : demographicManager.getActiveDemographics(loggedInInfo, offset, limit)) {
            result.getContent().add(demoConverter.getAsTransferObject(loggedInInfo, demo));
        }

        return result;
    }

    /**
     * Gets detailed demographic data.
     *
     * @param id Id of the demographic to get data for
     * @return Returns data for the demographic provided
     */
    @GET
    @Path("/{dataId}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DemographicTo1 getDemographicData(@PathParam("dataId") Integer id, @QueryParam("includes[]") List<String> include) throws PatientDirectiveException {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("r", id);
        Demographic demo = demographicManager.getDemographic(loggedInInfo, id);
        if (demo == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("Demographic record not found: " + id).build());
        }
        return buildDemographicTo1(loggedInInfo, demo, id, include);
    }

    private DemographicTo1 buildDemographicTo1(LoggedInInfo loggedInInfo, Demographic demo, Integer id, List<String> include) {
        List<DemographicExt> demoExts = demographicManager.getDemographicExts(loggedInInfo, id);
        if (demoExts != null && !demoExts.isEmpty()) {
            DemographicExt[] demoExtArray = demoExts.toArray(new DemographicExt[demoExts.size()]);
            demo.setExtras(demoExtArray);
        }

        DemographicTo1 result = demoConverter.getAsTransferObject(loggedInInfo, demo);

        DemographicCust demoCust = demographicManager.getDemographicCust(loggedInInfo, id);
        if (demoCust != null) {
            result.setNurse(demoCust.getNurse());
            result.setResident(demoCust.getResident());
            result.setMidwife(demoCust.getMidwife());
            result.setNotes(demoCust.getNotes());
        }

        List<WaitingList> waitingList = waitingListDao.search_wlstatus(id);
        if (waitingList != null && !waitingList.isEmpty()) {
            WaitingList wl = waitingList.get(0);
            result.setWaitingListID(wl.getListId());
            result.setWaitingListNote(wl.getNote());
            result.setOnWaitingListSinceDate(wl.getOnListSince());
        }

        List<WaitingListName> waitingListNames = waitingListNameDao.findAll(null, null);
        if (waitingListNames != null) {
            List<WaitingListNameTo1> waitingListNameTo1s = new ArrayList<>();
            for (WaitingListName waitingListName : waitingListNames) {
                if (waitingListName.getIsHistory().equals("Y")) continue;

                waitingListNameTo1s.add(waitingListNameConverter.getAsTransferObject(loggedInInfo, waitingListName));
            }
            result.setWaitingListNames(waitingListNameTo1s);
        }

        List<ProfessionalSpecialist> referralDocs = specialistDao.findAll();
        if (referralDocs != null) {
            List<ProfessionalSpecialistTo1> professionalSpecialistTo1s = new ArrayList<>();
            for (ProfessionalSpecialist referralDoc : referralDocs) {
                if (referralDoc != null) {
                    professionalSpecialistTo1s.add(specialistConverter.getAsTransferObject(loggedInInfo, referralDoc));
                }
            }
            result.setReferralDoctors(professionalSpecialistTo1s);
        }

        List<SecUserRole> doctorRoles = secUserRoleDao.getSecUserRolesByRoleName("doctor");
        List<ProviderTo1> providerTo1s = new ArrayList<>();
        if (doctorRoles != null) {
            for (SecUserRole doctor : doctorRoles) {
                Provider provider = providerDao.getProvider(doctor.getProviderNo());
                if (provider != null) {
                    providerTo1s.add(providerConverter.getAsTransferObject(loggedInInfo, provider));
                }
            }
            result.setDoctors(providerTo1s);
        }

        List<SecUserRole> nurseRoles = secUserRoleDao.getSecUserRolesByRoleName("nurse");
        providerTo1s.clear();
        if (nurseRoles != null) {
            for (SecUserRole nurse : nurseRoles) {
                Provider provider = providerDao.getProvider(nurse.getProviderNo());
                if (provider != null) {
                    providerTo1s.add(providerConverter.getAsTransferObject(loggedInInfo, provider));
                }
            }
            result.setNurses(providerTo1s);
        }

        List<SecUserRole> midwifeRoles = secUserRoleDao.getSecUserRolesByRoleName("midwife");
        providerTo1s.clear();
        if (midwifeRoles != null) {
            for (SecUserRole midwife : midwifeRoles) {
                Provider provider = providerDao.getProvider(midwife.getProviderNo());
                if (provider != null) {
                    providerTo1s.add(providerConverter.getAsTransferObject(loggedInInfo, provider));
                }
            }
            result.setMidwives(providerTo1s);
        }

        List<DemographicContact> demoContacts = demographicManager.getDemographicContacts(loggedInInfo, id);
        if (demoContacts != null) {
            List<DemographicContactFewTo1> demographicContactFewTo1s = new ArrayList<>();
            List<DemographicContactFewTo1> demographicContactFewTo1Pros = new ArrayList<>();
            for (DemographicContact demoContact : demoContacts) {
                Integer contactId = Integer.valueOf(demoContact.getContactId());
                DemographicContactFewTo1 demoContactTo1 = new DemographicContactFewTo1();

                if (demoContact.getCategory().equals(DemographicContact.CATEGORY_PERSONAL)) {
                    if (demoContact.getType() == DemographicContact.TYPE_DEMOGRAPHIC) {
                        Demographic contactD = demographicManager.getDemographic(loggedInInfo, contactId);
                        demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactD);
                        if (demoContactTo1.getPhone() == null || demoContactTo1.getPhone().equals("")) {
                            DemographicExt ext = demographicManager.getDemographicExt(loggedInInfo, id, "demo_cell");
                            if (ext != null) demoContactTo1.setPhone(ext.getValue());
                        }
                    } else if (demoContact.getType() == DemographicContact.TYPE_CONTACT) {
                        Contact contactC = contactDao.find(contactId);
                        demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactC);
                    }
                    demographicContactFewTo1s.add(demoContactTo1);
                } else if (demoContact.getCategory().equals(DemographicContact.CATEGORY_PROFESSIONAL)) {
                    if (demoContact.getType() == DemographicContact.TYPE_PROVIDER) {
                        Provider contactP = providerDao.getProvider(contactId.toString());
                        demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactP);
                    } else if (demoContact.getType() == DemographicContact.TYPE_PROFESSIONALSPECIALIST) {
                        ProfessionalSpecialist contactS = specialistDao.find(contactId);
                        demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactS);
                    }
                    demographicContactFewTo1Pros.add(demoContactTo1);
                }
            }
            result.setDemoContacts(demographicContactFewTo1s);
            result.setDemoContactPros(demographicContactFewTo1Pros);
        }

        List<String> patientStatusList = demographicManager.getPatientStatusList();
        List<String> rosterStatusList = demographicManager.getRosterStatusList();
        List<StatusValueTo1> statusValueTo1s = new ArrayList<>();
        if (patientStatusList != null) {
            for (String ps : patientStatusList) {
                StatusValueTo1 value = new StatusValueTo1(ps);
                statusValueTo1s.add(value);
            }
            result.setPatientStatusList(statusValueTo1s);
        }

        statusValueTo1s.clear();
        if (rosterStatusList != null) {
            for (String rs : rosterStatusList) {
                StatusValueTo1 value = new StatusValueTo1(rs);
                statusValueTo1s.add(value);
            }
            result.setRosterStatusList(statusValueTo1s);
        }

        if (include.contains(IncludeType.ALLERGIES.getValue())) {
            result.setAllergies(new AllergyConverter().getAllAsTransferObjects(loggedInInfo, allergyManager.getActiveAllergies(loggedInInfo, demo.getDemographicNo())));
        }

        if (include.contains(IncludeType.MEASUREMENTS.getValue())) {
            List<String> heightType = new ArrayList<>();
            heightType.add(MeasurementType.HEIGHT.getAbbreviation());
            List<String> weightType = new ArrayList<>();
            weightType.add(MeasurementType.WEIGHT.getAbbreviation());
            List<Measurement> heights = measurementManager.getMeasurementByType(loggedInInfo, demo.getDemographicNo(), heightType);
            List<Measurement> weights = measurementManager.getMeasurementByType(loggedInInfo, demo.getDemographicNo(), weightType);
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.YEAR, -3);
            List<Measurement> measurements = measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(loggedInInfo, demo.getDemographicNo(), calendar.getTime());
            //Just send most recent height and weight
            if (!heights.isEmpty() && !measurements.contains(heights.get(0))) {
                measurements.add(heights.get(0));
            }
            if (!weights.isEmpty() && !measurements.contains(weights.get(0))) {
                measurements.add(weights.get(0));
            }
            result.setMeasurements(new MeasurementConverter().getAllAsTransferObjects(loggedInInfo, new ArrayList<>(measurements)));
        }

        if (include.contains(IncludeType.NOTES.getValue())) {
            // Avoid sending out 'concerns' as it is too sensitive to pass
            List<String> cppCodeList = CppCode.toStringList();
            cppCodeList.remove(CppCode.CONCERNS.getCode());
            result.setEncounterNotes(noteManager.getActiveCppNotes(loggedInInfo, demo.getDemographicNo(), cppCodeList.toArray(new String[0])));
        }

        if (include.contains(IncludeType.MEDICATIONS.getValue())) {
            List<String> singleLineMedications = rxManager.getCurrentSingleLineMedications(loggedInInfo, demo.getDemographicNo());
            result.setMedicationSummary(singleLineMedications);
        }

        return result;
    }

    /**
     * Gets basic demographic data.
     *
     * @param id       Id of the demographic to get data for
     * @param includes An array of strings that include additional information in the returned data
     *                 Possible includes are:
     *                 - contacts = includes the DemographicContacts in the results
     * @return Returns data for the demographic provided
     */
    @GET
    @Path("/basic/{dataId}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DemographicTo1 getBasicDemographicData(@PathParam("dataId") Integer id, @QueryParam("includes[]") List<String> includes) throws PatientDirectiveException {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("r", id);
        Demographic demo = demographicManager.getDemographic(loggedInInfo, id);
        if (demo == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("Demographic record not found: " + id).build());
        }

        List<DemographicExt> demoExts = demographicManager.getDemographicExts(loggedInInfo, id);
        if (demoExts != null && !demoExts.isEmpty()) {
            DemographicExt[] demoExtArray = demoExts.toArray(new DemographicExt[demoExts.size()]);
            demo.setExtras(demoExtArray);
        }

        DemographicTo1 result = demoConverter.getAsTransferObject(loggedInInfo, demo);

        DemographicCust demoCust = demographicManager.getDemographicCust(loggedInInfo, id);
        if (demoCust != null) {
            result.setNurse(demoCust.getNurse());
            result.setResident(demoCust.getResident());
            //result.setAlert(demoCust.getBookingAlert());
            result.setMidwife(demoCust.getMidwife());
            result.setNotes(demoCust.getNotes());
        }

        List<WaitingList> waitingList = waitingListDao.search_wlstatus(id);
        if (waitingList != null && !waitingList.isEmpty()) {
            WaitingList wl = waitingList.get(0);
            result.setWaitingListID(wl.getListId());
            result.setWaitingListNote(wl.getNote());
            result.setOnWaitingListSinceDate(wl.getOnListSince());
        }

        List<String> patientStatusList = demographicManager.getPatientStatusList();
        List<String> rosterStatusList = demographicManager.getRosterStatusList();
        List<StatusValueTo1> statusValueTo1s = new ArrayList<>();
        if (patientStatusList != null) {
            for (String ps : patientStatusList) {
                StatusValueTo1 value = new StatusValueTo1(ps);
                statusValueTo1s.add(value);
            }
            result.setPatientStatusList(statusValueTo1s);
        }

        statusValueTo1s.clear();
        if (rosterStatusList != null) {
            for (String rs : rosterStatusList) {
                StatusValueTo1 value = new StatusValueTo1(rs);
                statusValueTo1s.add(value);
            }
            result.setRosterStatusList(statusValueTo1s);
        }

        // If the contacts are included add Demographic Contacts to the basic results (relationships/healthcareteam/etc)
        if (includes.contains(IncludeType.CONTACTS.getValue())) {
            List<DemographicContact> demoContacts = demographicManager.getDemographicContacts(loggedInInfo, id);
            if (demoContacts != null) {
                List<DemographicContactFewTo1> demographicContactFewTo1s = new ArrayList<>();
                List<DemographicContactFewTo1> demographicContactFewTo1Pros = new ArrayList<>();
                for (DemographicContact demoContact : demoContacts) {
                    // Check if 'demoContact' has given consent to be contacted;
                    // if not, skip sharing 'demoContact'.
                    if (!demoContact.isConsentToContact()) {
                        continue;
                    }

                    Integer contactId = Integer.valueOf(demoContact.getContactId());
                    DemographicContactFewTo1 demoContactTo1 = new DemographicContactFewTo1();

                    if (demoContact.getCategory().equals(DemographicContact.CATEGORY_PERSONAL)) {
                        if (demoContact.getType() == DemographicContact.TYPE_DEMOGRAPHIC) {
                            Demographic contactD = demographicManager.getDemographic(loggedInInfo, contactId);
                            demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactD);
                            if (demoContactTo1.getPhone() == null || demoContactTo1.getPhone().equals("")) {
                                DemographicExt ext = demographicManager.getDemographicExt(loggedInInfo, id, "demo_cell");
                                if (ext != null) demoContactTo1.setPhone(ext.getValue());
                            }
                        } else if (demoContact.getType() == DemographicContact.TYPE_CONTACT) {
                            Contact contactC = contactDao.find(contactId);
                            demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactC);
                        }
                        demographicContactFewTo1s.add(demoContactTo1);
                    } else if (demoContact.getCategory().equals(DemographicContact.CATEGORY_PROFESSIONAL)) {
                        if (demoContact.getType() == DemographicContact.TYPE_PROVIDER) {
                            Provider contactP = providerDao.getProvider(contactId.toString());
                            demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactP);
                        } else if (demoContact.getType() == DemographicContact.TYPE_PROFESSIONALSPECIALIST) {
                            ProfessionalSpecialist contactS = specialistDao.find(contactId);
                            demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactS);
                        }
                        demographicContactFewTo1Pros.add(demoContactTo1);
                    }
                }
                result.setDemoContacts(demographicContactFewTo1s);
                result.setDemoContactPros(demographicContactFewTo1Pros);
            }
        }
        return result;
    }

    /**
     * Returns a shorter summary of the Demographic profile.
     * Reduces bandwidth and processing time.
     */
    @GET
    @Path("/summary/{demographicNo}")
    @Produces({MediaType.APPLICATION_JSON})
    public DemographicTo1 getDemographicSummary(@PathParam("demographicNo") Integer demographicNo) {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("r", demographicNo);
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        if (demographic == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("Demographic record not found: " + demographicNo).build());
        }
        DemographicExt demographicExt = demographicManager.getDemographicExt(loggedInInfo, demographicNo, DemographicProperty.demo_cell);
        DemographicTo1 result = demoConverter.getAsTransferObject(loggedInInfo, demographic);
        if (demographicExt != null) {
            result.setAlternativePhone(demographicExt.getValue());
        }

        return result;
    }

    /**
     * Saves demographic information.
     *
     * @param data Detailed demographic data to be saved
     * @return Returns the saved demographic data
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DemographicTo1 createDemographicData(DemographicTo1 data) {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("w");
        if (data == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity("Request body is required").build());
        }
        Demographic demographic = demoConverter.getAsDomainObject(loggedInInfo, data);
        demographicManager.createDemographic(loggedInInfo, demographic, data.getAdmissionProgramId());
        return demoConverter.getAsTransferObject(loggedInInfo, demographic);
    }

    /**
     * Updates demographic information.
     *
     * @param data Detailed demographic data to be updated
     * @return Returns the updated demographic data
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DemographicTo1 updateDemographicData(DemographicTo1 data) {
        if (data == null || data.getDemographicNo() == null) {
            requireDemographicPrivilege("u");
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity("Request body with demographicNo is required").build());
        }
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("u", data.getDemographicNo());
        //update demographiccust
        if (data.getNurse() != null || data.getResident() != null || data.getAlert() != null || data.getMidwife() != null || data.getNotes() != null) {
            DemographicCust demoCust = demographicManager.getDemographicCust(loggedInInfo, data.getDemographicNo());
            if (demoCust == null) {
                demoCust = new DemographicCust();
                demoCust.setId(data.getDemographicNo());
            }
            demoCust.setNurse(data.getNurse());
            demoCust.setResident(data.getResident());
            demoCust.setAlert(data.getAlert());
            demoCust.setMidwife(data.getMidwife());
            demoCust.setNotes(data.getNotes());
            demographicManager.createUpdateDemographicCust(loggedInInfo, demoCust);
        }

        //update waitingList
        if (data.getWaitingListID() != null) {
            WLWaitingListUtil.updateWaitingListRecord(data.getWaitingListID().toString(), data.getWaitingListNote(), data.getDemographicNo().toString(), null);
        }

        Demographic demographic = demoConverter.getAsDomainObject(loggedInInfo, data);
        demographicManager.updateDemographic(loggedInInfo, demographic);

        return demoConverter.getAsTransferObject(loggedInInfo, demographic);
    }

    /**
     * Deletes demographic information.
     *
     * @param id Id of the demographic data to be deleted
     * @return Returns the deleted demographic data
     */
    @DELETE
    @Path("/{dataId}")
    public DemographicTo1 deleteDemographicData(@PathParam("dataId") Integer id) {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("w", id);
        Demographic demo = demographicManager.getDemographic(loggedInInfo, id);
        if (demo == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("Demographic record not found: " + id).build());
        }
        DemographicTo1 result = buildDemographicTo1(loggedInInfo, demo, id, Collections.emptyList());
        demographicManager.deleteDemographic(loggedInInfo, demo);
        return result;
    }

    /**
     * Search demographics - used by navigation of OSCAR webapp
     * <p>
     * Currently supports LastName[,FirstName] and address searches.
     *
     * @param query the search query string
     * @return Returns search results for demographics matching the query
     */
    @GET
    @Path("/quickSearch")
    @Produces("application/json")
    public AbstractSearchResponse<DemographicSearchResult> search(@QueryParam("query") String query) {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("r");

        AbstractSearchResponse<DemographicSearchResult> response = new AbstractSearchResponse<DemographicSearchResult>();

        List<DemographicSearchResult> results = new ArrayList<DemographicSearchResult>();

        if (query == null) {
            return response;
        }

        DemographicSearchRequest req = new DemographicSearchRequest();
        req.setActive(true);
        //caisi
        boolean outOfDomain = true;
        if (CarlosProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1) {
            outOfDomain = false;
        }
        req.setOutOfDomain(outOfDomain);


        if (query.startsWith("addr:")) {
            req.setMode(SEARCHMODE.Address);
            req.setKeyword(query.substring("addr:".length()));
        } else if (query.startsWith("chartNo:")) {
            req.setMode(SEARCHMODE.ChartNo);
            req.setKeyword(query.substring("chartNo:".length()));
        } else {
            req.setMode(SEARCHMODE.Name);
            req.setKeyword(query);
        }


        int count = demographicManager.searchPatientsCount(loggedInInfo, req);

        if (count > 0) {
            results = demographicManager.searchPatients(loggedInInfo, req, 0, 10);
            response.setContent(results);
            response.setTotal(count);
            response.setQuery(query);

        }


        return response;
    }

    @POST
    @Path("/search")
    @Produces("application/json")
    @Consumes("application/json")
    public AbstractSearchResponse<DemographicSearchResult> searchRequest(ObjectNode json,
            @DefaultValue("0") @QueryParam("startIndex") String startIndex,
            @DefaultValue("10") @QueryParam("itemsToReturn") String itemsToReturn) {
        // CXF's Integer query binding reports malformed numbers as 404. Parse here
        // so every invalid search option receives the same 400 client response.
        return search(json, searchInteger(startIndex, 0), searchInteger(itemsToReturn, 10));
    }

    /** Retains the typed entry point for existing Java callers of patient search. */
    public AbstractSearchResponse<DemographicSearchResult> search(ObjectNode json, Integer startIndex, Integer itemsToReturn) {
        LoggedInInfo loggedInInfo = requireDemographicPrivilege("r");

        AbstractSearchResponse<DemographicSearchResult> response = new AbstractSearchResponse<DemographicSearchResult>();

        int offset = startIndex == null ? 0 : startIndex;
        int limit = itemsToReturn == null ? 10 : itemsToReturn;
        if (offset < 0 || limit < 0 || limit > DemographicDao.MAX_SEARCH_RESULT_SIZE) {
            throw new BadRequestException("Invalid patient search pagination");
        }
        DemographicSearchRequest req = convertFromJSON(json);
        //caisi
        boolean outOfDomain = true;
        if (CarlosProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1) {
            outOfDomain = false;
        }
        req.setOutOfDomain(outOfDomain);


        List<DemographicSearchResult> results = new ArrayList<DemographicSearchResult>();

        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {

            int count = demographicManager.searchPatientsCount(loggedInInfo, req);

            if (count > 0) {
                results = demographicManager.searchPatients(loggedInInfo, req, offset, limit);
                response.setContent(results);
                response.setTotal(count);
            }
        }

        return response;
    }

    private DemographicSearchRequest convertFromJSON(ObjectNode json) {
        if (json == null) throw new BadRequestException("Patient search body is required");
        DemographicSearchRequest req = new DemographicSearchRequest();
        req.setMode(searchEnum(SEARCHMODE.class, searchText(json.get("type")), SEARCHMODE.Name));
        JsonNode term = json.get("term");
        // Existing clients may submit numeric demographic/HIN/DOB keywords as JSON numbers.
        req.setKeyword(term != null && term.isNumber() ? term.asText() : searchText(term));
        req.setActive(searchBoolean(json.get("active")));
        // Domain visibility is derived from server configuration in search(), never the client.
        req.setOutOfDomain(searchBoolean(json.get("outofdomain")));
        JsonNode params = json.get("params");
        if (params != null && !params.isNull()) {
            if (!params.isObject()) throw new BadRequestException("Invalid patient search sorting");
            Pattern namePattern = Pattern.compile("sorting\\[(\\w+)\\]");
            var fields = params.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                Matcher nameMatcher = namePattern.matcher(field.getKey());
                if (nameMatcher.matches()) {
                    req.setSortMode(searchEnum(SORTMODE.class, nameMatcher.group(1), null));
                    String direction = searchText(field.getValue());
                    if (direction == null) throw new BadRequestException("Invalid patient search sorting");
                    req.setSortDir(searchEnum(SORTDIR.class, direction, null));
                } else if (field.getKey().startsWith("sorting[")) {
                    throw new BadRequestException("Invalid patient search sorting");
                }
            }
        }
        return req;
    }

    private static int searchInteger(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new BadRequestException("Invalid patient search pagination");
        }
    }

    private static String searchText(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new BadRequestException("Patient search text must be a string");
        return value.textValue();
    }

    private static boolean searchBoolean(JsonNode value) {
        if (value == null || value.isNull()) return false;
        // Preserve clients that send either a JSON boolean or its string equivalent.
        if (value.isBoolean()) return value.booleanValue();
        if (value.isTextual()) {
            if ("true".equalsIgnoreCase(value.textValue())) return true;
            if ("false".equalsIgnoreCase(value.textValue())) return false;
        }
        throw new BadRequestException("Invalid patient search boolean option");
    }

    private static <E extends Enum<E>> E searchEnum(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException _) {
            // Do not echo submitted values: search requests may contain patient information.
            throw new BadRequestException("Invalid patient search option");
        }
    }

}
