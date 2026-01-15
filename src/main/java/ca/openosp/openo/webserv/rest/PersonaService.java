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
import java.util.List;
import java.util.ResourceBundle;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ca.openosp.OscarProperties;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Dashboard;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.AppManager;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.managers.DashboardManager;
import ca.openosp.openo.managers.MessagingManager;
import ca.openosp.openo.managers.PreferenceManager;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.webserv.rest.conversion.ProgramProviderConverter;
import ca.openosp.openo.webserv.rest.conversion.SecobjprivilegeConverter;
import ca.openosp.openo.webserv.rest.conversion.SecuserroleConverter;
import ca.openosp.openo.webserv.rest.to.AbstractSearchResponse;
import ca.openosp.openo.webserv.rest.to.DashboardPreferences;
import ca.openosp.openo.webserv.rest.to.GenericRESTResponse;
import ca.openosp.openo.webserv.rest.to.NavbarResponse;
import ca.openosp.openo.webserv.rest.to.PatientList;
import ca.openosp.openo.webserv.rest.to.PersonaResponse;
import ca.openosp.openo.webserv.rest.to.PersonaRightsResponse;
import ca.openosp.openo.webserv.rest.to.model.MenuItemTo1;
import ca.openosp.openo.webserv.rest.to.model.MenuTo1;
import ca.openosp.openo.webserv.rest.to.model.NavBarMenuTo1;
import ca.openosp.openo.webserv.rest.to.model.PatientListConfigTo1;
import ca.openosp.openo.webserv.rest.to.model.ProgramProviderTo1;
import ca.openosp.openo.webserv.rest.util.ClinicalConnectUtil;
import org.springframework.beans.factory.annotation.Autowired;


@Path("/persona")
@Consumes(MediaType.APPLICATION_JSON)
public class PersonaService extends AbstractServiceImpl {
    protected Logger logger = MiscUtils.getLogger();


    @Autowired
    private ProgramManager2 programManager2;

    @Autowired
    private MessagingManager messagingManager;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private ConsultationManager consultationManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private AppManager appManager;

    @Autowired
    private DashboardManager dashboardManager;


    @GET
    @Path("/rights")
    @Produces("application/json")
    public PersonaRightsResponse getMyRights() {
        PersonaRightsResponse response = new PersonaRightsResponse();

        SecuserroleConverter converter = new SecuserroleConverter();
        response.setRoles(converter.getAllAsTransferObjects(getLoggedInInfo(), securityInfoManager.getRoles(getLoggedInInfo())));

        SecobjprivilegeConverter converter2 = new SecobjprivilegeConverter();
        response.setPrivileges(converter2.getAllAsTransferObjects(getLoggedInInfo(), securityInfoManager.getSecurityObjects(getLoggedInInfo())));

        return response;
    }

    @GET
    @Path("/hasRight")
    @Produces("application/json")
    public GenericRESTResponse hasRight(@QueryParam("objectName") String objectName, @QueryParam("privilege") String privilege, @QueryParam("demographicNo") String demographicNo) {
        GenericRESTResponse response = new GenericRESTResponse();
        response.setSuccess(securityInfoManager.hasPrivilege(getLoggedInInfo(), objectName, privilege, demographicNo));

        return response;
    }

    @POST
    @Path("/hasRights")
    @Consumes("application/json")
    @Produces("application/json")
    public AbstractSearchResponse<Boolean> hasRights(ObjectNode json) {
        AbstractSearchResponse<Boolean> response = new AbstractSearchResponse<Boolean>();

        ArrayNode ja = (ArrayNode) json.get("items");
        for (int x = 0; x < ja.size(); x++) {
            ObjectNode o = (ObjectNode) ja.get(x);
            String objectName = o.get("objectName") != null ? o.get("objectName").asText() : null;
            String privilege = o.get("privilege") != null ? o.get("privilege").asText() : null;
            Integer demographicNo = null;
            if (o.has("demographicNo")) {
                demographicNo = o.get("demographicNo") != null ? o.get("demographicNo").asInt() : null;
            }
            response.getContent().add(securityInfoManager.hasPrivilege(getLoggedInInfo(), objectName, privilege, (demographicNo != null) ? demographicNo.toString() : null));
        }
        response.setTotal(response.getContent().size());

        return response;
    }

