<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `billingOB2.jsp` for the Ontario billing workflow.
  Expected request model data includes: ob2Model.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOHIPBillingHistoryViewModel" %>
<%
    // BillingOB2View2Action enforces _billing r and assembles the view model
    // with the 6 DAO lookups the JSP body used to perform. "OB" = OHIP
    // Billing, not obstetric.
    BillingOHIPBillingHistoryViewModel ob2Model = (BillingOHIPBillingHistoryViewModel) request.getAttribute("ob2Model");
    if (ob2Model == null) {
        // Defensive fallback: any caller that forwards directly here gets a
        // stub render. The canonical entrypoint is billing/CA/ON/ViewBillingOB2.
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingOB2.jsp reached without ob2Model — caller should route "
              + "through billing/CA/ON/ViewBillingOB2.");
        ob2Model = BillingOHIPBillingHistoryViewModel.builder().build();
    }
%>

<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>Billing History</title>
</head>

<body bgcolor="#FFFFFF" text="#000000">
<SCRIPT Language="Javascript">
    function printBill() {
        if (window.print) {
            window.print();
        } else {
            var WebBrowser = '<OBJECT ID="WebBrowser1" WIDTH=0 HEIGHT=0 CLASSID="CLSID:8856F961-340A-11D0-A96B-00C04FD705A2"></OBJECT>';
            document.body.insertAdjacentHTML('beforeEnd', WebBrowser);
            WebBrowser1.ExecWB(6, 2);
        }
    }
</script>

<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align='LEFT'><input type='button' name='print' value='Print'
                                onClick='window.print()'></th>
        <th align='CENTER'><font face="Arial, Helvetica, sans-serif"
                                 color="#FFFFFF">Billing Summary </font></th>
        <th align='RIGHT'><input type='button' name='close' value='Done'
                                 onClick='window.close()'></th>
    </tr>
</table>
<br>
<table width="600" border="1">
    <tr bgcolor="#CCCCCC">
        <td colspan="2" height="21"><font size="2"
                                          face="Arial, Helvetica, sans-serif"><b><font size="3">Patient
            Information</font></b></font></td>
    </tr>
    <tr>
        <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Patient Name: <carlos:encode value="${ob2Model.demoName}" context="html"/>
        </font></b></td>
        <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Health# : <carlos:encode value="${ob2Model.hin}" context="html"/>
        </font></b></td>
    </tr>
    <tr>
        <td><font size="2" face="Arial, Helvetica, sans-serif"><b>Sex:
            <carlos:encode value="${ob2Model.demoSex}" context="html"/>
        </b></font></td>
        <td><font size="2"><b><font
                face="Arial, Helvetica, sans-serif">D.O.B. : <carlos:encode value="${ob2Model.demoDob}" context="html"/>
        </font></b></font></td>
    </tr>
    <tr>
        <td><b><font size="2" face="Arial, Helvetica, sans-serif">Address:
            <carlos:encode value="${ob2Model.demoAddress}" context="html"/>
        </font></b></td>
        <td><b><font size="2" face="Arial, Helvetica, sans-serif">City:
            <carlos:encode value="${ob2Model.demoCity}" context="html"/>
        </font></b></td>
    </tr>
    <tr>
        <td><b><font size="2" face="Arial, Helvetica, sans-serif">Province:
            <carlos:encode value="${ob2Model.demoProvince}" context="html"/>
        </font></b></td>
        <td><b><font size="2" face="Arial, Helvetica, sans-serif">Postal
            Code: <carlos:encode value="${ob2Model.demoPostal}" context="html"/>
        </font></b></td>
    </tr>
