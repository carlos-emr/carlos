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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@include file="/casemgmt/taglibs.jsp" %>
<%@ page
        import="io.github.carlos_emr.carlos.providers.data.ProviderData, java.util.ArrayList,java.util.Map, java.util.List, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page
        import="io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingFavoritesDao, io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao, io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.selectProvider.title"/></title>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.css"/>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/js/demographicProviderAutocomplete.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/carlosAutocomplete.js"></script>

</head>

<script type="text/javascript">
    function prepSubmit() {

        var fwdProviders = "";
        var fwdFavorites = "";

        var fwdProvidersEl = document.getElementById("fwdProviders");
        var favoritesEl = document.getElementById("favorites");

        if (fwdProvidersEl) {
            for (i = 0; i < fwdProvidersEl.options.length; i++) {
                if (fwdProviders != "") {
                    fwdProviders = fwdProviders + ",";
                }
                fwdProviders = fwdProviders + fwdProvidersEl.options[i].value;
            }
        }

        if (favoritesEl) {
            for (i = 0; i < favoritesEl.options.length; i++) {
                if (fwdFavorites != "") {
                    fwdFavorites = fwdFavorites + ",";
                }
                fwdFavorites = fwdFavorites + favoritesEl.options[i].value;
            }
        }

        var isListView = '<%= Encode.forJavaScript(StringUtils.noNull(request.getParameter("isListView"))) %>';
        var docId = '<%= Encode.forJavaScript(StringUtils.noNull(request.getParameter("docId"))) %>';
        var labDisplay = '<%= Encode.forJavaScript(StringUtils.noNull(request.getParameter("labDisplay"))) %>';
        var frm = "reassignForm";

        if (docId != "" && labDisplay == "") {
            frm += "_" + docId;
            var form = self.opener.document.forms[frm];
            if (form) {
                if (form.selectedProviders) form.selectedProviders.value = fwdProviders;
                if (form.favorites) form.favorites.value = fwdFavorites;
            }
            self.opener.forwardDocument(docId);
            self.close();
        } else if (isListView === 'true') {
            var forwardListEl = document.getElementById("forwardList");
            if (forwardListEl) {
                forwardLabs(forwardListEl.value, fwdProviders);
            }
        } else {
            frm += "_" + docId;
            var form = self.opener.document.forms[frm];
            if (form) {
                if (form.selectedProviders) form.selectedProviders.value = fwdProviders;
                if (form.favorites) form.favorites.value = fwdFavorites;
                form.submit();
            }
            self.close();
        }


    }

</script>
<style>
    .input-error {
        border: red thin solid;
    }
</style>
</head>
<body>
<input type="hidden" id="forwardList" value="<c:out value="${ param.forwardList }" />"/>
<form name="providerSelectForm" class="mx-1">
    <p style="font-weight:bold;">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.forward.msgInstruction1"/>
    </p>

    <div>
		<input id="autocompleteprov" class="form-control" type="text" style="width:100%;" placeholder="lastname, firstname">
        <div id="autocomplete_choicesprov" class="autocomplete"></div>
    </div>

	<div id="transferDialog" style="display: flex; justify-content: space-between; margin-top: 20px;">
		<div>
			<label for="fwdProviders">Forward List</label><br>
			<select id="fwdProviders" class="form-select" size="5" style="width:250px;height:100px;overflow:auto;" multiple="multiple" ondblclick="removeProvider(this);"></select>
        </div>

		<div style="margin: 30px;">
			<input type="button" class="btn btn-secondary btn-sm" value=">>" onclick="copyProvider('favorites','fwdProviders');"><br><br>
			<input type="button" class="btn btn-secondary btn-sm" value="<<" onclick="copyProvider('fwdProviders','favorites');">
        </div>

        <div>
			<label for="favorites">Favorites</label><br>
			<select id="favorites" class="form-select" size="5" style="width: 250px; height: 100px; overflow: auto;" multiple="multiple" ondblclick="removeProvider(this);">
                <%
                    ProviderLabRoutingFavoritesDao favDao = (ProviderLabRoutingFavoritesDao) SpringUtils.getBean(ProviderLabRoutingFavoritesDao.class);
                    String user = (String) request.getSession().getAttribute("user");
                    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);

                    List<ProviderLabRoutingFavorite> currentFavorites = favDao.findFavorites(user);
                    for (ProviderLabRoutingFavorite fav : currentFavorites) {
                        Provider prov = providerDao.getProvider(fav.getRoute_to_provider_no());
                %>
                <option id="<%=Encode.forHtmlAttribute(prov.getProviderNo())%>" value="<%=Encode.forHtmlAttribute(prov.getProviderNo())%>"><%=Encode.forHtml(prov.getFormattedName())%>
                </option>
                <%
                    }
                %>
            </select>
        </div>
    </div>
    <div>
        <p><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.forward.msgInstruction2"/></p>
        <!-- <input type="button" id="submitButton" value="Submit" onclick="prepSubmit();return false;"> -->
    </div>
</form>
<script type="text/javascript">
    initProviderAutocomplete("#autocompleteprov", "<%= request.getContextPath() %>",
        function (providerNo, firstName, lastName) {
            document.getElementById("autocompleteprov").value = "";
            var selectObj = document.getElementById("fwdProviders");
            if (selectObj) {
                var option = document.createElement("option");
                option.text = lastName + ", " + firstName;
                option.value = providerNo;
                option.id = providerNo;
                selectObj.add(option, null);
            }
        });

    var autocompleteprovEl = document.getElementById("autocompleteprov");
    if (autocompleteprovEl) autocompleteprovEl.focus();

    function removeProvider(selectObj) {
        selectObj.remove(selectObj.selectedIndex);
    }

    function copyProvider(to, from) {
        var fromEl = document.getElementById(from);
        var toEl = document.getElementById(to);
        if (!fromEl || !toEl) return;

        var fromOptions = fromEl.options;
        var toOptions = toEl.options;

        for (var idx = 0; idx < fromOptions.length; ++idx) {
            if (fromOptions[idx].selected && toOptions.namedItem(fromOptions[idx].id) == null) {

                fromOptions[idx].selected = false;

                var option = document.createElement("option");
                option.text = fromOptions[idx].text;
                option.value = fromOptions[idx].value;
                option.id = fromOptions[idx].id;
                try {
                    // for IE earlier than version 8
                    toEl.add(option, toEl.options[null]);
                } catch (e) {
                    toEl.add(option, null);
                }
            }
        }

    }
</script>
</body>
</html>