    @POST
    @Path("/isAllowedAccessToPatientRecord")
    @Consumes("application/json")
    @Produces("application/json")
    public AbstractSearchResponse<Boolean> isAllowedAccessToPatientRecord(ObjectNode json) {
        AbstractSearchResponse<Boolean> response = new AbstractSearchResponse<Boolean>();

        Integer demographicNo = json.get("demographicNo") != null ? json.get("demographicNo").asInt() : null;

        response.getContent().add(securityInfoManager.isAllowedAccessToPatientRecord(getLoggedInInfo(), demographicNo));
        response.setTotal(response.getContent().size());

        return response;
    }

    @GET
    @Path("/navbar")
    @Produces("application/json")
    public NavbarResponse getMyNavbar() {
        Provider provider = getCurrentProvider();
        ResourceBundle bundle = getResourceBundle();

        NavbarResponse result = new NavbarResponse();

        /* program domain, current program */
        List<ProgramProvider> ppList = programManager2.getProgramDomain(getLoggedInInfo(), provider.getProviderNo());
        ProgramProviderConverter ppConverter = new ProgramProviderConverter();
        List<ProgramProviderTo1> programDomain = new ArrayList<ProgramProviderTo1>();

        for (ProgramProvider pp : ppList) {
            programDomain.add(ppConverter.getAsTransferObject(getLoggedInInfo(), pp));
        }
        result.setProgramDomain(programDomain);

        ProgramProvider pp = programManager2.getCurrentProgramInDomain(getLoggedInInfo(), provider.getProviderNo());
        if (pp != null) {
            ProgramProviderTo1 ppTo = ppConverter.getAsTransferObject(getLoggedInInfo(), pp);
            result.setCurrentProgram(ppTo);
        } else {
            if (result.getProgramDomain() != null && result.getProgramDomain().size() > 0) {
                result.setCurrentProgram(result.getProgramDomain().get(0));
            }
        }

        /* counts */

        int messageCount = messagingManager.getMyInboxMessageCount(getLoggedInInfo(), provider.getProviderNo(), false);
        int ptMessageCount = messagingManager.getMyInboxMessageCount(getLoggedInInfo(), provider.getProviderNo(), true);
        MenuTo1 messengerMenu = new MenuTo1();
        int menuItemCounter = 0;
        messengerMenu.add(menuItemCounter++, bundle.getString("navbar.newOscarDemoMessages"), "" + messageCount, "classic");
        messengerMenu.add(menuItemCounter++, bundle.getString("navbar.newOscarMessages"), "" + ptMessageCount, "classic");


        /* this is manual right now. Need to have this generated from some kind
         * of user data
         */
        NavBarMenuTo1 navBarMenu = new NavBarMenuTo1();
        navBarMenu.setMessengerMenu(messengerMenu);

        MenuTo1 patientSearchMenu = new MenuTo1().add(0, bundle.getString("navbar.menu.newPatient"), null, "#/newpatient")
                .add(1, bundle.getString("navbar.menu.advancedSearch"), null, "#/search");
        navBarMenu.setPatientSearchMenu(patientSearchMenu);

        int idCounter = 0;

        MenuTo1 menu = new MenuTo1()
                .add(idCounter++, bundle.getString("navbar.menu.schedule"), null, "../provider/providercontrol.jsp")
                .add(idCounter++, bundle.getString("navbar.menu.inbox"), null, "../documentManager/inboxManage.do?method=prepareForIndexPage", "inbox");

        if (!consultationManager.isConsultResponseEnabled()) {
            menu.addWithState(idCounter++, bundle.getString("navbar.menu.consults"), null, "consultRequests");
        } else if (!consultationManager.isConsultRequestEnabled()) {
            menu.addWithState(idCounter++, bundle.getString("navbar.menu.consultResponses"), null, "consultResponses");
        }

        //consult menu
        if (consultationManager.isConsultRequestEnabled() && consultationManager.isConsultResponseEnabled()) {
            MenuItemTo1 consultMenu = new MenuItemTo1(idCounter++, bundle.getString("navbar.menu.consults"), null);
            consultMenu.setDropdown(true);
            MenuTo1 consultMenuList = new MenuTo1()
                    .addWithState(idCounter++, bundle.getString("navbar.menu.consultRequests"), null, "consultRequests")
                    .addWithState(idCounter++, bundle.getString("navbar.menu.consultResponses"), null, "consultResponses");
            consultMenu.setDropdownItems(consultMenuList.getItems());
            menu.getItems().add(consultMenu);
        }
        String billingRegion = OscarProperties.getInstance().getProperty("billregion", "");
        menu.add(idCounter++, bundle.getString("navbar.menu.billing"), null, "../billing/CA/" + billingRegion + "/billingReportCenter.jsp?displaymode=billreport", "billing")
                .addWithState(idCounter++, bundle.getString("navbar.menu.tickler"), null, "ticklers")

                //.add(0,"K2A",null,"#/k2a")
                .add(idCounter++, bundle.getString("navbar.menu.admin"), null, "../administration/", "admin");

        MenuItemTo1 moreMenu = new MenuItemTo1(idCounter++, bundle.getString("navbar.menu.more"), null);
        moreMenu.setDropdown(true);

        MenuTo1 moreMenuList = new MenuTo1()
                .addWithState(idCounter++, bundle.getString("navbar.menu.reports"), null, "reports")
                .add(idCounter++, bundle.getString("navbar.menu.documents"), null, "../documentManager/documentReport.jsp?function=providers&functionid=" + provider.getPractitionerNo(), "edocView");


        if (ClinicalConnectUtil.isReady(provider.getProviderNo())) {
            moreMenuList.add(idCounter++, bundle.getString("navbar.menu.clinicalconnect"), null, "../commons/ClinicalConnectRedirect.jsp", "clinicalconnect");
        }


        List<Dashboard> dashboards = dashboardManager.getDashboards(getLoggedInInfo());

        if (dashboards != null) {
            if (!dashboards.isEmpty()) {
                for (Dashboard dashboard : dashboards) {
                    moreMenuList.add(dashboard.getId(), "Dashboard - " + dashboard.getName(), null, "dashboard/display/DashboardDisplay.do?method=getDashboard&dashboardId=" + dashboard.getId(), "dashboard" + dashboard.getId());
                }
            }

        }

        moreMenu.setDropdownItems(moreMenuList.getItems());
        menu.getItems().add(moreMenu);
        navBarMenu.setMenu(menu);

        MenuTo1 userMenu = new MenuTo1()
                .addWithState(0, bundle.getString("navbar.menu.settings"), null, "settings")
                .addWithState(1, bundle.getString("navbar.menu.support"), null, "support")
                .addWithState(2, bundle.getString("navbar.menu.help"), null, "help");
        navBarMenu.setUserMenu(userMenu);

        result.setMenus(navBarMenu);

        return result;
    }

