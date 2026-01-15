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
package ca.openosp.openo.webserv.rest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.dao.SecUserRoleDao;
import ca.openosp.openo.PMmodule.model.SecUserRole;
import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.caisi_integrator.ws.MatchingDemographicParameters;
import ca.openosp.openo.caisi_integrator.ws.MatchingDemographicTransferScore;
import ca.openosp.openo.commn.dao.ContactDao;
import ca.openosp.openo.commn.dao.ProfessionalSpecialistDao;
import ca.openosp.openo.commn.dao.WaitingListDao;
import ca.openosp.openo.commn.dao.WaitingListNameDao;
import ca.openosp.openo.commn.exception.PatientDirectiveException;
import ca.openosp.openo.commn.model.Contact;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicContact;
import ca.openosp.openo.commn.model.DemographicCust;
import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.commn.model.DemographicExt.DemographicProperty;
import ca.openosp.openo.commn.model.enumerator.CppCode;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.WaitingList;
import ca.openosp.openo.commn.model.WaitingListName;
import ca.openosp.openo.managers.AllergyManager;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.MeasurementManager;
import ca.openosp.openo.managers.NoteManager;
import ca.openosp.openo.managers.RxManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.web.DemographicSearchHelper;
import ca.openosp.openo.webserv.rest.conversion.AllergyConverter;
import ca.openosp.openo.webserv.rest.conversion.DemographicContactFewConverter;
import ca.openosp.openo.webserv.rest.conversion.DemographicConverter;
import ca.openosp.openo.webserv.rest.conversion.MeasurementConverter;
import ca.openosp.openo.webserv.rest.conversion.ProfessionalSpecialistConverter;
import ca.openosp.openo.webserv.rest.conversion.ProviderConverter;
import ca.openosp.openo.webserv.rest.conversion.WaitingListNameConverter;
import ca.openosp.openo.webserv.rest.to.AbstractSearchResponse;
import ca.openosp.openo.webserv.rest.to.OscarSearchResponse;
import ca.openosp.openo.webserv.rest.to.model.DemographicContactFewTo1;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest.SEARCHMODE;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest.SORTDIR;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest.SORTMODE;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchResult;
import ca.openosp.openo.webserv.rest.to.model.DemographicTo1;
import ca.openosp.openo.webserv.rest.to.model.ProfessionalSpecialistTo1;
import ca.openosp.openo.webserv.rest.to.model.ProviderTo1;
import ca.openosp.openo.webserv.rest.to.model.StatusValueTo1;
import ca.openosp.openo.webserv.rest.to.model.WaitingListNameTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.openosp.OscarProperties;
import ca.openosp.openo.waitinglist.util.WLWaitingListUtil;


/**
 * Defines a service contract for main operations on demographic.
 */
