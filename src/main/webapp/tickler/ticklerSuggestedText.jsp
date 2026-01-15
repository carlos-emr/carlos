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

<%@page import="ca.openosp.openo.commn.model.TicklerTextSuggest, ca.openosp.openo.commn.dao.TicklerTextSuggestDao" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%!
    TicklerTextSuggestDao ticklerTextSuggestDao = SpringUtils.getBean(TicklerTextSuggestDao.class);
%>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerEdit.title"/></title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

    <script type="application/javascript">
        function setEmpty(selectbox) {
            var emptyTxt = "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.AttachDocPopup.empty"/>";
            var emptyVal = "0";
            var op = document.createElement("option");
            try {
                selectbox.add(op);
            } catch (e) {
                selectbox.add(op, null);
            }
            selectbox.options[0].text = emptyTxt;
            selectbox.options[0].value = emptyVal;
        }

        function swap(srcName, dstName) {
            var src = document.getElementsByName(srcName)[0];
            var dst = document.getElementsByName(dstName)[0];
            var opt;

            //if nothing or dummy is being transfered do nothing
            if (src.selectedIndex == -1 || src.options[0].value == "0")
                return;

            //if dst has dummy clobber it with new options
            if (dst.options[0].value == "0")
                dst.remove(0);

            for (var idx = src.options.length - 1; idx >= 0; --idx) {

                if (src.options[idx].selected) {
                    opt = document.createElement("option");
                    try {  //ie method of adding option
                        dst.add(opt);
                        dst.options[dst.options.length - 1].text = src.options[idx].text;
                        dst.options[dst.options.length - 1].value = src.options[idx].value;
                        dst.options[dst.options.length - 1].className = src.options[idx].className;
                        src.remove(idx);
                    } catch (e) { //firefox method of adding option
                        dst.add(src.options[idx], null);
                        dst.options[dst.options.length - 1].selected = false;
                    }

                }

            } //end for

            if (src.options.length == 0)
                setEmpty(src);

        }

        function addToList(listName, srcId) {
            var dst = document.getElementsByName(listName)[0];
            var src = document.getElementById(srcId);
            //if dst has dummy clobber it with new options
            if (dst.options[0].value == "0")
                dst.remove(0);
            var opt = document.createElement("option");
            try {  //ie method of adding option
                dst.add(opt);
                dst.options[dst.options.length - 1].text = src.value;
                dst.options[dst.options.length - 1].value = src.value;
                dst.options[dst.options.length - 1].className = dst.options[dst.options.length - 2].className;
            } catch (e) { //firefox method of adding option
                opt.text = src.value;
                opt.value = src.value;
                opt.className = dst.options[dst.options.length - 2].className;
                dst.add(opt, null);
                dst.options[dst.options.length - 1].selected = false;
            }
            src.value = "";
        }

        function doSelect(listName) {
            var lst = document.getElementsByName(listName)[0];
            for (var idx = lst.options.length - 1; idx >= 0; --idx) {
                lst.options[idx].selected = true;
            }
        }
    </script>
    <link href="<%=request.getContextPath() %>/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
          type="text/css">
    <style>
        table {
            border-collapse: collapse;
            width: 100%;
        }

        .text-selection {
            width: 300px;
        }

    </style>
</head>
<body style="font-family:arial, sans-serif;">
<div class="container">
    <h3>Tickler <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.textSuggestTitle"/></h3>
    <form action="${pageContext.request.contextPath}/tickler/EditTicklerTextSuggest.do" method="post">
        <input type="hidden" name="method" value="updateTextSuggest">
        <table style="display: flex;justify-content: space-evenly;align-items: stretch;">

            <tr>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.activeText"/></th>
                <th></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.inactiveText"/></th>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <select class="form-control text-selection" name="activeText" multiple="true"
                                 size="10">
                        <% java.util.List<TicklerTextSuggest> activeTexts = ticklerTextSuggestDao.getActiveTicklerTextSuggests();
                            if (activeTexts.isEmpty()) {
                        %>
                        <option value=""></option>
                        <% } else {

                            for (TicklerTextSuggest tTextSuggestActive : activeTexts) {
                        %>
                        <option
                                value="<%=tTextSuggestActive.getId().toString()%>"><%=tTextSuggestActive.getSuggestedText()%>
                        </option>
                        <% }
                        }
                        %>
                    </select>
                </td>
                <td>
                    <input type="button" class="btn" name="movetoInactive" value=">>"
                           onclick="swap('activeText','inactiveText')"/>
                    <br/>
                    <input type="button" class="btn" name="movetoActive" value="<<"
                           onclick="swap('inactiveText','activeText')"/>
                </td>
                <td style="vertical-align: top">
                    <select class="form-control text-selection" name="inactiveText" multiple="true"
                                 size="10">
                        <%
                            java.util.List<TicklerTextSuggest> inactiveTexts = ticklerTextSuggestDao.getInactiveTicklerTextSuggests();
                            if (inactiveTexts.isEmpty()) {
                        %>
                        <option value=""></option>
                        <%
                        } else {
                            for (TicklerTextSuggest tTextSuggestInactive : inactiveTexts) {
                        %>
                        <option
                                value="<%=tTextSuggestInactive.getId().toString()%>"><%=tTextSuggestInactive.getSuggestedText()%>
                        </option>
                        <% }
                        }
                        %>
                    </select>
                </td>
            </tr>
            <tr>
                <td colspan="3">
                    <label for="newTextSuggest"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.enterText"/>:</label>
                    <div class="input-group">
                        <input id="newTextSuggest" class="form-control" name="newTextSuggest" type="text"
                               maxlength="100"/>
                        <div class="input-group-btn">
                            <input type="button" class="btn btn-default" name="addNewTextSuggest"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.addText"/>"
                                   onclick="addToList('activeText','newTextSuggest')"/>
                        </div>
                    </div>
                </td>
            </tr>
            <tr>
                <td colspan="3">
                    <div class="form-group pull-right">
                        <input type="button" class="btn btn-primary" name="saveTextChanges"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.save"/>"
                               onclick="doSelect('activeText');doSelect('inactiveText');document.tsTicklerForm.submit();"/>
                        <input type="button" class="btn btn-danger" name="cancelTextChanges"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerTextSuggest.cancel"/>"
                               onclick="window.close()"/>
                    </div>
                </td>
            </tr>
        </table>
    </form>
</div>
</body>
</html>