    @GET
    @Path("/setDefaultProgramInDomain")
    public GenericRESTResponse setDefaultProgram(@QueryParam("programId") Integer programId) {
        programManager2.setCurrentProgramInDomain(getLoggedInInfo().getLoggedInProviderNo(), programId);
        return new GenericRESTResponse();
    }

    @GET
    @Path("/patientLists")
    @Produces("application/json")
    public PersonaResponse getMyPatientLists() {
        Provider provider = getCurrentProvider();
        ResourceBundle bundle = getResourceBundle();

        String itemsToReturn = "8";
        String recentPatients = preferenceManager.getProviderPreference(getLoggedInInfo(), "recentPatients");
        if (recentPatients != null) {
            itemsToReturn = recentPatients;
        }

        PersonaResponse response = new PersonaResponse();

        response.getPatientListTabItems().add(new PatientList(0, bundle.getString("patientList.tab.appts"), "../ws/rs/schedule/day/today", "patientlist/patientList1.jsp", "GET"));

        if (!OscarProperties.getInstance().getBooleanProperty("disable.patientList.tab.recent", "true")) {
            response.getPatientListTabItems().add(new PatientList(1, bundle.getString("patientList.tab.recent"), "../ws/rs/providerService/getRecentDemographicsViewed?startIndex=0&itemsToReturn=" + itemsToReturn, "patientlist/recent.jsp", "GET"));
        }
        response.getPatientListMoreTabItems().add(new PatientList(0, bundle.getString("patientList.tab.patientSets"), "../ws/rs/reporting/demographicSets/patientList", "patientlist/demographicSets.jsp", "POST"));
        response.getPatientListMoreTabItems().add(new PatientList(1, bundle.getString("patientList.tab.caseload"), null, "patientlist/program.jsp", null));

        return response;
    }

