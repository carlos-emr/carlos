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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.AllergyDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.prescript.data.RxPatientData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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

        if ("reorder".equals(request.getParameter("method"))) {
            return reorder();
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", "r", null)) {
            throw new RuntimeException("missing required sec object (_allergy)");
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
