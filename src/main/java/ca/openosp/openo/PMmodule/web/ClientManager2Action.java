//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.PMmodule.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.ws.WebServiceException;

import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.util.DateUtils;
import org.springframework.beans.BeanUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.dao.VacancyDao;
import ca.openosp.openo.PMmodule.dao.VacancyTemplateDao;
import ca.openosp.openo.PMmodule.exception.AdmissionException;
import ca.openosp.openo.PMmodule.exception.AlreadyAdmittedException;
import ca.openosp.openo.PMmodule.exception.AlreadyQueuedException;
import ca.openosp.openo.PMmodule.exception.ClientAlreadyRestrictedException;
import ca.openosp.openo.PMmodule.exception.ProgramFullException;
import ca.openosp.openo.PMmodule.exception.ServiceRestrictionException;
import ca.openosp.openo.PMmodule.model.ClientReferral;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramClientRestriction;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.model.Vacancy;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ClientManager;
import ca.openosp.openo.PMmodule.service.ClientRestrictionManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.service.ProgramQueueManager;
import ca.openosp.openo.PMmodule.service.ProviderManager;
import ca.openosp.openo.PMmodule.web.formbean.ClientManagerFormBean;
import ca.openosp.openo.PMmodule.wlmatch.MatchBO;
import ca.openosp.openo.PMmodule.wlmatch.MatchingManager;
import ca.openosp.openo.PMmodule.wlmatch.VacancyDisplayBO;
import ca.openosp.openo.PMmodule.wlservice.WaitListService;
import ca.openosp.openo.caisi_integrator.ws.CachedAdmission;
import ca.openosp.openo.caisi_integrator.ws.CachedFacility;
import ca.openosp.openo.caisi_integrator.ws.CachedProgram;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdIntegerCompositePk;
import ca.openosp.openo.caisi_integrator.ws.Gender;
import ca.openosp.openo.caisi_integrator.ws.Referral;
import ca.openosp.openo.caisi_integrator.ws.ReferralWs;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.AdmissionDao;
import ca.openosp.openo.commn.dao.CdsClientFormDao;
import ca.openosp.openo.commn.dao.IntegratorConsentDao;
import ca.openosp.openo.commn.dao.OscarLogDao;
import ca.openosp.openo.commn.dao.RemoteReferralDao;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.utility.WebUtils;
import org.springframework.beans.factory.annotation.Required;

import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.demographic.data.DemographicRelationship;

