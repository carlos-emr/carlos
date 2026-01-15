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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="java.lang.*, java.util.*, java.text.*,java.sql.*, ca.openosp.*" errorPage="/errorpage.jsp" %>

<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Security" %>
<%@ page import="ca.openosp.openo.commn.dao.SecurityDao" %>
<%@ page import="ca.openosp.openo.security.MfaActions2Action" %>
<%@ page import="ca.openosp.openo.managers.MfaManager" %>
<%@ page import="ca.openosp.OscarProperties" %>


<%!
    OscarProperties op = OscarProperties.getInstance();
%>

<fmt:setBundle basename="oscarResources"/>

<html>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/checkPassword.js.jsp"></script>
        <script src="<%=request.getContextPath()%>/share/javascript/prototype.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdatesecurity.title"/></title>
        <link rel="stylesheet" type="text/css" href="bcArStyle.css">
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
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
        <script type="text/javascript">
            <!--
            function setfocus(el) {
                this.focus();
                document.updatearecord.elements[el].focus();
                document.updatearecord.elements[el].select();
            }

            function onsub() {
                if (document.updatearecord.user_name.value == "") {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formUserName"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgIsRequired"/>');
                    setfocus('user_name');
                    return false;
                }
                if (document.updatearecord.user_name.value.length > 10) {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formUserName"/>: <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgAtMost"/> 10 <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgAlphaNumeric"/>');
                    setfocus('user_name');
                    return false;
                }
                if (document.updatearecord.password.value == "") {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formPassword"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgIsRequired"/>');
                    setfocus('password');
                    return false;
                }
                if (document.updatearecord.password.value != "*********" && !validatePassword(document.updatearecord.password.value)) {
                    setfocus('password');
                    return false;
                }
                if (document.forms[0].password.value != document.forms[0].conPassword.value) {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgPasswordNotConfirmed"/>');
                    setfocus('conPassword');
                    return false;
                }
                if (document.updatearecord.provider_no.value == "") {
                    return false;
                }
                if (document.forms[0].b_ExpireSet.checked && document.forms[0].date_ExpireDate.value.length < 10) {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formDate"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgIsRequired"/>');
                    setfocus('date_ExpireDate');
                    return false;
                }
                if (document.forms[0].b_RemoteLockSet.checked || document.forms[0].b_LocalLockSet.checked) {
                    if (document.forms[0].pin.value == "") {
                        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formPIN"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgIsRequired"/>');
                        setfocus('pin');
                        return false;
                    }
                }
                if (document.forms[0].pin.value != "****" && !validatePin(document.forms[0].pin.value)) {
                    setfocus('pin');
                    return false;
                }
                if (document.forms[0].pin.value != document.forms[0].conPin.value) {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgPinNotConfirmed"/>');
                    setfocus('conPin');
                    return false;
                }
                return true;
            }


	/**
	 * Handles the change event of the MFA checkbox.
	 * @param {HTMLInputElement} checkbox - The MFA checkbox element.
	 */
	function handleMfaChange(checkbox) {
		updateMfaElementsVisibility(checkbox.checked, checkbox.checked);
	}

	function updatePinComponentsAccess(checked) {
		let pinCheckbox = document.getElementsByName('b_RemoteLockSet')
		let pinConfCheckbox = document.getElementsByName('b_LocalLockSet')
		let pinInput = document.getElementsByName('pin')
		let pinConfInput = document.getElementsByName('conPin')

		pinCheckbox[0].disabled = checked;
		pinConfCheckbox[0].disabled = checked;
		pinInput[0].disabled = checked;
		pinConfInput[0].disabled = checked;
	}

	/**
	 * Updates the visibility of MFA-related elements based on the provided flags.
	 *
	 * @param {boolean} showMfaNote - Whether to show the MFA note.
	 * @param {boolean} showResetMfaLink - Whether to show the reset MFA link.
	 */
	function updateMfaElementsVisibility(showMfaNote, showResetMfaLink) {
		let mfaNote = document.getElementById('mfaNote');
		let resetMfa = document.getElementById('resetMfaLink');

		if (resetMfa !== null)
			resetMfa.style.display = showResetMfaLink ? 'inline' : 'none';
		if (mfaNote !== null)
			mfaNote.style.display = showMfaNote ? 'inline' : 'none';
	}

	/**
	 * Handles the reset MFA action for a given sec ID.
	 *
	 * @param {number} securityId - The ID of the sec record.
	 */
	function handleResetMfa(securityId) {
		if (confirm("<fmt:message key="admin.securityAddRecord.mfa.reset.confirm"/>")) {
			let url = "${pageContext.request.contextPath}/securityRecord/mfa.do";
			let data = {
				method: '<%= MfaActions2Action.METHOD_RESET_MFA %>',
				securityId: securityId
			};
			new Ajax.Request(url, {
				method: 'get',
				parameters: data,
				onSuccess: function (transport) {
					updateMfaElementsVisibility(true, false);
				},
				onFailure: function () {
					console.log("error resetting MFA");
				}
			});
		}
	}

            //-->
        </script>
    </head>

    <body onLoad="setfocus('user_name')" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">

                <th align="CENTER"><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdatesecurity.description"/></font></th>
            </tr>
        </table>
        <table cellspacing="0" cellpadding="2" width="100%" border="0">
            <form method="post" action="securityupdate.jsp" name="updatearecord" onsubmit="return onsub()">
                <%
                    SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
                    Integer securityId = Integer.valueOf(request.getParameter("keyword"));
                    Security security = securityDao.find(securityId);

                    if (security == null) {
                %>
                <tr>
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdatesecurity.msgFailed"/></td>
                </tr>
                <%
                } else {
                %>
                <tr>
                    <td width="50%" align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formUserName"/>:
                    </td>
                    <td><input type="text" name="user_name" maxlength="10"
                               value="<%= security.getUserName() %>"></td>
                </tr>
                <tr>
                    <td align="right" nowrap><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formPassword"/>:
                    </td>
                    <td><input type="password" name="password" value="*********" maxlength="15"> <font
                            size="-2">(<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgAtLeast"/>
                        <%=op.getProperty("password_min_length")%> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgSymbols"/>)</font></td>
                </tr>
                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formConfirm"/>:
                    </td>
                    <td><input type="password" name="conPassword" value="*********" maxlength="15"></td>
                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formProviderNo"/>:
                        </div>
                    </td>
                    <td><%= security.getProviderNo() %>
                        <input type="hidden" name="provider_no"
                               value="<%= security.getProviderNo() %>"></td>
                </tr>
                <!-- new sec -->
                <tr>
                    <td align="right" nowrap><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formExpiryDate"/>:
                    </td>
                    <td><input type="checkbox" name="b_ExpireSet" value="1"
                            <%= security.getBExpireset()==0?"":"checked" %>> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formDate"/>: <input
                            type="text" name="date_ExpireDate" id="date_ExpireDate"
                            value="<%=  security.getDateExpiredate() ==null?"": security.getDateExpiredate()  %>"
                            size="10" readonly/> <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                      id="date_ExpireDate_cal"/></td>
                </tr>

		<% if (MfaManager.isOscarLegacyPinEnabled()) { %>

                <tr>
                    <td align="right" nowrap><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formRemotePIN"/>:
                    </td>
                    <td><input type="checkbox" name="b_RemoteLockSet" value="1"
                            <%=security.isUsingMfa() ? "disabled" : ""%>
                            <%= security.getBRemotelockset()==0?"":"checked" %>>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formLocalPIN"/>: <%=security.isUsingMfa() ? "disabled" : ""%>
                                                                                  value="1" <%= security.getBLocallockset()==0?"":"checked" %>>
                    </td>
                </tr>
                <!-- new sec -->
                <tr>
                    <td align="right" nowrap><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formPIN"/>:
                    </td>
                    <td><input type="password" name="pin" value="****" <%=security.isUsingMfa() ? "disabled" : ""%> size="6" maxlength="6"> <font
                            size="-2">(<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgAtLeast"/>
                        <%=op.getProperty("password_pin_min_length")%> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.msgDigits"/>)</font>
                    </td>
                </tr>
                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityrecord.formConfirm"/>:
                    </td>
                    <td><input type="password" name="conPin" value="****" <%=security.isUsingMfa() ? "disabled" : ""%> size="6" maxlength="6" /></td>
                </tr>

		<% } %>

                <%
                    if (!OscarProperties.getInstance().getBooleanProperty("mandatory_password_reset", "false")) {
                %>
                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.forcePasswordReset"/>:
                    </td>
                    <td>
                        <select name="forcePasswordReset">
                            <option value="1" <% if (security != null && security.isForcePasswordReset() != null && security.isForcePasswordReset()) { %>
                                    SELECTED <%}%>>true
                            </option>
                            <option value="0" <% if (security != null && security.isForcePasswordReset() != null && !security.isForcePasswordReset()) { %>
                                    SELECTED <%}%>>false
                            </option>
                        </select>
                    </td>
                </tr>
                <%} %>

			<%--	MFA Setting   --%>
		<% if (MfaManager.isOscarMfaEnabled()) { %>
		<tr>
			<td style="text-align: right">
				<fmt:message key="admin.securityAddRecord.mfa.title"/>:
			</td>
			<td style="">
				<label>
					<input type="checkbox" name="enableMfa" value="1"
						   onchange="handleMfaChange(this);
								   <%if (MfaManager.isOscarLegacyPinEnabled()) { %>
								   updatePinComponentsAccess(this.checked);
								   <% } %>"
							<%= security.isUsingMfa() ? "checked" : "" %>/>
					<fmt:message key="admin.securityAddRecord.mfa.description"/>
				</label>
				<% if (security.isUsingMfa() && !security.isMfaRegistrationNeeded()) { %>
				<a id="resetMfaLink" onclick="handleResetMfa(<%=securityId%>)"
				   style="margin-left: 4px; font-size: small; color: blue; text-decoration:
				   underline; cursor: pointer;"><fmt:message key="admin.securityAddRecord.mfa.reset.link"/></a>
				<% } %>
			</td>
		</tr>
		<tr>
			<td></td>
			<td style="padding-left: 8px;">
			<span id="mfaNote"
				  style="font-size: x-small; color: darkslategray; vertical-align: top;
				  display: <%= (security.isUsingMfa() && security.isMfaRegistrationNeeded()) ? "inline" : "none" %>">
				<fmt:message key="admin.securityAddRecord.mfa.note"/></span>
			</td>
		</tr>
		<% } %>

                <tr>
                    <td colspan="2" align="center">
                        <input type="hidden" name="security_no" value="<%= security.getSecurityNo() %>">
                        <input type="submit" name="subbutton"
                               value='<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdatesecurity.btnSubmit"/>'>
                        <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdatesecurity.btnDelete"/>"
                               onclick="window.location='securitydelete.jsp?keyword=<%=security.getSecurityNo()%>'">
                    </td>
                </tr>
                <%
                    }
                %>
            </form>
        </table>

        <p></p>
    </center>
    <script type="text/javascript">
        Calendar.setup({
            inputField: "date_ExpireDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "date_ExpireDate_cal"
        });
    </script>
    </body>
</html>
