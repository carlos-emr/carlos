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

--%>
<%--
  ChartSpace page shell (#4349, PR1).

  Purpose: static frame for the ChartSpace chart view. Blocks (currently only
           Allergies) are loaded and rendered client-side by js/chartspace/*.
  Request attribute: demographicNo (Integer), set by the gate action after its
           security check. No patient name or other PHI is rendered here.
  No CSRF bootstrap include: the page performs no POST (read-only JSON GET).
  @since 2026-10-09
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="chartspace.chartSpace.title" var="csTitle"/>
<fmt:message key="chartspace.chartSpace.sectionTop" var="csSectionTop"/>
<fmt:message key="chartspace.chartSpace.sectionRight" var="csSectionRight"/>
<fmt:message key="chartspace.chartSpace.sectionHidden" var="csSectionHidden"/>
<fmt:message key="chartspace.chartSpace.btnHidden" var="csBtnHidden"/>
<fmt:message key="chartspace.chartSpace.msgHiddenHasData" var="csMsgHiddenHasData"/>
<fmt:message key="chartspace.chartSpace.btnClose" var="csBtnClose"/>
<fmt:message key="chartspace.chartSpace.titleAllergies" var="csTitleAllergies"/>
<fmt:message key="chartspace.chartSpace.msgLoading" var="csMsgLoading"/>
<fmt:message key="chartspace.chartSpace.msgEmpty" var="csMsgEmpty"/>
<fmt:message key="chartspace.chartSpace.msgNoAccess" var="csMsgNoAccess"/>
<fmt:message key="chartspace.chartSpace.msgError" var="csMsgError"/>
<fmt:message key="chartspace.chartSpace.labelSeveritySevere" var="csSevSevere"/>
<fmt:message key="chartspace.chartSpace.labelSeverityModerate" var="csSevModerate"/>
<fmt:message key="chartspace.chartSpace.labelSeverityMild" var="csSevMild"/>
<fmt:message key="chartspace.chartSpace.labelSeverityNone" var="csSevNone"/>
<fmt:message key="chartspace.chartSpace.labelSeverityUnknown" var="csSevUnknown"/>
<%-- No <fmt:param>: JSTL returns these patterns verbatim (no MessageFormat), and
     chartspace.js substitutes {0} (block title) and {1} (record count) as text. --%>
<fmt:message key="chartspace.chartSpace.announceLoaded" var="csAnnounceLoaded"/>
<fmt:message key="chartspace.chartSpace.announceEmpty" var="csAnnounceEmpty"/>
<fmt:message key="chartspace.chartSpace.announceNoAccess" var="csAnnounceNoAccess"/>
<fmt:message key="chartspace.chartSpace.announceError" var="csAnnounceError"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><carlos:encode value="${csTitle}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/chartspace.css">
</head>
<body class="cs-page">
<header class="cs-header">
    <h1 class="cs-title"><carlos:encode value="${csTitle}"/></h1>
    <button type="button" id="cs-hidden-toggle" class="btn btn-outline-secondary btn-sm"
            aria-expanded="false" aria-controls="cs-hidden-panel" data-has-data="false" disabled>
        <carlos:encode value="${csBtnHidden}"/> (0)
    </button>
</header>

<div class="cs-layout">
    <main class="cs-main">
        <section id="cs-top" class="cs-top" aria-labelledby="cs-top-title">
            <h2 id="cs-top-title" class="visually-hidden"><carlos:encode value="${csSectionTop}"/></h2>
        </section>
    </main>
    <aside class="cs-side">
        <section id="cs-right" class="cs-right" aria-labelledby="cs-right-title">
            <h2 id="cs-right-title" class="visually-hidden"><carlos:encode value="${csSectionRight}"/></h2>
        </section>
    </aside>
</div>

<section id="cs-hidden-panel" class="cs-hidden-panel" role="region"
         aria-labelledby="cs-hidden-title" hidden>
    <div class="cs-hidden-head">
        <h2 id="cs-hidden-title" class="h6 mb-0" tabindex="-1"><carlos:encode value="${csSectionHidden}"/></h2>
        <button type="button" id="cs-hidden-close" class="btn btn-sm btn-outline-secondary">
            <carlos:encode value="${csBtnClose}"/>
        </button>
    </div>
    <div id="cs-hidden-body" class="cs-hidden-body"></div>
</section>

<%-- The single page-level status region: chartspace.js writes one short message
     per settled block. Kept outside the cards and the hidden panel so it is
     never hidden or moved. --%>
<div id="cs-announcer" class="visually-hidden" role="status" aria-live="polite"></div>

<script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/js/chartspace/chartspace-layout.js"></script>
<script src="${pageContext.request.contextPath}/js/chartspace/chartspace-allergies.js"></script>
<script src="${pageContext.request.contextPath}/js/chartspace/chartspace.js"></script>
<script>
    document.addEventListener('DOMContentLoaded', function () {
        window.ChartSpace.init({
            contextPath: '${carlos:forJavaScript(pageContext.request.contextPath)}',
            demographicNo: '${carlos:forJavaScript(demographicNo)}',
            i18n: {
                btnHidden: '${carlos:forJavaScript(csBtnHidden)}',
                msgHiddenHasData: '${carlos:forJavaScript(csMsgHiddenHasData)}',
                msgLoading: '${carlos:forJavaScript(csMsgLoading)}',
                msgEmpty: '${carlos:forJavaScript(csMsgEmpty)}',
                msgNoAccess: '${carlos:forJavaScript(csMsgNoAccess)}',
                msgError: '${carlos:forJavaScript(csMsgError)}',
                announceLoaded: '${carlos:forJavaScript(csAnnounceLoaded)}',
                announceEmpty: '${carlos:forJavaScript(csAnnounceEmpty)}',
                announceNoAccess: '${carlos:forJavaScript(csAnnounceNoAccess)}',
                announceError: '${carlos:forJavaScript(csAnnounceError)}',
                titles: {allergies: '${carlos:forJavaScript(csTitleAllergies)}'},
                severity: {
                    severe: '${carlos:forJavaScript(csSevSevere)}',
                    moderate: '${carlos:forJavaScript(csSevModerate)}',
                    mild: '${carlos:forJavaScript(csSevMild)}',
                    none: '${carlos:forJavaScript(csSevNone)}',
                    unknown: '${carlos:forJavaScript(csSevUnknown)}'
                }
            }
        });
    });
</script>
</body>
</html>
