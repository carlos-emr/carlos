<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page errorPage="/errorpage.jsp"
         import="java.util.*,java.math.*,java.net.*,java.sql.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.*" %>
<%@ page import="io.github.carlos_emr.carlos.billing.ca.on.pageUtil.*" %>
<%@ page import="io.github.carlos_emr.carlos.billing.ca.on.data.*" %>
<%@page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingCorrectionPrep" %>


<%
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    if (securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
        String billingNo = request.getParameter("xml_billing_no");
        String error = null;

        if (billingNo != null && !billingNo.equals("")) {

            //String user_no = (String) session.getAttribute("user");
            BillingCorrectionPrep bObj = new BillingCorrectionPrep();
            List lObj = bObj.getBillingClaimHeaderObj(billingNo);
            BillingClaimHeader1Data ch1Obj = (BillingClaimHeader1Data) lObj.get(0);
            boolean bs = bObj.updateBillingClaimHeader(ch1Obj, request);

            // update item objs
            if (lObj.size() > 1 && !"D".equals(ch1Obj.getStatus())) {
                //lObj.remove(0);
                bs = bObj.updateBillingItem(lObj, request);
            }

        } else {
            error = "No xml_billing_no parameter passed. Update failed";
        }

%>
<p>
        <%if(error == null) { %>
<h1>Successful Update of a billing Record.</h1>
<%} else { %>
<h1 style="color:red"><%=error %>
</h1>
<%} %>
</p>
<% if (request.getParameter("submit").equals("Submit&Correct Another")) { %>
<center><input type='button' name='back'
               value='Correct Another'
               onclick='window.location.href="billingONCorrection.jsp?billing_no="'/>
</center>
<%
} else {
    if (error == null) {
%>
<script LANGUAGE="JavaScript">
    self.close();
</script>
<%
        }
    }
} else {
%>
<h1 style="color:red">You don't have billing rights!</h1>
<% } %>
