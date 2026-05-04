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
  Purpose: Supports updateINRbilling in the Ontario billing workflow.
  Expected request model data includes: inrUpdateModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    updateINRbilling.jsp

    Purpose:
        HTML form for editing a single INR billing row's service code and
        diagnostic code before re-submission. Posts to DbUpdateINRbilling.

    Data binding:
        All dynamic values come from request attribute `inrUpdateModel`
        (an InrBillingUpdateViewModel built by InrBillingUpdate2Action).
        No scriptlets — DAO access lives in the assembler.

    @since 2026-04-26
--%>
<%@ taglib uri="carlos" prefix="carlos" %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>UPDATE INR BILLING</title>
    <script language="JavaScript">
        <!--
        var remote = null;

        function rs(n, u, w, h, x) {
            args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
            remote = window.open(u, n, args);
            if (remote != null) {
                if (remote.opener == null)
                    remote.opener = self;
            }
            if (x == 1) {
                return remote;
            }
        }

        function onUnbilled() {
            if (confirm("You are about to delete the inr billing record, are you sure?")) {
                serviceform.inraction.value = "delete";
                serviceform.inraction.type = "submit";
                serviceform.submit();
            } else {
                serviceform.inraction.value = "no_delete";
                window.close()
            }
        }

        var awnd = null;

        function ScriptAttach() {
            f0 = escape(document.serviceform.xml_diagnostic_detail.value);
            f1 = document.serviceform.xml_dig_search1.value;
            awnd = rs('att', '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingDigSearch?name=' + f0 + '&search=' + f1, 600, 600, 1);
            awnd.focus();
        }

        function OtherScriptAttach() {
            t0 = escape(document.serviceform.xml_other1.value);
            awnd = rs('att', '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCodeSearch?name=' + t0 + '&name1=' + "" + '&name2=' + "" + '&search=', 600, 600, 1);
            awnd.focus();
        }
        //-->
    </script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/web.css"/>
</head>

<body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align=CENTER NOWRAP><font face="Helvetica" color="#FFFFFF">UPDATE
            INR BILLING</font></th>
    </tr>
</table>

<table BORDER="0" CELLPADDING="1" CELLSPACING="0" WIDTH="100%"
       BGCOLOR="#C4D9E7">
    <FORM NAME="serviceform" ACTION="DbUpdateINRbilling" METHOD="POST">
        <tr valign="top">
            <td rowspan="2" ALIGN="right" valign="middle">
                <div align="center">
                    <p>&nbsp;</p>
                    <table width="80%" border="1" cellspacing="0" cellpadding="0">
                        <tr>
                            <td colspan="3"><font
                                    face="Verdana, Arial, Helvetica, sans-serif" size="1"></font></td>
                        </tr>
                        <tr>
                            <td width="29%"><font face="Arial, Helvetica, sans-serif"
                                                  color="#000000" size="1">Demographic Name </font></td>
                            <td width="50%"><font
                                    face="Verdana, Arial, Helvetica, sans-serif" size="1"> <input
                                    type="hidden" name="demono" value="<carlos:encode value='${inrUpdateModel.demoNo}' context='htmlAttribute'/>" size="20">
                                <input type="hidden" name="billinginr_no" value="<carlos:encode value='${inrUpdateModel.billingInrNo}' context='htmlAttribute'/>">
                                <input type="text" name="demo_name" value="<carlos:encode value='${inrUpdateModel.demoName}' context='htmlAttribute'/>"
                                       size="20" readonly> </font></td>
                            <td rowspan="9" width="21%" valign="middle">
                                <p><br>
                                </p>
                            </td>
                        </tr>
                        <tr>
                            <td width="29%"><font size="1"
                                                  face="Arial, Helvetica, sans-serif">Demographic HIN</font></td>
                            <td width="50%"><input type="text" name="demo_hin"
                                                   value="<carlos:encode value='${inrUpdateModel.demoHin}' context='htmlAttribute'/> " size="20" readonly></td>
                        </tr>
                        <tr>
                            <td width="29%"><font size="1"
                                                  face="Arial, Helvetica, sans-serif">Demographic DOB</font></td>
                            <td width="50%"><input type="text" name="demo_dob"
                                                   value="<carlos:encode value='${inrUpdateModel.demoDob}' context='htmlAttribute'/> " size="20" readonly></td>
                        </tr>
                        <tr>
                            <td width="29%"><font face="Arial, Helvetica, sans-serif"
                                                  color="#000000" size="1">Service Code </font></td>
                            <td width="50%"><font
                                    face="Verdana, Arial, Helvetica, sans-serif" size="1"> <input
                                    type="text" name="service_code" size="10"
                                    value="<carlos:encode value='${inrUpdateModel.serviceCode}' context='htmlAttribute'/>"> <input
                                    type="hidden" name="service_unit" value="1"> </font></td>
                        </tr>
                        <tr>
                            <td width="29%"><font size="1"
                                                  face="Arial, Helvetica, sans-serif">Diagnostic Code</font></td>
                            <td width="50%"><input type="text" name="diag_code" size="20"
                                                   value="<carlos:encode value='${inrUpdateModel.dxCode}' context='htmlAttribute'/>"></td>
                        </tr>
                        <tr>
                            <td width="29%"><font
                                    face="Verdana, Arial, Helvetica, sans-serif" color="#0000FF"
                                    size="1"><b><i> <input type="SUBMIT" value="update"
                                                           name="inraction"> </i></b></font></td>
                            <td width="50%"><font
                                    face="Verdana, Arial, Helvetica, sans-serif" size="1"> <input
                                    type="SUBMIT" value="delete" name="inraction"> </font></td>
                        </tr>
                    </table>
                    <p><font face="Verdana" color="#0000FF"><b><i> </i></b></font> <br>
                    </p>
                </div>
            </td>
        </tr>
    </form>
</table>
</body>
</html>
