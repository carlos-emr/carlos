<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
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

<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp"
         import="java.util.*, io.github.carlos_emr.carlos.report.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.login.*" %>
<%@ page import="org.apache.commons.lang3.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptTableFieldNameCaption" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String tableName = request.getParameter("tableName") != null ? request.getParameter("tableName") : "";
    String formTableName = request.getParameter("formTableName") != null ? request.getParameter("formTableName") : tableName;
    String configTableName = request.getParameter("configTableName") != null ? request.getParameter("configTableName") : formTableName;

// get form name
    String reportName = (new RptReportItem()).getReportName(reportId);

// get form parameters
    RptTableFieldNameCaption tableObj = new RptTableFieldNameCaption();

// add/delete action 
    String submitAdd = bundle.getString("global.btnAdd");
    String submitUpdate = bundle.getString("report.reportFormCaption.button.update");

    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitAdd)) {
        String strName = request.getParameter("name") != null ? request.getParameter("name") : "";
        String strCaption = request.getParameter("caption") != null ? request.getParameter("caption") : "";
        tableObj.setTable_name(tableName);
        tableObj.setName(strName);
        tableObj.setCaption(strCaption);
        tableObj.insertRecord();
    }
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitUpdate)) {
        String strName = request.getParameter("name") != null ? request.getParameter("name") : "";
        String strCaption = request.getParameter("caption") != null ? request.getParameter("caption") : "";
        tableObj.setTable_name(tableName);
        tableObj.setName(strName);
        tableObj.setCaption(strCaption);
        tableObj.updateRecord();
    }

// get display data
    Vector vecTableField = new Vector();
    vecTableField = tableObj.getTableNameCaption(tableName);
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="report.reportFormCaption.title"/></title>
        <LINK REL="StyleSheet" HREF="<%= request.getContextPath() %>/web.css" TYPE="text/css">
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                //document.forms[0].service_code.focus();
            }

            function onDelete() {
                ret = confirm("<%= bundle.getString("report.reportList.confirmDelete") %>");
                return ret;
            }

            function onRestore() {
                ret = confirm("<%= bundle.getString("report.reportList.confirmRestore") %>");
                return ret;
            }

            function goCaption() {
                //self.location.href = "<%= request.getContextPath() %>/report/ViewReportFormCaption?id=<e:forUriComponent value='<%= reportId %>' />&tableName=<e:forUriComponent value='<%= tableName %>' />";
            }

            function goPage(id) {
                self.location.href = "<%= request.getContextPath() %>/report/ViewReportFilter?id=" + id;
            }

            //-->

        </script>
    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0"
          rightmargin="0">
    <center></center>
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr BGCOLOR="#CCCCFF">
            <td><e:forHtmlContent value='<%= reportName %>' /> <fmt:message key="report.reportFormCaption.heading"/></td>
            <td width="10%" align="right" nowrap>
                <% if ("demographic".equals(tableName)) {%> <a
                    href="<%= request.getContextPath() %>/report/ViewReportFormDemoConfig?id=<e:forUriComponent value='<%= reportId %>' />&tableName=<e:forUriComponent value='<%= tableName %>' />&formTableName=<e:forUriComponent value='<%= formTableName %>' />&configTableName=<e:forUriComponent value='<%= configTableName %>' />"><fmt:message key="report.reportFormCaption.backToConfiguration"/></a> <% } else {%> <a
                    href="<%= request.getContextPath() %>/report/ViewReportFormConfig?id=<e:forUriComponent value='<%= reportId %>' />&tableName=<e:forUriComponent value='<%= tableName %>' />"><fmt:message key="report.reportFormCaption.backToConfiguration"/></a> <% }%>
            </td>
        </tr>
    </table>

    <table width="100%" border="0" cellspacing="2" cellpadding="2">
        <tr>
            <td width="70%">

                <table width="100%" border="0" cellspacing="1" cellpadding="2">
                    <%
                        for (int i = 0; i < vecTableField.size(); i++) {
                            String color = i % 2 == 0 ? "#EEEEFF" : "";
                            String captionName = (String) vecTableField.get(i);
                            String[] strTemp = captionName.split("\\|");
            String fieldName = "";
            String fieldCaption = "";
            String action = submitAdd;
            if (strTemp.length > 1) {
                fieldName = Encode.forHtml(strTemp[1]);
                fieldCaption = Encode.forHtmlAttribute(strTemp[0].trim());
            }
            if (fieldCaption.length() > 1) {
                color = "gold";
                action = submitUpdate;
            }
                    %>
                    <form method="post" name="baseurl<%=i%>"
                          action="<%= request.getContextPath() %>/report/ViewReportFormCaption">
                        <tr bgcolor="<%=color%>">
                            <td width="50%"><input type="text" name="caption"
                                                   value="<%=fieldCaption%>" size="36"/></td>
                            <td width="30%" nowrap><%=fieldName%>
                            </td>
                            <td align="center"><input type="submit" name="submit"
                                                      value="<%=action%>"/></td>
                            <input type="hidden" name="name" value="<%=fieldName%>">
                            <input type="hidden" name="id" value="<e:forHtmlAttribute value='<%= reportId %>' />">
                            <input type="hidden" name="tableName" value="<e:forHtmlAttribute value='<%= tableName %>' />">
                            <input type="hidden" name="formTableName" value="<e:forHtmlAttribute value='<%= formTableName %>' />">
                            <input type="hidden" name="configTableName"
                                   value="<e:forHtmlAttribute value='<%= configTableName %>' />">
                        </tr>
                    </form>
                    <% } %>
                </table>
            </td>
            <td></td>
        </tr>
    </table>


    </body>
</html>
