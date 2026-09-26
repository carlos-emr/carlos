<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    Provider linking rules were first implemented by Deval Italiya in
    open-osp/Open-O pull request #196 (GPL); this CARLOS page is adapted
    from that work.

--%>
<%--
    Provider Linking Rules (Administration > Labs/Inbox)

    One clinic-wide switch (global property row "provider_linking_rules", off when absent).
    When on, an HL7 lab or HRM report that is matched to a patient is also routed to the
    patient's Most Responsible Provider (demographic.provider_no), on top of the ordering or
    delivered-to provider: at HL7 upload, at manual lab Patient Match, and at HRM patient
    assignment. eDocuments are not affected. See docs/provider-linking-rules.md.

    Reached only through ProviderLinkingRules2Action (_admin read), which publishes:
      providerLinkingRulesEnabled  - current state
      providerLinkingRulesCanWrite - whether the viewer holds _admin write
      providerLinkingRulesSaved    - true right after a save (post/redirect/get)

    The form is a real POST form with an action URL, so CSRFGuard injects its token into it.
    It posts to admin/saveProviderLinkingRules (POST only, _admin write, audited). An unchecked
    checkbox sends nothing, which the save action reads as "off".

    @since 2026-09-26
--%>
<!DOCTYPE HTML>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html lang="${carlos:forHtmlAttribute(pageContext.response.locale.language)}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.providerLinkingRules.title"/></title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
</head>

<body class="BodyStyle">
<div class="container-fluid" style="max-width: 760px;">
    <h4 class="mt-2"><fmt:message key="admin.providerLinkingRules.heading"/></h4>

    <p><fmt:message key="admin.providerLinkingRules.descriptionDefault"/></p>
    <p><fmt:message key="admin.providerLinkingRules.descriptionEnabled"/></p>
    <p class="text-muted small"><fmt:message key="admin.providerLinkingRules.descriptionScope"/></p>

    <form id="providerLinkingRulesForm" name="providerLinkingRulesForm" method="post"
          action="${pageContext.request.contextPath}/admin/saveProviderLinkingRules">
        <div class="card">
            <div class="card-body">
                <div class="form-check form-switch mb-2">
                    <input class="form-check-input" type="checkbox" role="switch"
                           id="providerLinkingRulesEnabled" name="enabled" value="true"
                           ${providerLinkingRulesEnabled ? 'checked' : ''}
                           ${providerLinkingRulesCanWrite ? '' : 'disabled'}/>
                    <label class="form-check-label fw-bold" for="providerLinkingRulesEnabled">
                        <fmt:message key="admin.providerLinkingRules.switchLabel"/>
                    </label>
                </div>
                <div class="form-text">
                    <fmt:message key="admin.providerLinkingRules.privacyNote"/>
                </div>
            </div>
        </div>

        <div class="mt-3">
            <c:choose>
                <c:when test="${providerLinkingRulesCanWrite}">
                    <button type="submit" id="saveProviderLinkingRules" class="btn btn-primary btn-sm">
                        <fmt:message key="global.save"/>
                    </button>
                </c:when>
                <c:otherwise>
                    <span id="providerLinkingRulesReadOnly" class="text-muted">
                        <fmt:message key="admin.providerLinkingRules.readOnly"/>
                    </span>
                </c:otherwise>
            </c:choose>
            <c:if test="${providerLinkingRulesSaved}">
                <span id="providerLinkingRulesSaved" class="text-success ms-2" role="status">
                    <fmt:message key="admin.providerLinkingRules.saved"/>
                </span>
            </c:if>
        </div>
    </form>
</div>
</body>
</html>
