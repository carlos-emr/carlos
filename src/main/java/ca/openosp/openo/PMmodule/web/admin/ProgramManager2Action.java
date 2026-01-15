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

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.WebServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.dao.CriteriaDao;
import ca.openosp.openo.PMmodule.dao.CriteriaTypeOptionDao;
import ca.openosp.openo.PMmodule.dao.VacancyDao;
import ca.openosp.openo.PMmodule.dao.VacancyTemplateDao;
import ca.openosp.openo.PMmodule.model.Criteria;
import ca.openosp.openo.PMmodule.model.CriteriaSelectionOption;
import ca.openosp.openo.PMmodule.model.CriteriaType;
import ca.openosp.openo.PMmodule.model.CriteriaTypeOption;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramAccess;
import ca.openosp.openo.PMmodule.model.ProgramClientRestriction;
import ca.openosp.openo.PMmodule.model.ProgramClientStatus;
import ca.openosp.openo.PMmodule.model.ProgramFunctionalUser;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.model.ProgramQueue;
import ca.openosp.openo.PMmodule.model.ProgramSignature;
import ca.openosp.openo.PMmodule.model.ProgramTeam;
import ca.openosp.openo.PMmodule.model.Vacancy;
import ca.openosp.openo.PMmodule.model.VacancyTemplate;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ClientRestrictionManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.service.ProgramQueueManager;
import ca.openosp.openo.PMmodule.service.ProviderManager;
import ca.openosp.openo.PMmodule.service.VacancyTemplateManager;
import ca.openosp.openo.PMmodule.utility.ProgramAccessCache;
import ca.openosp.openo.caisi_integrator.ws.CachedProvider;
import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdStringCompositePk;
import ca.openosp.openo.caisi_integrator.ws.Referral;
import ca.openosp.openo.caisi_integrator.ws.ReferralWs;
import ca.openosp.openo.commn.dao.FacilityDao;
import ca.openosp.openo.commn.dao.FunctionalCentreDao;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.commn.model.FunctionalCentre;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.managers.TicklerManager;
import ca.openosp.openo.match.IMatchManager;
import ca.openosp.openo.match.MatchManager;
import ca.openosp.openo.match.MatchManagerException;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Required;

import ca.openosp.openo.log.LogAction;

import ca.openosp.openo.services.security.RolesManager;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for comprehensive program management in the PMmodule.
 * <p>
 * This action handles all aspects of program administration including:
 * <ul>
 * <li>Program CRUD operations (create, read, update, delete)</li>
 * <li>Program provider management</li>
 * <li>Team management and team-provider/client assignments</li>
 * <li>Program access control and role assignments</li>
 * <li>Functional user management</li>
 * <li>Client status management</li>
 * <li>Service restriction settings</li>
 * <li>Vacancy and vacancy template management</li>
 * <li>Program queue management</li>
 * <li>Program signature tracking for audit</li>
 * <li>Remote referral integration via CAISI Integrator</li>
 * </ul>
 * <p>
 * The action uses method-based routing where the "method" request parameter
 * determines which operation to execute. All properties are automatically
 * populated by Struts 2 property injection.
 *
 * @since 2005-10-01
 */