</table>
<table width="600" border="1">
    <tr bgcolor="#CCCCCC">
        <td colspan="2"><font size="2"
                              face="Arial, Helvetica, sans-serif"><b><font size="3">Billing
            Information</font></b></font></td>
    </tr>
    <tr>
        <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Billing Type: <carlos:encode value="${ob2Model.billType}" context="html"/>
        </font></b></td>
        <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Billing Date: <carlos:encode value="${ob2Model.billDate}" context="html"/>
        </font></b></td>
    </tr>
    <tr>
        <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Visit Type: <carlos:encode value="${ob2Model.visitType}" context="html"/>
        </font></b></td>
        <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Visit Date: <carlos:encode value="${ob2Model.visitDate}" context="html"/>
        </font></b></td>
    </tr>
    <tr>
        <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Visit Location: <carlos:encode value="${ob2Model.billLocation}" context="html"/>
        </font></b></td>
        <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Billing Physician#: <carlos:encode value="${ob2Model.providerFirst}" context="html"/> <carlos:encode value="${ob2Model.providerLast}" context="html"/>
        </font></b></td>
    </tr>
    <tr>
        <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Appointment Physician: <carlos:encode value="${ob2Model.apptProviderFirst}" context="html"/> <carlos:encode value="${ob2Model.apptProviderLast}" context="html"/>
        </font></b></td>
        <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Secondary Physician: <carlos:encode value="${ob2Model.asstProviderFirst}" context="html"/> <carlos:encode value="${ob2Model.asstProviderLast}" context="html"/>
        </font></b></td>
    </tr>
    <tr>
        <td width="54%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Creator: <carlos:encode value="${ob2Model.creatorFirst}" context="html"/> <carlos:encode value="${ob2Model.creatorLast}" context="html"/>
        </font></b></td>
        <td width="46%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Update Date: <carlos:encode value="${ob2Model.updateDate}" context="html"/>
        </font></b></td>
    </tr>
</table>
<table width="600" border="1">
    <tr>
        <td width="22%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Service Code</font></b></td>
        <td width="58%"><b><font face="Arial, Helvetica, sans-serif"
                                 size="2">Description</font></b></td>
        <td width="6%">
            <div align="right"><b><font size="2"
                                        face="Arial, Helvetica, sans-serif">#Unit</font></b></div>
        </td>
        <td width="14%">
            <div align="right"><b><font
                    face="Arial, Helvetica, sans-serif" size="2">$ Fee</font></b></div>
        </td>
    </tr>
    <c:if test="${ob2Model.billDetailLoaded}">
    <tr>
        <td width="22%"><font face="Arial, Helvetica, sans-serif"
                              size="2"><carlos:encode value="${ob2Model.serviceCode}" context="html"/>
        </font></td>

        <td width="58%"><font face="Arial, Helvetica, sans-serif"
                              size="2"><carlos:encode value="${ob2Model.serviceDesc}" context="html"/>
        </font></td>
        <td width="6%">
            <div align="right"><font size="2"
                                     face="Arial, Helvetica, sans-serif"><carlos:encode value="${ob2Model.billUnit}" context="html"/>
            </font></div>
        </td>
        <td width="14%">
            <div align="right"><font face="Arial, Helvetica, sans-serif"
                                     size="2"><carlos:encode value="${ob2Model.billAmount}" context="html"/>
            </font></div>
        </td>
    </tr>
    </c:if>
    <tr bgcolor="#CCCCCC">
        <td colspan="4"><font face="Arial, Helvetica, sans-serif"
                              size="2"><b>Diagnostic Code</b></font></td>

    </tr>
    <tr>
        <td width="22%"><font face="Arial, Helvetica, sans-serif"
                              size="2"><carlos:encode value="${ob2Model.diagCode}" context="html"/>
        </font></td>
        <td colspan="3">
            <div align="left"><font face="Arial, Helvetica, sans-serif"
                                    size="2"><carlos:encode value="${ob2Model.diagDesc}" context="html"/>
            </font></div>
        </td>

    </tr>
    <tr>
        <td width="22%">&nbsp;</td>
        <td colspan="2">
            <div align="right"><font face="Arial, Helvetica, sans-serif"
                                     size="2">Total: </font></div>
        </td>
        <td width="14%">
            <div align="right"><font face="Arial, Helvetica, sans-serif"
                                     size="2"><carlos:encode value="${ob2Model.billTotal}" context="html"/>
            </font></div>
        </td>
    </tr>
</table>

</body>
</html>
