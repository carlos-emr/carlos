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
    CARLOS EMR - Licence page

    Purpose:
    Licence popup reached from the "License" link on prescription, eform,
    prevention, provider-signature and admin screens via the extensionless
    Struts route /encounter/ViewLicense (struts-clinical.xml ->
    ViewClinical2Action).

    Content contract:
    The page states the CARLOS licence first, then reproduces the upstream
    OSCAR McMaster notice verbatim. The verbatim block is not editorial
    filler: GPL section 1 requires that the original copyright notices travel
    with the software, so it must not be trimmed, reworded, or have its GPL
    version changed. Additional attribution lives in NOTICE.md.

    @since 2026-09-18
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <meta charset="UTF-8">
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title><fmt:message key="encounter.license.title"/></title>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar d-flex align-items-center justify-content-between
                    py-2 mb-3 border-bottom" id="header">
            <div class="d-flex align-items-center gap-2">
                <span class="fw-semibold"><fmt:message key="encounter.license.title"/></span>
            </div>
            <div class="text-muted small">
                <a href="javascript:window.close()"><fmt:message key="global.btnClose"/></a>
            </div>
        </div>

        <div class="bg-light border rounded p-3 mb-3">
            <p class="mb-2">
                CARLOS EMR is free software, published under the GNU General Public Licence
                (GPL), version 2 or, at your option, any later version. You can redistribute
                it and/or modify it under those terms.
            </p>

            <p class="mb-2">
                This program is distributed in the hope that it will be useful, but
                <strong>WITHOUT ANY WARRANTY</strong>; without even the implied warranty of
                MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
                Public Licence for more details. The full text ships with the source as
                <code>COPYING.md</code> and is also published at
                <a href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
                   target="_blank" rel="noopener noreferrer">gnu.org</a>.
            </p>

            <p class="mb-0">
                CARLOS is forked from the OpenO EMR project, which was itself forked from
                OSCAR McMaster. Attribution for every upstream contributor &mdash; including
                the Department of Family Medicine at McMaster University, the Centre for
                Research on Inner City Health at St. Michael's Hospital, and the OpenOSP /
                OpenO EMR contributors &mdash; is preserved in the source files and listed in
                <a href="https://github.com/carlos-emr/carlos/blob/develop/NOTICE.md"
                   target="_blank" rel="noopener noreferrer">NOTICE.md</a>.
                CARLOS has no organizational affiliation with any of them.
            </p>
        </div>

        <p class="text-muted small mb-1">
            Upstream notice, reproduced verbatim as the GPL requires:
        </p>

        <div class="bg-light border rounded p-2">
            <pre>
/* *
 * Copyright (c) 2001-2015. Department of Family Medicine, McMaster University. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,USA.
 *
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
</pre>
        </div>

    </div>
    </body>
</html>