import ca.openosp.openo.services.LookupManager;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ClientManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private ClientRestrictionManager clientRestrictionManager = SpringUtils.getBean(ClientRestrictionManager.class);
    private LookupManager lookupManager = SpringUtils.getBean(LookupManager.class);
    private CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);
    private AdmissionManager admissionManager = SpringUtils.getBean(AdmissionManager.class);
    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);
    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);
    private ProgramQueueManager programQueueManager = SpringUtils.getBean(ProgramQueueManager.class);
    private IntegratorConsentDao integratorConsentDao = SpringUtils.getBean(IntegratorConsentDao.class);
    private CdsClientFormDao cdsClientFormDao = SpringUtils.getBean(CdsClientFormDao.class);
    private static AdmissionDao admissionDao = SpringUtils.getBean(AdmissionDao.class);
    private static ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    private static ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
    private RemoteReferralDao remoteReferralDao = SpringUtils.getBean(RemoteReferralDao.class);
    private VacancyDao vacancyDao = SpringUtils.getBean(VacancyDao.class);
    private VacancyTemplateDao vacancyTemplateDao = SpringUtils.getBean(VacancyTemplateDao.class);
    private MatchingManager matchingManager = new MatchingManager();



    public void setIntegratorConsentDao(IntegratorConsentDao integratorConsentDao) {
        this.integratorConsentDao = integratorConsentDao;
    }


    public void setCdsClientFormDao(CdsClientFormDao cdsClientFormDao) {
        this.cdsClientFormDao = cdsClientFormDao;
    }

    public String execute() {
        String method = request.getParameter("method");
        if ("admit".equals(method)) {
            return admit();
        } else if ("admit_select_program".equals(method)) {
            return admit_select_program();
        } else if ("cancel".equals(method)) {
            return cancel();
        } else if ("discharge".equals(method)) {
            return discharge();
        } else if ("discharge_community".equals(method)) {
            return discharge_community();
        } else if ("discharge_community_select_program".equals(method)) {
            return discharge_community_select_program();
        } else if ("nested_discharge_community_select_program".equals(method)) {
            return nested_discharge_community_select_program();
        } else if ("discharge_select_program".equals(method)) {
            return discharge_select_program();
        } else if ("nested_discharge_select_program".equals(method)) {
            return nested_discharge_select_program();
        } else if ("getGeneralFormsReport".equals(method)) {
            return getGeneralFormsReport();
        } else if ("edit".equals(method)) {
            return edit();
        } else if ("getLinks".equals(method)) {
            return getLinks();
        } else if ("refer".equals(method)) {
            return refer();
        } else if ("refer_select_program".equals(method)) {
            return refer_select_program();
        } else if ("vacancy_refer_select_program".equals(method)) {
            return vacancy_refer_select_program();
        } else if ("service_restrict".equals(method)) {
            return service_restrict();
        } else if ("restrict_select_program".equals(method)) {
            return restrict_select_program();
        } else if ("terminate_early".equals(method)) {
            return terminate_early();
        } else if ("override_restriction".equals(method)) {
            return override_restriction();
        } else if ("save".equals(method)) {
            return save();
        } else if ("save_joint_admission".equals(method)) {
            return save_joint_admission();
        } else if ("remove_joint_admission".equals(method)) {
            return remove_joint_admission();
        } else if ("search_programs".equals(method)) {
            return search_programs();
        } else if ("update".equals(method)) {
            return update();
        }  else if ("view_referral".equals(method)) {
            return view_referral();
        } else if ("view_admission".equals(method)) {
            return view_admission();
        }
        this.setView(new ClientManagerFormBean());

        return edit();
    }

    public String admit() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Admission admission = this.getAdmission();
        Program program = this.getProgram();
        String demographicNo = request.getParameter("id");

        Program fullProgram = programManager.getProgram(String.valueOf(program.getId()));

        try {
            admissionManager.processAdmission(Integer.valueOf(demographicNo), loggedInInfo.getLoggedInProviderNo(), fullProgram, admission.getDischargeNotes(), admission.getAdmissionNotes(), admission.isTemporaryAdmission());
        } catch (ProgramFullException e) {
            addActionMessage(getText("admit.error", "Program is full."));
        } catch (AdmissionException e) {
            addActionMessage(getText("admit.error", e.getMessage()));
        } catch (ServiceRestrictionException e) {
            addActionMessage(getText("admit.service_restricted", new String[]{e.getRestriction().getComments(), e.getRestriction().getProvider().getFormattedName()}));
        }

        LogAction.log("write", "admit", demographicNo, request);

        setEditAttributes(request, demographicNo);
        return "edit";
    }

    public String admit_select_program() {

        Program program = this.getProgram();
        String demographicNo = request.getParameter("id");
        setEditAttributes(request, demographicNo);

        program = programManager.getProgram(program.getId());

        request.setAttribute("do_admit", Boolean.valueOf(true));

        return "edit";
    }

    public String cancel() {

        Admission admission = this.getAdmission();
        admission.setDischargeNotes("");
        admission.setRadioDischargeReason("");

        this.setView(new ClientManagerFormBean());
        return edit();
    }

    public String discharge() {
        Admission admission = this.getAdmission();
        Program p = this.getProgram();
        String id = request.getParameter("id");
        List<Integer> dependents = clientManager.getDependentsList(Integer.valueOf(id));
        String formattedDischargeDate = request.getParameter("dischargeDate");
        Date dischargeDate = DateUtils.toDate(formattedDischargeDate);
        boolean success = true;

        try {
            admissionManager.processDischarge(p.getId(), Integer.valueOf(id), admission.getDischargeNotes(), admission.getRadioDischargeReason(), dischargeDate, dependents, false, false);
        } catch (AdmissionException e) {
            addActionMessage(getText("discharge.failure", e.getMessage()));
            success = false;
        }

        if (success) {
            addActionMessage(getText("discharge.success"));
            LogAction.log("write", "discharge", id, request);
        }

        setEditAttributes(request, id);
        admission.setDischargeNotes("");
        admission.setRadioDischargeReason("");
        admission.setDischargeDate(new Date());
        return "edit";
    }

    public String discharge_community() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Admission admission = this.getAdmission();
        Program program = this.getProgram();
        String clientId = request.getParameter("id");
        List<Integer> dependents = clientManager.getDependentsList(Integer.valueOf(clientId));

        try {
            admissionManager.processDischargeToCommunity(program.getId(), Integer.valueOf(clientId), loggedInInfo.getLoggedInProviderNo(), admission.getDischargeNotes(), admission.getRadioDischargeReason(), dependents, null);
            LogAction.log("write", "discharge", clientId, request);
            addActionMessage(getText("discharge.success"));
        } catch (AdmissionException e) {
            addActionMessage(getText("discharge.failure", e.getMessage()));
        }

        setEditAttributes(request, clientId);
        admission.setDischargeNotes("");
        admission.setRadioDischargeReason("");

        return "edit";
    }

    public String discharge_community_select_program() {
        String id = request.getParameter("id");

        setEditAttributes(request, id);

        Admission admission = null;
        if (admission != null) {
            request.setAttribute("admissionDate", admission.getAdmissionDate("yyyy-MM-dd"));
        }


        request.setAttribute("do_discharge", Boolean.valueOf(true));
        request.setAttribute("community_discharge", Boolean.valueOf(true));
        return "edit";
    }

    public String nested_discharge_community_select_program() {
        request.setAttribute("nestedReason", "true");
        return discharge_community_select_program();
    }

    public String discharge_select_program() {
        String id = request.getParameter("id");
        String admissionId = request.getParameter("admission.id");
        
        Program program = this.getProgram();
        request.setAttribute("programId", String.valueOf(program.getId()));
        setEditAttributes(request, id);

        request.setAttribute("do_discharge", Boolean.valueOf(true));

        if (admissionId != null) {
            Admission admission = admissionDao.find(Integer.parseInt(admissionId));
            if (admission != null) {
                request.setAttribute("admissionDate", admission.getAdmissionDate("yyyy-MM-dd"));
            }
        }

        return "edit";
    }

    public String nested_discharge_select_program() {
        request.setAttribute("nestedReason", "true");
        setEditAttributes(request, request.getParameter("id"));
        request.setAttribute("do_discharge", Boolean.valueOf(true));
        return "edit";
    }

    public String getGeneralFormsReport() {
        // Intake functionality removed
        request.setAttribute("generalIntakeNodes", new ArrayList<>());

        return "generalFormsReport";
    }

    public String edit() {
        String id = request.getParameter("id");

        if (id == null || id.equals("")) {
            Object o = request.getAttribute("demographicNo");

            if (o instanceof String) {
                id = (String) o;
            }

            if (o instanceof Long) {
                id = String.valueOf(o);
            }
        }

        setEditAttributes(request, id);

        LogAction.log("read", "pmm client record", id, request);

        String roles = (String) request.getSession().getAttribute("userrole");

        // for Vaccine Provider
        if (roles.indexOf("Vaccine Provider") != -1) {
            try {
                response.sendRedirect(request.getContextPath() + "/VaccineProviderReport.do?id=" + id);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        Demographic demographic = clientManager.getClientByDemographicNo(id);
        request.getSession().setAttribute("clientGender", demographic.getSex());
        request.getSession().setAttribute("clientAge", demographic.getAge());
        request.getSession().setAttribute("demographicId", demographic.getDemographicNo());

        return "edit";
    }

    public String getLinks() {
        return "links";
    }

    public String refer() {
        ClientReferral referral = this.getReferral();

        int clientId = Integer.parseInt(request.getParameter("id"));
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Program p1 = this.getProgram();

        Integer selectVacancyId = p1.getVacancyId();
        int programId = p1.getId();
        // if it's local
        if (programId != 0) {
            Program p = programManager.getProgram(programId);
            referral.setClientId((long) clientId);
            referral.setProgramId((long) programId);
            referral.setProviderNo(loggedInInfo.getLoggedInProviderNo());

            referral.setFacilityId(loggedInInfo.getCurrentFacility().getId());

            referral.setReferralDate(new Date());
            referral.setProgramType(p.getType());
            ClientManagerFormBean tabBean = this.getView();
            if (tabBean.getTab().equals("Refer to vacancy")) {
                //p = getMatchVacancy(p); //???????
                if (selectVacancyId != null) {
                    referral.setVacancyId(selectVacancyId);
                    referral.setSelectVacancy(vacancyDao.getVacancyById(selectVacancyId).getName());
                }

            } else {
                String vacancyId = request.getParameter("vacancyId");
                if (vacancyId == null || vacancyId.trim().length() == 0) {
                    referral.setSelectVacancy("none");
                } else {
                    Vacancy v = null;
                    try {
                        v = vacancyDao.getVacancyById(Integer.parseInt(vacancyId.trim()));
                    } catch (Exception e) {
                        MiscUtils.getLogger().error("error", e);
                    }
                    if (v != null) {
                        referral.setVacancyId(Integer.parseInt(vacancyId.trim()));
                        referral.setSelectVacancy(v.getName());
                    }
                }
            }

            referToLocalAgencyProgram(request, referral, p);
        }
        // remote referral
        else if (referral.getRemoteFacilityId() != null && referral.getRemoteProgramId() != null) {
            try {
                int remoteFacilityId = Integer.parseInt(referral.getRemoteFacilityId());
                int remoteProgramId = Integer.parseInt(referral.getRemoteProgramId());

                Referral integratorReferral = new Referral();
                integratorReferral.setDestinationIntegratorFacilityId(remoteFacilityId);
                integratorReferral.setDestinationCaisiProgramId(remoteProgramId);
                integratorReferral.setPresentingProblem(referral.getPresentProblems());
                integratorReferral.setReasonForReferral(referral.getNotes());
                integratorReferral.setSourceCaisiDemographicId(clientId);
                integratorReferral.setSourceCaisiProviderId(loggedInInfo.getLoggedInProviderNo());

                ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                referralWs.makeReferral(integratorReferral);

                // save local copy
                RemoteReferral remoteReferral = new RemoteReferral();
                remoteReferral.setFacilityId(loggedInInfo.getCurrentFacility().getId());
                remoteReferral.setDemographicId(clientId);
                remoteReferral.setPresentingProblem(referral.getPresentProblems());
                remoteReferral.setReasonForReferral(referral.getNotes());
                remoteReferral.setReferalDate(new GregorianCalendar());

                CachedFacility cachedFacility = CaisiIntegratorManager.getRemoteFacility(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteFacilityId);
                remoteReferral.setReferredToFacilityName(cachedFacility.getName());

                FacilityIdIntegerCompositePk remoteProgramCompositeKey = new FacilityIdIntegerCompositePk();
                remoteProgramCompositeKey.setIntegratorFacilityId(remoteFacilityId);
                remoteProgramCompositeKey.setCaisiItemId(remoteProgramId);
                CachedProgram cachedProgram = CaisiIntegratorManager.getRemoteProgram(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteProgramCompositeKey);
                remoteReferral.setReferredToProgramName(cachedProgram.getName());

                remoteReferral.setReferringProviderNo(loggedInInfo.getLoggedInProviderNo());
                remoteReferralDao.persist(remoteReferral);
            } catch (Exception e) {
                WebUtils.addErrorMessage(request.getSession(), "Error processing referral : " + e.getMessage());
                logger.error("Unexpected Error.", e);
            }
        }

        setEditAttributes(request, String.valueOf(clientId));
        this.setProgram(new Program());
        this.setReferral(new ClientReferral());

        return "edit";
    }

    private void referToLocalAgencyProgram(HttpServletRequest request, ClientReferral referral, Program p) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Program program = programManager.getProgram(p.getId());

        referral.setStatus(ClientReferral.STATUS_ACTIVE);

        boolean success = true;
        try {
            clientManager.processReferral(referral);
        } catch (AlreadyAdmittedException e) {
            addActionMessage(getText("refer.already_admitted"));
            success = false;
        } catch (AlreadyQueuedException e) {
            addActionMessage(getText("refer.already_referred"));
            success = false;
        } catch (ServiceRestrictionException e) {
            addActionMessage(getText("refer.service_restricted", new String[]{e.getRestriction().getComments(), e.getRestriction().getProvider().getFormattedName()}));

            // store this for display
            this.setServiceRestriction(e.getRestriction());

            // going to need this in case of override
            this.setReferral(referral);

            // store permission
            request.setAttribute("hasOverridePermission", caseManagementManager.hasAccessRight("Service restriction override on referral", "access", loggedInInfo.getLoggedInProviderNo(), String.valueOf(referral.getClientId()), "" + program.getId()));

            // jump to service restriction error page to allow overrides, etc.
            // return "service_restriction_error";
        }

        if (success) {
            addActionMessage(getText("refer.success"));
        }

        LogAction.log("write", "referral", String.valueOf(referral.getClientId()), request);
    }

    public String refer_select_program() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Program p = this.getProgram();
        ClientReferral r = getReferral();
        String id = request.getParameter("id");
        setEditAttributes(request, id);

        // if it's a local referral
        long programId = p.getId();
        if (programId != 0) {
            Program program = programManager.getProgram(programId);
            p.setName(program.getName());
            request.setAttribute("program", program);
        }
        // if it's a remote referal
        else if (r.getRemoteFacilityId() != null && r.getRemoteProgramId() != null) {
            try {
                FacilityIdIntegerCompositePk pk = new FacilityIdIntegerCompositePk();
                pk.setIntegratorFacilityId(Integer.parseInt(r.getRemoteFacilityId()));
                pk.setCaisiItemId(Integer.parseInt(r.getRemoteProgramId()));
                CachedProgram cachedProgram = CaisiIntegratorManager.getRemoteProgram(loggedInInfo, loggedInInfo.getCurrentFacility(), pk);

                p.setName(cachedProgram.getName());

                Program program = new Program();
                BeanUtils.copyProperties(cachedProgram, program);

                request.setAttribute("program", program);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        request.setAttribute("do_refer", true);
        request.setAttribute("temporaryAdmission", programManager.getEnabled());

        return "edit";
    }

    public String vacancy_refer_select_program() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Program p = this.getProgram();
        ClientReferral r = this.getReferral();
        r.setSelectVacancy(request.getParameter("vacancyName"));
        this.setReferral(r);
        String id = request.getParameter("id");
        setEditAttributes(request, id);

        // if it's a local referral
        long programId = p.getId();
        if (programId != 0) {
            Program program = programManager.getProgram(programId);
            p.setName(program.getName());
            p.setVacancyName(request.getParameter("vacancyName"));
            p.setVacancyId(Integer.valueOf(request.getParameter("vacancyId")));
            request.setAttribute("program", program);
        }
        // if it's a remote referal
        else if (r.getRemoteFacilityId() != null && r.getRemoteProgramId() != null) {
            try {
                FacilityIdIntegerCompositePk pk = new FacilityIdIntegerCompositePk();
                pk.setIntegratorFacilityId(Integer.parseInt(r.getRemoteFacilityId()));
                pk.setCaisiItemId(Integer.parseInt(r.getRemoteProgramId()));
                CachedProgram cachedProgram = CaisiIntegratorManager.getRemoteProgram(loggedInInfo, loggedInInfo.getCurrentFacility(), pk);

                p.setName(cachedProgram.getName());

                Program program = new Program();
                BeanUtils.copyProperties(cachedProgram, program);

                request.setAttribute("program", program);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        request.setAttribute("do_refer", true);
        request.setAttribute("temporaryAdmission", programManager.getEnabled());

        //return "edit";
        return "refer_vacancy";
    }

    public String service_restrict() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        ProgramClientRestriction restriction = this.getServiceRestriction();
        Integer days = this.getServiceRestrictionLength();

        Program p = this.getProgram();
        String id = request.getParameter("id");

        restriction.setProgramId(p.getId());
        restriction.setDemographicNo(Integer.valueOf(id));
        restriction.setStartDate(new Date());
        restriction.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        Calendar cal = new GregorianCalendar();
        cal.setTime(new Date());
        cal.set(Calendar.HOUR, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.DATE, cal.get(Calendar.DATE) + days);
        restriction.setEndDate(cal.getTime());
        restriction.setEnabled(true);

        boolean success;
        try {
            clientRestrictionManager.saveClientRestriction(restriction);
            success = true;
        } catch (ClientAlreadyRestrictedException e) {
            addActionMessage(getText("restrict.already_restricted"));
            success = false;
        }

        if (success) {
            addActionMessage(getText("restrict.success"));
        }
        this.setProgram(new Program());
        this.setServiceRestriction(new ProgramClientRestriction());
        this.setServiceRestrictionLength(null);

        Facility facility = (Facility) request.getSession().getAttribute("currentFacility");
        if (facility != null) {
            request.setAttribute("serviceRestrictions", clientRestrictionManager.getActiveRestrictionsForClient(Integer.valueOf(id), facility.getId(), new Date()));
        } else {
            request.setAttribute("serviceRestrictions", clientRestrictionManager.getActiveRestrictionsForClient(Integer.valueOf(id), 0, new Date()));
        }

        setEditAttributes(request, id);
        LogAction.log("write", "service_restriction", id, request);

        return "edit";
    }

    public String restrict_select_program() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Program p = this.getProgram();
        String id = request.getParameter("id");
        setEditAttributes(request, id);

        Program program = programManager.getProgram(p.getId());
        p.setName(program.getName());

        request.setAttribute("do_restrict", true);
        request.setAttribute("can_restrict", caseManagementManager.hasAccessRight("Create service restriction", "access", loggedInInfo.getLoggedInProviderNo(), id, "" + p.getId()));
        request.setAttribute("program", program);

        return "edit";
    }

    public String terminate_early() {

        int programClientRestrictionId = Integer.parseInt(request.getParameter("restrictionId"));
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        clientRestrictionManager.terminateEarly(programClientRestrictionId, loggedInInfo.getLoggedInProviderNo());

        return edit();
    }

    public String override_restriction() {
        ProgramClientRestriction restriction = this.getServiceRestriction();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        ClientReferral referral = this.getReferral();

        if (!caseManagementManager.hasAccessRight("Service restriction override on referral", "access", loggedInInfo.getLoggedInProviderNo(), "" + restriction.getDemographicNo(), "" + restriction.getProgramId())) {
            this.setReferral(new ClientReferral());
            setEditAttributes(request, "" + referral.getClientId());

            return "edit";
        }

        boolean success = true;
        try {
            clientManager.processReferral(referral, true);
        } catch (AlreadyAdmittedException e) {
            addActionMessage(getText("refer.already_admitted"));
            success = false;
        } catch (AlreadyQueuedException e) {
            addActionMessage(getText("refer.already_referred"));
            success = false;
        } catch (ServiceRestrictionException e) {
            throw new RuntimeException("service restriction encountered during override");
        }

        if (success) {
            addActionMessage(getText("refer.success"));
        }
        this.setProgram(new Program());
        this.setReferral(new ClientReferral());
        setEditAttributes(request, "" + referral.getClientId());
        LogAction.log("write", "referral", "" + referral.getClientId(), request);

        return "edit";
    }


    public String save() {
        return edit();
    }



    public String save_joint_admission() {
        JointAdmission jadmission = new JointAdmission();

        String headClientId = request.getParameter("headClientId");
        String clientId = request.getParameter("dependentClientId");
        String type = request.getParameter("type");
        Integer headInteger = Integer.valueOf(headClientId);
        Integer clientInteger = Integer.valueOf(clientId);

        jadmission.setAdmissionDate(new Date());
        jadmission.setHeadClientId(headInteger);
        jadmission.setArchived(false);
        jadmission.setClientId(clientInteger);
        jadmission.setProviderNo((String) request.getSession().getAttribute("user"));
        jadmission.setTypeId(Integer.valueOf(type));
        clientManager.saveJointAdmission(jadmission);
        setEditAttributes(request, request.getParameter("clientId"));

        return "edit";
    }

    public String remove_joint_admission() {
        String clientId = request.getParameter("dependentClientId");
        clientManager.removeJointAdmission(Integer.valueOf(clientId), (String) request.getSession().getAttribute("user"));
        setEditAttributes(request, request.getParameter("clientId"));
        return "edit";
    }

    public String search_programs() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);


        Program criteria = this.getProgram();
        List<Program> programs = programManager.search(criteria);
        request.setAttribute("programs", programs);

        if (CaisiIntegratorManager.isEnableIntegratedReferrals(loggedInInfo.getCurrentFacility())) {
            try {
                List<CachedProgram> results = CaisiIntegratorManager.getRemoteProgramsAcceptingReferrals(loggedInInfo, loggedInInfo.getCurrentFacility());

                filterResultsByCriteria(results, criteria);

                removeCommunityPrograms(results);

                request.setAttribute("remotePrograms", results);
            } catch (MalformedURLException e) {
                logger.error("unexpected error", e);
            } catch (WebServiceException e) {
                logger.error("unexpected error", e);
            }
        }

        ProgramUtils.addProgramRestrictions(request);

        return "search_programs";
    }

    private void removeCommunityPrograms(List<CachedProgram> results) {
        Iterator<CachedProgram> it = results.iterator();
        while (it.hasNext()) {
            CachedProgram cachedProgram = it.next();
            if ("community".equals(cachedProgram.getType())) it.remove();
        }
    }

    private void filterResultsByCriteria(List<CachedProgram> results, Program criteria) {

        Iterator<CachedProgram> it = results.iterator();
        while (it.hasNext()) {
            CachedProgram cachedProgram = it.next();
            String temp = StringUtils.trimToNull(criteria.getName());
            if (temp != null) {
                if (!cachedProgram.getName().toLowerCase().contains(temp.toLowerCase())) {
                    it.remove();
                    continue;
                }
            }

            temp = StringUtils.trimToNull(criteria.getType());
            if (temp != null) {
                if (!cachedProgram.getType().equals(temp)) {
                    it.remove();
                    continue;
                }
            }

            temp = StringUtils.trimToNull(criteria.getManOrWoman());
            if (temp != null) {
                if (cachedProgram.getGender() != null && !cachedProgram.getGender().name().equals(temp.toUpperCase())) {
                    it.remove();
                    continue;
                }
            }

            if (criteria.isTransgender() && cachedProgram.getGender() != Gender.T) {
                it.remove();
                continue;
            }

            if (criteria.isFirstNation() && !cachedProgram.isFirstNation()) {
                it.remove();
                continue;
            }


            if (criteria.isAlcohol() && !cachedProgram.isAlcohol()) {
                it.remove();
                continue;
            }

            temp = StringUtils.trimToNull(criteria.getAbstinenceSupport());
            if (temp != null) {
                if (cachedProgram.getAbstinenceSupport() != null && !cachedProgram.getAbstinenceSupport().equals(temp)) {
                    it.remove();
                    continue;
                }
            }

            if (criteria.isPhysicalHealth() && !cachedProgram.isPhysicalHealth()) {
                it.remove();
                continue;
            }

            if (criteria.isMentalHealth() && !cachedProgram.isMentalHealth()) {
                it.remove();
                continue;
            }

            if (criteria.isHousing() && !cachedProgram.isHousing()) {
                it.remove();
                continue;
            }
        }
    }


    public String update() {
        return edit();
    }

    public String view_referral() {
        String referralId = request.getParameter("referralId");
        ClientReferral referral = clientManager.getClientReferral(referralId);
        Demographic client = clientManager.getClientByDemographicNo("" + referral.getClientId());

        String providerNo = referral.getProviderNo();
        Provider provider = providerManager.getProvider(providerNo);


        this.setReferral(referral);
        this.setClient(client);
        this.setProvider(provider);
        OscarLogDao logDao = SpringUtils.getBean(OscarLogDao.class);
        List<OscarLog> logs = logDao.findByActionAndData("update_referral_date", referralId);
        if (logs.size() > 0)
            request.setAttribute("referral_date_updates", logs);

        logs = logDao.findByActionAndData("update_completion_date", referralId);
        if (logs.size() > 0)
            request.setAttribute("completion_date_updates", logs);


        return "view_referral";
    }

    public String view_admission() {
        String admissionId = request.getParameter("admissionId");
        Admission admission = admissionManager.getAdmission(Long.valueOf(admissionId));
        Demographic client = clientManager.getClientByDemographicNo("" + admission.getClientId());
        String providerNo = admission.getProviderNo();
        Provider provider = providerManager.getProvider(providerNo);


        this.setAdmission(admission);
        this.setClient(client);
        this.setProvider(provider);
        OscarLogDao logDao = SpringUtils.getBean(OscarLogDao.class);
        List<OscarLog> logs = logDao.findByActionAndData("update_admission_date", admissionId);
        if (logs.size() > 0)
            request.setAttribute("admission_date_updates", logs);

        logs = logDao.findByActionAndData("update_discharge_date", admissionId);
        if (logs.size() > 0)
            request.setAttribute("discharge_date_updates", logs);


        return "view_admission";
    }

    private boolean isInDomain(long programId, List<?> programDomain) {
        for (int x = 0; x < programDomain.size(); x++) {
            ProgramProvider p = (ProgramProvider) programDomain.get(x);

            if (p.getProgramId().longValue() == programId) {
                return true;
            }
        }

        return false;
    }

	/*
    private Program getMatchVacancy(Program p){

        List<VacancyDisplayBO> vacancyDisplayBOs = matchingManager.listNoOfVacanciesForWaitListProgram();

        Program program = p;
        for(int j=0;j<vacancyDisplayBOs.size();j++){
            if(vacancyDisplayBOs.get(j).getProgramId().equals(program.getId())){
                if(vacancyDisplayBOs.get(j).getNoOfVacancy() != 0){
                    program.setNoOfVacancy(vacancyDisplayBOs.get(j).getNoOfVacancy());
                    program.setVacancyName(vacancyDisplayBOs.get(j).getVacancyName());
                    program.setDateCreated(vacancyDisplayBOs.get(j).getCreated().toString());
                    int vacancyId = vacancyDisplayBOs.get(j).getVacancyID();
                    List<MatchBO> matchList= matchingManager.getClientMatches(vacancyId);
                    double percentageMatch = 0;
                    for(int k=0;k<matchList.size();k++){
                        percentageMatch = percentageMatch + matchList.get(k).getPercentageMatch();
                    }
                    program.setVacancyId(vacancyId);
                    program.setMatches(percentageMatch);
                }
            }
        }
        return program;
    }
    */

    private void setEditAttributes(HttpServletRequest request, String demographicNo) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        Integer facilityId = loggedInInfo.getCurrentFacility().getId();
        ClientManagerFormBean tabBean = this.getView();
        Integer demographicId = Integer.valueOf(demographicNo);

        request.setAttribute("id", demographicNo);
        request.setAttribute("client", clientManager.getClientByDemographicNo(demographicNo));

        // program domain
        List<Program> programDomain = new ArrayList<Program>();

        for (Iterator<?> i = providerManager.getProgramDomain(providerNo).iterator(); i.hasNext(); ) {
            ProgramProvider programProvider = (ProgramProvider) i.next();
            programDomain.add(programManager.getProgram(programProvider.getProgramId()));
        }

        request.setAttribute("programDomain", programDomain);

        // check role permission
        HttpSession se = request.getSession();
        List admissions = admissionManager.getCurrentAdmissions(Integer.valueOf(demographicNo));
        for (Iterator it = admissions.iterator(); it.hasNext(); ) {
            Admission admission = (Admission) it.next();
            String inProgramId = String.valueOf(admission.getProgramId());
            String inProgramType = admission.getProgramType();
            if ("service".equalsIgnoreCase(inProgramType)) {
                se.setAttribute("performDischargeService", Boolean.valueOf(caseManagementManager.hasAccessRight("perform discharges", "access", providerNo, demographicNo, inProgramId)));
                se.setAttribute("performAdmissionService", Boolean.valueOf(caseManagementManager.hasAccessRight("perform admissions", "access", providerNo, demographicNo, inProgramId)));

            }
        }

        String tabOverride = (String) request.getAttribute("tab.override");

        if (tabOverride != null && tabOverride.length() > 0) {
            tabBean.setTab(tabOverride);
        }

        if (tabBean.getTab().equals("Summary")) {

            // request.setAttribute("admissions", admissionManager.getCurrentAdmissions(Integer.valueOf(demographicNo)));
            // only allow bed/service programs show up.(not external program)
            List<Admission> currentAdmissionList = admissionManager.getCurrentAdmissionsByFacility(demographicId, facilityId);
            ArrayList<AdmissionForDisplay> admissionList = new ArrayList<AdmissionForDisplay>();
            for (Admission admission1 : currentAdmissionList) {
                if (!"External".equalsIgnoreCase(programManager.getProgram(admission1.getProgramId()).getType())) {
                    admissionList.add(new AdmissionForDisplay(admission1));
                }
            }
            addRemoteAdmissions(loggedInInfo, admissionList, demographicId);
            request.setAttribute("admissions", admissionList);

            // Intake functionality removed
            request.setAttribute("mostRecentQuickIntake", null);


            request.setAttribute("referrals", getReferralsForSummary(loggedInInfo, Integer.parseInt(demographicNo), facilityId));


            // CDS
            populateCdsData(request, Integer.parseInt(demographicNo), facilityId);
        }

        /* history */
        if (tabBean.getTab().equals("History")) {
            ArrayList<AdmissionForDisplay> allResults = new ArrayList<AdmissionForDisplay>();

            List<Admission> addLocalAdmissions = admissionManager.getAdmissionsByFacility(demographicId, facilityId);
            for (Admission admission : addLocalAdmissions)
                allResults.add(new AdmissionForDisplay(admission));

            addRemoteAdmissions(loggedInInfo, allResults, demographicId);

            request.setAttribute("admissionHistory", allResults);
            request.setAttribute("referralHistory", getReferralsForHistory(loggedInInfo, demographicId, facilityId));
        }

        List<?> currentAdmissions = admissionManager.getCurrentAdmissions(Integer.valueOf(demographicNo));

        for (int x = 0; x < currentAdmissions.size(); x++) {
            Admission admission = (Admission) currentAdmissions.get(x);

            if (isInDomain(admission.getProgramId().longValue(), providerManager.getProgramDomain(providerNo))) {
                request.setAttribute("isInProgramDomain", Boolean.TRUE);
                break;
            }
        }

        /* forms */
        if (tabBean.getTab().equals("Forms")) {
            // Intake functionality removed
            request.setAttribute("regIntakes", new ArrayList<>());
            request.setAttribute("quickIntakes", new ArrayList<>());
            request.setAttribute("indepthIntakes", new ArrayList<>());
            request.setAttribute("generalIntakes", new ArrayList<>());
            request.setAttribute("programIntakes", new ArrayList<>());
            request.setAttribute("programsWithIntake", new ArrayList<>());

            request.setAttribute("indepthIntakeNodes", new ArrayList<>());
            request.setAttribute("generalIntakeNodes", new ArrayList<>());


            /* consent forms */
            int clientId = Integer.parseInt(demographicNo);
            List<IntegratorConsent> consentTemp = integratorConsentDao.findByFacilityAndDemographic(facilityId, clientId);
            TreeMap<Date, HashMap<String, Object>> consents = new TreeMap<Date, HashMap<String, Object>>(Collections.reverseOrder());
            for (IntegratorConsent x : consentTemp) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("createdDate", DateFormatUtils.ISO_DATETIME_FORMAT.format(x.getCreatedDate()).replace('T', ' '));
                Provider provider = providerDao.getProvider(x.getProviderNo());
                map.put("providers", provider.getFormattedName());
                map.put("consentId", x.getId());

                consents.put(x.getCreatedDate(), map);
            }

            request.setAttribute("consents", consents.values());

            // CDS forms
            List<CdsClientForm> cdsForms = cdsClientFormDao.findByFacilityClient(facilityId, clientId);
            request.setAttribute("cdsForms", cdsForms);


        }

        /* refer */
        if (tabBean.getTab().equals("Refer") || tabBean.getTab().equals("Refer to vacancy")) {
            List<ClientReferral> clientReferrals = clientManager.getActiveReferrals(demographicNo, String.valueOf(facilityId));
            List<ClientReferral> clientReferralDisplay = new ArrayList<ClientReferral>();
            for (ClientReferral cr : clientReferrals) {
                Vacancy v = vacancyDao.getVacancyById(cr.getVacancyId() == null ? 0 : cr.getVacancyId());
                if (v != null) {
                    cr.setVacancyTemplateName(vacancyTemplateDao.getVacancyTemplate(v.getTemplateId()).getName());
                }
                clientReferralDisplay.add(cr);
            }
            request.setAttribute("referrals", clientReferralDisplay);

            if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                try {
                    ArrayList<RemoteReferral> results = new ArrayList<RemoteReferral>();

                    // get local data
                    List<RemoteReferral> remoteReferralsFromDB = remoteReferralDao.findByFacilityIdDemogprahicId(facilityId, demographicId);
                    results.addAll(remoteReferralsFromDB);

                    // get remote Data
                    ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());

                    Integer currentRemoteFacilityId = CaisiIntegratorManager.getCurrentRemoteFacility(loggedInInfo, loggedInInfo.getCurrentFacility()).getIntegratorFacilityId();
                    List<Referral> referrals = referralWs.getLinkedReferrals(Integer.parseInt(demographicNo));

                    if (referrals != null) {
                        for (Referral remoteReferral : referrals) {
                            if (currentRemoteFacilityId.equals(remoteReferral.getSourceIntegratorFacilityId()))
                                continue;

                            RemoteReferral temp = new RemoteReferral();
                            CachedFacility cachedFacility = CaisiIntegratorManager.getRemoteFacility(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteReferral.getDestinationIntegratorFacilityId());
                            temp.setReferredToFacilityName(cachedFacility.getName());

                            FacilityIdIntegerCompositePk pk = new FacilityIdIntegerCompositePk();
                            pk.setIntegratorFacilityId(remoteReferral.getDestinationIntegratorFacilityId());
                            pk.setCaisiItemId(remoteReferral.getDestinationCaisiProgramId());
                            CachedProgram cachedProgram = CaisiIntegratorManager.getRemoteProgram(loggedInInfo, loggedInInfo.getCurrentFacility(), pk);
                            temp.setReferredToProgramName(cachedProgram.getName());

                            temp.setReferalDate(remoteReferral.getReferralDate());

                            Provider tempProvider = providerDao.getProvider(remoteReferral.getSourceCaisiProviderId());
                            temp.setReferringProviderNo(tempProvider.getFormattedName());

                            temp.setReasonForReferral(remoteReferral.getReasonForReferral());
                            temp.setPresentingProblem(remoteReferral.getPresentingProblem());

                            results.add(temp);
                        }
                    }

                    Comparator<RemoteReferral> tempComparator = new Comparator<RemoteReferral>() {
                        @Override
                        public int compare(RemoteReferral o1, RemoteReferral o2) {
                            if (o1.getReferalDate() == null && o2.getReferalDate() == null) return (0);
                            if (o1.getReferalDate() == null) return (-1);
                            if (o2.getReferalDate() == null) return (1);
                            return (o1.getReferalDate().compareTo(o2.getReferalDate()));
                        }
                    };

                    Collections.sort(results, tempComparator);

                    request.setAttribute("remoteReferrals", results);
                } catch (Exception e) {
                    logger.error("Unexpected Error.", e);
                }
            }
            //Added for refer to Vacancy
            if (tabBean.getTab().equals("Refer to vacancy")) {
//				Program criteria = this.getProgram();				
//				List<Program> programs = programManager.search(criteria);

                //List<Program> programs = programManager.getPrograms(facilityId);

                //List<VacancyDisplayBO> vacancyDisplayBOs = matchingManager.listVacanciesForWaitListProgram();
                //get all vacancies.
                WaitListService s = new WaitListService();
                List<VacancyDisplayBO> vacancyDisplayBOs = s.listVacanciesForAllWaitListPrograms();

                List<Program> vacancyPrograms = new ArrayList<Program>();

                for (int j = 0; j < vacancyDisplayBOs.size(); j++) {
                    Program program = programManager.getProgram(vacancyDisplayBOs.get(j).getProgramId());

                    //if(vacancyDisplayBOs.get(j).getNoOfVacancy() != 0){
                    //program.setNoOfVacancy(vacancyDisplayBOs.get(j).getNoOfVacancy());
                    program.setVacancyName(vacancyDisplayBOs.get(j).getVacancyName());
                    program.setDateCreated(vacancyDisplayBOs.get(j).getCreated().toString());

                    int vacancyId = vacancyDisplayBOs.get(j).getVacancyID();
                    List<MatchBO> matchList = matchingManager.getClientMatches(vacancyId);
                    double percentageMatch = 0;
                    for (int k = 0; k < matchList.size(); k++) {
                        percentageMatch = percentageMatch + matchList.get(k).getPercentageMatch();
                    }

                    program.setVacancyId(vacancyId);
                    program.setMatches(percentageMatch);
                    program.setVacancyTemplateName(vacancyDisplayBOs.get(j).getVacancyTemplateName());
                    vacancyPrograms.add(program);
                }

                request.setAttribute("programs", vacancyPrograms);
            }

        }

        /* service restrictions */
        if (tabBean.getTab().equals("Service Restrictions")) {
            // request.setAttribute("serviceRestrictions", clientRestrictionManager.getActiveRestrictionsForClient(Integer.valueOf(demographicNo), new Date()));
            request.setAttribute("serviceRestrictions", clientRestrictionManager.getActiveRestrictionsForClient(Integer.valueOf(demographicNo), facilityId, new Date()));

            request.setAttribute("serviceRestrictionList", lookupManager.LoadCodeList("SRT", true, null, null));
        }

        /* discharge */
        if (tabBean.getTab().equals("Discharge")) {
            request.setAttribute("communityPrograms", programManager.getCommunityPrograms());
            request.setAttribute("serviceAdmissions", admissionManager.getCurrentServiceProgramAdmission(Integer.valueOf(demographicNo)));
            request.setAttribute("temporaryAdmissions", admissionManager.getCurrentTemporaryProgramAdmission(Integer.valueOf(demographicNo)));
            request.setAttribute("current_community_program", admissionManager.getCurrentCommunityProgramAdmission(Integer.valueOf(demographicNo)));
            request.setAttribute("dischargeReasons", lookupManager.LoadCodeList("DRN", true, null, null));
            request.setAttribute("dischargeReasons2", ""/*lookupManager.LoadCodeList("DR2", true, null, null)*/);
        }

        /* Relations */
        DemographicRelationship demoRelation = new DemographicRelationship();
        List<Map<String, Object>> relList = demoRelation.getDemographicRelationshipsWithNamePhone(loggedInInfo, demographicNo, facilityId);
        List<JointAdmission> list = clientManager.getDependents(Integer.valueOf(demographicNo));
        JointAdmission clientsJadm = clientManager.getJointAdmission(Integer.valueOf(demographicNo));
        int familySize = list.size() + 1;
        if (familySize > 1) {
            request.setAttribute("groupHead", "yes");
        }
        if (clientsJadm != null) {
            request.setAttribute("dependentOn", clientsJadm.getHeadClientId());
            List<JointAdmission> depList = clientManager.getDependents(clientsJadm.getHeadClientId());
            familySize = depList.size() + 1;
            Demographic headClientDemo = clientManager.getClientByDemographicNo("" + clientsJadm.getHeadClientId());
            request.setAttribute("groupName", headClientDemo.getFormattedName() + " Group");
        }

        if (relList != null && relList.size() > 0) {
            for (Map<String, Object> h : relList) {
                String demographic = (String) h.get("demographicNo");
                Integer demoLong = Integer.valueOf(demographic);
                JointAdmission demoJadm = clientManager.getJointAdmission(demoLong);

                // IS PERSON JOINTLY ADMITTED WITH ME, They will either have the same HeadClient or be my headClient
                if (clientsJadm != null && clientsJadm.getHeadClientId().longValue() == demoLong) { // they're my head client
                    h.put("jointAdmission", "head");
                } else if (demoJadm != null && clientsJadm != null && clientsJadm.getHeadClientId().longValue() == demoJadm.getHeadClientId().longValue()) {
                    // They depend on the same person i do!
                    h.put("jointAdmission", "dependent");
                } else if (demoJadm != null && demoJadm.getHeadClientId().longValue() == Long.valueOf(demographicNo).longValue()) {
                    // They depend on me
                    h.put("jointAdmission", "dependent");
                }
                // Can this person be added to my depended List
                if (clientsJadm == null && demoJadm == null && clientManager.getDependents(demoLong).size() == 0) {
                    // yes if - i am not dependent on anyone
                    // - this person is not dependent on someone
                    // - this person is not a head of a family already
                    h.put("dependentable", "yes");
                }
                if (demoJadm != null) { // DEPENDS ON SOMEONE
                    h.put("dependentOn", demoJadm.getHeadClientId());
                    if (demoJadm.getHeadClientId().longValue() == Long.valueOf(demographicNo).longValue()) {
                        h.put("dependent", demoJadm.getTypeId());
                    }
                } else if (clientsJadm != null && clientsJadm.getHeadClientId().longValue() == demoLong) { // HEAD PERSON WON'T DEPEND ON ANYONE
                    h.put("dependent", Long.valueOf(0));
                }
            }
            request.setAttribute("relations", relList);
            request.setAttribute("relationSize", familySize);

        }
    }


    private void addRemoteAdmissions(LoggedInInfo loggedInInfo, ArrayList<AdmissionForDisplay> admissionsForDisplay, Integer demographicId) {
        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {

            try {
                List<CachedAdmission> cachedAdmissions = null;
                try {
                    if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                        cachedAdmissions = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility()).getLinkedCachedAdmissionsByDemographicId(demographicId);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Unexpected error.", e);
                    CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
                }

                if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                    cachedAdmissions = IntegratorFallBackManager.getRemoteAdmissions(loggedInInfo, demographicId);
                }

                for (CachedAdmission cachedAdmission : cachedAdmissions)
                    admissionsForDisplay.add(new AdmissionForDisplay(loggedInInfo, cachedAdmission));

                Collections.sort(admissionsForDisplay, AdmissionForDisplay.ADMISSION_DATE_COMPARATOR);
            } catch (Exception e) {
                logger.error("Error retrieveing integrated admissions.", e);
            }
        }
    }

    private List<ReferralSummaryDisplay> getReferralsForSummary(LoggedInInfo loggedInInfo, Integer demographicNo, Integer facilityId) {
        ArrayList<ReferralSummaryDisplay> allResults = new ArrayList<ReferralSummaryDisplay>();

        List<ClientReferral> tempResults = clientManager.getActiveReferrals(String.valueOf(demographicNo), String.valueOf(facilityId));
        for (ClientReferral clientReferral : tempResults) {
            String vacancyName = clientReferral.getSelectVacancy();
            if (vacancyName != null) {
                List<Vacancy> vlist = vacancyDao.getVacanciesByName(vacancyName); //assume vacancyName is unique.
                if (vlist.size() > 0) {
                    Integer vacancyTemplateId = vlist.get(0).getTemplateId();
                    clientReferral.setVacancyTemplateName(vacancyTemplateDao.getVacancyTemplate(vacancyTemplateId).getName());
                }
            }
            allResults.add(new ReferralSummaryDisplay(clientReferral));

        }

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            try {
                ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());

                List<Referral> tempRemoteReferrals = referralWs.getLinkedReferrals(demographicNo);
                for (Referral referral : tempRemoteReferrals)
                    allResults.add(new ReferralSummaryDisplay(loggedInInfo, referral));

                Collections.sort(allResults, ReferralSummaryDisplay.REFERRAL_DATE_COMPARATOR);
            } catch (Exception e) {
                logger.error("Unexpected error.", e);
            }
        }

        return (allResults);
    }

    private List<ReferralHistoryDisplay> getReferralsForHistory(LoggedInInfo loggedInInfo, Integer demographicNo, Integer facilityId) {
        ArrayList<ReferralHistoryDisplay> allResults = new ArrayList<ReferralHistoryDisplay>();

        for (ClientReferral clientReferral : clientManager.getReferralsByFacility(demographicNo, facilityId))
            allResults.add(new ReferralHistoryDisplay(clientReferral));

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            try {
                ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());

                List<Referral> tempRemoteReferrals = referralWs.getLinkedReferrals(demographicNo);
                for (Referral referral : tempRemoteReferrals)
                    allResults.add(new ReferralHistoryDisplay(loggedInInfo, loggedInInfo.getCurrentFacility(), referral));

                Collections.sort(allResults, ReferralHistoryDisplay.REFERRAL_DATE_COMPARATOR);
            } catch (Exception e) {
                logger.error("Unexpected error.", e);
            }
        }

        return (allResults);
    }

    public static String getEscapedAdmissionSelectionDisplay(int admissionId) {
        Admission admission = admissionDao.getAdmission((long) admissionId);

        StringBuilder sb = new StringBuilder();
        if (admission != null) {
            sb.append(admission.getProgramName());
            sb.append(" ( ");
            sb.append(DateFormatUtils.ISO_DATE_FORMAT.format(admission.getAdmissionDate()));
            sb.append(" - ");
            if (admission.getDischargeDate() == null) sb.append("current");
            else sb.append(DateFormatUtils.ISO_DATE_FORMAT.format(admission.getDischargeDate()));
            sb.append(" )");
        }
        return (StringEscapeUtils.escapeHtml4(sb.toString()));
    }

    public static String getEscapedProviderDisplay(String providerNo) {
        Provider provider = providerDao.getProvider(providerNo);

        return (StringEscapeUtils.escapeHtml4(provider.getFormattedName()));
    }

    public static String getEscapedDateDisplay(Date d) {
        String display = DateFormatUtils.ISO_DATE_FORMAT.format(d);

        return (StringEscapeUtils.escapeHtml4(display));
    }

    @Required
    public void setClientRestrictionManager(ClientRestrictionManager clientRestrictionManager) {
        this.clientRestrictionManager = clientRestrictionManager;
    }


    public void setLookupManager(LookupManager lookupManager) {
        this.lookupManager = lookupManager;
    }

    public void setCaseManagementManager(CaseManagementManager caseManagementManager) {
        this.caseManagementManager = caseManagementManager;
    }

    public void setAdmissionManager(AdmissionManager mgr) {
        this.admissionManager = mgr;
    }



    public void setClientManager(ClientManager mgr) {
        this.clientManager = mgr;
    }

    public void setProgramManager(ProgramManager mgr) {
        this.programManager = mgr;
    }

    public void setProgramQueueManager(ProgramQueueManager mgr) {
        this.programQueueManager = mgr;
    }

    public void setProviderManager(ProviderManager mgr) {
        this.providerManager = mgr;
    }


    private void populateCdsData(HttpServletRequest request, Integer demographicNo, Integer facilityId) {
        List<Admission> admissions = admissionDao.getAdmissions(demographicNo);
        List<Program> domain = null;


        ArrayList<CdsClientForm> allLatestCdsForms = new ArrayList<CdsClientForm>();

        boolean restrict = "true".equals(OscarProperties.getInstance().getProperty("caisi.cds.restrict_by_program_domain", "false"));
        if (restrict) {
            domain = programManager.getProgramDomain(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
        }

        for (Admission admission : admissions) {
            CdsClientForm cdsClientForm = cdsClientFormDao.findLatestByFacilityAdmissionId(facilityId, admission.getId().intValue(), null);
            if (cdsClientForm != null) {
                if (restrict) {
                    if (isAdmissionInDomain(admission, domain)) {
                        allLatestCdsForms.add(cdsClientForm);
                    }
                } else {
                    allLatestCdsForms.add(cdsClientForm);
                }
            }
        }

        request.setAttribute("allLatestCdsForms", allLatestCdsForms);
    }

    private boolean isAdmissionInDomain(Admission admission, List<Program> domain) {
        for (Program p : domain) {
            if (p.getId().intValue() == admission.getProgramId().intValue()) {
                return true;
            }
        }
        return false;
    }

    public static String getCdsProgramDisplayString(CdsClientForm cdsClientForm) {
        Admission admission = admissionDao.getAdmission(cdsClientForm.getAdmissionId());
        Program program = programDao.getProgram(admission.getProgramId());

        String displayString = program.getName() + " : " + DateFormatUtils.ISO_DATE_FORMAT.format(admission.getAdmissionDate());
        return (StringEscapeUtils.escapeHtml4(displayString));
    }

    private ClientManagerFormBean view;
    private Admission admission;
    private Program program;
    private ClientReferral referral;
    private ProgramClientRestriction serviceRestriction;
    private Integer serviceRestrictionLength;
    private CaisiFormInstance form;
    private Demographic client;
    private Provider provider;

    public ClientManagerFormBean getView() {
        return view;
    }

    public void setView(ClientManagerFormBean view) {
        this.view = view;
    }

    public Admission getAdmission() {
        return admission;
    }

    public void setAdmission(Admission admission) {
        this.admission = admission;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }


    public ClientReferral getReferral() {
        return referral;
    }

    public void setReferral(ClientReferral referral) {
        this.referral = referral;
    }

    public ProgramClientRestriction getServiceRestriction() {
        return serviceRestriction;
    }

    public void setServiceRestriction(ProgramClientRestriction serviceRestriction) {
        this.serviceRestriction = serviceRestriction;
    }

    public Integer getServiceRestrictionLength() {
        return serviceRestrictionLength;
    }

    public void setServiceRestrictionLength(Integer serviceRestrictionLength) {
        this.serviceRestrictionLength = serviceRestrictionLength;
    }

    public CaisiFormInstance getForm() {
        return form;
    }

    public void setForm(CaisiFormInstance form) {
        this.form = form;
    }

    public Demographic getClient() {
        return client;
    }

    public void setClient(Demographic client) {
        this.client = client;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

}
