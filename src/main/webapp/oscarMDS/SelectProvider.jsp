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
<%@include file="/casemgmt/taglibs.jsp" %>
<%@ page
        import="ca.openosp.openo.providers.data.ProviderData, java.util.ArrayList,java.util.Map, java.util.List, ca.openosp.openo.utility.SpringUtils" %>
<%@ page
        import="ca.openosp.openo.commn.dao.ProviderLabRoutingFavoritesDao, ca.openosp.openo.commn.model.ProviderLabRoutingFavorite" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao, ca.openosp.openo.commn.model.Provider" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.selectProvider.title"/></title>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/yahoo-dom-event.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/connection-min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/animation-min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/datasource-min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/yui/js/autocomplete-min.js"></script>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/js/demographicProviderAutocomplete.js"></script>

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

        var isListView = <%=request.getParameter("isListView")%>;
        var docId = '<%=request.getParameter("docId")%>';
        var labDisplay = '<%=request.getParameter("labDisplay")%>';
        var frm = "reassignForm";

        if (docId != "null" && labDisplay == "null") {
            frm += "_" + docId;
            var form = self.opener.document.forms[frm];
            if (form) {
                if (form.selectedProviders) form.selectedProviders.value = fwdProviders;
                if (form.favorites) form.favorites.value = fwdFavorites;
            }
            self.opener.forwardDocument(docId);
            self.close();
        } else if (isListView != "null" && isListView == true) {
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
                <option id="<%=prov.getProviderNo()%>" value="<%=prov.getProviderNo()%>"><%=prov.getFormattedName()%>
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
    YAHOO.example.BasicRemote = function () {
        var url = "<%= request.getContextPath() %>/provider/SearchProvider.do";
        var oDS = new YAHOO.util.XHRDataSource(url, {connMethodPost: true, connXhrMode: 'ignoreStaleResponses'});
        oDS.responseType = YAHOO.util.XHRDataSource.TYPE_JSON;// Set the responseType
        // Define the schema of the delimited resultsTEST, PATIENT(1985-06-15)
        oDS.responseSchema = {
            resultsList: "results",
            fields: ["providerNo", "firstName", "lastName"]
        };
        // Enable caching
        oDS.maxCacheEntries = 0;

        // Instantiate the AutoComplete
        var oAC = new YAHOO.widget.AutoComplete("autocompleteprov", "autocomplete_choicesprov", oDS);
        oAC.queryMatchSubset = true;
        oAC.minQueryLength = 3;
        oAC.maxResultsDisplayed = 25;
        oAC.formatResult = resultFormatter3;
        //oAC.typeAhead = true;
        oAC.queryMatchContains = true;

        oAC.itemSelectEvent.subscribe(function (type, args) {
            var autocompleteEl = document.getElementById("autocompleteprov");
            if (autocompleteEl) autocompleteEl.value = "";
            var name = args[2][2] + ", " + args[2][1];
            var id = args[2][0];

            var selectObj = document.getElementById("fwdProviders");
            if (selectObj) {
                var option = document.createElement("option");
                option.text = name;
                option.value = id;
                option.id = id;

                try {
                    // for IE earlier than version 8
                    selectObj.add(option, selectObj.options[null]);
                } catch (e) {
                    selectObj.add(option, null);
                }
            }

        });


        return {
            oDS: oDS,
            oAC: oAC
        };
    }();

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
