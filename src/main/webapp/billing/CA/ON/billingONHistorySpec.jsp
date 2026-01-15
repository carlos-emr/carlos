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
<!DOCTYPE html>
<%@ page
        import="java.util.*, java.sql.*, java.net.*, ca.openosp.*, ca.openosp.openo.db.*" %>
<%@ page import="ca.openosp.openo.billing.ca.on.data.*" %>
<%@ page import="ca.openosp.openo.utility.DateRange" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.BillingItemData" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.JdbcBillingReviewImpl" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.BillingClaimHeader1Data" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.BillingDataHlp" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curProvider_no;
    curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    String strDay = "0";
    if (request.getParameter("day") != null) strDay = request.getParameter("day");

    Calendar calendar = Calendar.getInstance();
    String strToday = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DATE);
    calendar.add(Calendar.DATE, Integer.parseInt(strDay) * (-1));
    String strStartDay = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DATE);
    ;

    DateRange pDateRange = new DateRange(MyDateFormat.getSysDate(strStartDay), MyDateFormat.getSysDate(strToday));

    String serviceCode = request.getParameter("serviceCode") != null ? request.getParameter("serviceCode") : "";
%>


<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>
<html>
<head>
    <title>BILLING HISTORY</title>
    <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->
    <script src="${pageContext.request.contextPath}/js/global.js"></script>
    <script language="JavaScript">
        function upCaseCtrl(ctrl) {
            ctrl.value = ctrl.value.toUpperCase();
        }
    </script>
</head>
<body>

<table style="width:100%">
    <tr class="myDarkGreen">
        <th>BILLING HISTORY</th>
    </tr>
</table>

<form method="post" name="titlesearch" action="billingONHistorySpec.jsp">
    <table style="width:95%; margin:auto;">
        <tr>
            <td style="text-align:left"><%=Encode.forHtml(request.getParameter("demo_name"))%>
                (<%=Encode.forHtml(request.getParameter("demographic_no"))%>)
                <%=strToday + " - " + strStartDay %>
            </td>
            <td style="text-align:right">Service Code <input type="text"
                                                             name="serviceCode"
                                                             value="<%=Encode.forHtml(serviceCode) %>" maxlength="5"
                                                             onBlur="upCaseCtrl(this)"/> <input type="hidden" name="day"
                                                                                                value="<%=strDay %>"/>
                <input type="hidden" name="demo_name"
                       value="<%=Encode.forHtml(request.getParameter("demo_name")) %>"/> <input
                        type="hidden" name="demographic_no"
                        value="<%=Encode.forHtml(request.getParameter("demographic_no")) %>"/> <input
                        type="submit" name="submit" value="Search"/></td>
        </tr>
    </table>
</form>


<table style="width:95%; margin:auto;" class="table table-striped table-condensed">
    <thead>
    <tr class="myYellow">
        <th style="text-align:center; white-space:nowrap;"><b>Invoice No.</b></th>
        <th style="text-align:center"><b>Appt. Date</b></th>
        <th style="text-align:center"><b>Bill Type</b></th>
        <th style="text-align:center"><b>Service Code</b></th>
        <th style="text-align:center"><b>Dx</b></th>
        <th style="text-align:center"><b>Fee</b></th>
    </tr>
    </thead>
    <tbody>
        <% // new billing records
JdbcBillingReviewImpl dbObj = new JdbcBillingReviewImpl();
String limit = "";

List aL = dbObj.getBillingHist(request.getParameter("demographic_no"), 10000000, 0, pDateRange);
int nItems=0;
for(int i=0; i<aL.size(); i=i+2) {
	BillingClaimHeader1Data obj = (BillingClaimHeader1Data) aL.get(i);
	BillingItemData itObj = (BillingItemData) aL.get(i+1);
	String strServiceCode = itObj.getService_code();
	if(!serviceCode.equals("")) {
		if(strServiceCode.indexOf(serviceCode) < 0) {
			continue;
		}
	}
%>
    <tr>
        <td style="text-align:center"><%=obj.getId()%>
        </td>
        <td style="text-align:center"><%=obj.getBilling_date()%> <%--=obj.getBilling_time()--%></td>
        <td style="text-align:center"><%=BillingDataHlp.propBillingType.getProperty(obj.getStatus(), "")%>
        </td>
        <td style="text-align:center"><%=strServiceCode%>
        </td>
        <td style="text-align:center"><%=itObj.getDx()%>
        </td>
        <td style="text-align:center"><%=obj.getTotal()%>
        </td>
    </tr>
        <%
	nItems++;
}
%>
    <tbody>

</table>
<br> &nbsp;<%=nItems %> Items
<p>

<table style="width:100%">
    <tr>
        <td style="text-align:right"><a href="" onClick="self.close();">Close
            the Window</a></td>
    </tr>
</table>


</body>
</html>