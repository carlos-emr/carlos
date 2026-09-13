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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.struts2.ActionSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.owasp.encoder.Encode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts 2 action for displaying and managing patient allergies.
 * <p>
 * This action handles:
 * <ul>
 * <li>Displaying patient allergy information</li>
 * <li>Reordering allergies in the display list</li>
 * <li>Managing RxSessionBean for prescription context</li>
 * <li>Routing to ShowAllergies2.jsp</li>
 * </ul>
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
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String reorder() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", "r", null)) {
            throw new RuntimeException("missing required sec object (_allergy)");
        }

        String demoNoParam = request.getParameter("demographicNo");
        if (demoNoParam == null || !demoNoParam.matches("\\d{1,9}")) {
            return "failure";
        }
        reorder(request);
        try {
            RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, demoNoParam);
            if (patient != null) {
                // demoNoParam validated as numeric at method entry
                request.getSession().setAttribute("Patient", patient); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
            }
            response.sendRedirect(request.getContextPath() + "/rx/showAllergy?demographicNo=" + Encode.forUriComponent(demoNoParam));
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
     * @return String "success" to forward to ShowAllergies2.jsp, "failure" if
     *         demographicNo is missing or patient cannot be loaded, or null
     *         for method-dispatch paths that write the response directly
     * @throws IOException if servlet I/O fails
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

        String user_no = (String) request.getSession().getAttribute("user");
        String demo_no = request.getParameter("demographicNo");
        String view = request.getParameter("view");

        if (demo_no == null) {
            return "failure";
        }
        if (!demo_no.matches("\\d{1,9}")) {
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

        // demographicNo validated via Integer.parseInt(); bean setters use validated values
        request.getSession().setAttribute("RxSessionBean", bean); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep

        RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, bean.getDemographicNo());

        if (patient == null) {
            return "failure";
        }
        request.getSession().setAttribute("Patient", patient); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        return "success";
    }

    /**
     * Retrieves and processes allergy data for a patient and calculates allergy warnings
     * based on severity. Outputs the resulting data in JSON format.
     *
     * This method checks system preferences and handles the allergy list. It determines
     * the highest severity allergy when the system preference for displaying the highest
     * allergy warnings is enabled.
     *
     * @param loggedInInfo LoggedInInfo object containing user session details and security information.
     */
    private void getAllergyData(LoggedInInfo loggedInInfo) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("id", request.getParameter("id"));
        ArrayNode warnings = result.putArray("results");
        ArrayNode unchecked = result.putArray("unchecked");
        result.put("checkComplete", false);
        Allergy[] allergies = new Allergy[0];
        try {
            if (!"false".equals(CarlosProperties.getInstance().getProperty("rx.disable_allergy_warnings", "false"))) {
                result.put("disabled", true);
            } else {
                RxSessionBean session = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
                allergies = RxPatientData.getPatient(loggedInInfo, session.getDemographicNo()).getActiveAllergies();
                List<Allergy> missing = new ArrayList<>();
                Allergy[] matches = new RxDrugData().getAllergyWarnings(request.getParameter("atcCode"), allergies, missing);
                boolean highestOnly = systemPreferencesDao.isReadBooleanPreference(
                        SystemPreferences.RX_PREFERENCE_KEYS.rx_show_highest_allergy_warning);
                Allergy highest = null;
                for (Allergy allergy : matches) {
                    if (!highestOnly) warnings.add(allergyJson(mapper, allergy));
                    if (highest == null || severity(allergy) > severity(highest)) highest = allergy;
                }
                if (highestOnly && highest != null) warnings.add(allergyJson(mapper, highest));
                // An unresolved allergen is not a negative allergy check. Keep every unresolved
                // entry even when the preference limits confirmed matches to highest severity.
                for (Allergy allergy : missing) unchecked.add(allergyJson(mapper, allergy));
                result.put("checkComplete", missing.isEmpty());
            }
        } catch (Exception error) {
            MiscUtils.getLogger().error("Unable to complete prescription allergy check", error);
            result.put("checkFailed", true);
            result.put("checkComplete", false);
            unchecked.removeAll();
            for (Allergy allergy : allergies) unchecked.add(allergyJson(mapper, allergy));
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(mapper.writeValueAsBytes(result));
    }

    private static ObjectNode allergyJson(ObjectMapper mapper, Allergy allergy) {
        ObjectNode item = mapper.createObjectNode();
        item.put("DESCRIPTION", StringUtils.trimToEmpty(allergy.getDescription()));
        item.put("reaction", StringUtils.trimToEmpty(allergy.getReaction()));
        item.put("severity", StringUtils.trimToEmpty(allergy.getSeverityOfReactionDesc()));
        return item;
    }

    private static int severity(Allergy allergy) {
        try {
            int level = Integer.parseInt(allergy.getSeverityOfReaction());
            return level >= 1 && level <= 3 ? level : 0; // 5 means No Reaction.
        } catch (NumberFormatException ignored) {
            return 0;
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
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", "u", null)) {
            throw new SecurityException("missing required sec object (_allergy)");
        }

        String direction = request.getParameter("direction");
        if (direction == null || (!"up".equals(direction) && !"down".equals(direction))) {
            MiscUtils.getLogger().warn("Invalid direction parameter for allergy reorder");
            return;
        }
        String demographicNo = request.getParameter("demographicNo");
        if (demographicNo == null || !demographicNo.matches("\\d{1,9}")) {
            MiscUtils.getLogger().warn("Invalid demographicNo for allergy reorder");
            return;
        }
        String allergyIdParam = request.getParameter("allergyId");
        if (allergyIdParam == null || !allergyIdParam.matches("\\d{1,9}")) {
            MiscUtils.getLogger().warn("Invalid allergyId for allergy reorder");
            return;
        }
        int allergyId;
        try {
            long parsedAllergyId = Long.parseLong(allergyIdParam);
            if (parsedAllergyId > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid allergyId");
            }
            allergyId = (int) parsedAllergyId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid allergyId", e);
        }
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
