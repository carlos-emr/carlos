<%--

    Copyright (c) 2007 Peter Hutten-Czapski based on OSCAR general requirements
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
<!DOCTYPE html>
<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%@page import="java.util.*" %>
<%@page import="ca.openosp.openo.commn.model.ProviderPreference" %>
<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%@page import="ca.openosp.openo.waitinglist.bean.*" %>
<%@ page import="ca.openosp.openo.waitinglist.bean.WLWaitingListNameBeanHandler" %>
<%@ page import="ca.openosp.openo.waitinglist.bean.WLWaitingListNameBean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<html>
    <head>
        <script src="${pageContext.request.contextPath}/js/global.js"></script>
        <!-- Bootstrap 2.3.1 -->
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/bootstrap-responsive.css" rel="stylesheet">

        <title>Change Waiting List Name</title>

        <script>

            function resetFields(actionType) {

                if (actionType == "create") {
                    document.forms[0].wlChangedName.value = "";
                } else if (actionType == "change") {
                    document.forms[0].wlNewName.value = "";
                } else if (actionType == "remove") {
                    document.forms[0].wlChangedName.value = "";
                    document.forms[0].wlNewName.value = "";
                }
            }

        </script>
        <%
            ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);

            String groupNo = "";
            if (providerPreference.getMyGroupNo() != null) {
                groupNo = providerPreference.getMyGroupNo();
            }
            WLWaitingListNameBeanHandler wlNameHd = new WLWaitingListNameBeanHandler(groupNo, (String) session.getAttribute("user"));

            List allWaitingListName = wlNameHd.getWaitingListNameList();
        %>
    </head>
    <body>

    <%
        String message = "";
        if (request.getAttribute("message") != null) {
            message = (String) request.getAttribute("message");
        }
    %>


    <form action="${pageContext.request.contextPath}/oscarWaitingList/WLEditWaitingListNameAction.do?edit=Y.do" method="post">
        <input type="hidden" name="actionChosen" id="actionChosen"/>

        <h3>&nbsp;&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarwaitinglist.displayPatientWaitingList.waitinglist"/></h3>
        <%
            if (message != null && !message.equals("")) {
        %>
        <div class="alert"><fmt:setBundle basename="oscarResources"/><fmt:message key="<%=message%>"/></div>
        <%
            }
        %>
        <div class="container-fluid  form-horizontal span12" id="editWrapper">
            <div class="row">
                <div class="span6">
                    <fieldset>
                        <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="marc-hi.affinityDomains.manageExisting"/></legend>
                        <label class="control-label" for="selectedWL"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.config.MessengerAdmin.rename"/></label>
                        <div class="controls">
                            <select name="selectedWL" id="selectedWL">
                                <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.cbselectwaitinglist"/></option>
                                <%
                                    for (int i = 0; i < allWaitingListName.size(); i++) {
                                        WLWaitingListNameBean wLBean = (WLWaitingListNameBean) allWaitingListName.get(i);
                                        String id = wLBean.getId();
                                        String name = wLBean.getWaitingListName();
                                        String selected = id.compareTo((String) request.getAttribute("WLId") == null ? "0" : (String) request.getAttribute("WLId")) == 0 ? "SELECTED" : "";
                                %>
                                <option value="<%=id%>" <%=selected%>><%=name%>
                                </option>
                                <%}%>
                            </select>
                            <input type="text" class="input-medium" name="wlChangedName" placeholder="" value="">
                            <input type="submit" class="btn" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnSave"/>"
                                   onclick="resetFields('change');document.forms[0].actionChosen.value='change'">
                        </div> <!-- class="controls" -->
                    </fieldset>
                </div> <!-- class="span4" -->
                <div class="span6">
                    <fieldset>
                        <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formReportStatusNew"/></legend>
                        <label class="control-label" for="wlNewName"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.config.MessengerAdmin.newGroup"/></label>
                        <div class="controls">
                            <input type="text" class="input-medium" name="wlNewName" id="wlNewName" placeholder="" value="">
                            <input type="submit" class="btn" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnSave"/>"
                                   onclick="resetFields('create');document.forms[0].actionChosen.value='create'">
                        </div> <!-- class="controls" -->
                    </fieldset>
                </div> <!-- class="span4" -->
                <div class="span6">
                    <fieldset>
                        <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnDelete"/></legend>
                        <label class="control-label" for="selectedWL2"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnDeleteList"/></label>
                        <div class="controls">
                            <select name="selectedWL2" id="selectedWL2">
                                <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.cbselectwaitinglist"/></option>
                                <%
                                    for (int i = 0; i < allWaitingListName.size(); i++) {
                                        WLWaitingListNameBean wLBean = (WLWaitingListNameBean) allWaitingListName.get(i);
                                        String id = wLBean.getId();
                                        String name = wLBean.getWaitingListName();
                                        String selected = id.compareTo((String) request.getAttribute("WLId") == null ? "0" : (String) request.getAttribute("WLId")) == 0 ? "SELECTED" : "";
                                %>
                                <option value="<%=id%>" <%=selected%>><%=name%>
                                </option>
                                <%}%>
                            </select>
                            <input type="submit" class="btn btn-warning" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnDelete"/>"
                                   onclick="resetFields('remove');document.forms[0].actionChosen.value='remove'">
                        </div> <!-- class="controls" -->
                    </fieldset>
                </div> <!-- class="span4" -->
            </div> <!-- class="row" -->
        </div>
        <!-- end editWrapper -->
        <div>
            <input type="reset" class="btn btn-link" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>'
                   onClick="window.close();">
        </div>
    </form>

    </body>
</html>