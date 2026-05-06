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
  Purpose: Add/Edit favourite service code macros for the Ontario billing workflow.
  Expected request model data: favouriteModel (BillingOnFavouriteViewModel).
  Security and mutation routing are handled by ViewBillingOnFavourite2Action; this
  JSP is pure presentation.
  Keep request setup in the paired action and use CARLOS encoding helpers for all
  dynamic output rendered by the page.
  @since 2026-04-13
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.util.ResourceBundle" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    // Resolve i18n strings needed inside <script> blocks.
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    pageContext.setAttribute("ctx",             request.getContextPath());
    pageContext.setAttribute("msgConfirmDelete", bundle.getString("billing.billingOnFavourite.msgConfirmDelete"));
    pageContext.setAttribute("msgConfirmSave",   bundle.getString("billing.billingOnFavourite.msgConfirmSave"));
    pageContext.setAttribute("alertInvalidCode", bundle.getString("billing.billingOnFavourite.alertInvalidServiceCode"));
    pageContext.setAttribute("alertInvalidNum",  bundle.getString("billing.billingOnFavourite.alertInvalidNumber"));
    pageContext.setAttribute("alertInvalidDx",   bundle.getString("billing.billingOnFavourite.alertInvalidDx"));
%>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="billing.billingOnFavourite.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <%-- jQuery UI JS required for .autocomplete() — CSS is already in global-head.jspf --%>
    <script src="${ctx}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <style>
        .billing-ac-item { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 3px 6px; cursor: pointer; }
        .billing-ac-item strong { font-weight: 600; }
        .ui-autocomplete { max-height: 280px; overflow-y: auto; overflow-x: hidden; z-index: 9999 !important; }
        .ui-menu-item > div:hover, .ui-menu-item:hover { background-color: #e8f0fe; }
        /* Fixed-width inputs sized to their expected content */
        .fav-code { width: calc(6ch + 1rem); }
        .fav-unit { width: calc(3ch + 1rem); }
        .fav-at   { width: calc(4ch + 1rem); }
        .fav-dx   { width: calc(4ch + 1rem); }
    </style>
</head>
<body class="p-2">

<h5 class="mb-3"><fmt:message key="billing.billingOnFavourite.title"/></h5>

<c:if test="${not empty favouriteModel.messageKey}">
    <div class="alert alert-${favouriteModel.messageLevel} py-1 mb-2 small">
        <fmt:message key="${favouriteModel.messageKey}">
            <c:if test="${not empty favouriteModel.messageName}">
                <fmt:param value="${carlos:forHtml(favouriteModel.messageName)}"/>
            </c:if>
        </fmt:message>
    </div>
</c:if>

<%-- ===== Form 1: select an existing favourite to edit or delete ===== --%>
<div class="card mb-3">
    <div class="card-body py-2">
        <form method="post" name="baseur0" id="baseur0"
              action="${ctx}/billing/CA/ON/ViewBillingONFavourite">
            <div class="d-flex align-items-center gap-2 flex-wrap">
                <label for="favSelect" class="fw-semibold mb-0">
                    <fmt:message key="billing.billingOnFavourite.labelChooseFavourite"/>
                </label>
                <select name="name" id="favSelect" class="form-select form-select-sm" style="width: auto;">
                    <option value=""><fmt:message key="billing.billingOnFavourite.optChooseOne"/></option>
                    <c:forEach var="favName" items="${favouriteModel.names}">
                        <option value="<carlos:encode value='${favName.value}' context='htmlAttribute'/>">
                            <carlos:encode value="${favName.value}" context="html"/>
                        </option>
                    </c:forEach>
                </select>
                <%-- Hidden field provides submit=Search for isMutationSubmission --%>
                <input type="hidden" name="submit" value="Search">
                <button type="submit" name="action" value="Edit" class="btn btn-secondary btn-sm">
                    <fmt:message key="billing.billingOnFavourite.btnEdit"/>
                </button>
                <button type="submit" name="action" value="Delete" id="btnDelete"
                        class="btn btn-danger btn-sm">
                    <fmt:message key="billing.billingOnFavourite.btnDelete"/>
                </button>
            </div>
        </form>
    </div>
</div>

<%-- ===== Form 2: add / edit service code macro ===== --%>
<div class="card">
    <div class="card-body py-2">
        <form method="post" name="baseurl" id="baseurl"
              action="${ctx}/billing/CA/ON/ViewBillingONFavourite">

            <%-- Name row + Search button (Search button comes before the hidden Save field
                 so getParameter("submit") returns "Search" when Search is clicked) --%>
            <div class="row align-items-center mb-2">
                <label for="favName" class="col-auto col-form-label col-form-label-sm fw-semibold">
                    <fmt:message key="billing.billingOnFavourite.labelName"/>
                </label>
                <div class="col">
                    <div class="d-flex align-items-center gap-2 flex-wrap">
                        <input class="form-control form-control-sm" type="text" name="name" id="favName"
                               value="<carlos:encode value='${favouriteModel.formFields[\"name\"]}' context='htmlAttribute'/>"
                               maxlength="50"
                               placeholder="<fmt:message key='billing.billingOnFavourite.nameHint'/>"/>
                        <%-- value="Search" is the mutation-signal sentinel checked by isMutationSubmission --%>
                        <button type="submit" name="submit" value="Search" class="btn btn-secondary btn-sm">
                            <fmt:message key="billing.billingOnFavourite.btnSearch"/>
                        </button>
                    </div>
                </div>
            </div>

            <%-- Service code rows --%>
            <c:forEach begin="0" end="${favouriteModel.serviceFieldCount - 1}" var="i">
                <c:set var="codeKey" value="serviceCode${i}"/>
                <c:set var="unitKey" value="serviceUnit${i}"/>
                <c:set var="atKey"   value="serviceAt${i}"/>
                <div class="row align-items-center mb-1">
                    <label class="col-auto col-form-label col-form-label-sm fw-semibold" style="min-width: 9rem;">
                        <fmt:message key="billing.billingOnFavourite.labelServiceCode">
                            <fmt:param value="${i + 1}"/>
                        </fmt:message>
                    </label>
                    <div class="col">
                        <div class="d-flex align-items-center gap-1 flex-wrap">
                            <input class="form-control form-control-sm fav-code" type="text"
                                   name="serviceCode${i}"
                                   value="<carlos:encode value='${favouriteModel.formFields[codeKey]}' context='htmlAttribute'/>"
                                   maxlength="5" onblur="upCaseCtrl(this)"/>
                            <span class="text-muted small"><fmt:message key="billing.billingOnFavourite.codeHint"/></span>
                            <label class="fw-semibold mb-0 ms-2">
                                <fmt:message key="billing.billingOnFavourite.labelUnit"/>
                            </label>
                            <input class="form-control form-control-sm fav-unit" type="text"
                                   name="serviceUnit${i}"
                                   value="<carlos:encode value='${favouriteModel.formFields[unitKey]}' context='htmlAttribute'/>"
                                   maxlength="2"/>
                            <span class="text-muted small"><fmt:message key="billing.billingOnFavourite.unitHint"/></span>
                            <label class="fw-semibold mb-0 ms-2">
                                <fmt:message key="billing.billingOnFavourite.labelAt"/>
                            </label>
                            <input class="form-control form-control-sm fav-at" type="text"
                                   name="serviceAt${i}"
                                   value="<carlos:encode value='${favouriteModel.formFields[atKey]}' context='htmlAttribute'/>"
                                   maxlength="4"/>
                            <span class="text-muted small"><fmt:message key="billing.billingOnFavourite.atHint"/></span>
                        </div>
                    </div>
                </div>
            </c:forEach>

            <%-- Dx row --%>
            <div class="row align-items-center mb-2">
                <label class="col-auto col-form-label col-form-label-sm fw-semibold" style="min-width: 9rem;">
                    <fmt:message key="billing.billingOnFavourite.labelDx"/>
                </label>
                <div class="col">
                    <div class="d-flex align-items-center gap-1 flex-wrap">
                        <input class="form-control form-control-sm fav-dx" type="text" name="dx"
                               value="<carlos:encode value='${favouriteModel.formFields[\"dx\"]}' context='htmlAttribute'/>"
                               maxlength="4"
                               placeholder="<fmt:message key='billing.billingOnFavourite.dxHint'/>"/>
                        <label class="fw-semibold mb-0 ms-2">
                            <fmt:message key="billing.billingOnFavourite.labelDx1"/>
                        </label>
                        <input class="form-control form-control-sm fav-dx" type="text" name="dx1"
                               value="<carlos:encode value='${favouriteModel.formFields[\"dx1\"]}' context='htmlAttribute'/>"
                               maxlength="4"/>
                        <label class="fw-semibold mb-0 ms-2">
                            <fmt:message key="billing.billingOnFavourite.labelDx2"/>
                        </label>
                        <input class="form-control form-control-sm fav-dx" type="text" name="dx2"
                               value="<carlos:encode value='${favouriteModel.formFields[\"dx2\"]}' context='htmlAttribute'/>"
                               maxlength="4"/>
                    </div>
                </div>
            </div>

            <%-- Save / Close row.
                 The hidden submit=Save field comes after the Search button in DOM order, so
                 getParameter("submit") still returns "Search" when Search is clicked, but
                 returns "Save" when the visible (unnamed) save button is clicked. --%>
            <div class="d-flex gap-2 justify-content-center pt-2 border-top">
                <input type="hidden" name="action"
                       value='<carlos:encode value="${favouriteModel.action}" context="htmlAttribute"/>'/>
                <input type="hidden" name="submit" value="Save"/>
                <button type="submit" class="btn btn-primary btn-sm" id="btnSave">
                    <fmt:message key="admin.resourcebaseurl.btnSave"/>
                </button>
                <button type="button" class="btn btn-secondary btn-sm" onclick="window.close()">
                    <fmt:message key="admin.resourcebaseurl.btnExit"/>
                </button>
            </div>

        </form>
    </div>
</div>

<script>
    // i18n strings for confirm/alert dialogs
    var i18n = {
        confirmDelete: '${carlos:forJavaScript(msgConfirmDelete)}',
        confirmSave:   '${carlos:forJavaScript(msgConfirmSave)}',
        alertCode:     '${carlos:forJavaScript(alertInvalidCode)}',
        alertNum:      '${carlos:forJavaScript(alertInvalidNum)}',
        alertDx:       '${carlos:forJavaScript(alertInvalidDx)}'
    };

    document.getElementById('btnDelete').addEventListener('click', function (e) {
        if (!confirm(i18n.confirmDelete)) { e.preventDefault(); }
    });

    document.getElementById('btnSave').addEventListener('click', function (e) {
        if (!checkAllFields() || !confirm(i18n.confirmSave)) { e.preventDefault(); }
    });

    function upCaseCtrl(ctrl) { ctrl.value = ctrl.value.toUpperCase(); }

    function isServiceCode(s) {
        if (s.length === 0) return true;
        if (s.length !== 5) return false;
        if (s.charAt(0) < 'A' || s.charAt(0) > 'Z') return false;
        if (s.charAt(4) < 'A' || s.charAt(4) > 'Z') return false;
        for (var i = 1; i < 4; i++) {
            var c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    function isNumber(s) {
        for (var i = 0; i < s.length; i++) {
            var c = s.charAt(i);
            if (c !== '.' && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    function checkAllFields() {
        var form = document.forms['baseurl'];
        for (var i = 0; i < 10; i++) {
            var code = form.elements['serviceCode' + i];
            var unit = form.elements['serviceUnit' + i];
            var at   = form.elements['serviceAt'   + i];
            if (code && code.value.length > 0 && !isServiceCode(code.value)) {
                alert(i18n.alertCode); return false;
            }
            if (unit && unit.value.length > 0 && !isNumber(unit.value)) {
                alert(i18n.alertNum); return false;
            }
            if (at && at.value.length > 0 && !isNumber(at.value)) {
                alert(i18n.alertNum); return false;
            }
        }
        var dx = form.elements['dx'];
        if (dx && dx.value.length > 0 && (!isNumber(dx.value) || dx.value.length !== 3)) {
            alert(i18n.alertDx); return false;
        }
        return true;
    }

    window.addEventListener('load', function () {
        var f = document.forms['baseurl'];
        if (f && f.elements['name']) {
            f.elements['name'].focus();
            f.elements['name'].select();
        }
    });

    // Autocomplete — reuses the same pattern as billingON.jsp
    jQuery(document).ready(function () {
        var ctx = '${carlos:forJavaScript(ctx)}';

        function escHtml(s) {
            return jQuery('<div>').text(s || '').html();
        }

        function renderCodeItem(ul, item) {
            var li    = jQuery('<li>').addClass('ui-menu-item');
            var inner = jQuery('<div>').addClass('billing-ac-item');
            inner.html('<strong>' + escHtml(item.code) + '</strong> – ' +
                       escHtml(item.description || item.label || ''));
            li.append(inner).appendTo(ul);
            return li;
        }

        function initCodeAutocomplete($inputs, ajaxUrl) {
            $inputs.each(function () {
                var $input = jQuery(this);
                var inst = $input.autocomplete({
                    source: function (request, response) {
                        jQuery.getJSON(ctx + ajaxUrl, { term: request.term }, response);
                    },
                    minLength: 2,
                    delay: 250,
                    select: function (event, ui) {
                        this.value = ui.item.code.toUpperCase();
                        return false;
                    }
                }).data('ui-autocomplete');
                if (inst) { inst._renderItem = renderCodeItem; }
            });
        }

        initCodeAutocomplete(
            jQuery("input[name^='serviceCode']"),
            '/billing/CA/ON/ViewBillingCodeSearchAjax'
        );
        initCodeAutocomplete(
            jQuery("input[name='dx'], input[name='dx1'], input[name='dx2']"),
            '/billing/CA/ON/ViewBillingDigSearchAjax'
        );
    });
</script>
</body>
</html>