@Path("/demographics")
@Component("demographicService")
@Consumes(MediaType.APPLICATION_JSON)
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
        OscarSearchResponse<DemographicTo1> result = new OscarSearchResponse<DemographicTo1>();

        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 0;
        }

        result.setLimit(limit);
        result.setOffset(offset);
        result.setTotal(demographicManager.getActiveDemographicCount(getLoggedInInfo()).intValue());

        for (Demographic demo : demographicManager.getActiveDemographics(getLoggedInInfo(), offset, limit)) {
            result.getContent().add(demoConverter.getAsTransferObject(getLoggedInInfo(), demo));
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
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        Demographic demo = demographicManager.getDemographic(getLoggedInInfo(), id);
        if (demo == null) return null;

        List<DemographicExt> demoExts = demographicManager.getDemographicExts(getLoggedInInfo(), id);
        if (demoExts != null && !demoExts.isEmpty()) {
            DemographicExt[] demoExtArray = demoExts.toArray(new DemographicExt[demoExts.size()]);
            demo.setExtras(demoExtArray);
        }

        DemographicTo1 result = demoConverter.getAsTransferObject(getLoggedInInfo(), demo);

        DemographicCust demoCust = demographicManager.getDemographicCust(getLoggedInInfo(), id);
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

                waitingListNameTo1s.add(waitingListNameConverter.getAsTransferObject(getLoggedInInfo(), waitingListName));
            }
            result.setWaitingListNames(waitingListNameTo1s);
        }

        List<ProfessionalSpecialist> referralDocs = specialistDao.findAll();
        if (referralDocs != null) {
            List<ProfessionalSpecialistTo1> professionalSpecialistTo1s = new ArrayList<>();
            for (ProfessionalSpecialist referralDoc : referralDocs) {
                if (referralDoc != null) {
                    professionalSpecialistTo1s.add(specialistConverter.getAsTransferObject(getLoggedInInfo(), referralDoc));
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
                    providerTo1s.add(providerConverter.getAsTransferObject(getLoggedInInfo(), provider));
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
                    providerTo1s.add(providerConverter.getAsTransferObject(getLoggedInInfo(), provider));
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
                    providerTo1s.add(providerConverter.getAsTransferObject(getLoggedInInfo(), provider));
                }
            }
            result.setMidwives(providerTo1s);
        }

        List<DemographicContact> demoContacts = demographicManager.getDemographicContacts(getLoggedInInfo(), id);
        if (demoContacts != null) {
            List<DemographicContactFewTo1> demographicContactFewTo1s = new ArrayList<>();
            List<DemographicContactFewTo1> demographicContactFewTo1Pros = new ArrayList<>();
            for (DemographicContact demoContact : demoContacts) {
                Integer contactId = Integer.valueOf(demoContact.getContactId());
                DemographicContactFewTo1 demoContactTo1 = new DemographicContactFewTo1();

                if (demoContact.getCategory().equals(DemographicContact.CATEGORY_PERSONAL)) {
                    if (demoContact.getType() == DemographicContact.TYPE_DEMOGRAPHIC) {
                        Demographic contactD = demographicManager.getDemographic(getLoggedInInfo(), contactId);
                        demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactD);
                        if (demoContactTo1.getPhone() == null || demoContactTo1.getPhone().equals("")) {
                            DemographicExt ext = demographicManager.getDemographicExt(getLoggedInInfo(), id, "demo_cell");
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
            result.setAllergies(new AllergyConverter().getAllAsTransferObjects(loggedInInfo, allergyManager.getActiveAllergies(getLoggedInInfo(), demo.getDemographicNo())));
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
        Demographic demo = demographicManager.getDemographic(getLoggedInInfo(), id);
        if (demo == null) return null;

        List<DemographicExt> demoExts = demographicManager.getDemographicExts(getLoggedInInfo(), id);
        if (demoExts != null && !demoExts.isEmpty()) {
            DemographicExt[] demoExtArray = demoExts.toArray(new DemographicExt[demoExts.size()]);
            demo.setExtras(demoExtArray);
        }

        DemographicTo1 result = demoConverter.getAsTransferObject(getLoggedInInfo(), demo);

        DemographicCust demoCust = demographicManager.getDemographicCust(getLoggedInInfo(), id);
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
            List<DemographicContact> demoContacts = demographicManager.getDemographicContacts(getLoggedInInfo(), id);
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
                            Demographic contactD = demographicManager.getDemographic(getLoggedInInfo(), contactId);
                            demoContactTo1 = demoContactFewConverter.getAsTransferObject(demoContact, contactD);
                            if (demoContactTo1.getPhone() == null || demoContactTo1.getPhone().equals("")) {
                                DemographicExt ext = demographicManager.getDemographicExt(getLoggedInInfo(), id, "demo_cell");
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
        Demographic demographic = demographicManager.getDemographic(getLoggedInInfo(), demographicNo);
        DemographicExt demographicExt = demographicManager.getDemographicExt(getLoggedInInfo(), demographicNo, DemographicProperty.demo_cell);
        DemographicTo1 result = demoConverter.getAsTransferObject(getLoggedInInfo(), demographic);
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
        Demographic demographic = demoConverter.getAsDomainObject(getLoggedInInfo(), data);
        demographicManager.createDemographic(getLoggedInInfo(), demographic, data.getAdmissionProgramId());
        return demoConverter.getAsTransferObject(getLoggedInInfo(), demographic);
    }

    /**
     * Updates demographic information.
     *
     * @param data Detailed demographic data to be updated
     * @return Returns the updated demographic data
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public DemographicTo1 updateDemographicData(DemographicTo1 data) {
        //update demographiccust
        if (data.getNurse() != null || data.getResident() != null || data.getAlert() != null || data.getMidwife() != null || data.getNotes() != null) {
            DemographicCust demoCust = demographicManager.getDemographicCust(getLoggedInInfo(), data.getDemographicNo());
            if (demoCust == null) {
                demoCust = new DemographicCust();
                demoCust.setId(data.getDemographicNo());
            }
            demoCust.setNurse(data.getNurse());
            demoCust.setResident(data.getResident());
            demoCust.setAlert(data.getAlert());
            demoCust.setMidwife(data.getMidwife());
            demoCust.setNotes(data.getNotes());
            demographicManager.createUpdateDemographicCust(getLoggedInInfo(), demoCust);
        }

        //update waitingList
        if (data.getWaitingListID() != null) {
            WLWaitingListUtil.updateWaitingListRecord(data.getWaitingListID().toString(), data.getWaitingListNote(), data.getDemographicNo().toString(), null);
        }

        Demographic demographic = demoConverter.getAsDomainObject(getLoggedInInfo(), data);
        demographicManager.updateDemographic(getLoggedInInfo(), demographic);

        return demoConverter.getAsTransferObject(getLoggedInInfo(), demographic);
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
        Demographic demo = demographicManager.getDemographic(getLoggedInInfo(), id);
        DemographicTo1 result = getDemographicData(id, Collections.EMPTY_LIST);
        if (demo == null) {
            throw new IllegalArgumentException("Unable to find demographic record with ID " + id);
        }

        demographicManager.deleteDemographic(getLoggedInInfo(), demo);
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

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_demographic", "r", null)) {
            throw new RuntimeException("Access Denied");
        }

        AbstractSearchResponse<DemographicSearchResult> response = new AbstractSearchResponse<DemographicSearchResult>();

        List<DemographicSearchResult> results = new ArrayList<DemographicSearchResult>();

        if (query == null) {
            return response;
        }

        DemographicSearchRequest req = new DemographicSearchRequest();
        req.setActive(true);
        req.setIntegrator(false); //this should be configurable by persona

        //caisi
        boolean outOfDomain = true;
        if (OscarProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1) {
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


        int count = demographicManager.searchPatientsCount(getLoggedInInfo(), req);

        if (count > 0) {
            results = demographicManager.searchPatients(getLoggedInInfo(), req, 0, 10);
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
    public AbstractSearchResponse<DemographicSearchResult> search(ObjectNode json, @QueryParam("startIndex") Integer startIndex, @QueryParam("itemsToReturn") Integer itemsToReturn) {
        AbstractSearchResponse<DemographicSearchResult> response = new AbstractSearchResponse<DemographicSearchResult>();

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_demographic", "r", null)) {
            throw new RuntimeException("Access Denied");
        }

        DemographicSearchRequest req = convertFromJSON(json);
        //caisi
        boolean outOfDomain = true;
        if (OscarProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1) {
            outOfDomain = false;
        }
        req.setOutOfDomain(outOfDomain);


        List<DemographicSearchResult> results = new ArrayList<DemographicSearchResult>();

        if (json.get("term") != null && json.get("term").asText().length() >= 1) {

            int count = demographicManager.searchPatientsCount(getLoggedInInfo(), req);

            if (count > 0) {
                results = demographicManager.searchPatients(getLoggedInInfo(), req, startIndex, itemsToReturn);
                response.setContent(results);
                response.setTotal(count);
            }
        }

        return response;
    }

    @POST
    @Path("/searchIntegrator")
    @Produces("application/json")
    @Consumes("application/json")
    public AbstractSearchResponse<DemographicSearchResult> searchIntegrator(ObjectNode json, @QueryParam("itemsToReturn") Integer itemsToReturn) {
        AbstractSearchResponse<DemographicSearchResult> response = new AbstractSearchResponse<DemographicSearchResult>();

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_demographic", "r", null)) {
            throw new RuntimeException("Access Denied");
        }

        List<DemographicSearchResult> results = new ArrayList<DemographicSearchResult>();

        if (json.get("term") != null && json.get("term").asText().length() >= 1) {

            MatchingDemographicParameters matches = CaisiIntegratorManager.getMatchingDemographicParameters(getLoggedInInfo(), convertFromJSON(json));
            List<MatchingDemographicTransferScore> integratorSearchResults = null;
            try {
                matches.setMaxEntriesToReturn(itemsToReturn);
                matches.setMinScore(7);
                integratorSearchResults = DemographicSearchHelper.getIntegratedSearchResults(getLoggedInInfo(), matches);
                MiscUtils.getLogger().info("Integrator search results : " + (integratorSearchResults == null ? "null" : String.valueOf(integratorSearchResults.size())));
            } catch (Exception e) {
                MiscUtils.getLogger().error("error searching integrator", e);
            }

            if (integratorSearchResults != null) {
                for (MatchingDemographicTransferScore matchingDemographicTransferScore : integratorSearchResults) {
                    if (isLocal(matchingDemographicTransferScore)) {
                        MiscUtils.getLogger().warn("ignoring remote demographic since we already have them locally");
                        continue;
                    }
                    if (matchingDemographicTransferScore.getDemographicTransfer() != null) {
                        DemographicTransfer obj = matchingDemographicTransferScore.getDemographicTransfer();
                        DemographicSearchResult item = new DemographicSearchResult();
                        item.setLastName(obj.getLastName());
                        item.setFirstName(obj.getFirstName());
                        item.setSex(obj.getGender().toString());
                        item.setDob(obj.getBirthDate().getTime());
                        item.setRemoteFacilityId(obj.getIntegratorFacilityId());
                        item.setDemographicNo(obj.getCaisiDemographicId());
                        results.add(item);
                    }

                }

            }

        }

        response.setContent(results);
        response.setTotal((response.getContent() != null) ? response.getContent().size() : 0);

        return response;
    }

    private DemographicSearchRequest convertFromJSON(ObjectNode json) {
        if (json == null) return null;

        String searchType = json.get("type") != null ? json.get("type").asText() : null;

        DemographicSearchRequest req = new DemographicSearchRequest();

        req.setMode(SEARCHMODE.valueOf(searchType));
        if (req.getMode() == null) {
            req.setMode(SEARCHMODE.Name);
        }

        req.setKeyword(json.get("term") != null ? json.get("term").asText() : null);
        req.setActive(Boolean.valueOf(json.get("active") != null ? json.get("active").asText() : "false"));
        req.setIntegrator(Boolean.valueOf(json.get("integrator") != null ? json.get("integrator").asText() : "false"));
        req.setOutOfDomain(Boolean.valueOf(json.get("outofdomain") != null ? json.get("outofdomain").asText() : "false"));

        Pattern namePtrn = Pattern.compile("sorting\\[(\\w+)\\]");

        ObjectNode params = (ObjectNode) json.get("params");
        if (params != null) {
            java.util.Iterator<String> fieldNames = params.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                Matcher nameMtchr = namePtrn.matcher(key);
                if (nameMtchr.find()) {
                    String var = nameMtchr.group(1);
                    req.setSortMode(SORTMODE.valueOf(var));
                    req.setSortDir(SORTDIR.valueOf(params.get(key).asText()));
                }
            }
        }
        return req;
    }

    private boolean isLocal(MatchingDemographicTransferScore matchingDemographicTransferScore) {
        String hin = matchingDemographicTransferScore.getDemographicTransfer().getHin();

        if (hin != null && !hin.isEmpty()) {
            DemographicSearchRequest dsr = new DemographicSearchRequest();
            dsr.setActive(true);
            dsr.setKeyword(hin);
            dsr.setMode(SEARCHMODE.HIN);
            dsr.setOutOfDomain(true);
            dsr.setSortMode(SORTMODE.Name);
            dsr.setSortDir(SORTDIR.asc);

            if (demographicManager.searchPatientsCount(getLoggedInInfo(), dsr) > 0) {
                return true;
            }
        }

        return false;

    }
}
