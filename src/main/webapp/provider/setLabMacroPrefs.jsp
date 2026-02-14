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
<%--
    setLabMacroPrefs.jsp
    Author: Peter Hutten-Czapski
    Purpose: Manages lab macro preferences for providers in CARLOS EMR.
    Features: Bootstrap UI for creating/editing/deleting lab macros stored as JSON.
    Parameters: method (POST) - "saveLabMacroPrefs" triggers save.
    `@since` 2026-02-13
--%>

<%@page import="java.util.*" %>
<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider"%>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao"%>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty"%>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils"%>

<%@page import="com.fasterxml.jackson.databind.JsonNode"%>
<%@page import="com.fasterxml.jackson.databind.ObjectMapper"%>

<%@page import="org.owasp.encoder.Encode"%>

<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<fmt:setBundle basename="oscarResources"/>

<%

String curProviderNo = (String) session.getAttribute("user");

if (curProviderNo == null || curProviderNo.isEmpty()) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}
ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

List<Provider> providerList = providerDao.getActiveProviders();

ObjectMapper mapper = SpringUtils.getBean(ObjectMapper.class);

%>
<!DOCTYPE HTML>
<html>
<head>

<title><fmt:message key="provider.labMacroPrefs.msgPrefs"/></title>

<!-- Bootstrap -->
<link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/bootstrap/5.0.2/css/bootstrap.css">

<script>
function assembleJSON() {
    let macros = [];
    const elements = document.querySelectorAll('[id^="macro_"]');

    elements.forEach(el => {
        // Check if element is visible
        if (window.getComputedStyle(el).display !== 'none') {
            let suffix = el.id.split('_')[1];
            let nameField = document.getElementById('name_' + suffix);

            // Check if name field exists and has length > 0
            if (nameField && nameField.value.length > 0) {
                let commentField = document.getElementById('comment_' + suffix);
                let ticklerTo = document.getElementById('ticklerTo_' + suffix);
                let messageField = document.getElementById('message_' + suffix);
                let quantityField = document.getElementById('quantity_' + suffix);
                let timeUnitsField = document.getElementById('timeUnits_' + suffix);

                let macroObj = {
                    name: nameField.value,
                    acknowledge: {
                        comment: commentField ? commentField.value : ''
                    },
                    closeOnSuccess: true
                };

                // Add tickler if it exists
                if (ticklerTo && ticklerTo.value.length > 0) {
                    macroObj.tickler = {
                        taskAssignedTo: ticklerTo.value,
                        message: messageField ? messageField.value : ''
                    };

                    if (quantityField && parseInt(quantityField.value) > 0) {
                        macroObj.tickler.quantity = quantityField.value;
                        macroObj.tickler.timeUnits = timeUnitsField ? timeUnitsField.value : '';
                    }
                }
                macros.push(macroObj);
            }
        }
    });

    let jsonStr = macros.length > 0 ? JSON.stringify(macros) : '';
    let jsonOutput = document.getElementById('macroJSON');
    if (jsonOutput) {
        jsonOutput.value = jsonStr;
    }
}

function toggleMe(el){
    el.style.display = (el.style.display === 'none') ? 'block' : 'none';
}

</script>
<style>
    .MainTableTopRow {
        background-color: gainsboro;
    }
</style>

</head>
<body>

<table style="width:100%" id="scrollNumber1">
	<tr class="MainTableTopRow">
		<td class="MainTableTopRowLeftColumn"><h4>&nbsp;<fmt:message key="provider.labMacroPrefs.msgPrefs" /></h4></td>
		<td style="text-align:center;" class="MainTableTopRowRightColumn"><fmt:message key="provider.labMacroPrefs.title" /></td>
	</tr>
</table>
			<!-- form starts here -->

<form name="labMacroPrefsForm" method="post" action="${pageContext.request.contextPath}/setProviderStaleDate.do">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
<input type="hidden" name="method" value="saveLabMacroPrefs">
<div class="container"><br>

