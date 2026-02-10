<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
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
<%@ page import="io.github.carlos_emr.carlos.login.*" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptReportItem" %>
<%
    boolean bDeletedList = false;
    String msg = "Report List";
    Properties prop = new Properties();
    RptReportItem reportItem = new RptReportItem();

    // delete/undelete list
    if (request.getParameter("undelete") != null && "true".equals(request.getParameter("undelete"))) {
        bDeletedList = true;
    }

    // delete action - with proper validation
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Delete")) {
        try {
            // check the input data
            String id = request.getParameter("id");
            int nId = id != null ? Integer.parseInt(id) : 0;
            if (!reportItem.deleteRecord(nId)) {
                msg = "The report is NOT deleted by some reasons. Please check your action.";
            }
        } catch (NumberFormatException e) {
            msg = "Invalid report ID format";
        }
    }

    // undelete action - with proper validation
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Restore")) {
        try {
            // check the input data
            String id = request.getParameter("id");
            int nId = id != null ? Integer.parseInt(id) : 0;
            if (!reportItem.unDeleteRecord(nId)) {
                msg = "The report is NOT undeleted by some reasons. Please check your action.";
            }
        } catch (NumberFormatException e) {
            msg = "Invalid report ID format";
        }
    }

    // add action - with proper validation and sanitization
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Add")) {
        // check the input data
        String report_name = request.getParameter("name");

        // Validate input
        if (report_name == null || report_name.trim().length() < 2) {
            msg = "Report name must be at least 2 characters";
        } else {
            // Sanitize input to prevent XSS
            String sanitizedName = StringEscapeUtils.escapeHtml4(report_name);
            reportItem.setReport_name(sanitizedName);
            if (!reportItem.insertRecord()) {
                msg = "The report is NOT added by some reasons. Please check your action.";
            }
        }
    }

    // search the list
    int n = bDeletedList ? 0 : 1;
    String link = bDeletedList ? "<a href='reportFormRecord.jsp'>Report list</a>" : "<a href='reportFormRecord.jsp?undelete=true'>Deleted report list</a>";
    Vector vec = reportItem.getNameList(n);

    // Store values for JSTL use
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

            // Modified to validate id parameter - prevent SQL injection
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
                <th><c:out value="${msg}"/></th>
            </tr>
        </table>
    </center>
    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
        <tr BGCOLOR="#CCCCFF">
            <td align="right"><c:out value="${link}" escapeXml="false"/></td>
        </tr>
    </table>
    <table width="100%" border="0" cellspacing="2" cellpadding="2">
        <% for (int i = 0; i < vec.size(); i++) {
            String color = i % 2 == 0 ? "#EEEEFF" : "";
            prop = (Properties) vec.get(i);
            String itemId = prop.getProperty("id");
            String propValue = prop.getProperty(itemId);

            // Store for JSTL access
            pageContext.setAttribute("color", color);
            pageContext.setAttribute("itemId", itemId);
            pageContext.setAttribute("propValue", propValue);
            pageContext.setAttribute("rowNum", i + 1);
        %>
        <form method="post" name="baseurl<%=i+1%>" action="reportFormRecord.jsp">
            <tr bgcolor="<c:out value="${color}"/>">
                <td align="right"><b><c:out value="${rowNum}"/></b></td>
                <td onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                    onMouseout="this.style.backgroundColor='<c:out value="${color}"/>';"
                    onClick="goPage('<c:out value="${itemId}"/>')">
                    <c:out value="${propValue}"/>
                </td>
                <td align="right">
                    <input type="hidden" name="id" value="<c:out value="${itemId}"/>">
                    <% if (!bDeletedList) { %>
                        <input type="submit" name="submit" value="Delete" onclick="javascript:return onDelete();">
                    <% } else { %>
                        <input type="submit" name="submit" value="Restore" onclick="javascript:return onRestore();">
                    <% } %>
                </td>
            </tr>
        </form>
        <% } %>
    </table>

    <hr>
    <table width="60%" border="0" cellspacing="2" cellpadding="2">
        <tr>
            <td>Add a new report</td>
        </tr>
        <tr>
            <td align="center">
                <form method="post" name="baseurl" action="reportFormRecord.jsp">
                    <input type="text" name="name" value="" size="60"/>
                    <input type="submit" name="submit" value="Add" onclick="javascript:return onAdd();"/>
                </form>
            </td>
        </tr>
    </table>
    </body>
</html>
