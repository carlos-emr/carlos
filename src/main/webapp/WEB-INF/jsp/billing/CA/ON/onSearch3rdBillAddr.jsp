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
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.OnSearch3rdBillAddrViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.ViewOnSearch3rdBillAddr2Action" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<fmt:setBundle basename="oscarResources"/>
<%--
  Defensive model-resolver: ensures ${searchAddrModel} is set on the request
  even if this JSP is reached without going through ViewOnSearch3rdBillAddr2Action.
  Re-runs the _billing r privilege check for parity.
--%>
<%
    if (request.getAttribute("searchAddrModel") == null) {
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
                    "onSearch3rdBillAddr.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("onSearch3rdBillAddr.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("onSearch3rdBillAddr.jsp fallback: missing required sec object (_billing)");
        }
        request.setAttribute("searchAddrModel", new ViewOnSearch3rdBillAddr2Action().assembleViewModel(request));
    }
%>
<html>
    <head>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <title>Add/Edit 3rd Bill Address</title>
        <link rel="stylesheet" type="text/css" href="billingON.css"/>
        <script language="JavaScript">

            function setfocus() {
                this.focus();
                document.forms[0].keyword.focus();
                document.forms[0].keyword.select();
            }

            function check() {
                document.forms[0].submit.value = "Search";
                return true;
            }

            function setOpenerProperty(path, value) {
                var tokens = path.match(/[^.\[\]'"]+/g);
                if (!tokens || tokens.length === 0) return;
                var obj = opener;
                for (var i = 0; i < tokens.length - 1; i++) {
                    if (obj == null) return;
                    obj = obj[tokens[i]];
                }
                if (obj != null) obj[tokens[tokens.length - 1]] = value;
            }

            <c:if test="${searchAddrModel.paramProvided}">

            function typeInData1(data) {
                if (opener.updateElement != undefined) {
                    opener.updateElement('<carlos:encode value="${searchAddrModel.param}" context="javaScriptBlock"/>', data);
                } else {
                    setOpenerProperty('<carlos:encode value="${searchAddrModel.param}" context="javaScriptBlock"/>', data);
                }

                self.close();
            }

            <c:if test="${searchAddrModel.param2Provided}">

            function typeInData2(data1, data2) {
                setOpenerProperty('<carlos:encode value="${searchAddrModel.param}" context="javaScriptBlock"/>', data1);
                setOpenerProperty('<carlos:encode value="${searchAddrModel.param2}" context="javaScriptBlock"/>', data2);
                self.close();
            }

            </c:if>
            </c:if>


        </script>
    </head>
    <body bgcolor="white" bgproperties="fixed" onload="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">

    <form method="post" name="titlesearch" action="${pageContext.request.contextPath}/billing/CA/ON/ViewOnSearch3rdBillAddr" onSubmit="return check();">
        <table border="0" cellpadding="1" cellspacing="0" width="100%" class="myDarkGreen">
            <tr>
                <td class="searchTitle" colspan="4"><font color="white">Search
                    Address</font></td>
            </tr>
            <tr class="myYellow">
                <td class="blueText" width="10%" nowrap><input type="radio"
                                                               name="search_mode" value="search_name" checked> Name
                </td>
                <td class="blueText" nowrap><input type="radio"
                                                   name="search_mode" value="postcode"> Postcode
                </td>
                <td class="blueText" nowrap><input type="radio"
                                                   name="search_mode" value="telephone"> Tel.
                </td>
                <td valign="middle" rowspan="2" align="left"><input type="text"
                                                                    name="keyword" value="" size="17" maxlength="100">
                    <input
                            type="hidden" name="orderby" value="company_name"> <input
                            type="hidden" name="limit1" value="0"> <input type="hidden"
                                                                          name="limit2" value="20"> <input type="hidden"
                                                                                                           name="submit"
                                                                                                           value='Search'>
                    <input type="submit" value='Search'>
                </td>
            </tr>
        </table>
        <input type='hidden' name='param'
               value="<carlos:encode value='${searchAddrModel.param}' context='htmlAttribute'/>">
        <input type='hidden' name='param2'
               value="<carlos:encode value='${searchAddrModel.param2}' context='htmlAttribute'/>">
        <table width="95%" border="0">
            <tr>
                <td align="left">Results based on keyword(s): <carlos:encode value='${searchAddrModel.keyword}' context='html'/>
                </td>
            </tr>
        </table>
    </form>
    <center>
        <table width="100%" border="0" cellpadding="0" cellspacing="2" class="myYellow">
            <tr class="title">
                <th width="20%">Attention</th>
                <th width="20%">Company name</th>
                <th width="25%">Address</th>
                <th width="10%">City</th>
                <th width="10%">Postcode</th>
                <th>Phone</th>
                <!--  >th width="20%">Fax</b></th-->
            </tr>

            <c:forEach var="addr" items="${searchAddrModel.addresses}" varStatus="ctr">
                <c:set var="bgColor" value="${ctr.index % 2 == 0 ? '#EEEEFF' : 'ivory'}"/>
                <tr align="center" bgcolor="${bgColor}"
                    onMouseOver="this.style.cursor='pointer';this.style.backgroundColor='pink';"
                    onMouseout="this.style.backgroundColor='<carlos:encode value="${bgColor}" context="javaScriptAttribute"/>';"
                    onClick="<carlos:encode value='${addr.onClickHandler}' context='javaScriptAttribute'/>">
                    <td><carlos:encode value='${addr.attention}' context='html'/>
                    </td>
                    <td><carlos:encode value='${addr.companyNameDisplay}' context='html'/>
                    </td>
                    <td><carlos:encode value='${addr.addressDisplay}' context='html'/>
                    </td>
                    <td><carlos:encode value='${addr.city}' context='html'/>
                    </td>
                    <td><carlos:encode value='${addr.postcode}' context='html'/>
                    </td>
                    <td><carlos:encode value='${addr.telephone}' context='html'/>
                    </td>
                </tr>
            </c:forEach>
        </table>

        <c:if test="${searchAddrModel.showNoResults}"><fmt:message key="demographic.search.noResultsWereFound"/></c:if>
        <script language="JavaScript">
            <!--
            function last() {
                document.nextform.action = "${pageContext.request.contextPath}/billing/CA/ON/ViewOnSearch3rdBillAddr?param=<carlos:encode value='${searchAddrModel.param}' context='uriComponent'/>&param2=<carlos:encode value='${searchAddrModel.param2}' context='uriComponent'/>&keyword=<carlos:encode value='${searchAddrModel.keyword}' context='uriComponent'/>&search_mode=<carlos:encode value='${searchAddrModel.searchMode}' context='uriComponent'/>&orderby=<carlos:encode value='${searchAddrModel.orderBy}' context='uriComponent'/>&limit1=${searchAddrModel.prevPageLimit1}&limit2=<carlos:encode value='${searchAddrModel.limit2}' context='uriComponent'/>";
                document.nextform.submit();
            }

            function next() {
                document.nextform.action = "${pageContext.request.contextPath}/billing/CA/ON/ViewOnSearch3rdBillAddr?param=<carlos:encode value='${searchAddrModel.param}' context='uriComponent'/>&param2=<carlos:encode value='${searchAddrModel.param2}' context='uriComponent'/>&keyword=<carlos:encode value='${searchAddrModel.keyword}' context='uriComponent'/>&search_mode=<carlos:encode value='${searchAddrModel.searchMode}' context='uriComponent'/>&orderby=<carlos:encode value='${searchAddrModel.orderBy}' context='uriComponent'/>&limit1=${searchAddrModel.nextPageLimit1}&limit2=<carlos:encode value='${searchAddrModel.limit2}' context='uriComponent'/>";
                document.nextform.submit();
            }

            //-->
        </SCRIPT>

        <form method="post" name="nextform" action="${pageContext.request.contextPath}/billing/CA/ON/ViewOnSearch3rdBillAddr">
            <c:if test="${searchAddrModel.showPrevPage}">
                <input type="submit" class="mbttn" name="submit"
                       value="<fmt:message key="demographic.demographicsearch2apptresults.btnPrevPage"/>"
                       onClick="last()">
            </c:if>
            <c:if test="${searchAddrModel.showNextPage}">
                <input type="submit" class="mbttn" name="submit"
                       value="<fmt:message key="demographic.demographicsearch2apptresults.btnNextPage"/>"
                       onClick="next()">
            </c:if>
        </form>
        <br>
        <a href="${pageContext.request.contextPath}/billing/CA/ON/OnAddEdit3rdAddr">Add/Edit Address</a></center>
    </body>
</html>
