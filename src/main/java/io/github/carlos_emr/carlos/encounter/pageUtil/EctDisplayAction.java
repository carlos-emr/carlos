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

package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.services.security.SecurityManager;
import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import io.github.carlos_emr.carlos.utility.LogSanitizer;


/**
 * Base action class for populating left navbar of encounter
 *
 * @author rjonasz
 */
public class EctDisplayAction extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    private static HashMap<String, String> Actions = null;
    protected static final String ELLIPSES = "...";
    protected static final int MAX_LEN_TITLE = 48;
    protected static final int CROP_LEN_TITLE = 45;
    protected static final int MAX_LEN_KEY = 12;
    protected static final int CROP_LEN_KEY = 9;

    private boolean enabled;

    protected SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public EctDisplayAction() {
        super();
        if (Actions == null) {
            Actions = new HashMap<String, String>();
            Actions.put("labs", "/encounter/displayLabs.do");
            Actions.put("forms", "/encounter/displayForms.do");
            Actions.put("msgs", "/encounter/displayMessages.do");
            Actions.put("eforms", "/encounter/displayEForms.do");
            Actions.put("docs", "/encounter/displayDocuments.do");
            Actions.put("measurements", "/encounter/displayMeasurements.do");
            Actions.put("tickler", "/encounter/displayTickler.do");
            Actions.put("Dx", "/encounter/displayDisease.do");
            Actions.put("preventions", "/encounter/displayPrevention.do");
            Actions.put("consultation", "/encounter/displayConsultation.do");
            Actions.put("allergies", "/encounter/displayAllergy.do");
            Actions.put("unresolvedIssues", "/encounter/displayIssues.do");
            Actions.put("resolvedIssues", "/encounter/displayIssues.do");
            Actions.put("Rx", "/encounter/displayRx.do");
            Actions.put("success", "/encounter/LeftNavBarDisplay.jsp");
            Actions.put("error", "/encounter/LeftNavBarError.jsp");
            Actions.put("HRM", "/encounter/displayHRM.do");

            if (logger.isDebugEnabled()) {
                logger.debug("Instantiated encounter display actions: " + Actions);
            }
        }

    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws IOException, ServletException {
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        String forward = "error";
        String cmd = getCmd();
        String navName;
        if ((navName = (String) request.getAttribute("navbarName")) != null) navName += "+" + cmd;
        else navName = cmd;

        request.setAttribute("navbarName", navName);

        boolean isJsonRequest = request.getParameter("json") != null && request.getParameter("json").equalsIgnoreCase("true");
        request.setAttribute("isJsonRequest", isJsonRequest);

        if (bean == null || request.getParameter("demographicNo") != null) {
            bean = new EctSessionBean();
            bean.currentDate = UtilDateUtilities.StringToDate(request.getParameter("curDate"));

            if (bean.currentDate == null) {
                bean.currentDate = new Date();
            }
            bean.providerNo = request.getParameter("providerNo");
            if (bean.providerNo == null) {
                bean.providerNo = (String) request.getSession().getAttribute("user");
            }
            bean.demographicNo = request.getParameter("demographicNo");
            bean.appointmentNo = request.getParameter("appointmentNo");
            bean.curProviderNo = request.getParameter("curProviderNo");
            bean.reason = request.getParameter("reason");
            bean.encType = request.getParameter("encType");
            bean.userName = request.getParameter("userName");
            if (bean.userName == null) {
                bean.userName = ((String) request.getSession().getAttribute("userfirstname")) + " " + ((String) request.getSession().getAttribute("userlastname"));
            }

            bean.appointmentDate = request.getParameter("appointmentDate");
            bean.startTime = request.getParameter("startTime");
            bean.status = request.getParameter("status");
            bean.date = request.getParameter("date");
            bean.check = "myCheck";
            bean.oscarMsgID = request.getParameter("msgId");
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            request.getSession().setAttribute("EctSessionBean", bean);
            request.getSession().setAttribute("eChartID", bean.eChartId);
            if (request.getParameter("source") != null) {
                bean.source = request.getParameter("source");
            }

            request.setAttribute("EctSessionBean", bean);
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", bean.demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        //Can we handle request?
        //Check attrib first so we know if we are in a chain call before a direct request
        String params = (String) request.getAttribute("cmd");
        if (params == null) params = request.getParameter("cmd");
        request.setAttribute("cmd", params);

        if (params != null) {
            //Check to see if this call is for us
            if (params.indexOf(cmd) > -1) {

                NavBarDisplayDAO Dao = (NavBarDisplayDAO) request.getAttribute("DAO");
                if (Dao == null) Dao = new NavBarDisplayDAO();

                String headingColour = request.getParameter("hC");
                if (headingColour != null) {
                    Dao.setHeadingColour(headingColour);
                }

                Dao.setReloadUrl(request.getRequestURL().toString() + "?" + request.getQueryString());
                Dao.setDivId(cmd);

                SecurityManager securityMgr = new SecurityManager();
                if (securityMgr.hasReadAccess("_" + cmd.toLowerCase(), request.getSession().getAttribute("userrole") + "," + request.getSession().getAttribute("user"))) {

                    if (getInfo(bean, request, Dao)) {
                        request.setAttribute("DAO", Dao);

                        String regex = "\\b" + cmd + "\\b";
                        String remainingCmds = params.replaceAll(regex, "").trim();

                        //Are there more commmands to forward to or do we print what we have?
                        if (remainingCmds.length() > 0) {
                            request.setAttribute("cmd", remainingCmds);
                            int pos = remainingCmds.indexOf(' ');
                            if (pos > -1) forward = remainingCmds.substring(0, pos);
                            else forward = remainingCmds;

                            if (Actions.get(forward) == null) {
                                MiscUtils.getLogger().error("forward not found, returning error");
                                forward = "error";
                            }
                        } else if (isJsonRequest) {
                            ObjectNode json = objectMapper.valueToTree(Dao.getMap());
                            response.getOutputStream().write(json.toString().getBytes());
                            return null;
                        } else {
                            forward = "success";
                        }
                    }
                } else {
                    return null;
                }
            }
        }
        if (forward != null && !forward.equals("success")) {
            MiscUtils.getLogger().error("Forward :{} navName :{} cmd {} params {}", LogSanitizer.sanitize(forward), LogSanitizer.sanitize(navName), LogSanitizer.sanitize(cmd), LogSanitizer.sanitize(params));
        }

        // Use include() for XHR requests only. Struts' forward() closes the output stream
        // in Tomcat 11, truncating AJAX responses at the 8KB buffer boundary — include()
        // leaves the stream open. For non-XHR requests, return "success" so Struts performs
        // a FORWARD dispatch, allowing CsrfGuardScriptInjectionFilter to run on the FORWARD.
        if ("success".equals(forward) && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            String jspPath = Actions.get("success");
            request.getRequestDispatcher(jspPath).include(request, response);
            return NONE;
        }

        return forward;
    }

    /**
     * Must be implemented by subclasses to populate DAO object
     *
     * @param bean     Current session information
     * @param request  Current request
     * @param Dao      View DAO responsible for rendering encounter
     * @return Returns true if the content was loaded successfully and false otherwise. Please note that returning false will case
     * an error message rendered for this action.
     */
    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        return true;
    }

    /**
     * Must be implemented by subclasses to retrieve module name
     *
     * @return Returns name of the module corresponding to the mapping in the {@link #Actions}
     */
    public String getCmd() {
        return "";
    }

    /**
     * Creates a new display item with the specified title and
     * a link that cannot be clicked at.
     *
     * @param title Title to be displayed for the item
     * @return Returns the new item.
     */
    protected NavBarDisplayDAO.Item newItem(String title) {
        return newItem(title, null);
    }

    /**
     * Creates a new display item with the specified title and color and
     * a link that cannot be clicked at.
     *
     * @param title Title to be displayed for the item
     * @param color Color of the link to be displayed in the item (e.g. "red", or "green")
     * @return Returns the new item.
     */
    protected NavBarDisplayDAO.Item newItem(String title, String color) {
        return newItem(title, "return false;", color);
    }

    /**
     * Creates a new display item with the specified title and color and
     * link.
     *
     * @param title Title to be displayed for the item
     * @param url url
     * @param color Color of the link to be displayed in the item (e.g. "red", or "green")
     * @return Returns the new item.
     */
    protected NavBarDisplayDAO.Item newItem(String title, String url, String color) {
        NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
        item.setTitle(title);
        if (color != null) {
            item.setColour(color);
        }

        if (url != null) {
            item.setURL(url);
        } else {
            // for all null urls, make sure we don't allow clicking them
            item.setURL("return false;");
        }
        item.setURLJavaScript(true);

        return item;
    }

    /**
     * Checks if the action is enabled. Non-enabled actions should not render the encounter
     * screen widget (i.e. return true in {@link #getInfo(EctSessionBean, HttpServletRequest, NavBarDisplayDAO)}
     * and must not modify the nav bar daos).
     *
     * @return Returns true of the actions is enabled and false otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets if the action is enabled. Non-enabled actions should not render the encounter
     * screen widget (i.e. return true in {@link #getInfo(EctSessionBean, HttpServletRequest, NavBarDisplayDAO)}
     * and must not modify the nav bar daos).
     *
     * @param enabled Boolean flag that indicates if the actions is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
