<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String curUser_no = (String) session.getAttribute("user");
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";

    String fieldName = request.getParameter("fieldname") != null ? request.getParameter("fieldname") : "pg1_priCare";
%>

<%@ page import="java.sql.*" errorPage="/errorpage.jsp" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<jsp:useBean id="providerNameBean" class="java.util.Properties" scope="page"/>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Provider List</title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/css/receptionistapptstyle.css">
        <script language="JavaScript">
            //<!--

            function typeInData(v) {
                self.close();
                <% if(fieldName.equals("pg1_priCare") ) {%>
                opener.document.forms[0].pg1_priCare.value = v;
                <% } else {%>
                opener.document.forms[0]["<%= Encode.forJavaScript(StringUtils.noNull(fieldName)) %>"].value = v;
                <% }%>
                opener.recheckForm();
            }

            //-->
        </SCRIPT>
    </head>
    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">

    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="<%=deepcolor%>">
            <th><font face="Helvetica">Provider List</font></th>
        </tr>
        <tr>
            <td align="right">
                <!--input type="button" name="Button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>" onClick="window.print()"-->
                <input type="button" name="Button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>"
                       onClick="window.close()">
                </th>
        </tr>
    </table>

    <CENTER>
        <table width="100%" border="0" bgcolor="silver" cellspacing="2"
               cellpadding="2">
            <tr bgcolor='<%=deepcolor%>'>
                <TH align="center" width="50%" nowrap><a
                        href="formbcarpg1namepopup.jsp?orderby=first_name">First Name</a></TH>
                <TH align="center" width="50%"><a
                        href="formbcarpg1namepopup.jsp?orderby=last_name">Last Name</a></TH>
            </tr>
            <%
                int nItems = 0;
                String bgColor = "white";
                String lastName = null, firstName = null;
                for (Provider p : providerDao.getBillableProvidersInBC()) {

                    providerNameBean.setProperty(p.getProviderNo(), p.getFormattedName());
                    nItems++;
                    bgColor = nItems % 2 == 0 ? weakcolor : "white";
                    lastName = p.getLastName() != null ? p.getLastName() : "";
                    firstName = p.getFirstName() != null ? p.getFirstName() : "";
            %>
            <tr bgcolor="<%=bgColor%>"
                onMouseOver="this.style.backgroundColor='pink';"
                onMouseout="this.style.backgroundColor='<%=bgColor%>';"
                onClick='typeInData("<%= Encode.forJavaScriptAttribute(firstName + " " + lastName) %>");'>
                <td nowrap><%= Encode.forHtml(firstName) %>
                </td>
                <td nowrap><%= Encode.forHtml(lastName) %>
                </td>
            </tr>
            <%
                }
            %>

        </table>
        <br>


    </body>
</html>
