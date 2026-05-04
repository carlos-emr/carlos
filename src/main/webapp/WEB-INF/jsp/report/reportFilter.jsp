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

<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp"
         import="java.util.*, io.github.carlos_emr.carlos.report.data.*" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportFilter" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%
    String reportId = request.getParameter("id") != null ? request.getParameter("id") : "0";
// get form name
    String reportName = (new RptReportItem()).getReportName(reportId);
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String submitDelete = bundle.getString("report.reportList.btnDelete");


    boolean bDeletedList = false;
    String msg = bundle.getString("report.reportList.limitTo");
    Properties prop = new Properties();
    RptReportFilter reportFilter = new RptReportFilter();

// delete/undelete list
    if (request.getParameter("undelete") != null && "true".equals(request.getParameter("undelete"))) {
        bDeletedList = true;
    }
// delete action
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitDelete)) {
        // check the input data
        String id = request.getParameter("id");
        int nId = id != null ? Integer.parseInt(id) : 0;
    }

// search the list
    int n = bDeletedList ? 0 : 1;
    String link = bDeletedList
            ? "<a href='" + request.getContextPath() + "/report/ViewReportFormRecord'>" + bundle.getString("report.reportList.linkReportList") + "</a>"
            : "<a href='" + request.getContextPath() + "/report/ViewReportFormRecord?undelete=true'>" + bundle.getString("report.reportList.linkDeletedReportList") + "</a>";
    Vector vec = reportFilter.getNameList(reportId, n);
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><%=bDeletedList ? bundle.getString("report.reportList.deleted") + " " : ""%><fmt:message key="report.reportList.title"/></title>
        <LINK REL="StyleSheet" HREF="<%= request.getContextPath() %>/web.css" TYPE="text/css">
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
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
            <td><%=msg%>
            </td>
            <td width="10%" align="right" nowrap><a
                    href="<%= request.getContextPath() %>/report/ViewReportFormRecord"><fmt:message key="report.reportList.backToReportList"/></a> | <a
                    href="<%= request.getContextPath() %>/report/ViewReportFormConfig?id=<carlos:encode value='<%= reportId %>' context="uriComponent"/>"><fmt:message key="report.reportList.configuration"/></a></td>
        </tr>
    </table>
    <table width="100%" border="0" cellspacing="2" cellpadding="2">
        <form method="post" name="baseurl" action="<%= request.getContextPath() %>/report/ViewReportResult">
            <%
                Vector vecJS = new Vector();
                for (int i = 0; i < vec.size(); i++) {
                    String color = i % 2 == 0 ? "#EEEEFF" : "";
                    String[] strElt = (String[]) vec.get(i);
                    String itemId = strElt[3];
                    vecJS.add(strElt[4]);
            %>

            <tr bgcolor="<%=color%>">
                <td align="right" width="20%"><b><input type="checkbox"
                                                        name="<%="filter_" + itemId%>" <%="1".equals(itemId)?"checked":""%>></b>
                </td>
                <td><carlos:encode value='<%= strElt[0] %>' context="html"/>
                </td>
                <td width="5%" align="right"><input type="hidden"
                                                    name="<%="value_" + itemId%>" value="<carlos:encode value='<%= strElt[1] %>' context="htmlAttribute"/>"> <input
                        type="hidden" name="<%="position_" + itemId%>" value="<carlos:encode value='<%= strElt[2] %>' context="htmlAttribute"/>">
                    <input type="hidden" name="<%="dateFormat_" + itemId%>"
                           value="<carlos:encode value='<%= strElt[5] %>' context="htmlAttribute"/>"></td>
            </tr>
            <% } %>
            <tr bgcolor="silver">
                <td colspan="2" align="center"><input type="hidden" name="id"
                                                      value="<carlos:encode value='<%= reportId %>' context="htmlAttribute"/>"> <input type="submit" name="submit"
                    value="<fmt:message key='report.reportList.reportHtml'/>"> | <input
                        type="submit" name="submit"
                        value="<fmt:message key='report.reportList.reportCsv'/>"></td>
                <td align='right'></td>
            </tr>
        </form>
    </table>


    </body>
    <script type="text/javascript">
        <%
        for(int i=0; i<vecJS.size(); i++) {
            out.print(vecJS.get(i));
        }
        %>
    </script>
</html>