    @GET
    @Path("/patientList/config")
    @Produces("application/json")
    public PatientListConfigTo1 getMyPatientListConfig() {
        Provider provider = getCurrentProvider();
        PatientListConfigTo1 patientListConfigTo1 = new PatientListConfigTo1();
        UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        String numberOfApptsToShow = propDao.getStringValue(provider.getProviderNo(), "patientListConfig.numberOfApptsToShow");
        if (numberOfApptsToShow != null) {
            try {
                patientListConfigTo1.setNumberOfApptstoShow(Integer.parseInt(numberOfApptsToShow));
            } catch (Exception e) {
                logger.error("numberOfAppts is not a number" + numberOfApptsToShow, e);
            }
        }

        String showReason = propDao.getStringValue(provider.getProviderNo(), "patientListConfig.showReason");
        if (showReason != null) {
            try {
                patientListConfigTo1.setShowReason(Boolean.parseBoolean(showReason));
            } catch (Exception e) {
                logger.error("showReason is not a boolean" + showReason, e);
            }
        }

        return patientListConfigTo1;
    }

    @POST
    @Path("/patientList/config")
    @Produces("application/json")
    @Consumes("application/json")
    public PatientListConfigTo1 saveMyPatientListConfig(PatientListConfigTo1 patientListConfigTo1) {
        Provider provider = getCurrentProvider();

        UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        Integer numberOfApptsToShow = patientListConfigTo1.getNumberOfApptstoShow();

        if (numberOfApptsToShow != null && numberOfApptsToShow > 0) {
            UserProperty prop = propDao.getProp(provider.getProviderNo(), "patientListConfig.numberOfApptsToShow");
            if (prop != null) {
                prop.setValue(String.valueOf(numberOfApptsToShow));
            } else {
                prop = new UserProperty();
                prop.setName("patientListConfig.numberOfApptsToShow");
                prop.setProviderNo(provider.getProviderNo());
                prop.setValue(String.valueOf(numberOfApptsToShow));
            }
            propDao.saveProp(prop);
        }

        boolean showReason = patientListConfigTo1.isShowReason();
        UserProperty prop = propDao.getProp(provider.getProviderNo(), "patientListConfig.showReason");
        if (prop != null) {
            prop.setValue(Boolean.toString(showReason));
        } else {
            prop = new UserProperty();
            prop.setName("patientListConfig.showReason");
            prop.setProviderNo(provider.getProviderNo());
            prop.setValue(Boolean.toString(showReason));
        }
        propDao.saveProp(prop);


        return patientListConfigTo1;
    }

