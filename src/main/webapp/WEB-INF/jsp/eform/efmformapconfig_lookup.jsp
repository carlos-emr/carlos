<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page import="java.io.*, java.util.*, io.github.carlos_emr.carlos.eform.*, io.github.carlos_emr.carlos.eform.data.*, io.github.carlos_emr.carlos.eform.EFormUtil"
%>
<%@ page import="io.github.carlos_emr.carlos.eform.data.DatabaseAP" %>
<%@ page import="io.github.carlos_emr.carlos.eform.data.EForm" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormLoader" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.ParameterizedSql" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // Security: require _eform read privilege (consistent with all other eForm endpoints)
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return;
    }

    // Validate fid: must be digits to prevent NumberFormatException in EForm constructor
    String fid = request.getParameter("fid");
    if (fid == null || !fid.matches("\\d+")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid fid");
        return;
    }
%><input type="hidden" name="oscarAPCacheLookupType" value="<carlos:encode value='<%= request.getParameter("oscarAPCacheLookupType") != null ? request.getParameter("oscarAPCacheLookupType") : "" %>' context="htmlAttribute"/>"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%><%
    String[] keys = request.getParameterValues("key");
    if (keys == null) {
        keys = new String[0];
    }
    EFormLoader loader = EFormLoader.getInstance();
    DatabaseAP ap;
    String provider_no = (String) session.getAttribute("user");
    String demographic_no = request.getParameter("demographic_no");
    // Load the eForm by its actual fid (from the URL parameter) to get the correct
    // AP configuration. Previously hardcoded to "1" which broke when the RTL eForm
    // had any other fid (e.g., after database re-seeding).
    EForm form = null;
    form = new EForm(fid, demographic_no);
    form.setProviderNo(provider_no);  //needs providers for the action
    String appointmentParam = request.getParameter("appointment");
    if (appointmentParam != null && !appointmentParam.matches("\\d+")) { appointmentParam = null; } // validate numeric to prevent SQL injection
    // appointmentParam is validated numeric above and re-validated before binding
    // into DatabaseAP SQL.
    form.setAppointmentNo(appointmentParam);
//form.setApptProvider(request.getParameter("apptProvider"));
    // Keys that could not be resolved, as opposed to keys that legitimately resolved to nothing.
    // Only the former are reported to the page: a patient with no recorded allergies is data, and
    // flagging that would fire the notice on almost every form and train clinicians to ignore it.
    java.util.List<String> unresolvedKeys = new java.util.ArrayList<String>();
    for (String key : keys) {
        ap = EFormLoader.getAP(key);
        if (ap != null) {
            try {
                String sql = ap.getApSQL();
                String output = ap.getApOutput();
                //replace ${demographic} with demogrpahicNo
                if (sql != null) {
                    ParameterizedSql query = form.parameterizeAllFields(sql);

                    ArrayList<String> names = DatabaseAP.parserGetNames(output); //a list of ${apName} --> apName
                    ArrayList<String> values = EFormUtil.getValues(names, query);
                    if (values.isEmpty()) {
                        // Genuinely no rows. An empty field is the correct rendering of that.
                        output = "";
                    } else if (values.size() != names.size()) {
                        // Fewer values than the AP declares output names: a column/name mismatch, or
                        // a SQLException that getValues swallowed into a short list. This used to be
                        // silent in both directions — no log, and a blank field the clinician could
                        // not tell apart from "no data" before saving it into the record.
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                                "AP config lookup returned an unusable result for key=" + key
                                        + " fid=" + fid + ": output declares " + names.size()
                                        + " names but the query returned " + values.size() + " values");
                        unresolvedKeys.add(key);
                        output = "";
                    } else {
                        for (int i = 0; i < names.size(); i++) {
                            output = DatabaseAP.parserReplace(names.get(i), SafeEncode.forHtml(values.get(i)), output);
                        }
                    }
                }
%><input type="hidden" name="<carlos:encode value='<%= key %>' context="htmlAttribute"/>" value="<carlos:encode value='<%= output %>' context="htmlAttribute"/>"/><%
} catch (Exception e) {
    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("AP config lookup failed for key=" + key + " fid=" + fid, e);
    unresolvedKeys.add(key);
%><input type="hidden" name="<carlos:encode value='<%= key %>' context="htmlAttribute"/>" value=""/><%
    }
} else {
    // A key the form asks for that apconfig.xml does not define. Previously the only branch here
    // with no log at all, so a misconfigured form blanked a clinical field with no trace anywhere.
    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
            "AP config lookup requested an unconfigured key=" + key + " fid=" + fid);
    unresolvedKeys.add(key);
%><input type="hidden" name="<carlos:encode value='<%= key %>' context="htmlAttribute"/>" value=""/><%
        }
    }
    // Reserved name, read and skipped by APCache.js the same way oscarAPCacheLookupType is.
    if (!unresolvedKeys.isEmpty()) {
%><input type="hidden" name="oscarAPCacheLookupFailures" value="<carlos:encode value='<%= String.join(",", unresolvedKeys) %>' context="htmlAttribute"/>"/><%
    }
%>
