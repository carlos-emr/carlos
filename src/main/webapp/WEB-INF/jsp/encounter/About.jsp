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

    Originally written for the Department of Family Medicine, McMaster University,
    Hamilton, Ontario, Canada.
    Now maintained by the CARLOS EMR Project.
    https://github.com/carlos-emr/carlos

    Modifications by CARLOS Contributors, 2026.

--%>
<%--
    CARLOS EMR - About page

    Purpose:
    Authenticated "About" dialog reached from the eChart, prescription, fax
    cover page and messenger toolbars via the extensionless Struts route
    /encounter/ViewAbout (see struts-clinical.xml -> ViewClinical2Action).

    Content contract:
    - The build stamp lives in a `.build_info` element. Automated browser
      checks (scripts/rx-fax-*-playwright-checks.js) read `.build_info` to
      assert the deployed WAR reports a real version and not an unresolved
      Maven placeholder, so the class name is part of that contract.
      Build identity comes from BuildInfo via CarlosProperties, never from
      operator-owned carlos.properties -- see docs/build-identity.md.
    - Attribution text is a user-facing summary of NOTICE.md. GPL requires
      that the upstream copyright notices stay intact, so heritage holders
      are named here rather than dropped; NOTICE.md remains the full list.

    @since 2026-09-18
--%>

<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
        <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <meta charset="UTF-8">
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title><fmt:message key="global.about"/> CARLOS EMR</title>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar d-flex align-items-center justify-content-between
                    py-2 mb-3 border-bottom" id="header">
            <div class="d-flex align-items-center gap-2">
                <span class="fw-semibold"><fmt:message key="global.about"/> CARLOS EMR</span>
            </div>
            <div class="text-muted small">
                <a href="javascript:window.close()"><fmt:message key="global.btnClose"/></a>
            </div>
        </div>

        <p class="build_info text-muted small">
            build date: <%= SafeEncode.forHtmlContent(CarlosProperties.getBuildDate()) %><br/>
            build tag: <%= SafeEncode.forHtmlContent(CarlosProperties.getBuildTag()) %>
        </p>

        <div class="bg-light border rounded p-3 mb-3">
            <h2 class="h6">CARLOS EMR</h2>

            <p class="mb-2">
                CARLOS (Clinical Assisting Recording Ledger Open Source) is an independent
                open-source electronic medical record system for Canadian healthcare,
                developed and maintained by the CARLOS community of healthcare providers
                and developers.
            </p>

            <p class="mb-0">
                Project home:
                <a href="https://github.com/carlos-emr/carlos" target="_blank" rel="noopener noreferrer">
                    github.com/carlos-emr/carlos</a>
            </p>
        </div>

        <div class="bg-light border rounded p-3 mb-3">
            <h2 class="h6">Licence</h2>

            <p class="mb-2">
                CARLOS EMR is free software, published under the GNU General Public Licence
                (GPL), version 2 or, at your option, any later version. You can redistribute
                it and/or modify it under those terms.
            </p>

            <p class="mb-2">
                This program is distributed in the hope that it will be useful, but
                <strong>WITHOUT ANY WARRANTY</strong>; without even the implied warranty of
                MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
                Public Licence for more details.
            </p>

            <p class="mb-0">
                You should have received a copy of the GNU General Public Licence along with
                this program; the full text ships with the source as <code>COPYING.md</code>
                and is also published at
                <a href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
                   target="_blank" rel="noopener noreferrer">gnu.org</a>.
            </p>
        </div>

        <div class="bg-light border rounded p-3 mb-3">
            <h2 class="h6">Heritage and attribution</h2>

            <p class="mb-2">
                CARLOS builds on more than twenty years of open-source work. It is forked
                from the OpenO EMR project, which was itself forked from OSCAR McMaster.
                The copyright notices of every upstream contributor are preserved in the
                source files, as the GPL requires.
            </p>

            <ul class="mb-2">
                <li>Department of Family Medicine, McMaster University, Hamilton, Ontario,
                    Canada &mdash; original OSCAR McMaster work (2001&ndash;2020)</li>
                <li>Centre for Research on Inner City Health, St. Michael's Hospital, Toronto
                    (2005&ndash;2012)</li>
                <li>OpenOSP and the OpenO EMR contributors &mdash; intermediate fork
                    (2025&ndash;2026)</li>
                <li>CARLOS Contributors (2026&ndash;present)</li>
                <li>&hellip; and many other individuals and organizations listed in
                    <code>NOTICE.md</code></li>
            </ul>

            <p class="mb-0">
                The complete attribution list is maintained at
                <a href="https://github.com/carlos-emr/carlos/blob/main/NOTICE.md"
                   target="_blank" rel="noopener noreferrer">NOTICE.md</a>.
            </p>
        </div>

        <div class="bg-light border rounded p-3 mb-3">
            <h2 class="h6">Trademarks and affiliation</h2>

            <p class="mb-2">
                "OSCAR" is an official mark of McMaster University. References to OSCAR in
                this software and its source code are historical and descriptive only.
            </p>

            <p class="mb-0">
                CARLOS has no organizational affiliation with McMaster University or its
                Department of Family Medicine, with the OpenOSP organization, or with any
                other organization named in the historical copyright notices. Those notices
                reflect the open-source heritage of the code and GPL attribution
                requirements, not any current relationship.
            </p>
        </div>

        <p class="text-end">
            <a href="javascript:window.close()"><fmt:message key="global.btnClose"/></a>
        </p>

    </div>
    </body>
</html>
