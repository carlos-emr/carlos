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
<%--
    oscarMDS/SelectProvider.jsp - the lab Forward provider picker (route oscarMDS/ViewSelectProvider).

    Purpose: choose the providers a lab or document is forwarded to, from a provider search
    (the Forward List) and the sender's forwarding favourites (the Favorites list, edited with
    the >> and << buttons and a double click).

    Two ways in:
      - a popup (window.open) from the CML, MDS and PathNet lab pages and the patient lab list;
        its Forward button (#submitButton) posts the choice through the opener's reassignForm;
      - the Inbox's Forward dialog, which loads this page by AJAX and has its own Forward button.

    Parameters: forwardList (the Inbox's selection, echoed into #forwardList). The docId parameter
    the pre-#4558 page read is no longer used: nothing opens the picker with one, and the document
    viewers forward through the Inbox's dialog.

    @since 2026-08-04
--%>
<%@include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ page
        import="io.github.carlos_emr.carlos.providers.data.ProviderData, java.util.ArrayList,java.util.Map, java.util.List, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page
        import="io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingFavoritesDao, io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao, io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.utility.RequestNegotiation" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<fmt:message key="oscarMDS.selectProvider.msgOpenerGone" var="msgOpenerGone"/>
<fmt:message key="oscarMDS.selectProvider.msgChooseProvider" var="msgChooseProvider"/>
<fmt:message key="oscarMDS.index.msgSelectOneLab" var="msgSelectOneLab"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title><fmt:message key="oscarMDS.selectProvider.title"/></title>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.css"/>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/js/demographicProviderAutocomplete.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/carlosAutocomplete.js"></script>

</head>

<%--
    Forward from a lab page or the patient lab list (issue #4558).

    Those pages open this picker with window.open and keep the lab selection in a form named
    reassignForm with a real action: one hidden flaggedLabs per lab page, or the list's flaggedLabs
    checkboxes. prepSubmit() posts that selection, the Forward List and the Favorites list to the
    form's own action, from the opener window, in the one contract ReportReassign2Action reads (the
    one the Inbox already sends):
      flaggedLabs       {"files":["<id>:<TYPE>", ...]}
      selectedProviders {"providers":[...]}
      selectedFavorites {"favorites":[...]}   the list as shown, so its edits are kept
    A lab's TYPE is its control's data-lab-type, else the form's labType field. The opener then
    follows the action's redirect back to its own page.

    The form is built in the opener's document so that the opener's CSRFGuard client, which hooks
    form.submit() there, adds the token, and so the result loads in the opener.

    The Inbox does not call prepSubmit(): it loads this page into its own dialog
    (oscarMDSIndex.js ForwardSelectedRows), whose Forward button posts by AJAX, and this page
    renders no Forward button for that request. The helpers stay local to prepSubmit() because the
    dialog re-runs these inline scripts in the Inbox window's global scope.
--%>
<script type="text/javascript">
    function prepSubmit() {
        var msgOpenerGone = '<carlos:encode value='${msgOpenerGone}' context="javaScriptBlock"/>';
        var msgChooseProvider = '<carlos:encode value='${msgChooseProvider}' context="javaScriptBlock"/>';
        var msgSelectOneLab = '<carlos:encode value='${msgSelectOneLab}' context="javaScriptBlock"/>';

        function optionValues(id) {
            var el = document.getElementById(id);
            var values = [];
            if (el) {
                for (var i = 0; i < el.options.length; i++) {
                    values.push(el.options[i].value);
                }
            }
            return values;
        }

        function openerForm() {
            try {
                var opener = window.opener;
                if (!opener || opener.closed) {
                    return null;
                }
                var form = opener.document.forms["reassignForm"];
                // A reassignForm without an action has nowhere to post the selection.
                return form && form.getAttribute("action") ? form : null;
            } catch (e) {
                // A cross-origin or navigated-away opener cannot be read.
                return null;
            }
        }

        function selectedLabs(form) {
            var defaultType = form.elements["labType"] ? form.elements["labType"].value : "";
            var files = [];
            for (var i = 0; i < form.elements.length; i++) {
                var el = form.elements[i];
                if (el.name !== "flaggedLabs" || (el.type === "checkbox" && !el.checked) || el.value === "") {
                    continue;
                }
                var type = el.getAttribute("data-lab-type") || defaultType;
                files.push(el.value + ":" + type);
            }
            return files;
        }

        var fwdProvidersEl = document.getElementById("fwdProviders");
        var providers = optionValues("fwdProviders");
        var favorites = optionValues("favorites");
        var source = openerForm();
        if (!source) {
            alert(msgOpenerGone);
            return false;
        }
        var files = selectedLabs(source);
        if (files.length === 0) {
            alert(msgSelectOneLab);
            return false;
        }
        if (providers.length === 0) {
            if (fwdProvidersEl) fwdProvidersEl.classList.add("input-error");
            alert(msgChooseProvider);
            var search = document.getElementById("autocompleteprov");
            if (search) search.focus();
            return false;
        }

        var doc = source.ownerDocument;
        var post = doc.createElement("form");
        post.setAttribute("method", "post");
        post.setAttribute("action", source.getAttribute("action"));
        post.style.display = "none";
        var fields = {
            flaggedLabs: JSON.stringify({files: files}),
            selectedProviders: JSON.stringify({providers: providers}),
            selectedFavorites: JSON.stringify({favorites: favorites})
        };
        // The filters the page posts back to itself (ReportReassign2Action builds the return URL from them).
        ["providerNo", "searchProviderNo", "status", "lname", "fname", "hnum"].forEach(function (name) {
            var field = source.elements[name];
            if (field && typeof field.value === "string" && field.value !== "") {
                fields[name] = field.value;
            }
        });
        Object.keys(fields).forEach(function (name) {
            var input = doc.createElement("input");
            input.type = "hidden";
            input.name = name;
            input.value = fields[name];
            post.appendChild(input);
        });
        doc.body.appendChild(post);
        post.submit();
        window.close();
        return false;
    }

</script>
<style>
    .input-error {
        border: red thin solid;
    }
</style>
</head>
<body>
<input type="hidden" id="forwardList" value="${carlos:forHtmlAttribute(param.forwardList)}"/>
<form name="providerSelectForm" class="mx-1">
    <p style="font-weight:bold;">
        <fmt:message key="oscarMDS.forward.msgInstruction1"/>
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
                <option id="<carlos:encode value='<%= prov.getProviderNo() %>' context="htmlAttribute"/>" value="<carlos:encode value='<%= prov.getProviderNo() %>' context="htmlAttribute"/>"><carlos:encode value='<%= prov.getFormattedName() %>' context="html"/>
                </option>
                <%
                    }
                %>
            </select>
        </div>
    </div>
    <div>
        <p><fmt:message key="oscarMDS.forward.msgInstruction2"/></p>
        <%-- Popup only: the Inbox loads this page by AJAX into a dialog that has its own Forward button. --%>
        <% if (!RequestNegotiation.isAjax(request)) { %>
        <button type="button" id="submitButton" class="btn btn-primary btn-sm" onclick="prepSubmit(); return false;"><fmt:message key="oscarMDS.index.btnForward"/></button>
        <% } %>
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
