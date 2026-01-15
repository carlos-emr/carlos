<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

--%>

v<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }
%>

<%@ page
        import="ca.openosp.openo.form.*, java.util.*,ca.openosp.openo.billing.ca.bc.pageUtil.*,ca.openosp.openo.db.*,ca.openosp.openo.billing.ca.bc.MSP.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title>OSCAR BC Billing - WCB</title>
        <script language="JavaScript">
            function billingFormActive() {
                oscarLog("billingFormActive")
                if (window.opener && window.opener.replaceWCB) {
                    oscarLog("Calling on replaceWCB");
                    window.opener.replaceWCB('<%=request.getAttribute("WCBFormId")%>');
                }
                oscarLog("billingFormActiveEnd");
                window.close();
            }
        </script>
    </head>
    <body onLoad="billingFormActive()" bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
    <input type="button" value="Close" onclick="window.close();"/>
    </body>
</html>


<%!

    String checked(String val, String str, boolean dfault) {
        String retval = "";
        if (str == null || str.equals("null")) {
            str = "";
        }
        if (str.equals("") && dfault) {
            retval = "CHECKED";
        } else if (str != null && str.equalsIgnoreCase(val)) {
            retval = "CHECKED";
        }
        return retval;
    }

    boolean isEmpty(ArrayList a) {
        boolean isEmpty = false;
        if (a.size() == 0) isEmpty = true;
        return isEmpty;
    }
%>
