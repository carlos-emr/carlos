<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Report in CSV")) {
        if (true) {
            out.clearBuffer();
            request.getRequestDispatcher("reportDownload").include(request, response); //forward request&response to the target page
            return;
        }
    }
%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp"
         import="java.util.*, io.github.carlos_emr.carlos.report.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.pageUtil.*" %>
<%@ page import="io.github.carlos_emr.carlos.login.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportConfigData" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportCreator" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%@ page import="io.github.carlos_emr.carlos.report.pageUtil.RptFormQuery" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.ParameterizedSql" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    String SAVE_AS = "default";
    String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";
// get form name
    String reportName = (new RptReportItem()).getReportName(reportId);
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    // Security note:
    // getQueryStr() reloads report filter SQL fragments from server-side
    // reportFilter rows instead of trusting client-posted hidden value_* fields.
    // User-entered filter values are carried only as ParameterizedSql bind values.
    RptFormQuery formQuery = new RptFormQuery();
    ParameterizedSql psql = formQuery.getQueryStr(reportId, request);

    RptReportConfigData formConfig = new RptReportConfigData();
    Vector[] vecField = formConfig.getAllFieldNameValue(SAVE_AS, reportId);
    Vector vecFieldCaption = vecField[1];
    Vector vecFieldName = vecField[0];
    Vector vecFieldValue = (new RptReportCreator()).query(psql, vecFieldCaption);

%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="report.reportList.title"/></title>
        <LINK REL="StyleSheet" HREF="<%= request.getContextPath() %>/web.css" TYPE="text/css">
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                //document.forms[0].service_code.focus();
            }

            function onDelete() {
                ret = confirm("<fmt:message key='report.reportList.confirmDelete'/>");
                return ret;
            }

            function onRestore() {
                ret = confirm("<fmt:message key='report.reportList.confirmRestore'/>");
                return ret;
            }

            function onAdd() {
                if (document.baseurl.name.value.length < 2) {
                    alert("<fmt:message key='report.reportList.validName'/>");
                    return false;
                } else {
                    return true;
                }
            }

            function goPage(id) {
                self.location.href = "<%= request.getContextPath() %>/report/ViewReportFilter?id=" + id;
            }

            //-->

        </script>
    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0"
    rightmargin="0">
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr>
            <td align="left">&nbsp;</td>
        </tr>
    </table>

    <center>
        <table BORDER="1" CELLPADDING="0" CELLSPACING="0" WIDTH="80%">
            <tr BGCOLOR="#CCFFFF">
                <th><carlos:encode value='<%= reportName %>' context="html"/>
                </th>
            </tr>
        </table>
    </center>
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr BGCOLOR="#CCCCFF">
            <td></td>
            <td width="10%" align="right" nowrap><a
                    href="<%= request.getContextPath() %>/report/ViewReportFilter?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>"><fmt:message key="report.reportList.backToFilter"/></a></td>
        </tr>
    </table>


    <hr>
    <table BORDER="0" CELLPADDING="1" CELLSPACING="1" WIDTH="100%"
           class="sortable tabular_list">
        <thead>
        <tr BGCOLOR="#66CCCC">
            <% for (int i = 0; i < vecFieldCaption.size(); i++) { %>
            <th><carlos:encode value='<%= (String) vecFieldCaption.get(i) %>' context="html"/>
            </th>
            <% } %>
        </tr>
        </thead>
        <% for (int i = 0; i < vecFieldValue.size(); i++) {
            String color = i % 2 == 0 ? "#EEEEFF" : "#DDDDFF";
            Properties prop = (Properties) vecFieldValue.get(i);
        %>
        <tr BGCOLOR="<%=color%>">
            <% for (int j = 0; j < vecFieldCaption.size(); j++) { %>
            <td><carlos:encode value='<%= prop.getProperty((String) vecFieldCaption.get(j), "") %>' context="html"/>&nbsp;</td>
            <% } %>
        </tr>
        <% } %>
    </table>

    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/css.js">
    <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/common.js">
        <script language="javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/standardista-table-sorting.js">
        </body>
</html>
