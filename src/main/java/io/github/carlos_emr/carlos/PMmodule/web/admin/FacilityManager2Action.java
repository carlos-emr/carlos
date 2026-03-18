/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.web.admin;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.web.FacilityDischargedClients;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.WebUtils;

import io.github.carlos_emr.carlos.log.LogAction;

import io.github.carlos_emr.carlos.services.LookupManager;

/**
 *
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class FacilityManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private AdmissionDao admissionDao;
    private DemographicDao demographicDao;
    private ProgramManager programManager;
    private LookupManager lookupManager;

    private FacilityDao facilityDao;
    private EFormDao eFormDao = (EFormDao) SpringUtils.getBean(EFormDao.class);

    public void setFacilityDao(FacilityDao facilityDao) {
        this.facilityDao = facilityDao;
    }

    private static final String FORWARD_EDIT = "edit";
    private static final String FORWARD_VIEW = "view";
    private static final String FORWARD_LIST = "list";

    private static final String BEAN_FACILITIES = "facilities";
    private static final String BEAN_ASSOCIATED_PROGRAMS = "associatedPrograms";
    private static final String BEAN_ASSOCIATED_CLIENTS = "associatedClients";
    private static final String registrationIntakeName = "Registration Intake";

    public String unspecified() {
        return list();
    }

    public String list() {
        List<Facility> facilities = facilityDao.findAll(true);

        request.setAttribute(BEAN_FACILITIES, facilities);

        // get agency's organization list from the lookup table
        request.setAttribute("orgList", lookupManager.LoadCodeList("OGN", true, null, null));

        // get agency's sector list from the lookup table
        request.setAttribute("sectorList", lookupManager.LoadCodeList("SEC", true, null, null));

        return FORWARD_LIST;
    }

    public String view() {
        String idStr = request.getParameter("id");
        Integer id = Integer.valueOf(idStr);
        Facility facility = facilityDao.find(id);


        this.setFacility(facility);
        this.setRegistrationIntakeForms(eFormDao.getEfromInGroupByGroupName(registrationIntakeName));

        List<FacilityDischargedClients> facilityClients = new ArrayList<FacilityDischargedClients>();

        // Get program list by facility id
        for (Program program : programManager.getPrograms(id)) {
            if (program != null) {
                // Get admission list by program id and automatic_discharge=true

                List<Admission> admissions = admissionDao.getAdmissionsByProgramId(program.getId(), Boolean.valueOf(true), Integer.valueOf(-7));
                if (admissions != null) {
                    Iterator<Admission> it = admissions.iterator();
                    while (it.hasNext()) {

                        Admission admission = it.next();

                        // Get demographic list by demographic_no
                        Demographic client = demographicDao.getClientByDemographicNo(admission.getClientId());

                        String name = client.getFirstName() + " " + client.getLastName();
                        String dob = client.getFormattedDob();
                        String pName = program.getName();
                        Date dischargeDate = admission.getDischargeDate();
                        String dDate = dischargeDate.toString();

                        // today's date
                        Calendar calendar = Calendar.getInstance();

                        // today's date - days
                        calendar.add(Calendar.DAY_OF_YEAR, -1);

                        Date oneDayAgo = calendar.getTime();

                        FacilityDischargedClients fdc = new FacilityDischargedClients();
                        fdc.setName(name);
                        fdc.setDob(dob);
                        fdc.setProgramName(pName);
                        fdc.setDischargeDate(dDate);

                        if (dischargeDate.after(oneDayAgo)) {
                            fdc.setInOneDay(true);
                        } else {
                            fdc.setInOneDay(false);
                        }
                        facilityClients.add(fdc);

                    }
                }
            }
        }
        request.setAttribute(BEAN_ASSOCIATED_CLIENTS, facilityClients);

        request.setAttribute(BEAN_ASSOCIATED_PROGRAMS, programManager.getPrograms(id));

        request.setAttribute("id", facility.getId());

        return FORWARD_VIEW;
    }

    public String edit() {
        String id = request.getParameter("id");
        Facility facility = facilityDao.find(Integer.valueOf(id));

        this.setFacility(facility);
        this.setRegistrationIntakeForms(eFormDao.getEfromInGroupByGroupName(registrationIntakeName));

        request.setAttribute("id", facility.getId());
        request.setAttribute("orgId", facility.getOrgId());
        request.setAttribute("sectorId", facility.getSectorId());

        // get agency's organization list from the lookup table
        request.setAttribute("orgList", lookupManager.LoadCodeList("OGN", true, null, null));

        // get agency's sector list from the lookup table
        request.setAttribute("sectorList", lookupManager.LoadCodeList("SEC", true, null, null));

        return FORWARD_EDIT;
    }

    public String delete() {
        String id = request.getParameter("id");
        Facility facility = facilityDao.find(Integer.valueOf(id));
        facility.setDisabled(true);
        facilityDao.merge(facility);

        return list();
    }

    public String add() {
        Facility facility = new Facility("", "");
        this.setFacility(facility);

        // get agency's organization list from the lookup table
        request.setAttribute("orgList", lookupManager.LoadCodeList("OGN", true, null, null));

        // get agency's sector list from the lookup table
        request.setAttribute("sectorList", lookupManager.LoadCodeList("SEC", true, null, null));

        return FORWARD_EDIT;
    }

    public String save() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Facility facility = this.getFacility();

        if (request.getParameter("facility.hic") == null) facility.setHic(false);

        try {
            facility.setEnableHealthNumberRegistry(WebUtils.isChecked(request, "facility.enableHealthNumberRegistry"));
            facility.setEnableDigitalSignatures(WebUtils.isChecked(request, "facility.enableDigitalSignatures"));

            facility.setEnableAnonymous(WebUtils.isChecked(request, "facility.enableAnonymous"));
            facility.setEnablePhoneEncounter(WebUtils.isChecked(request, "facility.enablePhoneEncounter"));
            facility.setEnableGroupNotes(WebUtils.isChecked(request, "facility.enableGroupNotes"));
            facility.setEnableEncounterTime(WebUtils.isChecked(request, "facility.enableEncounterTime"));
            facility.setEnableEncounterTransportationTime(WebUtils.isChecked(request, "facility.enableEncounterTransportationTime"));
            if (facility.getRegistrationIntake() != null && facility.getRegistrationIntake() < 0)
                facility.setRegistrationIntake(null);

            if (facility.getId() == null || facility.getId() == 0) facilityDao.persist(facility);
            else facilityDao.merge(facility);

            // if we just updated our current facility, refresh local cached data in the session / thread local variable
            if (loggedInInfo.getCurrentFacility().getId().intValue() == facility.getId().intValue()) {
                request.getSession().setAttribute(SessionConstants.CURRENT_FACILITY, facility);
                loggedInInfo.setCurrentFacility(facility);
            }

            addActionMessage(getText("facility.saved", facility.getName()));

            request.setAttribute("id", facility.getId());

            LogAction.log("write", "facility", facility.getId().toString(), request);

            return list();
        } catch (Exception e) {
            addActionMessage(getText("duplicateKey", "The name " + facility.getName()));
            return FORWARD_EDIT;
        }
    }

    public void setAdmissionDao(AdmissionDao admissionDao) {
        this.admissionDao = admissionDao;
    }

    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    public void setLookupManager(LookupManager lookupManager) {
        this.lookupManager = lookupManager;
    }

    public void setProgramManager(ProgramManager mgr) {
        this.programManager = mgr;
    }

    private Facility facility;
    private List<EForm> registrationIntakeForms;

    @StrutsParameter(depth = 1)
    public Facility getFacility() {
        return facility;
    }

    @StrutsParameter
    public void setFacility(Facility facility) {
        this.facility = facility;
    }

    public List<EForm> getRegistrationIntakeForms() {
        return registrationIntakeForms;
    }

    public void setRegistrationIntakeForms(List<EForm> registrationIntakeForms) {
        this.registrationIntakeForms = registrationIntakeForms;
    }
}
