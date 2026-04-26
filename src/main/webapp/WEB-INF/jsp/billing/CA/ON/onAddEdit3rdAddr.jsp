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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.OnAddEdit3rdAddrViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.web.OnAddEdit3rdAddr2Action" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<fmt:setBundle basename="oscarResources"/>
<%--
  Defensive model-resolver: ensures ${addrModel} is set on the request even on
  the unlikely path where this JSP is reached without going through
  OnAddEdit3rdAddr2Action. Re-runs the _billing w privilege check for parity.
--%>
<%
    if (request.getAttribute("addrModel") == null) {
        if (session.getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return;
        }
        SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "onAddEdit3rdAddr.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("onAddEdit3rdAddr.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("onAddEdit3rdAddr.jsp fallback: missing required sec object (_billing)");
        }
        request.setAttribute("addrModel", new OnAddEdit3rdAddr2Action().assembleViewModel(request));
    }
%>

<html>
    <head>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <title>Add/Edit Service Code</title>
        <link rel="stylesheet" type="text/css" href="billingON.css"/>
        <link rel="StyleSheet" type="text/css" href="${pageContext.request.contextPath}/web.css"/>
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.request.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- main calendar program -->
        <script type="text/javascript" src="${pageContext.request.contextPath}/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>">
        </script>
        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/share/calendar/calendar-setup.js"></script>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                document.forms[0].company_name.focus();
                document.forms[0].company_name.select();
            }

            function onSearch() {
                //document.forms[0].submit.value="Search";
                var ret = checkServiceCode();
                return ret;
            }

            function onSave() {
                //document.forms[0].submit.value="Save";
                var ret = checkServiceCode();
                if (ret == true) {
                    ret = checkAllFields();
                }
                if (ret == true) {
                    ret = confirm("Are you sure you want to save?");
                }
                return ret;
            }

            function checkServiceCode() {
                var b = true;
                if (document.forms[0].service_code.value.length != 5 || !isServiceCode(document.forms[0].service_code.value)) {
                    b = false;
                    alert("You must type in a service code with 5 (upper case) letters/digits. The service code ends with \'A\' or \'B\'...");
                }
                return b;
            }

            function isServiceCode(s) {
                // temp for 0.
                if (s.length == 0) return true;
                if (s.length != 5) return false;
                if ((s.charAt(0) < "A") || (s.charAt(0) > "Z")) return false;
                if ((s.charAt(4) < "A") || (s.charAt(4) > "Z")) return false;

                var i;
                for (i = 1; i < s.length - 1; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (((c < "0") || (c > "9"))) return false;
                }
                return true;
            }

            function checkAllFields() {
                var b = true;
                for (var i = 0; i < 10; i++) {
                    var fieldItem = eval("document.forms[1].serviceCode" + i);
                    if (fieldItem.value.length > 0) {
                        if (!isServiceCode(fieldItem.value)) {
                            b = false;
                            alert("You must type in a Service Code in the field!");
                        }
                    }
                    var fieldItem1 = eval("document.forms[1].serviceUnit" + i);
                    var fieldItem2 = eval("document.forms[1].serviceAt" + i);
                    if (fieldItem1.value.length > 0) {
                        if (!isNumber(fieldItem1.value)) {
                            b = false;
                            alert("You must type in a number in the field!");
                        }
                    }
                    if (fieldItem2.value.length > 0) {
                        if (!isNumber(fieldItem2.value)) {
                            b = false;
                            alert("You must type in a number in the field!");
                        }
                    }
                }
                var fieldItemDx = eval("document.forms[1].dx");
                if (fieldItemDx.value.length > 0) {
                    if (!isNumber(fieldItemDx.value) || fieldItemDx.value.length != 3) {
                        b = false;
                        alert("You must type in a number in the right Dx field!");
                    }
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                // All characters are numbers.
                return true;
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            //-->

        </script>
    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0"
          rightmargin="0">
    <center>
        <table BORDER="1" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
            <tr class="myDarkGreen">
                <%-- The action's assembleViewModel composes the message from
                     static strings + SafeEncode.forHtml(companyName), so it
                     contains pre-encoded operator input wrapped in legacy
                     <font color> tags. Output unescaped. --%>
                <th><font color="white"><c:out value="${addrModel.message}" escapeXml="false"/></font></th>
            </tr>
        </table>
    </center>

    <table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%"
           class="myYellow">
        <form method="post" name="baseur0" action="${pageContext.request.contextPath}/billing/CA/ON/OnAddEdit3rdAddr">

            <tr>
                <td align="right" width="50%"><select name="company_name"
                                                      id="company_name">
                    <option selected="selected" value="">- choose one -</option>
                    <c:forEach var="propT" items="${addrModel.companyOptions}">
                        <option value="<carlos:encode value='${propT["company_name"]}' context='htmlAttribute'/>"><carlos:encode value='${propT["company_name"]}' context='html'/></option>
                    </c:forEach>
                </select></td>
                <td><input type="hidden" name="submit" value="Search"> <input
                        type="submit" name="action" value=" Edit "></td>
            </tr>
        </form>
    </table>
    <table width="100%" border="0" cellspacing="2" cellpadding="2">
        <form method="post" name="baseurl" action="${pageContext.request.contextPath}/billing/CA/ON/OnAddEdit3rdAddr">
            <tr class="myGreen">
                <td align="right"><b>Company Name</b></td>
                <td><input type="text" name="company_name"
                           value="<carlos:encode value='${addrModel.companyName}' context='htmlAttribute'/>" size='40'
                           maxlength='50'/> <input type="submit" name="submit" value="Search"
                                                   onclick="javascript:return onSearch();"></td>
            </tr>
            <tr class="myIvory">
                <td align="right"><b>Attention</b></td>
                <td><input type="text" name="attention"
                           value="<carlos:encode value='${addrModel.attention}' context='htmlAttribute'/>" size='40'
                           maxlength='50'/></td>
            </tr>
            <tr class="myGreen">
                <td align="right"><b>Address</b></td>
                <td><input type="text" name="address"
                           value="<carlos:encode value='${addrModel.address}' context='htmlAttribute'/>" size='40' maxlength='50'/>
                </td>
            </tr>
            <tr class="myIvory">
                <td align="right"><b>City</b></td>
                <td><input type="text" name="city"
                           value="<carlos:encode value='${addrModel.city}' context='htmlAttribute'/>" size='40' maxlength='50'/>
                </td>
            </tr>
            <tr class="myGreen">
                <td align="right"><b>Province</b></td>
                <td><input type="text" name="province"
                           value="<carlos:encode value='${addrModel.province}' context='htmlAttribute'/>" size='20'
                           maxlength='20'/></td>
            </tr>
            <tr class="myIvory">
                <td align="right"><b>postcode</b></td>
                <td><input type="text" name="postcode"
                           value="<carlos:encode value='${addrModel.postcode}' context='htmlAttribute'/>" size='10'
                           maxlength='10'/></td>
            </tr>
            <tr class="myGreen">
                <td align="right"><b>Tel.</b></td>
                <td><input type="text" name="telephone"
                           value="<carlos:encode value='${addrModel.telephone}' context='htmlAttribute'/>" size='40'
                           maxlength='50'/></td>
            </tr>
            <tr class="myIvory">
                <td align="right"><b>Fax</b></td>
                <td><input type="text" name="fax"
                           value="<carlos:encode value='${addrModel.fax}' context='htmlAttribute'/>" size='40' maxlength='50'/>
                </td>
            </tr>
            <tr>
                <td align="center" class="myGreen" colspan="2"><input
                        type="hidden" name="action" value="<carlos:encode value='${addrModel.action}' context='htmlAttribute'/>"> <input
                        type="submit" name="submit"
                        value="<fmt:message key="admin.resourcebaseurl.btnSave"/>"
                        onclick="javascript:return onSave();"> <input type="button"
                                                                      name="Cancel"
                                                                      value="<fmt:message key="admin.resourcebaseurl.btnExit"/>"
                                                                      onClick="window.close()"> <input type="hidden"
                                                                                                       name="id"
                                                                                                       value="<carlos:encode value='${addrModel.id}' context='htmlAttribute'/>"/>
                </td>
            </tr>
        </form>
    </table>
    </body>
    <script type="text/javascript">
        //Calendar.setup( { inputField : "billingservice_date", ifFormat : "%Y-%m-%d", showsTime :false, button : "billingservice_date_cal", singleClick : true, step : 1 } );
    </script>
</html>
