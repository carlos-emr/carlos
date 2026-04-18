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
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportConfigData" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptTableFieldNameCaption" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";
    String SAVE_AS = "default";
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
// get form name
    String reportName = (new RptReportItem()).getReportName(reportId);

// get form parameters
    RptReportConfigData confObj = new RptReportConfigData();
    RptTableFieldNameCaption tableObj = new RptTableFieldNameCaption();
    Vector vecTableName = new Vector();
    if (confObj.getReportTableNameList(reportId) != null) vecTableName = confObj.getReportTableNameList(reportId);
    String tableName = request.getParameter("tableName");
    if (tableName == null) tableName = vecTableName.size() >= 1 ? (String) vecTableName.get(0) : "";

// add/delete action 
    String submitAdd = bundle.getString("global.btnAdd");
    String submitDelete = bundle.getString("global.btnDelete");
    String submitGo = bundle.getString("report.reportFormConfig.button.go");

    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitAdd)) {
        String strCapName = request.getParameter("selField") != null ? request.getParameter("selField") : "";
        String[] strTemp = strCapName.split("\\|");
        if (strTemp.length > 1) {
            String fieldName = strTemp[1];
            String fieldCaption = strTemp[0];
            confObj.setReport_id(Integer.parseInt(reportId));
            confObj.setTable_name(tableName);
            confObj.setName(fieldName);
            confObj.setCaption(fieldCaption);
            confObj.setSave(SAVE_AS);
            confObj.insertRecordWithOrder();
        }
    }
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitDelete)) {
        String strCapName = request.getParameter("selConfig") != null ? request.getParameter("selConfig") : "";
        String[] strTemp = strCapName.split("\\|");
        if (strTemp.length > 1) {
            String fieldName = strTemp[1];
            String fieldCaption = strTemp[0];
            confObj.setReport_id(Integer.parseInt(reportId));
            confObj.setTable_name(tableName);
            confObj.setName(fieldName);
            confObj.setCaption(fieldCaption);
            confObj.setSave(SAVE_AS);
            confObj.deleteRecord();
        }
    }
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitGo)) {
        tableName = request.getParameter("selTable") != null ? request.getParameter("selTable") : "";
    }

// get display data
    Vector vecConfigField = new Vector();
    Vector vecTableField = new Vector();
    Vector vecFormTable = new Vector();
    if ("".equals(tableName)) {
        // get form table list to choose: name/tablename
        vecFormTable = tableObj.getFormTableNameList();
    } else {
        // standard
        vecConfigField = confObj.getConfigNameList(SAVE_AS, reportId);
        vecTableField = tableObj.getTableNameCaption(tableName);
    }

%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="report.reportFormConfig.title"/></title>
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
                //self.location.href = "<%= request.getContextPath() %>/report/ViewReportFormCaption?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>&tableName=<carlos:encode value='<%= tableName %>' context="uriComponent"/>";
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
            <td><carlos:encode value='<%= reportName %>' context="html"/> <fmt:message key="report.reportFormConfig.heading"/></td>
            <td width="10%" align="right" nowrap><a
                    href="<%= request.getContextPath() %>/report/ViewReportFilter?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>"><fmt:message key="report.reportFormConfig.backToReport"/></a></td>
        </tr>
    </table>

    <table width="100%" border="1" cellspacing="0" cellpadding="2">
        <form method="post" name="baseurl0" action="<%= request.getContextPath() %>/report/ViewReportFormConfig">
            <% if (vecFormTable.size() > 0) { %>

            <tr>
                <td colspan="3" align="center"><font color="red"><fmt:message key="report.reportFormConfig.msgSelectFormFirst"/></font> <select name="selTable">
                    <%
                        for (int i = 0; i < vecFormTable.size(); i = i + 2) {
                            String formName = (String) vecFormTable.get(i);
                            String formTable = (String) vecFormTable.get(i + 1);
                    %>
                    <option value="<%=formTable%>"><%=formName%>
                    </option>
                    <% } %>
                    </select> <input type="submit" name="submit" value="<fmt:message key='report.reportFormConfig.button.go'/>"/></td>
            </tr>
            <% } %>
            <tr bgcolor="<%="#EEEEFF"%>">
                <td align="center" width="45%"><fmt:message key="report.reportFormConfig.label.form"/> | <a
                        href="<%= request.getContextPath() %>/report/ViewReportFormDemoConfig?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>&tableName=<%="demographic"%>&formTableName=<carlos:encode value='<%= tableName %>' context="uriComponent"/>&configTableName=<carlos:encode value='<%= tableName %>' context="uriComponent"/>"><fmt:message key="report.reportFormConfig.label.patientProfile"/></a> <br/>
                    <select size=28 name="selField" ondblclick="javascript:onSelField();">
                        <%
                            String strMatchConfig = "";
                            for (int i = 0; i < vecConfigField.size(); i++) {
                                strMatchConfig += StringUtils.replace((String) vecConfigField.get(i), "|", "\\|") + "|";
                            }
                            for (int i = 0; i < vecTableField.size(); i++) {
                                String color = i % 2 == 0 ? "#EEEEFF" : "";
                                String captionName = (String) vecTableField.get(i);
                                if (captionName.matches(strMatchConfig)) continue;
                                String captionNameAttr = Encode.forHtmlAttribute(captionName);
                                String captionNameHtml = Encode.forHtml(captionName);
                        %>
                        <option value="<%=captionNameAttr%>"><%=captionNameHtml%>
                        </option>
                        <% } %>
                    </select> <br>
                    <a
                            href="<%= request.getContextPath() %>/report/ViewReportFormCaption?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>&tableName=<carlos:encode value='<%= tableName %>' context="uriComponent"/>"><fmt:message key="report.reportFormConfig.linkAddCaption"/></a></td>

                <td align="center" width="20%" nowrap valign="top">
                    <table width="100%" border="0" cellspacing="0" cellpadding="2">
                        <tr>
                            <td colspan="2"><fmt:message key="report.reportFormConfig.label.fields"/> | <fmt:message key="report.reportFormConfig.label.selected"/> <br>
                                <br>
                                ==<input type="submit" name="submit" value="<fmt:message key='global.btnAdd'/>"/>=&gt;&gt; <br>
                                <br>
                                &lt;&lt;=<input type="submit" name="submit" value="<fmt:message key='global.btnDelete'/>"/>==
                        </tr>
                    </table>
                </td>

                <td width="45%" align="center"><select size=28 name="selConfig"
                                                       ondblclick="javascript:onSelField();">
                    <% for (int i = 0; i < vecConfigField.size(); i++) {
                        String captionName = (String) vecConfigField.get(i);
                        String captionNameAttr = Encode.forHtmlAttribute(captionName);
                        String captionNameHtml = Encode.forHtml(captionName);
                    %>
                    <option value="<%=captionNameAttr%>"><%=captionNameHtml%>
                    </option>
                    <% } %>
                </select> <br>
                    <a
                            href="<%= request.getContextPath() %>/report/ViewReportFormOrder?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>&save=<%=SAVE_AS%>&tableName=<carlos:encode value='<%= tableName %>' context="uriComponent"/>"><fmt:message key="report.reportFormConfig.linkChangeOrder"/></a> <input type="hidden" name="id" value="<carlos:encode value='<%= reportId %>' context="htmlAttribute"/>"> <input
                            type="hidden" name="tableName" value="<carlos:encode value='<%= tableName %>' context="htmlAttribute"/>"> <input
                            type="hidden" name="configTableName" value="<carlos:encode value='<%= tableName %>' context="htmlAttribute"/>">
                </td>
            </tr>
        </form>
    </table>


    </body>
</html>
