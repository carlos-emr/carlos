<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.form.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%
    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));

    // for oscarcitizens
    String historyet = request.getParameter("historyet") == null ? "" : ("&historyet=" + request.getParameter("historyet"));

    if (true) {
        out.clearBuffer();
        if (formId == 0) {
            request.getRequestDispatcher("/WEB-INF/jsp/form/formbcarpg1.jsp?demographic_no=" + demoNo + "&formId=" + formId).include(request, response);
        } else {
            FrmRecord rec = (new FrmRecordFactory()).factory("BCAR");
            java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

            // The page the record was last saved from, as the form posts it back in c_lastVisited. Only this
            // form's own pages are taken: an empty value made this page include itself until the stack
            // overflowed, and the value is request data, so it must not name any other page under WEB-INF.
            String lastVisited = props.getProperty("c_lastVisited", "");
            if (!lastVisited.matches("pg[1-3]")) {
                lastVisited = "pg1";
            }
            request.getRequestDispatcher("/WEB-INF/jsp/form/formbcar" + lastVisited
                    + ".jsp?demographic_no=" + demoNo + "&formId=" + formId + historyet).include(request, response);
        }

        return;
    }
%>
