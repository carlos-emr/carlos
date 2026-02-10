<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page errorPage="/errorpage.jsp"
         import="java.util.*, io.github.carlos_emr.carlos.report.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportFilter" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%
    // Validate and sanitize report ID parameter
    int reportIdInt = 0;
    String reportId = "0";
    try {
        String idParam = request.getParameter("id");
        if (idParam != null && !idParam.trim().isEmpty()) {
            reportIdInt = Integer.parseInt(idParam);
            reportId = String.valueOf(reportIdInt); // Safe, validated version
        }
    } catch (NumberFormatException e) {
        // Log attempt and redirect or display error
        System.err.println("Possible SQL injection attempt with id: " + request.getParameter("id"));
        response.sendRedirect("reportFormRecord.jsp?error=invalid_id");
        return;
    }

    // get form name (using validated ID)
    String reportName = (new RptReportItem()).getReportName(reportId);

    boolean bDeletedList = false;
    String msg = "Limit To";
    Properties prop = new Properties();
    RptReportFilter reportFilter = new RptReportFilter();

    // delete/undelete list
    if (request.getParameter("undelete") != null && "true".equals(request.getParameter("undelete"))) {
        bDeletedList = true;
    }

    // delete action
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Delete")) {
        try {
            // check the input data
            String id = request.getParameter("id");
            int nId = id != null ? Integer.parseInt(id) : 0;
        } catch (NumberFormatException e) {
            // Handle invalid number
            System.err.println("Invalid ID format: " + request.getParameter("id"));
        }
    }

    // search the list
    int n = bDeletedList ? 0 : 1;
    String link = bDeletedList ? "<a href='reportFormRecord.jsp'>Report list</a>" : "<a href='reportFormRecord.jsp?undelete=true'>Deleted report list</a>";
    Vector vec = reportFilter.getNameList(reportId, n);

    // Store values for JSTL use
    pageContext.setAttribute("reportId", reportId);
    pageContext.setAttribute("reportName", reportName);
    pageContext.setAttribute("msg", msg);
    pageContext.setAttribute("link", link);
    pageContext.setAttribute("bDeletedList", bDeletedList ? "Deleted" : "");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><c:out value="${bDeletedList}"/> Report List</title>
        <LINK REL="StyleSheet" HREF="<%= request.getContextPath() %>/web.css" TYPE="text/css">
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
            }

            function onDelete() {
                ret = confirm("Are you sure you want to delete it?");
                return ret;
            }

            function onRestore() {
                ret = confirm("Are you sure you want to restore it?");
                return ret;
            }

            function onAdd() {
                if (document.baseurl.name.value.length < 2) {
                    alert("Please type in a valid name!");
                    return false;
                } else {
                    return true;
                }
            }

            // Modified function for SQL injection prevention
            function goPage(id) {
                // Ensure id is a number
                id = parseInt(id);
                if (!isNaN(id)) {
                    self.location.href = "reportFilter.jsp?id=" + id;
                } else {
                    alert("Invalid report ID");
                }
            }

            //-->

        </script>
    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr>
            <td align="left">&nbsp;</td>
        </tr>
    </table>

    <center>
        <table BORDER="1" CELLPADDING="0" CELLSPACING="0" WIDTH="80%">
            <tr BGCOLOR="#CCFFFF">
                <th><c:out value="${reportName}"/></th>
            </tr>
        </table>
    </center>
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr BGCOLOR="#CCCCFF">
            <td><c:out value="${msg}"/></td>
            <td width="10%" align="right" nowrap><a href="reportFormRecord.jsp">Back to Report List</a> |
                <a href="reportFormConfig.jsp?id=<c:out value="${reportId}"/>">Configuration</a></td>
        </tr>
    </table>
    <table width="100%" border="0" cellspacing="2" cellpadding="2">
        <form method="post" name="baseurl" action="reportResult.jsp">
            <%
                Vector vecJS = new Vector();
                for (int i = 0; i < vec.size(); i++) {
                    String color = i % 2 == 0 ? "#EEEEFF" : "";
                    String[] strElt = (String[]) vec.get(i);
                    String itemId = strElt[3];
                    vecJS.add(strElt[4]);

                    // Store for JSTL access
                    pageContext.setAttribute("color", color);
                    pageContext.setAttribute("itemId", itemId);
                    pageContext.setAttribute("display", strElt[0]);
                    pageContext.setAttribute("valueField", strElt[1]);
                    pageContext.setAttribute("positionField", strElt[2]);
                    pageContext.setAttribute("dateFormat", strElt[5]);
                    pageContext.setAttribute("checked", "1".equals(itemId) ? "checked" : "");
            %>
            <tr bgcolor="<c:out value="${color}"/>">
                <td align="right" width="20%">
                    <b><input type="checkbox" name="filter_<c:out value="${itemId}"/>" <c:if test="${checked eq 'checked'}">checked</c:if>></b>
                </td>
                <td><c:out value="${display}"/></td>
                <td width="5%" align="right">
                    <input type="hidden" name="value_<c:out value="${itemId}"/>" value="<c:out value="${valueField}"/>">
                    <input type="hidden" name="position_<c:out value="${itemId}"/>" value="<c:out value="${positionField}"/>">
                    <input type="hidden" name="dateFormat_<c:out value="${itemId}"/>" value="<c:out value="${dateFormat}"/>">
                </td>
            </tr>
            <% } %>
            <tr bgcolor="silver">
                <td colspan="2" align="center">
                    <input type="hidden" name="id" value="<c:out value="${reportId}"/>">
                    <input type="submit" name="submit" value="Report in HTML"> |
                    <input type="submit" name="submit" value="Report in CSV">
                </td>
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
