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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.model.ProgramTeam;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.service.ProviderManager;
import ca.openosp.openo.PMmodule.web.formbean.StaffEditProgramContainer;
import ca.openosp.openo.commn.dao.FacilityDao;
import ca.openosp.openo.commn.dao.SecRoleDao;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for managing staff (healthcare providers) in the Program Management module.
 * <p>
 * This action handles comprehensive staff management including:
 * <ul>
 * <li>Listing and searching for providers by facility and program</li>
 * <li>Editing provider details and program assignments</li>
 * <li>Assigning providers to programs with specific roles</li>
 * <li>Managing provider team assignments within programs</li>
 * <li>Adding/removing providers from facilities</li>
 * <li>Managing program-specific provider roles and permissions</li>
 * </ul>
 * <p>
 * The action uses method-based routing where the "method" request parameter
 * determines which operation to execute. All properties are automatically
 * populated by Struts 2 property injection.
 *
 * @since 2005-10-01
 */
public class StaffManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();

    private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    private FacilityDao facilityDao = SpringUtils.getBean(FacilityDao.class);

    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);

    private ProviderManager providerManager = SpringUtils.getBean(ProviderManager.class);

    private SecRoleDao secRoleDao = SpringUtils.getBean(SecRoleDao.class);

    /**
     * Main execution method that routes to appropriate sub-methods based on method parameter.
     * <p>
     * Routes to the following operations:
     * <ul>
     * <li>list() - Default, lists all active providers (or filtered by search)</li>
     * <li>search() - Search providers by facility and/or program</li>
     * <li>edit() - Display provider edit form with program assignments</li>
     * <li>add_to_facility() - Add provider to a facility</li>
     * <li>remove_from_facility() - Remove provider from a facility</li>
     * <li>assign_role() - Assign or update provider's role in a program</li>
     * <li>assign_team() - Add provider to a team within a program</li>
     * <li>remove_team() - Remove provider from a team</li>
     * <li>remove_entry() - Remove provider from a program entirely</li>
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
        if ("add_to_facility".equals(method)) {
            return add_to_facility();
        } else if ("remove_from_facility".equals(method)) {
            return remove_from_facility();
        } else if ("search".equals(method)) {
            return search();
        } else if ("edit".equals(method)) {
            return edit();
        } else if ("assign_team".equals(method)) {
            return assign_team();
        } else if ("remove_team".equals(method)) {
            return remove_team();
        } else if ("assign_role".equals(method)) {
            return assign_role();
        } else if ("remove_entry".equals(method)) {
            return remove_entry();
        }
        return list();
    }

    public String add_to_facility() {

        int facilityId = Integer.parseInt(request.getParameter("facility_id"));
        String providerId = request.getParameter("id");

        providerDao.addProviderToFacility(providerId, facilityId);

        return edit();
    }

    public String remove_from_facility() {

        int facilityId = Integer.parseInt(request.getParameter("facility_id"));
        String providerId = request.getParameter("id");

        providerDao.removeProviderFromFacility(providerId, facilityId);

        return edit();
    }

    public void setEditAttributes(HttpServletRequest request, Provider provider) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        request.setAttribute("id", provider.getProviderNo());
        request.setAttribute("providerName", provider.getFormattedName());

        /* programs the providers is already a staff member of */
        List pp = programManager.getProgramProvidersByProvider(provider.getProviderNo());
        for (Iterator iter = pp.iterator(); iter.hasNext(); ) {
            ProgramProvider p = (ProgramProvider) iter.next();
            String name = programManager.getProgramName(String.valueOf(p.getProgramId()));
            if (name == null) {
                log.warn("Program doesn't have a name?");
                name = "";
            }
            p.setProgramName(name);
        }
        request.setAttribute("programs", sortProgramProviders(pp));

        List<Program> allPrograms = programManager.getCommunityPrograms(loggedInInfo.getCurrentFacility().getId());
        List<StaffEditProgramContainer> allProgramsInContainer = new ArrayList<StaffEditProgramContainer>();
        for (Program p : allPrograms) {
            StaffEditProgramContainer container = new StaffEditProgramContainer(p,
                    programManager.getProgramTeams(String.valueOf(p.getId())));
            allProgramsInContainer.add(container);
        }
        request.setAttribute("all_programs", allProgramsInContainer);
        // request.setAttribute("roles",roleManager.getRoles());
        request.setAttribute("roles", secRoleDao.findAll());

        List<Facility> allFacilities = facilityDao.findAll(true);
        request.setAttribute("all_facilities", allFacilities);

        List<Integer> providerFacilities = providerDao.getFacilityIds(provider.getProviderNo());
        request.setAttribute("providerFacilities", providerFacilities);
    }

    protected List sortProgramProviders(List pps) {
        Collections.sort(pps, new Comparator() {
            public int compare(Object o1, Object o2) {
                ProgramProvider pp1 = (ProgramProvider) o1;
                ProgramProvider pp2 = (ProgramProvider) o2;

                return pp1.getProgramName().compareTo(pp2.getProgramName());
            }
        });
        return pps;
    }


    /**
     * Lists all active healthcare providers.
     * <p>
     * Retrieves all active providers, facilities, and programs for display
     * in the staff management list view. This is the default action when
     * no method is specified.
     *
     * @return String "list" to forward to the list view
     */
    public String list() {
        request.setAttribute("providers", providerManager.getActiveProviders());
        request.setAttribute("facilities", facilityDao.findAll(true));
        request.setAttribute("programs", programManager.getAllPrograms("Any", "Any", 0));

        LogAction.log("read", "full providers list", "", request);
        return "list";
    }

    /** Facility ID for filtering provider search */
    private String facilityId;

    /** Program ID for filtering provider search */
    private String programId;

    /**
     * Searches for providers filtered by facility and/or program.
     * <p>
     * Filters the provider list based on the selected facility and program.
     * If facility is "0" (all facilities), program filter is reset. The
     * facilityId and programId properties are populated via Struts 2
     * property injection.
     *
     * @return String "list" to forward to the list view with filtered results
     */
    public String search() {
        if (facilityId == null)
            facilityId = "0";
        if (programId == null)
            programId = "0";
        if (facilityId.equals("0")) {
            this.setProgramId("0");
            programId = "0";
        }

        request.setAttribute("facilities", facilityDao.findAll(true));
        if (facilityId.equals("0") == false)
            request.setAttribute("programs", programManager.getAllPrograms("Any", "Any", Integer.valueOf(facilityId)));

        request.setAttribute("providers", providerManager.getActiveProviders(facilityId, programId));

        LogAction.log("read", "full providers list", "", request);
        return "list";
    }

    public String edit() {
        String id = request.getParameter("id");

        if (id != null) {
            Provider provider = providerManager.getProvider(id);

            if (provider == null) {
                addActionMessage(getText("error.provider.missing"));
                return list();
            }
            this.setProvider(provider);
            setEditAttributes(request, provider);
        }

        return "edit";
    }

    /** Program provider object for program assignment operations */
    private ProgramProvider program_provider;

    /** Provider object being edited or managed */
    private Provider provider;

    /**
     * Gets the program provider form object.
     *
     * @return ProgramProvider the program provider being edited
     */
    public ProgramProvider getProgram_provider() {
        return program_provider;
    }

    /**
     * Sets the program provider form object.
     * <p>
     * Called by Struts 2 during form submission to populate the object.
     *
     * @param program_provider ProgramProvider the program provider to set
     */
    public void setProgram_provider(ProgramProvider program_provider) {
        this.program_provider = program_provider;
    }

    public String assign_team() {
        Provider provider = this.getProvider();
        ProgramProvider pp = this.getProgram_provider();
        ProgramProvider existingPP = null;

        existingPP = programManager.getProgramProvider(provider.getProviderNo(), String.valueOf(pp.getProgramId()));
        String teamId = request.getParameter("teamId");
        ProgramTeam team = programManager.getProgramTeam(teamId);
        if (existingPP != null && team != null) {
            existingPP.getTeams().add(team);
            programManager.saveProgramProvider(existingPP);
        }

        setEditAttributes(request, providerManager.getProvider(provider.getProviderNo()));
        this.setProgram_provider(new ProgramProvider());
        return "edit";
    }

    public String remove_team() {
        Provider provider = this.getProvider();
        ProgramProvider pp = this.getProgram_provider();
        ProgramProvider existingPP = null;

        existingPP = programManager.getProgramProvider(provider.getProviderNo(), String.valueOf(pp.getProgramId()));
        String teamId = request.getParameter("teamId");
        if (existingPP != null && teamId != null && teamId.length() > 0) {
            long team_id = Long.valueOf(teamId);
            for (Iterator iter = existingPP.getTeams().iterator(); iter.hasNext(); ) {
                ProgramTeam temp = (ProgramTeam) iter.next();
                if (temp.getId() == team_id) {
                    existingPP.getTeams().remove(temp);
                    break;
                }
            }
            programManager.saveProgramProvider(existingPP);
        }

        setEditAttributes(request, providerManager.getProvider(provider.getProviderNo()));
        this.setProgram_provider(new ProgramProvider());
        return "edit";
    }

    public String assign_role() {
        Provider provider = this.getProvider();
        ProgramProvider pp = this.getProgram_provider();
        ProgramProvider existingPP = null;

        if ((existingPP = programManager.getProgramProvider(provider.getProviderNo(),
                String.valueOf(pp.getProgramId()))) != null) {
            if (pp.getRoleId().longValue() == 0) {
                programManager.deleteProgramProvider(String.valueOf(existingPP.getId()));
            } else {
                existingPP.setRoleId(pp.getRoleId());
                programManager.saveProgramProvider(existingPP);
            }
        } else {
            pp.setProviderNo(provider.getProviderNo());
            programManager.saveProgramProvider(pp);
        }

        setEditAttributes(request, providerManager.getProvider(provider.getProviderNo()));
        this.setProgram_provider(new ProgramProvider());
        return "edit";
    }

    public String remove_entry() {

        Provider provider = this.getProvider();
        ProgramProvider pp = this.getProgram_provider();

        programManager.deleteProgramProvider(String.valueOf(pp.getId()));

        setEditAttributes(request, providerManager.getProvider(provider.getProviderNo()));
        this.setProgram_provider(new ProgramProvider());
        return "edit";
    }

    public void setProgramManager(ProgramManager mgr) {
        this.programManager = mgr;
    }

    public void setProviderManager(ProviderManager mgr) {
        this.providerManager = mgr;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public String getProgramId() {
        return programId;
    }

    public void setProgramId(String programId) {
        this.programId = programId;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }
}
