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
    Lab Display Settings

    System-wide (not per-provider) settings for the inline preview of PDFs embedded in HL7 lab
    results (value type ED) on the lab display and the inbox lab view (#3977):
      * lab_pdf_inline_preview - show the PDF in a frame under its result row (default on).
      * lab_pdf_max_size - the largest PDF previewed inline, entered here in whole MiB
        (default 10, at most 100). Larger PDFs show a "use Download PDF" message.
    The Download PDF link is unaffected by both settings.

    Rendered by LabDisplaySettings2Action, which requires _admin read to view and _admin write
    to save (POST only).

    @since 2026-09-30
--%>
<!DOCTYPE HTML>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.SystemPreferences" %>
<fmt:setBundle basename="oscarResources"/>

<html lang="${pageContext.response.locale.language}">
    <head>
    <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico"/>
        <title><fmt:message key="admin.labDisplaySettings.title"/></title>
        <link href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/global.js"></script>
        <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    </head>

    <body class="BodyStyle">

    <h4><fmt:message key="admin.labDisplaySettings.heading"/></h4>

    <%-- Enter in the size field submits the form implicitly, without the Save button's onclick;
         onsubmit gives that submit the same save intent so it saves (and needs _admin write)
         instead of silently re-rendering the stored values over the administrator's edit. --%>
    <form name="labDisplaySettingsForm" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/admin/LabDisplaySettings"
          onsubmit="this.dboperation.value='Save';">
        <input type="hidden" name="dboperation" value="">

        <div class="card" style="max-width: 640px;">
            <div class="card-body">
                <div class="form-check form-switch mb-2">
                    <input class="form-check-input" type="checkbox" id="lab_pdf_inline_preview"
                           name="<%=SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview.name()%>"
                           value="true" ${labPdfInlinePreview ? "checked" : ""} />
                    <label class="form-check-label" for="lab_pdf_inline_preview">
                        <fmt:message key="admin.labDisplaySettings.inlinePreview"/>
                    </label>
                </div>
                <div class="form-text text-muted mb-3">
                    <fmt:message key="admin.labDisplaySettings.inlinePreviewHelp"/>
                </div>

                <label class="form-label" for="lab_pdf_max_size_mb">
                    <fmt:message key="admin.labDisplaySettings.maxSize"/>
                </label>
                <input class="form-control" style="max-width: 10em;" type="number" min="1"
                       max="<carlos:encode value="${labPdfMaxSizeMbLimit}" context="htmlAttribute"/>" step="1"
                       id="lab_pdf_max_size_mb" name="lab_pdf_max_size_mb"
                       value="<carlos:encode value="${labPdfMaxSizeMb}" context="htmlAttribute"/>" required/>
                <div class="form-text text-muted">
                    <fmt:message key="admin.labDisplaySettings.maxSizeHelp">
                        <fmt:param value="${labPdfMaxSizeMbLimit}"/>
                    </fmt:message>
                </div>
            </div>
        </div>

        <p></p>
        <input type="button"
               onclick="document.forms['labDisplaySettingsForm'].dboperation.value='Save'; document.forms['labDisplaySettingsForm'].submit();"
               name="saveLabDisplaySettings" value="<fmt:message key='global.save'/>"/>
        <c:if test="${saved}">
            <span id="labDisplaySettingsSaved" style="color:green;"><fmt:message key="admin.labDisplaySettings.saved"/></span>
        </c:if>
        <c:if test="${saveFailed}">
            <span id="labDisplaySettingsSaveFailed" style="color:#b00020;">
                <fmt:message key="admin.labDisplaySettings.saveFailed"/>
            </span>
        </c:if>
        <c:if test="${invalidSize}">
            <span id="labDisplaySettingsInvalid" style="color:#b00020;">
                <fmt:message key="admin.labDisplaySettings.invalidSize">
                    <fmt:param value="${labPdfMaxSizeMbLimit}"/>
                </fmt:message>
            </span>
        </c:if>
    </form>
    </body>
</html>
