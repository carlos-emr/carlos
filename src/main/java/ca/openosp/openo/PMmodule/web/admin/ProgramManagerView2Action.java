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

package ca.openosp.openo.PMmodule.web.admin;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.ws.WebServiceException;

import ca.openosp.openo.PMmodule.model.*;
import ca.openosp.openo.caisi_integrator.ws.*;
import ca.openosp.openo.util.DateUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.dao.ClientReferralDAO;
import ca.openosp.openo.PMmodule.dao.VacancyDao;
import ca.openosp.openo.PMmodule.exception.AdmissionException;
import ca.openosp.openo.PMmodule.exception.ProgramFullException;
import ca.openosp.openo.PMmodule.exception.ServiceRestrictionException;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ClientManager;
import ca.openosp.openo.PMmodule.service.ClientRestrictionManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.service.ProgramQueueManager;
import ca.openosp.openo.PMmodule.service.VacancyTemplateManager;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.FacilityDao;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.managers.TicklerManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Required;

import ca.openosp.openo.log.LogAction;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ProgramManagerView2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();
    private ClientRestrictionManager clientRestrictionManager = SpringUtils.getBean(ClientRestrictionManager.class);
    private FacilityDao facilityDao = SpringUtils.getBean(FacilityDao.class);
    private CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);
    private AdmissionManager admissionManager = SpringUtils.getBean(AdmissionManager.class);
    private ClientManager clientManager = SpringUtils.getBean(ClientManager.class);
    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private ProgramManagerAction programManagerAction = SpringUtils.getBean(ProgramManagerAction.class);
    private ProgramQueueManager programQueueManager = SpringUtils.getBean(ProgramQueueManager.class);
    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    public void setFacilityDao(FacilityDao facilityDao) {
        this.facilityDao = facilityDao;
    }

    public void setProgramManagerAction(ProgramManagerAction programManagerAction) {
        this.programManagerAction = programManagerAction;
    }

    public String execute() {
        String method = request.getParameter("method");
        if ("remove_remote_queue".equals(method)) {
            return remove_remote_queue();
        } else if ("admit".equals(method)) {
            return admit();
        } else if ("override_restriction".equals(method)) {
            return override_restriction();
        } else if ("assign_team_client".equals(method)) {
            return assign_team_client();
        } else if ("assign_status_client".equals(method)) {
            return assign_status_client();
        } else if ("batch_discharge".equals(method)) {
            return batch_discharge();
        } else if ("reject_from_queue".equals(method)) {
            return reject_from_queue();
        } else if ("select_client_for_admit".equals(method)) {
            return select_client_for_admit();
        } else if ("select_client_for_reject".equals(method)) {
            return select_client_for_reject();
        }
        return view();
    }

    @SuppressWarnings("unchecked")
    public String view() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

                // find the program id
        String programId = request.getParameter("id");

        request.getSession().setAttribute("case_program_id", programId);

        if (request.getParameter("newVacancy") != null && "true".equals(request.getParameter("newVacancy")))
            request.setAttribute("vacancyOrTemplateId", "");
        else
            request.setAttribute("vacancyOrTemplateId", this.getVacancyOrTemplateId());

        if (programId == null) {
            programId = (String) request.getAttribute("id");
        }

        String demographicNo = request.getParameter("clientId");

        if (demographicNo != null) {
            request.setAttribute("clientId", demographicNo);
        }

        request.setAttribute("temporaryAdmission", programManager.getEnabled());

        // check role permission
        HttpSession se = request.getSession();
        String providerNo = (String) se.getAttribute("user");
        se.setAttribute("performAdmissions", Boolean.valueOf(caseManagementManager.hasAccessRight("perform admissions", "access", providerNo, "", programId)));

        // check if the user is from program's staff
        request.setAttribute("userIsProgramProvider", Boolean.valueOf(programManager.getProgramProvider(providerNo, programId) != null));

        // need the queue to determine which tab to go to first
        List<ProgramQueue> queue = programQueueManager.getActiveProgramQueuesByProgramId(Long.valueOf(programId));
        request.setAttribute("queue", queue);

        if (CaisiIntegratorManager.isEnableIntegratedReferrals(loggedInInfo.getCurrentFacility())) {
            request.setAttribute("remoteQueue", getRemoteQueue(loggedInInfo, Integer.parseInt(programId)));
        }

        HashSet<Long> genderConflict = new HashSet<Long>();
        HashSet<Long> ageConflict = new HashSet<Long>();
        for (ProgramQueue programQueue : queue) {
            Demographic demographic = clientManager.getClientByDemographicNo(String.valueOf(programQueue.getClientId()));
            Program program = programManager.getProgram(programQueue.getProgramId());

            if (program.getManOrWoman() != null && demographic.getSex() != null) {
                if ("Man".equals(program.getManOrWoman()) && !"M".equals(demographic.getSex())) {
                    genderConflict.add(programQueue.getClientId());
                }
                if ("Woman".equals(program.getManOrWoman()) && !"F".equals(demographic.getSex())) {
                    genderConflict.add(programQueue.getClientId());
                }
                if ("Transgendered".equals(program.getManOrWoman()) && !"T".equals(demographic.getSex())) {
                    genderConflict.add(programQueue.getClientId());
                }
            }

            if (demographic.getAge() != null) {
                int age = Integer.parseInt(demographic.getAge());
                if (age < program.getAgeMin() || age > program.getAgeMax()) ageConflict.add(programQueue.getClientId());
            }
        }
        request.setAttribute("genderConflict", genderConflict);
        request.setAttribute("ageConflict", ageConflict);

        if (this.getTab() == null || this.getTab().equals("")) {
            if (queue != null && queue.size() > 0) {
                this.setTab("Queue");
            } else {
                this.setTab("General");
            }
        }

        Program program = programManager.getProgram(programId);
        request.setAttribute("program", program);
        Facility facility = facilityDao.find(program.getFacilityId());
        if (facility != null) request.setAttribute("facilityName", facility.getName());

        if (this.getTab().equals("Service Restrictions")) {
            request.setAttribute("service_restrictions", clientRestrictionManager.getActiveRestrictionsForProgram(Integer.valueOf(programId), new Date()));
        }
        if (this.getTab().equals("Staff")) {
            request.setAttribute("providers", programManager.getProgramProviders(programId));
        }

        if (this.getTab().equals("Function User")) {
            request.setAttribute("functional_users", programManager.getFunctionalUsers(programId));
        }

        if (this.getTab().equals("Teams")) {
            List<ProgramTeam> teams = programManager.getProgramTeams(programId);

            for (ProgramTeam team : teams) {
                team.setProviders(programManager.getAllProvidersInTeam(Integer.valueOf(programId), team.getId()));
                team.setAdmissions(programManager.getAllClientsInTeam(Integer.valueOf(programId), team.getId()));
            }

            request.setAttribute("teams", teams);
        }

        if (this.getTab().equals("Clients")) {
            request.setAttribute("client_statuses", programManager.getProgramClientStatuses(Integer.valueOf(programId)));

            // request.setAttribute("admissions", admissionManager.getCurrentAdmissionsByProgramId(programId));
            // clients should be active
            List<Admission> admissions = new ArrayList<Admission>();
            List<Admission> ads = admissionManager.getCurrentAdmissionsByProgramId(programId);
            for (Admission admission : ads) {
                Integer clientId = admission.getClientId();
                if (clientId > 0) {
                    Demographic client = clientManager.getClientByDemographicNo(Integer.toString(clientId));
                    if (client != null) {
                        String clientStatus = client.getPatientStatus();
                        if (clientStatus != null && clientStatus.equals("AC")) admissions.add(admission);
                    }
                }
            }
            request.setAttribute("admissions", admissions);

            request.setAttribute("program_name", program.getName());

            List<ProgramTeam> teams = programManager.getProgramTeams(programId);

            for (ProgramTeam team : teams) {
                team.setProviders(programManager.getAllProvidersInTeam(Integer.valueOf(programId), team.getId()));
                team.setAdmissions(programManager.getAllClientsInTeam(Integer.valueOf(programId), team.getId()));
            }

            request.setAttribute("teams", teams);

            List<Program> batchAdmissionPrograms = new ArrayList<Program>();

            List<Program> batchAdmissionServicePrograms = new ArrayList<Program>();
            List<Program> servicePrograms;
            servicePrograms = programManager.getServicePrograms();
            for (Program sp : servicePrograms) {
                if (sp.isAllowBatchAdmission() && sp.isActive()) {
                    batchAdmissionServicePrograms.add(sp);
                }
            }

            request.setAttribute("communityPrograms", programManager.getCommunityPrograms());
            request.setAttribute("allowBatchDischarge", program.isAllowBatchDischarge());
            request.setAttribute("servicePrograms", batchAdmissionServicePrograms);
        }

        if (this.getTab().equals("Access")) {
            request.setAttribute("accesses", programManager.getProgramAccesses(programId));
        }


        if (this.getTab().equals("Client Status")) {
            request.setAttribute("client_statuses", programManager.getProgramClientStatuses(Integer.valueOf(programId)));
        }

        LogAction.log("view", "program", programId, request);

        request.setAttribute("id", programId);

        return "view";
    }

    protected List<ProgramManagerAction.RemoteQueueEntry> getRemoteQueue(LoggedInInfo loggedInInfo, int programId) {
        try {
            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            List<Referral> remoteReferrals = referralWs.getReferralsToProgram(programId);

            ArrayList<ProgramManagerAction.RemoteQueueEntry> results = new ArrayList<>();
            for (Referral remoteReferral : remoteReferrals) {
                ProgramManagerAction.RemoteQueueEntry remoteQueueEntry = new ProgramManagerAction.RemoteQueueEntry();
                //remoteQueueEntry.setReferral(remoteReferral);

                DemographicTransfer demographicTransfer = demographicWs.getDemographicByFacilityIdAndDemographicId(remoteReferral.getSourceIntegratorFacilityId(), remoteReferral.getSourceCaisiDemographicId());
                if (demographicTransfer != null) {
                    remoteQueueEntry.setClientName(demographicTransfer.getLastName() + ", " + demographicTransfer.getFirstName());
                } else {
                    remoteQueueEntry.setClientName("N/A");
                }

                FacilityIdStringCompositePk pk = new FacilityIdStringCompositePk();
                pk.setIntegratorFacilityId(remoteReferral.getSourceIntegratorFacilityId());
                pk.setCaisiItemId(remoteReferral.getSourceCaisiProviderId());
                CachedProvider cachedProvider = CaisiIntegratorManager.getProvider(loggedInInfo, loggedInInfo.getCurrentFacility(), pk);
                if (cachedProvider != null) {
                    remoteQueueEntry.setProviderName(cachedProvider.getLastName() + ", " + cachedProvider.getFirstName());
                } else {
                    remoteQueueEntry.setProviderName("N/A");
                }

                results.add(remoteQueueEntry);
            }
            return (results);
        } catch (MalformedURLException e) {
            logger.error("Unexpected Error.", e);
        } catch (WebServiceException e) {
            logger.error("Unexpected Error.", e);
        }

        return (null);
    }

    public String remove_remote_queue() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer remoteReferralId = Integer.valueOf(request.getParameter("remoteReferralId"));

        try {
            ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            referralWs.removeReferral(remoteReferralId);
        } catch (MalformedURLException e) {
            logger.error("Unexpected error", e);
        } catch (WebServiceException e) {
            logger.error("Unexpected error", e);
        }

        return view();
    }


    public String admit() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String programId = request.getParameter("id");
        String clientId = request.getParameter("clientId");
        String queueId = request.getParameter("queueId");

        ProgramQueue queue = programQueueManager.getProgramQueue(queueId);
        Program fullProgram = programManager.getProgram(String.valueOf(programId));
        String dischargeNotes = request.getParameter("admission.dischargeNotes");
        String admissionNotes = request.getParameter("admission.admissionNotes");
        String formattedAdmissionDate = request.getParameter("admissionDate");
        Date admissionDate = DateUtils.toDate(formattedAdmissionDate);
        List<Integer> dependents = clientManager.getDependentsList(Integer.valueOf(clientId));

        try {
            admissionManager.processAdmission(Integer.valueOf(clientId), loggedInInfo.getLoggedInProviderNo(), fullProgram, dischargeNotes, admissionNotes, queue.isTemporaryAdmission(), dependents, admissionDate);

            //change vacancy status to filled after one patient is admitted to associated program in that vacancy.
            Vacancy vacancy = VacancyTemplateManager.getVacancyByName(queue.getVacancyName());
            if (vacancy != null) {
                vacancy.setStatus("Filled");
                VacancyTemplateManager.saveVacancy(vacancy);
            }

            addActionMessage(getText("admit.success"));
        } catch (ProgramFullException e) {
            addActionMessage(getText("admit.full"));
        } catch (AdmissionException e) {
            addActionMessage(getText("admit.error", e.getMessage()));
            logger.error("Error", e);
        } catch (ServiceRestrictionException e) {
            addActionMessage(getText("admit.service_restricted", e.getRestriction().getComments(), e.getRestriction().getProvider().getFormattedName()));
            // store this for display
            this.setServiceRestriction(e.getRestriction());

            request.getSession().setAttribute("programId", programId);
            request.getSession().setAttribute("admission.dischargeNotes", dischargeNotes);
            request.getSession().setAttribute("admission.admissionNotes", admissionNotes);

            request.setAttribute("id", programId);

            request.setAttribute("hasOverridePermission", caseManagementManager.hasAccessRight("Service restriction override on admission", "access", loggedInInfo.getLoggedInProviderNo(), clientId, programId));

            return "service_restriction_error";
        }

        LogAction.log("view", "admit to program", clientId, request);

        return view();
    }

    public String override_restriction() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String programId = (String) request.getSession().getAttribute("programId");
        String clientId = request.getParameter("clientId");
        String queueId = request.getParameter("queueId");

        String dischargeNotes = (String) request.getSession().getAttribute("admission.dischargeNotes");
        String admissionNotes = (String) request.getSession().getAttribute("admission.admissionNotes");

        request.setAttribute("id", programId);

        if (!caseManagementManager.hasAccessRight("Service restriction override on referral", "access", loggedInInfo.getLoggedInProviderNo(), clientId, programId)) {
            return view();
        }

        ProgramQueue queue = programQueueManager.getProgramQueue(queueId);
        Program fullProgram = programManager.getProgram(String.valueOf(programId));

        try {
            admissionManager.processAdmission(Integer.valueOf(clientId), loggedInInfo.getLoggedInProviderNo(), fullProgram, dischargeNotes, admissionNotes, queue.isTemporaryAdmission(), true);
            addActionMessage(getText("admit.success"));
        } catch (ProgramFullException e) {
            addActionMessage(getText("admit.full"));
            logger.error("Error", e);
        } catch (AdmissionException e) {
            addActionMessage(getText("admit.error", e.getMessage()));
            logger.error("Error", e);
        } catch (ServiceRestrictionException e) {
            throw new RuntimeException(e);
        }

        LogAction.log("view", "override service restriction", clientId, request);

        LogAction.log("view", "admit to program", clientId, request);

        return view();
    }

    public String assign_team_client() {
        String admissionId = request.getParameter("admissionId");
        String teamId = request.getParameter("teamId");
        String programName = request.getParameter("program_name");
        Admission ad = admissionManager.getAdmission(Long.valueOf(admissionId));

        ad.setTeamId(Integer.valueOf(teamId));

        admissionManager.saveAdmission(ad);
        addActionMessage(getText("program.saved", programName));

        LogAction.log("write", "edit program - assign client to team", "", request);
        return view();
    }

    public String assign_status_client() {
        String admissionId = request.getParameter("admissionId");
        String statusId = request.getParameter("clientStatusId");
        String programName = request.getParameter("program_name");
        Admission ad = admissionManager.getAdmission(Long.valueOf(admissionId));

        ad.setClientStatusId(Integer.valueOf(statusId));

        admissionManager.saveAdmission(ad);
        addActionMessage(getText("program.saved", programName));

        LogAction.log("write", "edit program - assign client to status", "", request);
        return view();
    }

    public String batch_discharge() {
        logger.info("do batch discharge");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String type = request.getParameter("type");
        String admitToProgramId;
        if (type != null && type.equalsIgnoreCase("community")) {
            admitToProgramId = request.getParameter("batch_discharge_community_program");
        } else {
            logger.warn("Invalid program type for batch discharge");
            admitToProgramId = "";
        }

        String message = "";

        // get clients
        Enumeration e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            if (name.startsWith("checked_") && request.getParameter(name).equals("on")) {
                String admissionId = name.substring(8);
                Admission admission = admissionManager.getAdmission(Long.valueOf(admissionId));
                if (admission == null) {
                    logger.warn("admission #" + admissionId + " not found.");
                    continue;
                }


                // in case some clients maybe is already in the community program
                if (type != null) {
                    if (type.equals("community")) {
                        Integer clientId = admission.getClientId();

                        // if discharged program is service program,
                        // if the client is already in the community program, then cannot do batch discharge to the community program.
                        Admission admission_community_program = admissionManager.getCurrentCommunityProgramAdmission(clientId);
                        if (admission_community_program != null) {
                            message += admission.getClient().getFormattedName() + " is already in one community program. You cannot do batch discharge for this client! \n";
                            continue;
                        }
                    }
                }
                // lets see if there's room first
                if (!"service".equals(type)) {
                    Program programToAdmit = programManager.getProgram(admitToProgramId);
                    if (programToAdmit == null) {
                        message += "Admitting program not found!";
                        continue;
                    }
                    if (programToAdmit.getNumOfMembers() >= programToAdmit.getMaxAllowed()) {
                        message += "Program Full. Cannot admit " + admission.getClient().getFormattedName() + "\n";
                        continue;
                    }
                }
                admission.setDischargeDate(new Date());
                admission.setDischargeNotes("Batch discharge");
                admission.setAdmissionStatus(Admission.STATUS_DISCHARGED);
                admissionManager.saveAdmission(admission);

                // The service program can only be batch discharged, can not be admitted to another program.
                if (!"service".equals(type)) {
                    Admission newAdmission = new Admission();
                    newAdmission.setAdmissionDate(new Date());
                    newAdmission.setAdmissionNotes("Batch Admit");
                    newAdmission.setAdmissionStatus(Admission.STATUS_CURRENT);
                    newAdmission.setClientId(admission.getClientId());
                    newAdmission.setProgramId(Integer.valueOf(admitToProgramId));
                    newAdmission.setProviderNo(loggedInInfo.getLoggedInProviderNo());
//					newAdmission.setTeamId(0);

                    admissionManager.saveAdmission(newAdmission);
                }
            }
        }
        addActionMessage(getText("errors.detail", message));

        return view();
    }

    private void createWaitlistRejectionNotificationTickler(LoggedInInfo loggedInInfo, Facility facility, String clientId, Integer vacancyId, String creatorProviderNo) {
        if (vacancyId == null)
            return;
        VacancyDao vacancyDao = SpringUtils.getBean(VacancyDao.class);
        Vacancy vacancy = vacancyDao.find(vacancyId);
        if (vacancy == null)
            return;

        Demographic d = demographicDao.getDemographic(clientId);
        Tickler t = new Tickler();
        t.setCreator(creatorProviderNo);
        t.setDemographicNo(Integer.parseInt(clientId));
        t.setMessage("Client=[" + d.getFormattedName() + "] rejected from vacancy=[" + vacancy.getName() + "]");
        t.setProgramId(vacancy.getWlProgramId());
        t.setServiceDate(new Date());
        t.setTaskAssignedTo(facility.getAssignRejectedVacancyApplicant());
        t.setUpdateDate(new Date());

        ticklerManager.addTickler(loggedInInfo, t);
    }

    public String reject_from_queue() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String notes = request.getParameter("admission.admissionNotes");
        String programId = request.getParameter("id");
        String clientId = request.getParameter("clientId");
        String rejectionReason = request.getParameter("radioRejectionReason");

        List<Integer> dependents = clientManager.getDependentsList(Integer.valueOf(clientId));

        logger.debug("rejecting from queue: program_id=" + programId + ",clientId=" + clientId);

        ProgramQueue queue = this.programQueueManager.getActiveProgramQueue(programId, clientId);

        programQueueManager.rejectQueue(programId, clientId, notes, rejectionReason);

        //TODO: WL notification
        ClientReferralDAO clientReferralDao = SpringUtils.getBean(ClientReferralDAO.class);
        Facility facility = loggedInInfo.getCurrentFacility();
        if (facility.getAssignRejectedVacancyApplicant() != null && facility.getAssignRejectedVacancyApplicant().length() > 0) {
            Integer vacancyId = null;
            if (queue != null) {
                ClientReferral referral = clientReferralDao.getClientReferral(queue.getReferralId());
                if (referral != null) {
                    vacancyId = referral.getVacancyId();
                }
            }
            createWaitlistRejectionNotificationTickler(loggedInInfo, facility, clientId, vacancyId, loggedInInfo.getLoggedInProviderNo());
        }
        if (dependents != null) {
            for (Integer l : dependents) {
                logger.debug("rejecting from queue: program_id=" + programId + ",clientId=" + l.intValue());
                programQueueManager.rejectQueue(programId, l.toString(), notes, rejectionReason);
            }
        }

        return view();
    }

    public String select_client_for_admit() {
        String programId = request.getParameter("id");
        String clientId = request.getParameter("clientId");
        String queueId = request.getParameter("queueId");

        Program program = programManager.getProgram(String.valueOf(programId));
        ProgramQueue queue = programQueueManager.getProgramQueue(queueId);

        //int numMembers = program.getNumOfMembers().intValue();
        //int maxMem = program.getMaxAllowed().intValue();
        //int familySize = clientManager.getDependents(new Long(clientId)).size();
        // TODO: add warning if this admission ( w/ dependents) will exceed the maxMem

        request.setAttribute("do_admit", Boolean.TRUE);

        return view();
    }

    public String select_client_for_reject() {
        request.setAttribute("do_reject", Boolean.TRUE);

        return view();
    }


    @Required
    public void setClientRestrictionManager(ClientRestrictionManager clientRestrictionManager) {
        this.clientRestrictionManager = clientRestrictionManager;
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


    private String tab;
    private String subtab;
    private String clientId;
    private String queueId;
    private String vacancyOrTemplateId;

    private String radioRejectionReason;
    private ProgramClientRestriction serviceRestriction;

    public String getRadioRejectionReason() {
        return radioRejectionReason;
    }

    public void setRadioRejectionReason(String radioRejectionReason) {
        this.radioRejectionReason = radioRejectionReason;
    }

    /**
     * @return Returns the tab.
     */
    public String getTab() {
        return tab;
    }

    /**
     * @param tab The tab to set.
     */
    public void setTab(String tab) {
        this.tab = tab;
    }

    /**
     * @return the subtab
     */
    public String getSubtab() {
        return subtab;
    }

    /**
     * @param subtab the subtab to set
     */
    public void setSubtab(String subtab) {
        this.subtab = subtab;
    }

    /**
     * @return Returns the clientId.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @param clientId The clientId to set.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getQueueId() {
        return queueId;
    }

    public void setQueueId(String queueId) {
        this.queueId = queueId;
    }


    public ProgramClientRestriction getServiceRestriction() {
        return serviceRestriction;
    }

    public void setServiceRestriction(ProgramClientRestriction serviceRestriction) {
        this.serviceRestriction = serviceRestriction;
    }


    public String getVacancyOrTemplateId() {
        return vacancyOrTemplateId;
    }

    public void setVacancyOrTemplateId(String vacancyOrTemplateId) {
        this.vacancyOrTemplateId = vacancyOrTemplateId;
    }

}