<%
String status = (String) request.getAttribute("status");
if ("saveLabMacroPrefs".equals(status)) {
%>
    <div class="alert alert-success"><fmt:message key="provider.labMacroPrefs.msgSuccess" /></div>
<% } %>
<%
    UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
    UserProperty up = upDao.getProp(curProviderNo,UserProperty.LAB_MACRO_JSON);
    if(up != null && !StringUtils.isEmpty(up.getValue())) {

    %>
<%
    try {
//[{"name":"APT","acknowledge":{"comment":"APT"},"tickler":{"taskAssignedTo":"101","message":"APT"},"closeOnSuccess":true},{"name":"TBS","acknowledge":{"comment":"TBS"},"tickler":{"taskAssignedTo":"101","message":"TBS"},"closeOnSuccess":true}]
        JsonNode macros = mapper.readTree(up.getValue());
            if(macros != null && macros.isArray()) {
                int x = 0;
                for(JsonNode macro : macros) {
                String name = macro.path("name").asText("");
                String comment = "";
                String ticklerTo = "";
                String message = "";
                String quantity = "0";
                String timeUnits = "1";

                JsonNode acknowledge = macro.path("acknowledge");
                if(!acknowledge.isMissingNode()){
                    comment = acknowledge.path("comment").asText("");
                }

                // Tickler block
                JsonNode tickler = macro.path("tickler");
                if(!tickler.isMissingNode()){
                    ticklerTo = tickler.path("taskAssignedTo").asText("");
                    message = tickler.path("message").asText("");
                    if(tickler.has("quantity") && tickler.has("timeUnits")){
                        quantity = tickler.path("quantity").asText("");
                        timeUnits = tickler.path("timeUnits").asText("");
                    }
                }
                boolean closeOnSuccess = macro.path("closeOnSuccess").asBoolean(false);

%>

 <div class="form-group row" id="macro_<%=x%>">
    <div class="col-sm-2">
     <label for="name_<%=x%>"><fmt:message key="global.macro" /></label><br><input type="text" id="name_<%=x%>" class="" placeholder="<fmt:message key="name" />" style="width:90px;" value="<%=Encode.forHtmlAttribute(name)%>">
    </div>
    <div class="col-sm-3">
     <label for="comment_<%=x%>"><fmt:message key="caseload.msgLab" />&nbsp;<fmt:message key="oscarMDS.segmentDisplay.btnComment" /></label><br><input type="text" id="comment_<%=x%>" class="" style="width:95%;" value="<%=Encode.forHtmlAttribute(comment)%>" placeholder="<fmt:message key="oscarMDS.segmentDisplay.btnComment" />">
    </div>
    <div class="col-sm-2">
      <%
        String val1 = ticklerTo;
        if(val1 == null) val1 = "";
        %>
		    <label for="ticklerTo_<%=x%>"><fmt:message key="tickler.ticklerMain.msgAssignedTo" /></label><br><select id="ticklerTo_<%=x%>" name="ticklerTo_<%=x%>" class="form-control input-sm" style="width:95%;">
            <option value="" <%=(val1.equals("")?" selected=\"selected\"":"") %> >-</option>
			<%for(Provider p: providerList) {%>
				<option value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"<%=(val1.equals(p.getProviderNo())?" selected=\"selected\"":"") %>><%=Encode.forHtml(p.getFullName())%></option>
						<%}%>
			</select>
    </div>
    <div class="col-sm-2 ">
     <label for="message_<%=x%>"><fmt:message key="global.tickler" /></label><br><input type="text" id="message_<%=x%>" class="" style="width:95%;" placeholder="<fmt:message key="tickler.ticklerMain.msgMessage" />" value="<%=Encode.forHtmlAttribute(message)%>">
    </div>
    <div class="col-sm-3 ">
     <label for="quantity_<%=x%>"><fmt:message key="tickler.ticklerMain.msgDate" /></label><br><input type="number" id="quantity_<%=x%>" class="" style="width:50px;" value="<%=Encode.forHtmlAttribute(quantity)%>"><select id="timeUnits_<%=x%>"  style="width:80px;">
            <option value="1" <%=(timeUnits.equals("1")?" selected=\"selected\"":"") %>><fmt:message key="global.days" /></option>
            <option value="7" <%=(timeUnits.equals("7")?" selected=\"selected\"":"") %>><fmt:message key="global.weeks" /></option>
            <option value="30" <%=(timeUnits.equals("30")?" selected=\"selected\"":"") %>><fmt:message key="global.months" /></option>
            <option value="365" <%=(timeUnits.equals("365")?" selected=\"selected\"":"") %>><fmt:message key="global.years" /></option>
        </select>
    </div>
    <div class="col-sm-2">
     &nbsp;<input type="button" id="delete_<%=x%>" class="btn btn-link" value="<fmt:message key="global.btnDelete" />" onclick="document.getElementById('macro_<%=x%>').style.display = 'none';">
    </div>
 </div>

        <%      x++;
                }
            }
        }catch(java.io.IOException e ) {
            MiscUtils.getLogger().error("Invalid JSON for lab macros",e);
%>
  <div class="alert alert-danger"><fmt:message key="error.msgException" /></div>
<%
		}
}
%>

 <div class="form-group row" id="macro_new">
    <div class="col-sm-2">
     <label for="name_new"><fmt:message key="global.macro" /></label><br><input type="text" id="name_new" class="" style="width:90px;" placeholder="<fmt:message key="name" />" value="">
    </div>
    <div class="col-sm-3">
     <label for="comment_new"><fmt:message key="caseload.msgLab" />&nbsp;<fmt:message key="oscarMDS.segmentDisplay.btnComment" /></label><br><input type="text" id="comment_new" class="" style="width:95%;" value="" placeholder="<fmt:message key="oscarMDS.segmentDisplay.btnComment" />">
    </div>
    <div class="col-sm-2">
					<label for="ticklerTo_new"><fmt:message key="tickler.ticklerMain.msgAssignedTo" /></label><select id="ticklerTo_new" name="ticklerTo_new" class="form-control input-sm" style="width:95%;">
					<option value="" selected="selected">-</option>
					<%for(Provider p: providerList) {%>
						<option value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"><%=Encode.forHtml(p.getFullName())%></option>
						<%}%>
					</select>
    </div>
    <div class="col-sm-2">
     <label for="message_new"><fmt:message key="global.tickler" /></label><br><input type="text" id="message_new" class="" placeholder="<fmt:message key="tickler.ticklerMain.msgMessage" />" style="width:95%;" value="">
    </div>
    <div class="col-sm-3 ">
     <label for="timeUnits_new"><fmt:message key="tickler.ticklerMain.msgDate" /></label><br><input type="number" id="quantity_new" class="" style="width:50px;" value="0"><select id="timeUnits_new">
            <option value="1"><fmt:message key="global.days" /></option>
            <option value="7"><fmt:message key="global.weeks" /></option>
            <option value="30"><fmt:message key="global.months" /></option>
            <option value="365"><fmt:message key="global.years" /></option>
        </select>
    </div>
    <div class="col-sm-2">
        &nbsp;<input type="button" id="add_new" class="btn btn-link" value="Add" style="visibility:hidden;">
    </div>
</div>

  <div class="form-group row">
<br>
    <div class="col-sm-5 col-sm-offset-1">
        <input type="submit" class="btn btn-primary" value="<fmt:message key="global.btnSave" />" onclick="assembleJSON();"/>
<input type="button" class="btn" value="<fmt:message key="global.btnClose" />" onclick="window.close();"/>
<a href="javascript:void(0);" onclick="assembleJSON(); toggleMe(document.getElementById('raw'));" style="color:white">Show macro JSON</a>
    </div>
    <div class="col-sm-5 ">

    </div>
  </div>
<div>
</div>
  <div class="form-group row" style="display:none;" id="raw">
  <textarea name="labMacroJSON.value" id="macroJSON" style="width:80%;height:80%" rows="25"><%=Encode.forHtml((up != null && up.getValue() != null)?up.getValue():"")%></textarea>
  <input type="submit" class="btn" value="<fmt:message key="global.btnSave" />" />
  </div>
</div>
</form>
</body>
</html>
