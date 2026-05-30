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
import java.util.Set;
import java.util.regex.Pattern;
import io.github.carlos_emr.carlos.utility.LogSafe;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


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

    // CWE-501 trust boundary validation patterns
    private static final Pattern SAFE_STATUS = Pattern.compile("[a-zA-Z]{1,2}");
    private static final Pattern SAFE_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern SAFE_TIME = Pattern.compile("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?");
    // Any character except ASCII control chars (allows Unicode for bilingual Canadian EMR)
    private static final Pattern SAFE_TEXT = Pattern.compile("[^\\p{Cntrl}]*");
    private static final Set<String> VALID_SOURCES = Set.of("encounter", "messenger");

    private boolean enabled;

    protected SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public EctDisplayAction() {
        super();
        if (Actions == null) {
            Actions = new HashMap<String, String>();
            Actions.put("labs", "/encounter/displayLabs");
            Actions.put("forms", "/encounter/displayForms");
            Actions.put("msgs", "/encounter/displayMessages");
            Actions.put("eforms", "/encounter/displayEForms");
            Actions.put("docs", "/encounter/displayDocuments");
            Actions.put("measurements", "/encounter/displayMeasurements");
            Actions.put("tickler", "/encounter/displayTickler");
            Actions.put("Dx", "/encounter/displayDisease");
            Actions.put("preventions", "/encounter/displayPrevention");
            Actions.put("consultation", "/encounter/displayConsultation");
            Actions.put("allergies", "/encounter/displayAllergy");
            Actions.put("unresolvedIssues", "/encounter/displayIssues");
            Actions.put("resolvedIssues", "/encounter/displayIssues");
            Actions.put("Rx", "/encounter/displayRx");
            Actions.put("success", "/WEB-INF/jsp/encounter/LeftNavBarDisplay.jsp");
            Actions.put("error", "/WEB-INF/jsp/encounter/LeftNavBarError.jsp");
            Actions.put("HRM", "/encounter/displayHRM");

            if (logger.isDebugEnabled()) {
                logger.debug("Instantiated encounter display actions: " + Actions);
            }
        }

    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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

        boolean rebuildBean = bean == null || request.getParameter("demographicNo") != null;

        // Extract and validate demographicNo early so the privilege check can run before any session mutation
        String demoNoParam;
        if (rebuildBean) {
            demoNoParam = request.getParameter("demographicNo");
            if (demoNoParam != null && !demoNoParam.isEmpty() && !demoNoParam.matches("\\d+")) {
                throw new SecurityException("Invalid non-numeric demographicNo");
            }
        } else {
            demoNoParam = bean.demographicNo;
        }

        // Privilege check BEFORE any session.setAttribute to prevent unauthorized session mutation (CWE-501)
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", demoNoParam)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (rebuildBean) {
            bean = new EctSessionBean();
            bean.currentDate = UtilDateUtilities.StringToDate(request.getParameter("curDate"));

            if (bean.currentDate == null) {
                bean.currentDate = new Date();
            }
            bean.providerNo = request.getParameter("providerNo");
            if (bean.providerNo != null && !bean.providerNo.matches("[a-zA-Z0-9]{1,6}")) {
                logger.warn("Invalid providerNo rejected at trust boundary, falling back to session user: {}", LogSafe.sanitize(bean.providerNo)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                bean.providerNo = null;
            }
            if (bean.providerNo == null) {
                bean.providerNo = (String) request.getSession().getAttribute("user"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated provider from own session
            }
            bean.demographicNo = demoNoParam;
            String apptNoParam = request.getParameter("appointmentNo");
            if (apptNoParam != null && !apptNoParam.isEmpty() && !apptNoParam.matches("\\d+")) {
                throw new SecurityException("Invalid non-numeric appointmentNo");
            }
            bean.appointmentNo = apptNoParam;
            bean.curProviderNo = request.getParameter("curProviderNo");
            if (bean.curProviderNo != null && !bean.curProviderNo.isEmpty() && !bean.curProviderNo.matches("[a-zA-Z0-9]{1,6}")) {
                logger.warn("Invalid curProviderNo rejected, falling back to logged-in provider: {}", LogSafe.sanitize(bean.curProviderNo)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                bean.curProviderNo = null;
            }
            // Fall back to authenticated provider — the logged-in user IS the provider unless viewing another provider's schedule
            if (bean.curProviderNo == null || bean.curProviderNo.trim().isEmpty()) {
                bean.curProviderNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProvider().getProviderNo();
            }
            // CWE-501 trust boundary: validate structured fields, sanitize free-text
            String reasonParam = request.getParameter("reason");
            if (reasonParam != null && (!SAFE_TEXT.matcher(reasonParam).matches() || reasonParam.length() > 255)) {
                logger.warn("Rejected invalid reason at trust boundary");
                reasonParam = null;
            }
            bean.reason = reasonParam;
            String encTypeParam = request.getParameter("encType");
            if (encTypeParam != null && !encTypeParam.matches("[a-zA-Z0-9_ ]{1,50}")) {
                logger.warn("Rejected invalid encType at trust boundary: {}", LogSafe.sanitize(encTypeParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                encTypeParam = null;
            }
            bean.encType = encTypeParam;
            bean.userName = request.getParameter("userName");
            if (bean.userName == null) {
                bean.userName = ((String) request.getSession().getAttribute("userfirstname")) + " " + ((String) request.getSession().getAttribute("userlastname")); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated user's name from own session
            } else if (!SAFE_TEXT.matcher(bean.userName).matches() || bean.userName.length() > 100) {
                logger.warn("Rejected invalid userName at trust boundary, falling back to session-derived name");
                bean.userName = ((String) request.getSession().getAttribute("userfirstname")) + " " + ((String) request.getSession().getAttribute("userlastname")); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated user's name from own session after rejecting invalid param
            }

            String apptDateParam = request.getParameter("appointmentDate");
            if (apptDateParam != null && !SAFE_DATE.matcher(apptDateParam).matches()) {
                logger.warn("Rejected invalid appointmentDate at trust boundary: {}", LogSafe.sanitize(apptDateParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                apptDateParam = null;
            }
            bean.appointmentDate = apptDateParam;
            String startTimeParam = request.getParameter("startTime");
            if (startTimeParam != null && !SAFE_TIME.matcher(startTimeParam).matches()) {
                logger.warn("Rejected invalid startTime at trust boundary: {}", LogSafe.sanitize(startTimeParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                startTimeParam = null;
            }
            bean.startTime = startTimeParam;
            String statusParam = request.getParameter("status");
            if (statusParam != null && !SAFE_STATUS.matcher(statusParam).matches()) {
                logger.warn("Rejected invalid status at trust boundary: {}", LogSafe.sanitize(statusParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                statusParam = null;
            }
            bean.status = statusParam;
            String dateParam = request.getParameter("date");
            if (dateParam != null && !SAFE_DATE.matcher(dateParam).matches()) {
                logger.warn("Rejected invalid date at trust boundary: {}", LogSafe.sanitize(dateParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                dateParam = null;
            }
            bean.date = dateParam;
            bean.check = "myCheck";
            bean.oscarMsgID = request.getParameter("msgId");
            if (bean.oscarMsgID != null && !bean.oscarMsgID.matches("\\d+")) {
                logger.warn("Invalid msgId: {}", LogSafe.sanitize(bean.oscarMsgID)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                bean.oscarMsgID = null;
            }
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- demographicNo/appointmentNo validated numeric;
            // status validated [a-zA-Z]{1,2}; dates validated YYYY-MM-DD; time validated HH:MM; encType validated alphanumeric;
            // reason/userName sanitized for control chars and length-capped; eChartId is server-generated;
            // providerNo validated via [a-zA-Z0-9]{1,6} pattern at line 156 (null/empty/invalid → session fallback); used only as DAO lookup key;
            // authz enforced at privilege gate (line 144) before session mutation
            request.getSession().setAttribute("EctSessionBean", bean);
            request.getSession().setAttribute("eChartID", bean.eChartId); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- server-generated ID from EctSessionBean.setUpEncounterPage()
            String sourceParam = request.getParameter("source");
            if (sourceParam != null) {
                bean.source = VALID_SOURCES.contains(sourceParam) ? sourceParam : null;
            }

            request.setAttribute("EctSessionBean", bean);
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
                if (hasReadAccessForDisplayCommand(securityMgr, cmd)) {

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
            MiscUtils.getLogger().error("Forward :{} navName :{} cmd {} params {}", LogSafe.sanitize(forward), LogSafe.sanitize(navName), LogSafe.sanitize(cmd), LogSafe.sanitize(params));
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
    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    private boolean hasReadAccessForDisplayCommand(SecurityManager securityMgr, String cmd) {
        return securityMgr.hasReadAccess("_" + cmd.toLowerCase(),
                request.getSession().getAttribute("userrole") + "," + request.getSession().getAttribute("user"));
    }

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