    /**
     * This will be a REST based way to get access to groups of preferences. It's not fully implemented yet
     *
     * @param obj
     * @return
     */
    @POST
    @Path("/preferences")
    @Produces("application/json")
    @Consumes("application/json")
    public PersonaResponse getPreferences(ObjectNode obj) {
        Provider provider = getCurrentProvider();

        //not yet used..need a way to just load specific groups of properties.
        String type = obj.get("type") != null ? obj.get("type").asText() : null;

        PersonaResponse response = new PersonaResponse();
        DashboardPreferences prefs = new DashboardPreferences();

        //this needs to be more structured after the alpha. Create a manager a way to load with defaults
        UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        String strVal = propDao.getStringValue(provider.getProviderNo(), "dashboard.expiredTicklersOnly");
        if (strVal == null) {
            prefs.setExpiredTicklersOnly(true);
        } else if (strVal != null && "true".equalsIgnoreCase(strVal)) {
            prefs.setExpiredTicklersOnly(true);
        }

        response.setDashboardPreferences(prefs);

        return response;
    }

    @POST
    @Path("/updatePreference")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse updatePreference(ObjectNode json) {
        Provider provider = getCurrentProvider();
        GenericRESTResponse response = new GenericRESTResponse();

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_pref", "u", null)) {
            throw new RuntimeException("Access Denied");
        }

        UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = userPropertyDao.getProp(provider.getProviderNo(), json.get("key") != null ? json.get("key").asText() : null);
        if (up != null) {
            up.setValue(json.get("value") != null ? json.get("value").asText() : null);
            userPropertyDao.merge(up);
            response.setSuccess(true);
        }

        return response;
    }

    @POST
    @Path("/updatePreferences")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse updatePreferences(ObjectNode json) {
        Provider provider = getCurrentProvider();
        GenericRESTResponse response = new GenericRESTResponse();

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_pref", "u", null)) {
            throw new RuntimeException("Access Denied");
        }

        Boolean value = null;

        if (json.has("expiredTicklersOnly")) {
            value = json.get("expiredTicklersOnly").asBoolean();
        }

        if (value != null) {

            UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
            UserProperty prop = propDao.getProp(provider.getProviderNo(), "dashboard.expiredTicklersOnly");
            if (prop != null) {
                prop.setValue(String.valueOf(value));
            } else {
                prop = new UserProperty();
                prop.setName("dashboard.expiredTicklersOnly");
                prop.setProviderNo(provider.getProviderNo());
                prop.setValue(String.valueOf(value));
            }

            propDao.saveProp(prop);

            response.setSuccess(true);
        } else {
            response.setSuccess(false);
        }


        return response;

    }


    @GET
    @Path("/dashboardMenu")
    @Produces("application/json")
    public NavbarResponse getDashboardMenu() {

        List<Dashboard> dashboards = dashboardManager.getDashboards(getLoggedInInfo());

        ResourceBundle bundle = getResourceBundle();

        NavbarResponse result = new NavbarResponse();

        if (dashboards != null) {
            NavBarMenuTo1 navBarMenu = new NavBarMenuTo1();

            MenuTo1 dashboardMenu = new MenuTo1();
            dashboardMenu.add(null, bundle.getString("navbar.menu.dashboard"), null, "dashboard");

            if (!dashboards.isEmpty()) {

                MenuItemTo1 dashboardDropdownMenu = new MenuItemTo1(null, bundle.getString("navbar.menu.dashboard"), null);
                MenuTo1 dashboardDropdownList = new MenuTo1();

                for (Dashboard dashboard : dashboards) {
                    dashboardDropdownList.addWithState(dashboard.getId(),
                            dashboard.getName(), dashboard.getName(), "DashboardDisplay/" + dashboard.getId());
                }

                dashboardDropdownMenu.setDropdown(Boolean.TRUE);
                dashboardDropdownMenu.setDropdownItems(dashboardDropdownList.getItems());

                dashboardMenu.getItems().add(dashboardDropdownMenu);

            }

            navBarMenu.setMenu(dashboardMenu);

            result.setMenus(navBarMenu);
        }

        return result;

    }
}

