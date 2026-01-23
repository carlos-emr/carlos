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


package ca.openosp.openo.prescript.pageUtil;

import ca.openosp.OscarProperties;
import ca.openosp.openo.PMmodule.caisi_integrator.RemoteDrugAllergyHelper;
import ca.openosp.openo.commn.dao.AllergyDao;
import ca.openosp.openo.commn.dao.SystemPreferencesDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.SystemPreferences;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.prescript.data.RxDrugData;
import ca.openosp.openo.prescript.data.RxPatientData;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Struts 2 action for displaying and managing patient allergies.
 * <p>
 * This action handles:
 * <ul>
 * <li>Displaying patient allergy information</li>
 * <li>Reordering allergies in the display list</li>
 * <li>Managing RxSessionBean for prescription context</li>
 * <li>Routing to appropriate JSP based on RX3 configuration</li>
 * </ul>
 * <p>
 * Supports both legacy (ShowAllergies.jsp) and RX3 (ShowAllergies2.jsp) interfaces
 * based on system and user preferences.
 *
 * @since 2006-04-20
 */
public final class RxShowAllergy2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private AllergyDao allergyDao = (AllergyDao) SpringUtils.getBean(AllergyDao.class);
    private SystemPreferencesDao systemPreferencesDao = (SystemPreferencesDao) SpringUtils.getBean(SystemPreferencesDao.class);

    /**
     * Handles allergy reordering and redirects to the allergies display page.
     * <p>
     * Reorders the allergy based on request parameters and redirects back to
     * the allergies list page for the specified demographic.
     *
     * Expected request parameters:
     * <ul>
     * <li>demographicNo - String demographic number of the patient</li>
     * <li>allergyId - Integer ID of the allergy to reorder</li>
     * <li>direction - String direction to move ("up" or "down")</li>
     * </ul>
     *
     * @return String NONE (redirect handled manually)
     * @throws RuntimeException if redirect fails
     */
    public String reorder() {
        reorder(request);
        try {
            response.sendRedirect(request.getContextPath() + "/oscarRx/ShowAllergies.jsp?demographicNo=" + request.getParameter("demographicNo"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

    /**
     * Main execution method for displaying patient allergies.
     * <p>
     * This method:
     * <ul>
     * <li>Checks security privileges for allergy access</li>
     * <li>Determines RX3 interface preference (system-wide or user-specific)</li>
     * <li>Sets up or retrieves RxSessionBean for the session</li>
     * <li>Loads patient data including allergies</li>
     * <li>Redirects to appropriate allergies display JSP</li>
     * </ul>
     * <p>
     * Routes to reorder() method if method parameter equals "reorder".
     *
     * Expected request parameters:
     * <ul>
     * <li>demographicNo - String demographic number of the patient (required)</li>
     * <li>view - String view mode (optional)</li>
     * <li>method - String method name for routing (optional, "reorder" supported)</li>
     * </ul>
     *
     * @return String "failure" if demographicNo is missing, null for redirect
     * @throws IOException if redirect fails
     * @throws ServletException if servlet processing fails
     */
    public String execute()
            throws IOException, ServletException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", "r", null)) {
            throw new RuntimeException("missing required sec object (_allergy)");
        }

        String method = request.getParameter("method");

        String dispatchResult = switch (method != null ? method : "") {
            case "reorder" -> reorder();
            case "allergyData" -> {
                getAllergyData(loggedInInfo);
                yield null;
            }
            default -> null;
        };

        if (dispatchResult != null || (method != null && !method.isEmpty())) {
            return dispatchResult;
        }

        boolean useRx3 = false;
        String rx3 = OscarProperties.getInstance().getProperty("RX3");
        if (rx3 != null && rx3.equalsIgnoreCase("yes")) {
            useRx3 = true;
        }
        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        String provider = (String) request.getSession().getAttribute("user");
        UserProperty propUseRx3 = userPropertyDAO.getProp(provider, UserProperty.RX_USE_RX3);
        if (propUseRx3 != null && propUseRx3.getValue().equalsIgnoreCase("yes"))
            useRx3 = true;


        String user_no = (String) request.getSession().getAttribute("user");
        String demo_no = request.getParameter("demographicNo");
        String view = request.getParameter("view");

        if (demo_no == null) {
            return "failure";
        }
        // Setup bean
        RxSessionBean bean;

        if (request.getSession().getAttribute("RxSessionBean") != null) {
            bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
            if ((bean.getProviderNo() != user_no) || (bean.getDemographicNo() != Integer.parseInt(demo_no))) {
                bean = new RxSessionBean();
            }

        } else {
            bean = new RxSessionBean();
        }


        bean.setProviderNo(user_no);
        bean.setDemographicNo(Integer.parseInt(demo_no));
        if (view != null) {
            bean.setView(view);
        }

        request.getSession().setAttribute("RxSessionBean", bean);

        if (request.getParameter("method") != null && request.getParameter("method").equals("reorder")) {
            reorder(request);
        }

        RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, bean.getDemographicNo());

        String forward = request.getContextPath() + "/oscarRx/ShowAllergies.jsp?demographicNo=" + demo_no;
        if (useRx3) {
            forward = request.getContextPath() + "/oscarRx/ShowAllergies2.jsp?demographicNo=" + demo_no;
        }
        if (patient != null) {
            request.getSession().setAttribute("Patient", patient);
            response.sendRedirect(forward);
        } else {//no records found
            response.sendRedirect("error.html");
        }
        return null;
    }

    /**
     * Retrieves and processes allergy data for a patient, including local and remote allergy information,
     * and calculates allergy warnings based on severity. Outputs the resulting data in JSON format.
     *
     * This method checks system preferences and handles merging allergy lists from local and remote
     * data sources. It determines the highest severity allergy when the system preference for displaying
     * the highest allergy warnings is enabled.
     *
     * @param loggedInInfo LoggedInInfo object containing user session details and security information.
     */
    private void getAllergyData(LoggedInInfo loggedInInfo) {
        boolean rxShowAllAllergyWarnings = systemPreferencesDao.isReadBooleanPreference(SystemPreferences.RX_PREFERENCE_KEYS.rx_show_highest_allergy_warning);

        String atcCode = request.getParameter("atcCode");
        String id = request.getParameter("id");
        String disabled = ca.openosp.OscarProperties.getInstance().getProperty("rx3.disable_allergy_warnings", "false");
        if (disabled.equals("false")) {

            ObjectMapper objectMapper = new ObjectMapper();
            RxSessionBean rxSessionBean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
            Allergy[] allergies = RxPatientData.getPatient(loggedInInfo, rxSessionBean.getDemographicNo()).getActiveAllergies();

            if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                try {
                    ArrayList<Allergy> remoteAllergies = RemoteDrugAllergyHelper.getRemoteAllergiesAsAllergyItems(loggedInInfo, rxSessionBean.getDemographicNo());

                    // now merge the 2 lists
                    Collections.addAll(remoteAllergies, allergies);
                    allergies = remoteAllergies.toArray(new Allergy[0]);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("error getting remote allergies", e);
                }
            }

            Allergy[] allergyWarnings = null;
            RxDrugData drugData = new RxDrugData();

            try {
                allergyWarnings = drugData.getAllergyWarnings(atcCode, allergies);


                Allergy highestSeverityAllergy = null;

                ObjectNode result = objectMapper.createObjectNode();
                result.put("id", id);
                ArrayNode allergyResultArray = objectMapper.createArrayNode();
                if (allergyWarnings != null && allergyWarnings.length > 0) {
                    highestSeverityAllergy = allergyWarnings[0];
                    for (Allergy allergy : allergyWarnings) {
                        ObjectNode allergyResult = objectMapper.createObjectNode();
                        allergyResult.put("DESCRIPTION", StringUtils.trimToEmpty(allergy.getDescription()));
                        allergyResult.put("reaction", StringUtils.trimToEmpty(allergy.getReaction()));
                        allergyResult.put("severity", StringUtils.trimToEmpty(allergy.getSeverityOfReactionDesc()));
                        if (rxShowAllAllergyWarnings) {
                            int highestSeverity = Integer.parseInt(highestSeverityAllergy.getSeverityOfReaction());
                            int thisSeverity = Integer.parseInt(allergy.getSeverityOfReaction());
                            if (thisSeverity > highestSeverity) {
                                highestSeverityAllergy = allergy;
                            }
                        } else {
                            allergyResultArray.add(allergyResult);
                        }
                    }
                }
                if (rxShowAllAllergyWarnings && highestSeverityAllergy != null) {
                    ObjectNode allergyResult = objectMapper.createObjectNode();
                    allergyResult.put("DESCRIPTION", StringUtils.trimToEmpty(highestSeverityAllergy.getDescription()));
                    allergyResult.put("reaction", StringUtils.trimToEmpty(highestSeverityAllergy.getReaction()));
                    allergyResult.put("severity", StringUtils.trimToEmpty(highestSeverityAllergy.getSeverityOfReactionDesc()));
                    allergyResultArray.add(allergyResult);
                }
                result.set("results", allergyResultArray);

                response.setContentType("application/json");
                response.getOutputStream().write(result.toString().getBytes());

            } catch (Exception e) {
                MiscUtils.getLogger().error("Error in getAllergyData", e);
            }
        }
    }

    /**
     * Reorders allergies in the patient's allergy list by swapping positions.
     * <p>
     * Moves the specified allergy up or down in the display order by swapping
     * position values with the adjacent allergy. Changes are persisted to the database.
     * <p>
     * Direction "up" moves the allergy earlier in the list (lower index).
     * Direction "down" moves the allergy later in the list (higher index).
     * Boundary conditions are handled (cannot move first item up or last item down).
     *
     * Expected request parameters:
     * <ul>
     * <li>allergyId - Integer ID of the allergy to reorder</li>
     * <li>demographicNo - String demographic number of the patient</li>
     * <li>direction - String direction to move ("up" or "down")</li>
     * </ul>
     *
     * @param request HttpServletRequest containing reordering parameters
     */
    private void reorder(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String direction = request.getParameter("direction");
        String demographicNo = request.getParameter("demographicNo");
        int allergyId = Integer.parseInt(request.getParameter("allergyId"));
        try {
            Allergy[] allergies = RxPatientData.getPatient(loggedInInfo, demographicNo).getActiveAllergies();
            for (int x = 0; x < allergies.length; x++) {
                if (allergies[x].getAllergyId() == allergyId) {
                    if (direction.equals("up")) {
                        if (x == 0) {
                            continue;
                        }
                        //move ahead
                        int myPosition = allergies[x].getPosition();
                        int swapPosition = allergies[x - 1].getPosition();
                        allergies[x].setPosition(swapPosition);
                        allergies[x - 1].setPosition(myPosition);
                        allergyDao.merge(allergies[x]);
                        allergyDao.merge(allergies[x - 1]);
                    }
                    if (direction.equals("down")) {
                        if (x == (allergies.length - 1)) {
                            continue;
                        }
                        int myPosition = allergies[x].getPosition();
                        int swapPosition = allergies[x + 1].getPosition();
                        allergies[x].setPosition(swapPosition);
                        allergies[x + 1].setPosition(myPosition);
                        allergyDao.merge(allergies[x]);
                        allergyDao.merge(allergies[x + 1]);
                    }
                }
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
        }

    }
}
