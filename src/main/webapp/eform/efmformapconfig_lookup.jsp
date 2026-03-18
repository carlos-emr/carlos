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
<input type="hidden" name="oscarAPCacheLookupType" value="<%= request.getParameter("oscarAPCacheLookupType") %>"/><%
    String[] keys = request.getParameterValues("key");
    if (keys == null) {
        keys = new String[0];
    }
    EFormLoader loader = EFormLoader.getInstance();
    DatabaseAP ap;
    String provider_no = (String) session.getAttribute("user");
    String demographic_no = request.getParameter("demographic_no");
    String fid = request.getParameter("fid");
    EForm form = null;
    form = new EForm("1", demographic_no);
    form.setProviderNo(provider_no);  //needs providers for the action
    form.setAppointmentNo(request.getParameter("appointment"));
//form.setApptProvider(request.getParameter("apptProvider"));
    for (String key : keys) {
        ap = EFormLoader.getAP(key);
        if (ap != null) {
            try {
                String sql = ap.getApSQL();
                String output = ap.getApOutput();
                //replace ${demographic} with demogrpahicNo
                if (sql != null) {
                    sql = form.replaceAllFields(sql);

                    ArrayList<String> names = DatabaseAP.parserGetNames(output); //a list of ${apName} --> apName
                    sql = DatabaseAP.parserClean(sql);  //replaces all other ${apName} expressions with 'apName'
                    ArrayList<String> values = EFormUtil.getValues(names, sql);
                    if (values.size() != names.size()) {
                        output = "";
                    } else {
                        for (int i = 0; i < names.size(); i++) {
                            output = DatabaseAP.parserReplace(names.get(i), org.apache.commons.text.StringEscapeUtils.escapeHtml4(values.get(i)), output);
                        }
                    }
                }
%><input type="hidden" name="<%=key%>" value="<%=output%>"/><%
} catch (Exception e) {
%><input type="hidden" name="<%=key%>" value=""/><%
    }
} else {
%><input type="hidden" name="<%=key%>" value=""/><%
        }
    }
%>