public class ProgramManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private ClientRestrictionManager clientRestrictionManager = SpringUtils.getBean(ClientRestrictionManager.class);
    private FacilityDao facilityDao = SpringUtils.getBean(FacilityDao.class);
    private AdmissionManager admissionManager = SpringUtils.getBean(AdmissionManager.class);
    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);
    private ProgramQueueManager programQueueManager = SpringUtils.getBean(ProgramQueueManager.class);
    private VacancyTemplateManager vacancyTemplateManager = SpringUtils.getBean(VacancyTemplateManager.class);
    //private RoleManager roleManager;
    private RolesManager roleManager = SpringUtils.getBean(RolesManager.class);
    private FunctionalCentreDao functionalCentreDao = SpringUtils.getBean(FunctionalCentreDao.class);
    private static VacancyTemplateDao vacancyTemplateDAO = SpringUtils.getBean(VacancyTemplateDao.class);
    private static CriteriaDao criteriaDAO = SpringUtils.getBean(CriteriaDao.class);
    //private static CriteriaTypeDao criteriaTypeDAO = SpringUtils.getBean(CriteriaTypeDao.class);
    private static CriteriaTypeOptionDao criteriaTypeOptionDAO = SpringUtils.getBean(CriteriaTypeOptionDao.class);
    //private static CriteriaSelectionOptionDao criteriaSelectionOptionDAO = (CriteriaSelectionOptionDao) SpringUtils.getBean(CriteriaSelectionOptionDao.class);

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    private IMatchManager matchManager = new MatchManager();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public void setFacilityDao(FacilityDao facilityDao) {
        this.facilityDao = facilityDao;
    }

    public void setFunctionalCentreDao(FunctionalCentreDao functionalCentreDao) {
        this.functionalCentreDao = functionalCentreDao;
    }

    private Program program;
    private ProgramProvider provider;
    private Admission admission;
    private ProgramAccess access;
    private ProgramFunctionalUser function;
    private ProgramQueue queue;
    private ProgramClientStatus client_status;
    private ProgramClientRestriction restriction;

    public ProgramClientRestriction getRestriction() {
        return restriction;
    }

    public void setRestriction(ProgramClientRestriction restriction) {
        this.restriction = restriction;
    }

    public ProgramClientStatus getClient_status() {
        return client_status;
    }

    public void setClient_status(ProgramClientStatus client_status) {
        this.client_status = client_status;
    }

    public ProgramTeam getTeam() {
        return team;
    }

    public void setTeam(ProgramTeam team) {
        this.team = team;
    }

    public ProgramQueue getQueue() {
        return queue;
    }

    public void setQueue(ProgramQueue queue) {
        this.queue = queue;
    }

    private ProgramTeam team;

    public ProgramFunctionalUser getFunction() {
        return function;
    }

    public void setFunction(ProgramFunctionalUser function) {
        this.function = function;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public ProgramProvider getProvider() {
        return provider;
    }

    public void setProvider(ProgramProvider provider) {
        this.provider = provider;
    }

    public Admission getAdmission() {
        return admission;
    }

    public void setAdmission(Admission admission) {
        this.admission = admission;
    }

    public ProgramAccess getAccess() {
        return access;
    }

    public void setAccess(ProgramAccess access) {
        this.access = access;
    }


    /**
     * Main execution method that routes to appropriate sub-methods based on method parameter.
     * <p>
     * Routes to 34 different methods for program management operations:
     * <ul>
     * <li>list() - Default, lists programs (filtered or all)</li>
     * <li>edit/add - Program form display and creation</li>
     * <li>save - Save program</li>
     * <li>delete - Delete program</li>
     * <li>save_provider/edit_provider/delete_provider - Provider management</li>
     * <li>save_team/edit_team/delete_team/assign_team/assign_team_client/remove_team - Team operations</li>
     * <li>save_access/edit_access/delete_access - Access control</li>
     * <li>save_function/edit_function/delete_function - Functional user management</li>
     * <li>save_status/edit_status/delete_status/assign_status_client - Client status</li>
     * <li>save_restriction_settings/enable_restriction/disable_restriction - Service restrictions</li>
     * <li>save_vacancy/save_vacancy_template/viewVacancyTemplate/chooseTemplate - Vacancy management</li>
     * <li>activeTmplStatus/inactiveTmplStatus/saveVacancyStatus - Vacancy template status</li>
     * <li>remove_queue/remove_remote_queue - Queue management</li>
     * <li>programSignatures - Signature display</li>
     * <li>assign_role - Role assignment</li>
     * </ul>
     *
     * Expected request parameters:
     * <ul>
     * <li>method - String method name (determines routing)</li>
     * </ul>
     *
     * @return String result name for Struts 2 result mapping
     */
    public String execute() {
        String method = request.getParameter("method");
        if ("edit".equals(method)) {
            return edit();
        } else if ("programSignatures".equals(method)) {
            return programSignatures();
        } else if ("add".equals(method)) {
            return add();
        } else if ("assign_role".equals(method)) {
            return assign_role();
        } else if ("assign_team".equals(method)) {
            return assign_team();
        } else if ("assign_team_client".equals(method)) {
            return assign_team_client();
        } else if ("delete".equals(method)) {
            return delete();
        } else if ("delete_access".equals(method)) {
            return delete_access();
        } else if ("delete_function".equals(method)) {
            return delete_function();
        } else if ("delete_provider".equals(method)) {
            return delete_provider();
        } else if ("delete_team".equals(method)) {
            return delete_team();
        } else if ("edit_access".equals(method)) {
            return edit_access();
        } else if ("edit_function".equals(method)) {
            return edit_function();
        } else if ("edit_provider".equals(method)) {
            return edit_provider();
        } else if ("edit_team".equals(method)) {
            return edit_team();
        } else if ("remove_queue".equals(method)) {
            return remove_queue();
        } else if ("remove_remote_queue".equals(method)) {
            return remove_remote_queue();
        } else if ("remove_team".equals(method)) {
            return remove_team();
        } else if ("save_restriction_settings".equals(method)) {
            return save_restriction_settings();
        } else if ("save".equals(method)) {
            return save();
        } else if ("viewVacancyTemplate".equals(method)) {
            return viewVacancyTemplate();
        } else if ("chooseTemplate".equals(method)) {
            return chooseTemplate();
        } else if ("save_vacancy".equals(method)) {
            return save_vacancy();
        } else if ("save_vacancy_template".equals(method)) {
            return save_vacancy_template();
        } else if ("save_access".equals(method)) {
            return save_access();
        } else if ("save_function".equals(method)) {
            return save_function();
        } else if ("save_provider".equals(method)) {
            return save_provider();
        } else if ("save_team".equals(method)) {
            return save_team();
        } else if ("delete_status".equals(method)) {
            return delete_status();
        } else if ("edit_status".equals(method)) {
            return edit_status();
        } else if ("save_status".equals(method)) {
            return save_status();
        } else if ("assign_status_client".equals(method)) {
            return assign_status_client();
        } else if ("disable_restriction".equals(method)) {
            return disable_restriction();
        } else if ("enable_restriction".equals(method)) {
            return enable_restriction();
        } else if ("activeTmplStatus".equals(method)) {
            return activeTmplStatus();
        } else if ("inactiveTmplStatus".equals(method)) {
            return inactiveTmplStatus();
        } else if ("saveVacancyStatus".equals(method)) {
            return saveVacancyStatus();
        } 
        return list();
    }

    public String list() {
        String searchStatus = this.getSearchStatus();
        String searchType = this.getSearchType();
        String searchFacilityId = this.getSearchFacilityId();

        String providerNo = (String) request.getSession().getAttribute("user");
        String userrole = (String) request.getSession().getAttribute("userrole");

        List<Program> list = null;
        if ("".equals(searchStatus)) {
            // what is 'any' used for? Temporarily commented them out.
            // when click 'program list' on PMM, it will not display community programs, only display bed and service programs.
            // searchStatus = "Any";
            // searchType = "Any";
            // searchFacilityId = "0";

            if (userrole.indexOf("admin") != -1) {
                list = programManager.getAllPrograms();
            } else {
                list = programManager.getProgramDomain(providerNo);
            }
        } else {
            list = programManager.getAllPrograms(searchStatus, searchType, Integer.parseInt(searchFacilityId));
        }
        request.setAttribute("programs", list);
        request.setAttribute("facilities", facilityDao.findAll(true));

        this.setSearchStatus(searchStatus);
        this.setSearchType(searchType);
        this.setSearchFacilityId(searchFacilityId);

        LogAction.log("read", "full program list", "", request);

        return "list";
    }

    public String edit() {
        String id = request.getParameter("id");

        request.setAttribute("view.tab", request.getParameter("view.tab"));
        if (request.getParameter("newVacancy") != null && "true".equals(request.getParameter("newVacancy")))
            request.setAttribute("vacancyOrTemplateId", "");
        else
            request.setAttribute("vacancyOrTemplateId", getVacancyOrTemplateId());


        if (id != null && id != "") {
            Program program = programManager.getProgram(id);

            if (program == null) {
                addActionMessage(getText("program.missing"));
                return list();
            }

            this.setProgram(program);
            request.setAttribute("oldProgram", program);

            List<FunctionalCentre> functionalCentres = functionalCentreDao.findAll();
            Collections.sort(functionalCentres, FunctionalCentre.ACCOUNT_ID_COMPARATOR);
            request.setAttribute("functionalCentres", functionalCentres);

            // request.setAttribute("programFirstSignature",programManager.getProgramFirstSignature(Integer.valueOf(id)));

            // programForm.set("programFirstSignature",programManager.getProgramFirstSignature(Integer.valueOf(id)));

            // List<ProgramSignature> pss = programManager.getProgramSignatures(Integer.valueOf(id));
            // programForm.set("programSignatures", (ProgramSignature[] ) pss.toArray(new ProgramSignature[pss.size()]));
            // request.setAttribute("programSignatures",programManager.getProgramSignatures(Integer.valueOf(id)));
        }

        setEditAttributes(request, id);

        if (id != null && id != "") {
            request.setAttribute("service_restrictions", clientRestrictionManager.getActiveRestrictionsForProgram(Integer.valueOf(id), new Date()));
            request.setAttribute("disabled_service_restrictions", clientRestrictionManager.getDisabledRestrictionsForProgram(Integer.valueOf(id), new Date()));
        }
        return "edit";
    }

    /**
     * Loads program signatures for display.
     * <p>
     * Retrieves all signatures associated with a program for audit tracking.
     * Signatures are created whenever a program is modified to track who made
     * changes and when.
     *
     * Expected request parameters:
     * <ul>
     * <li>programId - String ID of the program</li>
     * </ul>
     *
     * @return String "programSignatures" to forward to signatures view
     */
    public String programSignatures() {
        String programId = request.getParameter("programId");
        if (programId != null) {
            // List<ProgramSignature> pss = programManager.getProgramSignatures(Integer.valueOf(programId));
            // programForm.set("programSignatures", (ProgramSignature[] ) pss.toArray(new ProgramSignature[pss.size()]));
            request.setAttribute("programSignatures", programManager.getProgramSignatures(Integer.valueOf(programId)));
        }
        return "programSignatures";
    }

    public String add() {
        this.setProgram(new Program());

        setEditAttributes(request, null);

        return "edit";
    }


    public String assign_role() {

        Program program = this.getProgram();
        ProgramProvider provider = this.getProvider();

        ProgramProvider pp = programManager.getProgramProvider(String.valueOf(provider.getId()));

        pp.setRoleId(provider.getRoleId());

        programManager.saveProgramProvider(pp);
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - assign role", String.valueOf(program.getId()), request);
        this.setProvider(new ProgramProvider());

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String assign_team() {
        Program program = this.getProgram();
        ProgramProvider provider = this.getProvider();

        ProgramProvider pp = programManager.getProgramProvider(String.valueOf(provider.getId()));

        ProgramTeam team = programManager.getProgramTeam(request.getParameter("teamId"));

        if (team != null) {
            pp.getTeams().add(team);
        }

        programManager.saveProgramProvider(pp);
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - assign team", String.valueOf(program.getId()), request);
        this.setProvider(new ProgramProvider());

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String assign_team_client() {
        Program program = this.getProgram();
        Admission admission = this.getAdmission();

        Admission ad = admissionManager.getAdmission(admission.getId());

        ad.setTeamId(admission.getTeamId());

        admissionManager.saveAdmission(ad);
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - assign client to team", String.valueOf(program.getId()), request);

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String delete() {
        String id = request.getParameter("id");
        String name = request.getParameter("name");

        if (id == null) {
            return list();
        }

        /*
         * have to make sure 1) no clients 2) no queue
         */
        Program program = programManager.getProgram(id);
        if (program == null) {
            addActionMessage(getText("program.missing", name));
            return list();
        }

        int numAdmissions = admissionManager.getCurrentAdmissionsByProgramId(id).size();
        if (numAdmissions > 0) {
            addActionMessage(getText("program.delete.admission", name, String.valueOf(numAdmissions)));
            return list();
        }

        int numQueue = programQueueManager.getActiveProgramQueuesByProgramId(Long.valueOf(id)).size();
        if (numQueue > 0) {
            addActionMessage(getText("program.delete.queue", name, String.valueOf(numQueue)));
            return list();
        }

        programManager.removeProgram(id);
        programManager.deleteProgramProviderByProgramId(Long.valueOf(id));

        addActionMessage(getText("program.deleted", name));

        LogAction.log("write", "delete program", String.valueOf(program.getId()), request);

        return list();
    }

    public String delete_access() {
        Program program = this.getProgram();
        ProgramAccess access = this.getAccess();

        programManager.deleteProgramAccess(String.valueOf(access.getId()));
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - delete access", String.valueOf(program.getId()), request);

        this.setEditAttributes(request, String.valueOf(program.getId()));
        this.setAccess(new ProgramAccess());

        ProgramAccessCache.setAccessMap(program.getId());
        return edit();
    }

    public String delete_function() {

        Program program = this.getProgram();
        ProgramFunctionalUser function = this.getFunction();

        programManager.deleteFunctionalUser(String.valueOf(function.getId()));
        addActionMessage(getText("program.saved", program.getName()));
        LogAction.log("write", "edit program - delete function user", String.valueOf(program.getId()), request);

        this.setEditAttributes(request, String.valueOf(program.getId()));

        return edit();
    }

    public String delete_provider() {
        Program program = this.getProgram();
        ProgramProvider pp = this.getProvider();

        if (pp.getId() != null && pp.getId() >= 0) {
            programManager.deleteProgramProvider(String.valueOf(pp.getId()));
            addActionMessage(getText("program.saved", program.getName()));

            LogAction.log("write", "edit program - delete providers", String.valueOf(program.getId()), request);
        }
        this.setEditAttributes(request, String.valueOf(program.getId()));
        this.setProvider(new ProgramProvider());

        return edit();
    }

    public String delete_team() {
        Program program = this.getProgram();
        ProgramTeam team = this.getTeam();

        if (programManager.getAllProvidersInTeam(program.getId(), team.getId()).size() > 0 || programManager.getAllClientsInTeam(program.getId(), team.getId()).size() > 0) {
            addActionMessage(getText("program.team.not_empty", program.getName()));
            this.setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }

        programManager.deleteProgramTeam(String.valueOf(team.getId()));
        addActionMessage(getText("program.saved", program.getName()));

        this.setEditAttributes(request, String.valueOf(program.getId()));
        this.setFunction(new ProgramFunctionalUser());

        return edit();
    }

    public String edit_access() {
        Program program = this.getProgram();
        ProgramAccess access = this.getAccess();

        ProgramAccess pa = programManager.getProgramAccess(String.valueOf(access.getId()));

        if (pa == null) {
            addActionMessage(getText("program_access.missing"));
            setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }
        this.setAccess(pa);

        setEditAttributes(request, String.valueOf(program.getId()));

        ProgramAccessCache.setAccessMap(program.getId());

        return "edit";
    }

    public String edit_function() {

        Program program = this.getProgram();
        ProgramFunctionalUser function = this.getFunction();

        ProgramFunctionalUser pfu = programManager.getFunctionalUser(String.valueOf(function.getId()));

        if (pfu == null) {
            addActionMessage(getText("program_function.missing"));
            setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }
        this.setFunction(pfu);
        request.setAttribute("providerName", pfu.getProvider().getFormattedName());

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String edit_provider() {
        Program program = this.getProgram();
        ProgramProvider provider = this.getProvider();

        ProgramProvider pp = programManager.getProgramProvider(String.valueOf(provider.getId()));

        if (pp == null) {
            addActionMessage(getText("error.provider.missing"));
            setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }
        this.setProvider(pp);
        request.setAttribute("providerName", pp.getProvider().getFormattedName());

        LogAction.log("write", "edit program - edit providers", String.valueOf(program.getId()), request);

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String edit_team() {
        Program program = this.getProgram();
        ProgramTeam team = this.getTeam();

        ProgramTeam pt = programManager.getProgramTeam(String.valueOf(team.getId()));

        if (pt == null) {
            addActionMessage(getText("program_team.missing"));
            setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }
        this.setTeam(pt);
        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }


    public String remove_queue() {
        Program program = this.getProgram();

        ProgramQueue queue = this.getQueue();

        ProgramQueue fullQueue = programQueueManager.getProgramQueue(String.valueOf(queue.getId()));
        fullQueue.setStatus(ProgramQueue.STATUS_REMOVED);
        programQueueManager.saveProgramQueue(fullQueue);

        LogAction.log("write", "edit program - queue removal", String.valueOf(program.getId()), request);

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String remove_remote_queue() {
        Program program = this.getProgram();
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

        setEditAttributes(request, String.valueOf(program.getId()));
        return "edit";
    }

    public String remove_team() {
        Program program = this.getProgram();
        ProgramProvider provider = this.getProvider();

        ProgramProvider pp = programManager.getProgramProvider(String.valueOf(provider.getId()));

        String teamId = request.getParameter("teamId");

        if (teamId != null && teamId.length() > 0) {
            long team_id = Long.valueOf(teamId);

            for (Object o : pp.getTeams()) {
                ProgramTeam team = (ProgramTeam) o;

                if (team.getId() == team_id) {
                    pp.getTeams().remove(team);
                    break;
                }
            }

            programManager.saveProgramProvider(pp);
        }
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - assign team (removal)", String.valueOf(program.getId()), request);
        this.setProvider(new ProgramProvider());

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String save_restriction_settings() {
        Program program = this.getProgram();
        Program realProgram = programManager.getProgram(program.getId());

        if (program.getMaxAllowed() < program.getNumOfMembers()) {
            addActionMessage(getText("program.max_too_small", program.getName()));
            setEditAttributes(request, String.valueOf(program.getId()));

            return edit();
        }

        Integer maxRestrictionDays = program.getMaximumServiceRestrictionDays();
        int defaultRestrictionDays = program.getDefaultServiceRestrictionDays();
        if (maxRestrictionDays != null && maxRestrictionDays != 0 && defaultRestrictionDays > maxRestrictionDays) {
            addActionMessage(getText("program.default_restriction_exceeds_maximum", defaultRestrictionDays+"", ""+maxRestrictionDays));
            setEditAttributes(request, String.valueOf(program.getId()));

            return edit();
        }

        // copy over modified attributes
        realProgram.setDefaultServiceRestrictionDays(defaultRestrictionDays);
        if (maxRestrictionDays != null && maxRestrictionDays != 0)
            realProgram.setMaximumServiceRestrictionDays(maxRestrictionDays);

        // save program & sign the modification of the program
        programManager.saveProgram(realProgram);

        ProgramSignature programSignature = new ProgramSignature();
        programSignature.setProgramId(program.getId());
        programSignature.setProgramName(program.getName());
        String providerNo = (String) request.getSession().getAttribute("user");
        programSignature.setProviderId(providerNo);
        programSignature.setProviderName(providerManager.getProvider(providerNo).getFormattedName());
        programSignature.setCaisiRoleName("n/a");
        Date now = new Date();
        programSignature.setUpdateDate(now);
        programManager.saveProgramSignature(programSignature);

        addActionMessage(getText("program.saved", program.getName()));
        LogAction.log("write", "edit program", String.valueOf(program.getId()), request);

        setEditAttributes(request, String.valueOf(program.getId()));

        return edit();
    }

    public String save() {
        Program program = this.getProgram();

        try {
            program.setFacilityId(Integer.parseInt(request.getParameter("program.facilityId")));
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        if (request.getParameter("program.allowBatchAdmission") == null) {
            program.setAllowBatchAdmission(false);
        }
        if (request.getParameter("program.allowBatchDischarge") == null) {
            program.setAllowBatchDischarge(false);
        }
        if (request.getParameter("program.hic") == null) {
            program.setHic(false);
        }
        if (request.getParameter("program.holdingTank") == null) {
            program.setHoldingTank(false);
        }
        if (request.getParameter("program.transgender") == null) program.setTransgender(false);
        if (request.getParameter("program.firstNation") == null) program.setFirstNation(false);
        if (request.getParameter("program.alcohol") == null) program.setAlcohol(false);
        if (request.getParameter("program.physicalHealth") == null) program.setPhysicalHealth(false);
        if (request.getParameter("program.mentalHealth") == null) program.setMentalHealth(false);
        if (request.getParameter("program.enableOCAN") == null) program.setEnableOCAN(false);
        if (request.getParameter("program.housing") == null) program.setHousing(false);
        if (request.getParameter("program.enableEncounterTime") == null) program.setEnableEncounterTime(false);
        if (request.getParameter("program.enableEncounterTransportationTime") == null)
            program.setEnableEncounterTransportationTime(false);

        request.setAttribute("oldProgram", program);

        // if a program has a client in it, you cannot make it inactive
        if (request.getParameter("program.programStatus").equals("inactive")) {
            if (!("External".equals(request.getParameter("program.type")))) {
                // Admission ad = admissionManager.getAdmission(Long.valueOf(request.getParameter("id")));
                List admissions = admissionManager.getCurrentAdmissionsByProgramId(String.valueOf(program.getId()));
                if (admissions.size() > 0) {
                    addActionMessage(getText("program.client_in_the_program", program.getName()));
                    setEditAttributes(request, String.valueOf(program.getId()));
                    return "edit";
                }
                int numQueue = programQueueManager.getActiveProgramQueuesByProgramId((long) program.getId()).size();
                if (numQueue > 0) {
                    addActionMessage(getText("program.client_in_the_queue", program.getName(), String.valueOf(numQueue)));
                    setEditAttributes(request, String.valueOf(program.getId()));
                    return "edit";
                }
            }
        }

        if (program.isHoldingTank()) {
            addActionMessage(getText("program.invalid_holding_tank"));
            setEditAttributes(request, String.valueOf(program.getId()));
            return "edit";
        }

        saveProgram(request, program);
        addActionMessage(getText("program.saved", program.getName()));

        ProgramAccessCache.setAccessMap(program.getId());

        LogAction.log("write", "edit program", String.valueOf(program.getId()), request);

        setEditAttributes(request, String.valueOf(program.getId()));

        return edit();
    }

    private void saveProgram(HttpServletRequest request, Program program) {
        programManager.saveProgram(program);

        // if there were some changes happened to the program, then save the signature of this program
        Program oldProgram = new Program();
        oldProgram.setMaxAllowed(Integer.valueOf(request.getParameter("old_maxAllowed")));
        oldProgram.setName(request.getParameter("old_name"));
        oldProgram.setDescription(request.getParameter("old_descr"));
        oldProgram.setType(request.getParameter("old_type"));
        oldProgram.setAddress(request.getParameter("old_address"));
        oldProgram.setPhone(request.getParameter("old_phone"));
        oldProgram.setFax(request.getParameter("old_fax"));
        oldProgram.setUrl(request.getParameter("old_url"));
        oldProgram.setEmail(request.getParameter("old_email"));
        oldProgram.setEmergencyNumber(request.getParameter("old_emergencyNumber"));
        oldProgram.setLocation(request.getParameter("old_location"));
        oldProgram.setProgramStatus(request.getParameter("old_programStatus"));
        oldProgram.setManOrWoman(request.getParameter("old_manOrWoman"));
        oldProgram.setAbstinenceSupport(request.getParameter("old_abstinenceSupport"));
        oldProgram.setExclusiveView(request.getParameter("old_exclusiveView"));

        oldProgram.setHoldingTank(getParameterAsBoolean(request, "old_holdingTank"));
        oldProgram.setAllowBatchAdmission(getParameterAsBoolean(request, "old_allowBatchAdmission"));
        oldProgram.setAllowBatchDischarge(getParameterAsBoolean(request, "old_allowBatchDischarge"));
        oldProgram.setHic(getParameterAsBoolean(request, "old_hic"));
        oldProgram.setTransgender(getParameterAsBoolean(request, "old_transgender"));
        oldProgram.setFirstNation(getParameterAsBoolean(request, "old_firstNation"));
        oldProgram.setAlcohol(getParameterAsBoolean(request, "old_alcohol"));
        oldProgram.setPhysicalHealth(getParameterAsBoolean(request, "old_physicalHealth"));
        oldProgram.setMentalHealth(getParameterAsBoolean(request, "old_mentalHealth"));
        oldProgram.setEnableOCAN(getParameterAsBoolean(request, "old_enableOCAN"));
        oldProgram.setHousing(getParameterAsBoolean(request, "old_housing"));
        oldProgram.setFacilityId(getParameterAsInteger(request, "old_facility_id", 0));
        oldProgram.setEnableEncounterTime(getParameterAsBoolean(request, "old_enableEncounterTime"));
        oldProgram.setEnableEncounterTransportationTime(getParameterAsBoolean(request, "old_enableEncounterTransportationTime"));

        if (isChanged(program, oldProgram)) {
            ProgramSignature programSignature = new ProgramSignature();
            programSignature.setProgramId(program.getId());
            programSignature.setProgramName(program.getName());
            String providerNo = (String) request.getSession().getAttribute("user");
            programSignature.setProviderId(providerNo);
            programSignature.setProviderName(providerManager.getProvider(providerNo).getFormattedName());
            programSignature.setCaisiRoleName("n/a");
            Date now = new Date();
            programSignature.setUpdateDate(now);

            programManager.saveProgramSignature(programSignature);
        }
    }

    public String viewVacancyTemplate() {
        String templateId = request.getParameter("templateId");

        request.setAttribute("templateId", templateId);

        List<Criteria> criterias = criteriaDAO.getCriteriaByTemplateId(Integer.valueOf(templateId));
        request.setAttribute("criterias", criterias);

        return "edit";
    }

    public String chooseTemplate() {

        Program program = this.getProgram();

        this.setProgram(program);

        String templateId = request.getParameter("requiredVacancyTemplateId");

        request.setAttribute("view.tab", "vacancy_add");

        VacancyTemplate vt = vacancyTemplateDAO.getVacancyTemplate(Integer.valueOf(templateId));

        request.setAttribute("selectedTemplate", vt);

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String save_vacancy() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        Program program = this.getProgram();

        HashMap<String, String[]> parameters = new HashMap(request.getParameterMap());

        Integer templateId = null;
        String templateId_str = request.getParameter("requiredVacancyTemplateId");
        if (!StringUtils.isBlank(templateId_str))
            templateId = Integer.valueOf(templateId_str);

        String dateClosed = parameters.get("dateClosed")[0];
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", request.getLocale());
        Date dateClosedFormatted = new Date();
        if (!StringUtils.isBlank(dateClosed)) {
            try {
                dateClosedFormatted = formatter.parse(dateClosed);
            } catch (Exception e) {
                logger.warn("warn", e);
            }
        }

        Vacancy vacancy = new Vacancy();
        String vacancyId = request.getParameter("vacancyId");
        if (!StringUtils.isBlank(vacancyId)) {
            vacancy = VacancyTemplateManager.getVacancyById(Integer.valueOf(vacancyId));
            vacancy.setStatus(parameters.get("vacancyStatus")[0]);
            vacancy.setReasonClosed(parameters.get("reasonClosed")[0]);
            vacancy.setDateClosed(dateClosedFormatted);
            VacancyTemplateManager.saveVacancy(vacancy);

            Facility f = loggedInInfo.getCurrentFacility();
            if (f.getAssignNewVacancyTicklerProvider() != null && f.getAssignNewVacancyTicklerProvider().length() > 0
                    && f.getAssignNewVacancyTicklerDemographic() != null && f.getAssignNewVacancyTicklerDemographic() > 0) {
                createWaitlistNotificationTickler(loggedInInfo, f, vacancy, loggedInInfo.getLoggedInProviderNo());
            }
        } else {
            vacancy.setTemplateId(templateId);
            vacancy.setName(request.getParameter("vacancyName"));
            vacancy.setDateCreated(new Date());
            vacancy.setWlProgramId(program.getId());
            vacancy.setStatus(parameters.get("vacancyStatus")[0]);
            vacancy.setReasonClosed(parameters.get("reasonClosed")[0]);
            vacancy.setDateClosed(dateClosedFormatted);
            VacancyTemplateManager.saveVacancy(vacancy);

            Facility f = loggedInInfo.getCurrentFacility();
            if (f.getAssignNewVacancyTicklerProvider() != null && f.getAssignNewVacancyTicklerProvider().length() > 0
                    && f.getAssignNewVacancyTicklerDemographic() != null && f.getAssignNewVacancyTicklerDemographic() > 0) {
                createWaitlistNotificationTickler(loggedInInfo, f, vacancy, loggedInInfo.getLoggedInProviderNo());
            }

            List<Criteria> criteriaList = VacancyTemplateManager.getRefinedCriteriasByTemplateId(templateId);
            for (Criteria c : criteriaList) {
                CriteriaType type = VacancyTemplateManager.getCriteriaTypeById(c.getCriteriaTypeId());
                Criteria newCriteria = new Criteria();
                newCriteria.setVacancyId(vacancy.getId());
                newCriteria.setMatchScoreWeight(1.0); //???

                if (c.getCanBeAdhoc() == 1) { //mandatory and not changeable, disabled in the form and the value not contained in request. But it should keep same value in new criteria.
                    newCriteria.setCanBeAdhoc(c.getCanBeAdhoc());
                    newCriteria.setCriteriaTypeId(c.getCriteriaTypeId());
                    newCriteria.setCriteriaValue(c.getCriteriaValue());
                    newCriteria.setRangeEndValue(c.getRangeEndValue());
                    newCriteria.setRangeStartValue(c.getRangeStartValue());
                    VacancyTemplateManager.saveCriteria(newCriteria);
                    continue;
                }

                String required = type.getFieldName().toLowerCase().replaceAll(" ", "_") + "Required";
                if (request.getParameter(required) == null)
                    newCriteria.setCanBeAdhoc(0);
                else
                    newCriteria.setCanBeAdhoc(Integer.valueOf(request.getParameter(required)));

                String targetName = "targetOf" + type.getFieldName().toLowerCase().replaceAll(" ", "_");
                String[] answers = parameters.get(targetName);

                saveTemplateOrVacancy(parameters, answers, type, newCriteria, request);
            }

            //Call Match Manager
            //TODO do the testing
            try {
                matchManager.processEvent(vacancy, IMatchManager.Event.VACANCY_CREATED);
            } catch (MatchManagerException e) {
                //log.error("Match manager failed", e);
            }
        }

        setEditAttributes(request, String.valueOf(program.getId()));
        return edit();

    }

    private void createWaitlistWithdrawnNotificationTickler(LoggedInInfo loggedInInfo, Facility facility, Vacancy vacancy, String creatorProviderNo) {
        Tickler t = new Tickler();
        t.setCreator(creatorProviderNo);
        t.setDemographicNo(facility.getVacancyWithdrawnTicklerDemographic());
        t.setMessage("vacancy=[" + vacancy.getName() + "] withdrawn");
        t.setProgramId(vacancy.getWlProgramId());
        t.setServiceDate(new Date());
        t.setTaskAssignedTo(facility.getVacancyWithdrawnTicklerProvider());
        t.setUpdateDate(new Date());

        ticklerManager.addTickler(loggedInInfo, t);
    }

    private void createWaitlistNotificationTickler(LoggedInInfo loggedInInfo, Facility facility, Vacancy vacancy, String creatorProviderNo) {
        Tickler t = new Tickler();
        t.setCreator(creatorProviderNo);
        t.setDemographicNo(facility.getAssignNewVacancyTicklerDemographic());
        t.setMessage("New vacancy=[" + vacancy.getName() + "]");
        t.setProgramId(vacancy.getWlProgramId());
        t.setServiceDate(new Date());
        t.setTaskAssignedTo(facility.getAssignNewVacancyTicklerProvider());
        t.setUpdateDate(new Date());

        ticklerManager.addTickler(loggedInInfo, t);
    }

    public String save_vacancy_template() {

        Program program = this.getProgram();

        HashMap<String, String[]> parameters = new HashMap(request.getParameterMap());

        //save tmeplate
        String vacancyTemplateId = request.getParameter("vacancyOrTemplateId");
        VacancyTemplate vacancyTemplate = VacancyTemplateManager.createVacancyTemplate(vacancyTemplateId);
        vacancyTemplate.setName(request.getParameter("templateName"));
        if (request.getParameter("templateActive") == null) {
            vacancyTemplate.setActive(false);
        }
        vacancyTemplate.setWlProgramId(Integer.parseInt(request.getParameter("programId")));
        VacancyTemplateManager.saveVacancyTemplate(vacancyTemplate);

        //Save Criteria
        //List<CriteriaType> typeList = VacancyTemplateManager.getAllCriteriaTypes();
        List<CriteriaType> typeList = VacancyTemplateManager.getAllCriteriaTypesByWlProgramId(Integer.parseInt(request.getParameter("programId")));
        for (CriteriaType type : typeList) {
            Criteria criteria = new Criteria();
            criteria.setTemplateId(vacancyTemplate.getId());
            String required = type.getFieldName().toLowerCase().replaceAll(" ", "_") + "Required";
            criteria.setCanBeAdhoc(request.getParameter(required) == null ? 0 : Integer.valueOf(request.getParameter(required)));
            String targetName = "targetOf" + type.getFieldName().toLowerCase().replaceAll(" ", "_");
            String[] answers = parameters.get(targetName);

            saveTemplateOrVacancy(parameters, answers, type, criteria, request);

        }

        setEditAttributes(request, String.valueOf(program.getId()));

        return edit();
    }

    private void saveTemplateOrVacancy(HashMap<String, String[]> parameters, String[] answers, CriteriaType type, Criteria criteria, HttpServletRequest request) {

        if (type.getFieldType().equalsIgnoreCase("select_multiple") || type.getFieldType().equalsIgnoreCase("select_multiple_narrowing")) {

            saveCriteria(criteria, answers);

        } else if (type.getFieldType().equalsIgnoreCase("select_one_range")) {

            String sourceName = "sourceOf" + type.getFieldName().toLowerCase().replaceAll(" ", "_");
            String[] singleAnswers = parameters.get(sourceName);
            String answer = "";
            if (singleAnswers != null && singleAnswers.length > 0)
                answer = singleAnswers[0];
            criteria.setCriteriaValue(answer);

            String minName = type.getFieldName().toLowerCase().replaceAll(" ", "_") + "Minimum";
            String maxName = type.getFieldName().toLowerCase().replaceAll(" ", "_") + "Maximum";
            if (!StringUtils.isBlank(request.getParameter(minName)))
                criteria.setRangeStartValue(Integer.valueOf(request.getParameter(minName)));
            if (!StringUtils.isBlank(request.getParameter(maxName)))
                criteria.setRangeEndValue(Integer.valueOf(request.getParameter(maxName)));

            criteria.setCriteriaTypeId(type.getId());

            VacancyTemplateManager.saveCriteria(criteria);

        } else if (type.getFieldType().equalsIgnoreCase("select_one")) {

            String sourceName = "sourceOf" + type.getFieldName().toLowerCase().replaceAll(" ", "_");
            String[] singleAnswers = parameters.get(sourceName);
            String answer = "";
            if (singleAnswers != null && singleAnswers.length > 0)
                answer = singleAnswers[0];
            criteria.setCriteriaValue(answer);

            criteria.setCriteriaTypeId(type.getId());

            VacancyTemplateManager.saveCriteria(criteria);

        } else if (type.getFieldType().equalsIgnoreCase("number")) {

            String numberName = type.getFieldName().toLowerCase().replaceAll(" ", "_") + "Number";
            String[] numberAnswers = parameters.get(numberName);
            String number = "";
            if (numberAnswers != null && numberAnswers.length > 0)
                number = numberAnswers[0];
            criteria.setCriteriaValue(number);

            criteria.setCriteriaTypeId(type.getId());

            VacancyTemplateManager.saveCriteria(criteria);

        } else {
            //do nothing for now
        }
    }

    private void saveCriteria(Criteria criteria, String[] paramValues) {
        if (paramValues == null || paramValues.length < 1)
            return;
        for (int i = 0; i < paramValues.length; i++) {
            CriteriaTypeOption option = criteriaTypeOptionDAO.getByValue(paramValues[i]);
            if (option != null) {
                criteria.setCriteriaTypeId(option.getCriteriaTypeId());
            }

            //criteria.setMatchScoreWeight(Double.parseDouble("0.5"));
            VacancyTemplateManager.saveCriteria(criteria);

            //Save criteria_selection_option
            CriteriaSelectionOption selectedOption = new CriteriaSelectionOption();
            selectedOption.setCriteriaId(criteria.getId());
            //selectedOption.setOptionValue(String.valueOf(option.getId()));
            selectedOption.setOptionValue(option.getOptionValue());
            VacancyTemplateManager.saveCriteriaSelectedOption(selectedOption);
        }
    }

    public String save_access() {
        Program program = this.getProgram();
        ProgramAccess access = this.getAccess();

        access.setProgramId(program.getId().longValue());

        if (programManager.getProgramAccess(String.valueOf(access.getProgramId()), access.getAccessTypeId()) != null) {
            addActionMessage(getText("program.duplicate_access", program.getName()));
            this.setAccess(new ProgramAccess());
            setEditAttributes(request, String.valueOf(program.getId()));
            return "edit";
        }

        String roles[] = request.getParameterValues("checked_role");
        if (roles != null) {
            if (access.getRoles() == null) {
                access.setRoles(new HashSet());
            }
            for (String role : roles) {
                access.getRoles().add(roleManager.getRole(role));
                //access.getRoles().add()
            }
        }

        programManager.saveProgramAccess(access);

        LogAction.log("write", "access", String.valueOf(program.getId()), request);
        addActionMessage(getText("program.saved", program.getName()));
        this.setAccess(new ProgramAccess());
        setEditAttributes(request, String.valueOf(program.getId()));

        ProgramAccessCache.setAccessMap(program.getId());

        return "edit";
    }

    public String save_function() {
        Program program = this.getProgram();
        ProgramFunctionalUser function = this.getFunction();
        function.setProgramId(program.getId().longValue());

        Long pid = programManager.getFunctionalUserByUserType(program.getId().longValue(), function.getUserTypeId());

        if (pid != null && function.getId().longValue() != pid.longValue()) {
            addActionMessage(getText("program_function.duplicate", program.getName()));
            this.setFunction(new ProgramFunctionalUser());
            setEditAttributes(request, String.valueOf(program.getId()));
            return "edit";
        }
        programManager.saveFunctionalUser(function);
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - save function user", String.valueOf(program.getId()), request);

        this.setFunction(new ProgramFunctionalUser());
        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String save_provider() {
        Program program = this.getProgram();
        ProgramProvider provider = this.getProvider();

        provider.setProgramId(program.getId().longValue());

        if (programManager.getProgramProvider(provider.getProviderNo(), String.valueOf(program.getId())) != null) {
            addActionMessage(getText("program.provider.exists"));
            this.setProvider(new ProgramProvider());
            setEditAttributes(request, String.valueOf(program.getId()));
            return "edit";
        }

        programManager.saveProgramProvider(provider);
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - save providers", String.valueOf(program.getId()), request);
        this.setProvider(new ProgramProvider());
        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String save_team() {
        Program program = this.getProgram();
        ProgramTeam team = this.getTeam();

        team.setProgramId(program.getId());

        if (programManager.teamNameExists(program.getId(), team.getName())) {
            addActionMessage(getText("program_team.duplicate", team.getName()));
            this.setTeam(new ProgramTeam());
            setEditAttributes(request, String.valueOf(program.getId()));
            return "edit";
        }

        programManager.saveProgramTeam(team);

        LogAction.log("write", "edit program - save team", String.valueOf(program.getId()), request);
        addActionMessage(getText("program.saved", program.getName()));
        this.setTeam(new ProgramTeam());
        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    private void setEditAttributes(HttpServletRequest request, String programId) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (programId != null && programId != "") {
            request.setAttribute("id", programId);
            request.setAttribute("programName", programManager.getProgram(programId).getName());
            request.setAttribute("providers", programManager.getProgramProviders(programId));
            request.setAttribute("functional_users", programManager.getFunctionalUsers(programId));

            List teams = programManager.getProgramTeams(programId);

            for (Object team1 : teams) {
                ProgramTeam team = (ProgramTeam) team1;

                team.setProviders(programManager.getAllProvidersInTeam(Integer.valueOf(programId), team.getId()));
                team.setAdmissions(programManager.getAllClientsInTeam(Integer.valueOf(programId), team.getId()));
            }

            request.setAttribute("teams", teams);
            request.setAttribute("client_statuses", programManager.getProgramClientStatuses(Integer.valueOf(programId)));

            //this can be pretty big
            if (request.getAttribute("view.tab") != null && request.getAttribute("view.tab").equals("Clients"))
                request.setAttribute("admissions", admissionManager.getCurrentAdmissionsByProgramId(programId));

            request.setAttribute("accesses", programManager.getProgramAccesses(programId));
            request.setAttribute("queue", programQueueManager.getActiveProgramQueuesByProgramId(Long.valueOf(programId)));

            if (CaisiIntegratorManager.isEnableIntegratedReferrals(loggedInInfo.getCurrentFacility())) {
                request.setAttribute("remoteQueue", getRemoteQueue(loggedInInfo, Integer.parseInt(programId)));
            }

            request.setAttribute("programFirstSignature", programManager.getProgramFirstSignature(Integer.valueOf(programId)));
        }

        request.setAttribute("roles", roleManager.getRoles());
        request.setAttribute("functionalUserTypes", programManager.getFunctionalUserTypes());

        request.setAttribute("accessTypes", programManager.getAccessTypes());

        request.setAttribute("facilities", facilityDao.findAll(true));
    }

    public static class RemoteQueueEntry {
        private Referral remoteReferral = null;
        private String clientName = null;
        private String providerName = null;

        public Referral getReferral() {
            return remoteReferral;
        }

        public void setReferral(Referral remoteReferral) {
            this.remoteReferral = remoteReferral;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

    }

    protected List<RemoteQueueEntry> getRemoteQueue(LoggedInInfo loggedInInfo, int programId) {
        try {
            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            ReferralWs referralWs = CaisiIntegratorManager.getReferralWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            List<Referral> remoteReferrals = referralWs.getReferralsToProgram(programId);

            ArrayList<RemoteQueueEntry> results = new ArrayList<RemoteQueueEntry>();
            for (Referral remoteReferral : remoteReferrals) {
                RemoteQueueEntry remoteQueueEntry = new RemoteQueueEntry();
                remoteQueueEntry.setReferral(remoteReferral);

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

    public String delete_status() {
        Program program = this.getProgram();
        ProgramClientStatus status = this.getClient_status();

        if (programManager.getAllClientsInStatus(program.getId(), status.getId()).size() > 0) {
            addActionMessage(getText("program.status.not_empty", program.getName()));

            this.setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }

        programManager.deleteProgramClientStatus(String.valueOf(status.getId()));
        addActionMessage(getText("program.saved", program.getName()));

        this.setEditAttributes(request, String.valueOf(program.getId()));
        this.setFunction(new ProgramFunctionalUser());

        return edit();
    }

    public String edit_status() {
        Program program = this.getProgram();
        ProgramClientStatus status = this.getClient_status();;

        ProgramClientStatus pt = programManager.getProgramClientStatus(String.valueOf(status.getId()));

        if (pt == null) {
            addActionMessage(getText("program_status.missing"));
            setEditAttributes(request, String.valueOf(program.getId()));
            return edit();
        }
        this.setClient_status(pt);
        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String save_status() {
        Program program = this.getProgram();
        ProgramClientStatus status = this.getClient_status();;

        status.setProgramId(program.getId());

        if (programManager.clientStatusNameExists(program.getId(), status.getName())) {
            addActionMessage(getText("program_status.duplicate", status.getName()));
            this.setClient_status(new ProgramClientStatus());
            setEditAttributes(request, String.valueOf(program.getId()));
            return "edit";
        }

        programManager.saveProgramClientStatus(status);

        LogAction.log("write", "edit program - save status", String.valueOf(program.getId()), request);

        addActionMessage(getText("program.saved", program.getName()));
        this.setClient_status(new ProgramClientStatus());
        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String assign_status_client() {
        Program program = this.getProgram();
        Admission admission = this.getAdmission();

        Admission ad = admissionManager.getAdmission(admission.getId());

        ad.setClientStatusId(admission.getClientStatusId());

        admissionManager.saveAdmission(ad);
        addActionMessage(getText("program.saved", program.getName()));

        LogAction.log("write", "edit program - assign client to status", String.valueOf(program.getId()), request);

        setEditAttributes(request, String.valueOf(program.getId()));

        return "edit";
    }

    public String disable_restriction() {
        ProgramClientRestriction prc = this.getRestriction();
        clientRestrictionManager.disableClientRestriction(prc.getId());

        return edit();
    }

    public String enable_restriction() {
        ProgramClientRestriction prc = this.getRestriction();
        clientRestrictionManager.enableClientRestriction(prc.getId());

        return edit();
    }

    public String activeTmplStatus() {
        String vacancyId = this.getVacancyOrTemplateId();
        try {
            int templateId = Integer.parseInt(vacancyId);
            VacancyTemplate vacTmpl = vacancyTemplateDAO.getVacancyTemplate(templateId);
            if (vacTmpl != null) {
                vacTmpl.setActive(true);
                try {
                    vacancyTemplateDAO.mergeVacancyTemplate(vacTmpl);
                } catch (Exception e) {
                    logger.debug(e.toString());
                }
            }
        } catch (Exception e) {
            logger.debug(e.toString());
        }

        return edit();
    }

    public String inactiveTmplStatus() {
        String vacancyId = this.getVacancyOrTemplateId();
        try {
            int templateId = Integer.parseInt(vacancyId);
            VacancyTemplate vacTmpl = vacancyTemplateDAO.getVacancyTemplate(templateId);
            if (vacTmpl != null) {
                vacTmpl.setActive(false);
                try {
                    vacancyTemplateDAO.mergeVacancyTemplate(vacTmpl);
                } catch (Exception e) {
                    logger.debug(e.toString());
                }
            }
        } catch (Exception e) {
            logger.debug(e.toString());
        }

        return edit();
    }

    public String saveVacancyStatus() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String vacancyId = request.getParameter("vacancyId");
        String status = request.getParameter("status");
        boolean success = true;
        String error = "";

        VacancyDao vacancyDao = SpringUtils.getBean(VacancyDao.class);
        Vacancy vacancy = vacancyDao.find(Integer.parseInt(vacancyId));

        if (vacancy != null) {
            vacancy.setStatus(status);
            vacancy.setStatusUpdateUser(loggedInInfo.getLoggedInProviderNo());
            vacancy.setStatusUpdateDate(new Date());
            vacancyDao.merge(vacancy);

            Facility f = loggedInInfo.getCurrentFacility();
            if (status.equals("Withdrawn") && f.getVacancyWithdrawnTicklerProvider() != null && f.getVacancyWithdrawnTicklerProvider().length() > 0
                    && f.getVacancyWithdrawnTicklerDemographic() != null && f.getVacancyWithdrawnTicklerDemographic() > 0) {
                createWaitlistWithdrawnNotificationTickler(loggedInInfo, f, vacancy, loggedInInfo.getLoggedInProviderNo());
            }

        } else {
            error = "Vacancy not found";
            success = false;
        }

        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("success", success);
        obj.put("error", error);

        try {
            response.getWriter().print(obj.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().warn("error writing json", e);
        }
        return null;
    }

    private boolean isChanged(Program program1, Program program2) {
        boolean changed = false;

        if (!eq(program1.getName(), program2.getName())
                || !eq(program1.getType(), program2.getType())
                || !eq(program1.getDescription(), program2.getDescription())
                || !eq(program1.getAddress(), program2.getAddress())
                || !eq(program1.getPhone(), program2.getPhone())
                || !eq(program1.getFax(), program2.getFax())
                || !eq(program1.getUrl(), program2.getUrl())
                || !eq(program1.getEmail(), program2.getEmail())
                || !eq(program1.getEmergencyNumber(), program2.getEmergencyNumber())
                || !eq(program1.getLocation(), program2.getLocation())
                || !eq(program1.getProgramStatus(), program2.getProgramStatus())
                || !eq(program1.getManOrWoman(), program2.getManOrWoman())
                || !eq(program1.getAbstinenceSupport(), program2.getAbstinenceSupport())
                || !eq(program1.getExclusiveView(), program2.getExclusiveView())
                || !eq(program1.getMaxAllowed(), program2.getMaxAllowed())
                || (program1.isHoldingTank() ^ program2.isHoldingTank())
                || (program1.isAllowBatchAdmission() ^ program2.isAllowBatchAdmission())
                || (program1.isAllowBatchDischarge() ^ program2.isAllowBatchDischarge())
                || (program1.isHic() ^ program2.isHic())
                || (program1.isTransgender() ^ program2.isTransgender())
                || (program1.isFirstNation() ^ program2.isFirstNation())
                || (program1.isAlcohol() ^ program2.isAlcohol())
                || (program1.isPhysicalHealth() ^ program2.isPhysicalHealth())
                || (program1.isMentalHealth() ^ program2.isMentalHealth())
                || (program1.getFacilityId() != program2.getFacilityId())
                || (program1.isHousing() ^ program2.isHousing()))

            changed = true;

        return changed;
    }

    private boolean eq(String s1, String s2) {
        return ((s1 == null && s2 == null) || s1.equals(s2));
    }

    private boolean eq(Integer in1, Integer in2) {
        return ((in1 == null && in2 == null) || in1.equals(in2));
    }

    @Required
    public void setClientRestrictionManager(ClientRestrictionManager clientRestrictionManager) {
        this.clientRestrictionManager = clientRestrictionManager;
    }

    public void setAdmissionManager(AdmissionManager mgr) {
        this.admissionManager = mgr;
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

    public void setRolesManager(RolesManager mgr) {
        this.roleManager = mgr;
    }

    /**
     * @return the VacancyTemplateManager
     */
    public VacancyTemplateManager getVacancyTemplateManager() {
        return this.vacancyTemplateManager;
    }

    /**
     * @param VacancyTemplateManager the VacancyTemplateManager to set
     */
    public void setVacancyTemplateManager(VacancyTemplateManager VacancyTemplateManager) {
        this.vacancyTemplateManager = VacancyTemplateManager;
    }

    protected Integer getParameterAsInteger(HttpServletRequest request, String name, Integer defaultVal) {
        String param = request.getParameter(name);
        if (!(param == null || param.equals("null") || param.equals(""))) {
            return Integer.valueOf(param);
        }
        return defaultVal;
    }

    protected Boolean getParameterAsBoolean(HttpServletRequest request, String name, Boolean defaultVal) {
        String param = request.getParameter(name);
        if (param != null) {
            return Boolean.valueOf(param);
        }
        return defaultVal;
    }

    protected Boolean getParameterAsBoolean(HttpServletRequest request, String name) {
        return getParameterAsBoolean(request, name, false);
    }

    private String searchStatus;
    private String searchType;
    private String searchFacilityId;
    private String vacancyOrTemplateId;

    public String getSearchStatus() {
        return searchStatus;
    }

    public void setSearchStatus(String searchStatus) {
        this.searchStatus = searchStatus;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getSearchFacilityId() {
        return searchFacilityId;
    }

    public void setSearchFacilityId(String searchFacilityId) {
        this.searchFacilityId = searchFacilityId;
    }

    public String getVacancyOrTemplateId() {
        return vacancyOrTemplateId;
    }

    public void setVacancyOrTemplateId(String vacancyOrTemplateId) {
        this.vacancyOrTemplateId = vacancyOrTemplateId;
    }
}
