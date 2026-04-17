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

<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    boolean bDeletedList = false;
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String submitDelete = bundle.getString("report.reportList.btnDelete");
    String submitRestore = bundle.getString("report.reportList.btnRestore");
    String submitAdd = bundle.getString("report.reportList.btnAdd");
    String msg = bundle.getString("report.reportList.title");
    Properties prop = new Properties();
    RptReportItem reportItem = new RptReportItem();

// delete/undelete list
    if (request.getParameter("undelete") != null && "true".equals(request.getParameter("undelete"))) {
        bDeletedList = true;
    }
// delete action
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitDelete)) {
        // check the input data
        String id = request.getParameter("id");
        int nId = id != null ? Integer.parseInt(id) : 0;
        if (!reportItem.deleteRecord(nId)) {
            msg = bundle.getString("report.reportList.msgReportNotDeleted");
        }
    }
// undelete action
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitRestore)) {
        // check the input data
        String id = request.getParameter("id");
        int nId = id != null ? Integer.parseInt(id) : 0;
        if (!reportItem.unDeleteRecord(nId)) {
            msg = bundle.getString("report.reportList.msgReportNotUndeleted");
        }
    }
// add action
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitAdd)) {
        // check the input data
        String report_name = request.getParameter("name");
        reportItem.setReport_name(report_name);
        if (!reportItem.insertRecord()) {
            msg = bundle.getString("report.reportList.msgReportNotAdded");
        }
    }

// search the list
    int n = bDeletedList ? 0 : 1;
    String link = bDeletedList
            ? "<a href='" + request.getContextPath() + "/report/ViewReportFormRecord'>" + bundle.getString("report.reportList.linkReportList") + "</a>"
            : "<a href='" + request.getContextPath() + "/report/ViewReportFormRecord?undelete=true'>" + bundle.getString("report.reportList.linkDeletedReportList") + "</a>";
    Vector vec = reportItem.getNameList(n);
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><%=bDeletedList ? bundle.getString("report.reportList.deleted") + " " : ""%><%=bundle.getString("report.reportList.title")%></title>
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
                <th><%=msg%>
                </th>
            </tr>
        </table>
    </center>
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr BGCOLOR="#CCCCFF">
            <td align="right"><%=link%>
            </td>
        </tr>
    </table>
    <table width="100%" border="0" cellspacing="2" cellpadding="2">
        <% for (int i = 0; i < vec.size(); i++) {
            String color = i % 2 == 0 ? "#EEEEFF" : "";
            prop = (Properties) vec.get(i);
            String itemId = prop.getProperty("id");
        %>
        <form method="post" name="baseurl<%=i+1%>"
              action="<%= request.getContextPath() %>/report/ViewReportFormRecord">
            <tr bgcolor="<%=color%>">
                <td align="right"><b><%=i + 1%>
                </b></td>
                <td
                        onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                        onMouseout="this.style.backgroundColor='<%=color%>';"
                        onClick="goPage(<%=itemId%>)"><%=prop.getProperty(itemId)%>
                </td>
                <td width="5%" align="right"><input type="hidden" name="id"
                                                    value="<%=itemId%>"> <% if (!bDeletedList) { %> <input
                        type="submit" name="submit" value="<fmt:message key='report.reportList.btnDelete'/>"
                        onclick="javascript:return onDelete();"> <% } else { %> <input
                        type="submit" name="submit" value="<fmt:message key='report.reportList.btnRestore'/>"
                        onclick="javascript:return onRestore();"> <% } %>
                </td>
            </tr>
        </form>
        <% } %>
    </table>

    <hr>
    <table width="60%" border="0" cellspacing="2" cellpadding="2">
        <tr>
            <td><fmt:message key="report.reportList.addNewReport"/></td>
        </tr>
        <tr>
            <td align="center">
                <form method="post" name="baseurl" action="<%= request.getContextPath() %>/report/ViewReportFormRecord">
                    <input type="text" name="name" value="" size="60"/> <input
                        type="submit" name="submit" value="<fmt:message key='report.reportList.btnAdd'/>"
                        onclick="javascript:return onAdd();"/></form>
            </td>
        </tr>
    </table>

    </body>
</html>
