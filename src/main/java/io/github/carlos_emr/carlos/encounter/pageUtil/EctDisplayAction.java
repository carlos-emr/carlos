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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
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

    /**
     * Parameters the navbar reload URL must not carry. The first three are re-added by the
     * caller on every refresh (see {@code popColumn()} in newEncounterLayout.jsp and
     * encounter.js); the CSRF token has no business in a URL at all.
     */
    private static final Set<String> RELOAD_URL_SUPPRESSED_PARAMS =
            Set.of("reloadURL", "cmd", "numToDisplay", "CSRF-TOKEN");

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
        String cmd = getCmd();
        String navName = buildNavName(cmd);
        request.setAttribute("navbarName", navName);

        boolean isJsonRequest = isJsonRequest();
        request.setAttribute("isJsonRequest", isJsonRequest);

        bean = prepareEncounterSessionBean(bean);

        String params = resolveCommandParams();
        String forward = handleDisplayCommand(bean, cmd, params, isJsonRequest);

        logUnexpectedForward(forward, navName, cmd, params);
        return finalizeForward(forward);
    }

    private String buildNavName(String cmd) {
        String navName = (String) request.getAttribute("navbarName");
        return navName == null ? cmd : navName + "+" + cmd;
    }

    private boolean isJsonRequest() {
        return isAsciiTrue(request.getParameter("json"));
    }

    private boolean isAsciiTrue(String value) {
        return value != null
                && value.length() == 4
                && ((value.charAt(0) | 0x20) == 't')
                && ((value.charAt(1) | 0x20) == 'r')
                && ((value.charAt(2) | 0x20) == 'u')
                && ((value.charAt(3) | 0x20) == 'e');
    }

    private EctSessionBean prepareEncounterSessionBean(EctSessionBean bean) {
        boolean rebuildBean = bean == null || request.getParameter("demographicNo") != null;
        String demographicNo = resolveDemographicNo(bean, rebuildBean);
        ensureChartReadPrivilege(demographicNo);
        return rebuildBean ? rebuildEncounterSessionBean(demographicNo) : bean;
    }

    private String resolveDemographicNo(EctSessionBean bean, boolean rebuildBean) {
        if (!rebuildBean) {
            return bean.demographicNo;
        }

        String demographicNo = request.getParameter("demographicNo");
        if (demographicNo == null || demographicNo.isEmpty()) {
            throw new SecurityException("Missing required demographicNo");
        }
        if (!demographicNo.matches("\\d+")) {
            throw new SecurityException("Invalid non-numeric demographicNo");
        }
        return demographicNo;
    }

    private void ensureChartReadPrivilege(String demographicNo) {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eChart", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
    }

    private EctSessionBean rebuildEncounterSessionBean(String demographicNo) {
        EctSessionBean bean = new EctSessionBean();
        bean.currentDate = resolveCurrentDate();
        bean.providerNo = resolveProviderNo();
        bean.demographicNo = demographicNo;
        bean.appointmentNo = resolveNumericParameter("appointmentNo", "Invalid non-numeric appointmentNo");
        bean.curProviderNo = resolveCurrentProviderNo();
        populateEncounterRequestFields(bean);
        bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
        storeEncounterSessionBean(bean);
        return bean;
    }

    private Date resolveCurrentDate() {
        Date currentDate = UtilDateUtilities.StringToDate(request.getParameter("curDate"));
        return currentDate == null ? new Date() : currentDate;
    }

    private String resolveProviderNo() {
        String providerNo = request.getParameter("providerNo");
        if (providerNo != null && !providerNo.matches("[a-zA-Z0-9]{1,6}")) {
            logger.warn("Invalid providerNo rejected at trust boundary, falling back to session user: {}", LogSafe.sanitize(providerNo)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            providerNo = null;
        }
        if (providerNo == null) {
            providerNo = (String) request.getSession().getAttribute("user"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): fallback to authenticated provider from own session
        }
        return providerNo;
    }

    private String resolveCurrentProviderNo() {
        String currentProviderNo = request.getParameter("curProviderNo");
        if (currentProviderNo != null && !currentProviderNo.isEmpty() && !currentProviderNo.matches("[a-zA-Z0-9]{1,6}")) {
            logger.warn("Invalid curProviderNo rejected, falling back to logged-in provider: {}", LogSafe.sanitize(currentProviderNo)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            currentProviderNo = null;
        }
        if (currentProviderNo == null || currentProviderNo.trim().isEmpty()) {
            currentProviderNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProvider().getProviderNo();
        }
        return currentProviderNo;
    }

    private void populateEncounterRequestFields(EctSessionBean bean) {
        bean.reason = resolveReason();
        bean.encType = resolveEncounterType();
        bean.userName = resolveUserName();
        bean.appointmentDate = resolveDateParameter("appointmentDate", "Rejected invalid appointmentDate at trust boundary: {}");
        bean.startTime = resolveTimeParameter("startTime");
        bean.status = resolveStatusParameter("status");
        bean.date = resolveDateParameter("date", "Rejected invalid date at trust boundary: {}");
        bean.check = "myCheck";
        bean.oscarMsgID = resolveMsgId();
        bean.source = resolveSource();
    }

    private String resolveReason() {
        String reason = request.getParameter("reason");
        if (reason != null && (!SAFE_TEXT.matcher(reason).matches() || reason.length() > 255)) {
            logger.warn("Rejected invalid reason at trust boundary");
            return null;
        }
        return reason;
    }

    private String resolveEncounterType() {
        String encounterType = request.getParameter("encType");
        if (encounterType != null && !encounterType.matches("[a-zA-Z0-9_ ]{1,50}")) {
            logger.warn("Rejected invalid encType at trust boundary: {}", LogSafe.sanitize(encounterType)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        return encounterType;
    }

    private String resolveUserName() {
        String userName = request.getParameter("userName");
        if (userName == null) {
            return sessionUserName();
        }
        if (!SAFE_TEXT.matcher(userName).matches() || userName.length() > 100) {
            logger.warn("Rejected invalid userName at trust boundary, falling back to session-derived name");
            return sessionUserName();
        }
        return userName;
    }

    private String sessionUserName() {
        return ((String) request.getSession().getAttribute("userfirstname")) + " " + ((String) request.getSession().getAttribute("userlastname")); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): authenticated user's name from own session
    }

    private String resolveDateParameter(String parameterName, String invalidMessage) {
        String value = request.getParameter(parameterName);
        if (value != null && !SAFE_DATE.matcher(value).matches()) {
            logger.warn(invalidMessage, LogSafe.sanitize(value)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        return value;
    }

    private String resolveTimeParameter(String parameterName) {
        String value = request.getParameter(parameterName);
        if (value != null && !SAFE_TIME.matcher(value).matches()) {
            logger.warn("Rejected invalid startTime at trust boundary: {}", LogSafe.sanitize(value)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        return value;
    }

    private String resolveStatusParameter(String parameterName) {
        String value = request.getParameter(parameterName);
        if (value != null && !SAFE_STATUS.matcher(value).matches()) {
            logger.warn("Rejected invalid status at trust boundary: {}", LogSafe.sanitize(value)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        return value;
    }

    private String resolveMsgId() {
        String msgId = request.getParameter("msgId");
        if (msgId != null && !msgId.matches("\\d+")) {
            logger.warn("Invalid msgId: {}", LogSafe.sanitize(msgId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        return msgId;
    }

    private String resolveSource() {
        String source = request.getParameter("source");
        return source != null && VALID_SOURCES.contains(source) ? source : null;
    }

    private String resolveNumericParameter(String parameterName, String invalidMessage) {
        String value = request.getParameter(parameterName);
        if (value != null && !value.isEmpty() && !value.matches("\\d+")) {
            throw new SecurityException(invalidMessage);
        }
        return value;
    }

    private void storeEncounterSessionBean(EctSessionBean bean) {
        // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- demographicNo/appointmentNo validated numeric;
        // status validated [a-zA-Z]{1,2}; dates validated YYYY-MM-DD; time validated HH:MM; encType validated alphanumeric;
        // reason/userName sanitized for control chars and length-capped; eChartId is server-generated;
        // providerNo validated via [a-zA-Z0-9]{1,6} pattern before session fallback; used only as DAO lookup key;
        // authz enforced at the chart privilege gate before session mutation
        request.getSession().setAttribute("EctSessionBean", bean);
        request.getSession().setAttribute("eChartID", bean.eChartId); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- server-generated ID from EctSessionBean.setUpEncounterPage()
        request.setAttribute("EctSessionBean", bean);
    }

    private String resolveCommandParams() {
        String params = (String) request.getAttribute("cmd");
        if (params == null) {
            params = request.getParameter("cmd");
        }
        request.setAttribute("cmd", params);
        return params;
    }

    private String handleDisplayCommand(EctSessionBean bean, String cmd, String params, boolean isJsonRequest) throws IOException {
        String forward = "error";
        if (params == null || params.indexOf(cmd) <= -1) {
            return forward;
        }

        NavBarDisplayDAO dao = resolveDisplayDao(cmd);
        if (!hasReadAccessForDisplayCommand(new SecurityManager(), cmd)) {
            return null;
        }
        if (!getInfo(bean, request, dao)) {
            return forward;
        }

        request.setAttribute("DAO", dao);
        return resolveForward(cmd, params, dao, isJsonRequest);
    }

    private NavBarDisplayDAO resolveDisplayDao(String cmd) {
        NavBarDisplayDAO dao = (NavBarDisplayDAO) request.getAttribute("DAO");
        if (dao == null) {
            dao = new NavBarDisplayDAO();
        }

        String headingColour = request.getParameter("hC");
        if (headingColour != null) {
            dao.setHeadingColour(headingColour);
        }

        dao.setReloadUrl(buildReloadUrl(request.getRequestURI(), request.getParameterMap()));
        dao.setDivId(cmd);
        return dao;
    }

    /**
     * Builds the URL the left navbar posts back to when a single module is refreshed in
     * place (the hidden {@code reloadUrl} input rendered by LeftNavBarDisplay.jsp, read by
     * {@code reloadNav()} after a popup such as Add Tickler saves).
     *
     * <p>Two properties of the result are load-bearing:</p>
     *
     * <ul>
     *   <li><strong>Root-relative, never absolute.</strong> This used to return
     *   {@code request.getRequestURL()}, so the reload posted the server's own
     *   {@code scheme://host/...} back as the {@code reloadURL} argument. On a packaged
     *   deployment reached by IP address that value trips OWASP CRS rule 931100 ("URL
     *   Parameter using IP Address", CRITICAL) on top of the standing 3 points from 920350
     *   ("Host header is a numeric IP address"), so the WAF answered every navbar refresh
     *   with 403 and the module box rendered "&lt;module&gt; Error: 403" until the user
     *   reloaded the whole chart. Only the refresh was affected: the first render posts the
     *   relative URL from encounterConfig.urls.leftNavbar, which matches nothing.</li>
     *   <li><strong>The reload plumbing is not carried forward.</strong> The caller appends
     *   {@code reloadURL}, {@code cmd} and {@code numToDisplay} on every post, so echoing
     *   the incoming copies back nested the previous URL inside the next one and the value
     *   grew with each refresh. {@code CSRF-TOKEN} is dropped for its own reason: CarlosAjax
     *   posts it as a body parameter, and reflecting it into a URL would write the session's
     *   CSRF token into page markup and into any log or referrer that records the URL.</li>
     * </ul>
     *
     * <p>Every other parameter is preserved (notably {@code hC}, the module's heading
     * colour), so a refreshed box renders exactly like the first load.</p>
     *
     * @param requestUri   the already-encoded request URI of the display action, including
     *                     the context path (e.g. {@code /carlos/encounter/displayTickler})
     * @param parameterMap the request's parameter map; query-string and POST-body parameters
     *                     are merged by the container and both are honoured here
     * @return a root-relative URL suitable for a same-origin XHR from the encounter page
     */
    static String buildReloadUrl(String requestUri, Map<String, String[]> parameterMap) {
        String encodedQuery = buildEncodedQueryString(parameterMap);
        return encodedQuery.isEmpty() ? requestUri : requestUri + "?" + encodedQuery;
    }

    private static String buildEncodedQueryString(Map<String, String[]> parameterMap) {
        if (parameterMap == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            if (RELOAD_URL_SUPPRESSED_PARAMS.contains(entry.getKey())) {
                continue;
            }
            appendEncodedParameter(joiner, entry.getKey(), entry.getValue());
        }
        return joiner.toString();
    }

    /**
     * Appends one request parameter to the query string, repeating the key once per value so a
     * multi-valued parameter survives the round trip. A parameter present with no value at all
     * is emitted as a bare key, which is how it arrived.
     */
    private static void appendEncodedParameter(StringJoiner joiner, String name, String[] values) {
        String encodedKey = URLEncoder.encode(name, StandardCharsets.UTF_8);
        if (values == null || values.length == 0) {
            joiner.add(encodedKey);
            return;
        }
        for (String value : values) {
            String encodedValue = value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
            joiner.add(encodedKey + "=" + encodedValue);
        }
    }

    private String resolveForward(String cmd, String params, NavBarDisplayDAO dao, boolean isJsonRequest) throws IOException {
        String regex = "\\b" + Pattern.quote(cmd) + "\\b";
        String remainingCmds = params.replaceAll(regex, "").trim();
        if (remainingCmds.length() > 0) {
            return resolveChainedForward(remainingCmds);
        }
        if (isJsonRequest) {
            ObjectNode json = objectMapper.valueToTree(dao.getMap());
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(json.toString().getBytes(StandardCharsets.UTF_8));
            return null;
        }
        return "success";
    }

    private String resolveChainedForward(String remainingCmds) {
        request.setAttribute("cmd", remainingCmds);
        int pos = remainingCmds.indexOf(' ');
        String forward = pos > -1 ? remainingCmds.substring(0, pos) : remainingCmds;
        if (Actions.get(forward) == null) {
            MiscUtils.getLogger().error("forward not found, returning error");
            return "error";
        }
        return forward;
    }

    private void logUnexpectedForward(String forward, String navName, String cmd, String params) {
        if (forward != null && !forward.equals("success")) {
            MiscUtils.getLogger().error("Forward :{} navName :{} cmd {} params {}", LogSafe.sanitize(forward), LogSafe.sanitize(navName), LogSafe.sanitize(cmd), LogSafe.sanitize(params));
        }
    }

    private String finalizeForward(String forward) throws IOException, ServletException {
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
