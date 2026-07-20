<%--

    Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
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

    Maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

    Modifications by CARLOS Contributors, 2026.

--%>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.EFormDataDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EFormData" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
    }

    String requestId = request.getParameter("requestId");
    if (!StringUtils.isInteger(requestId)) {
        out.print("<em>No attachments</em>");
        return;
    }

    Integer fdid = Integer.valueOf(requestId);
    EFormDataDao eFormDataDao = SpringUtils.getBean(EFormDataDao.class);
    Integer demographicNo;
    try {
        EFormData eFormData = eFormDataDao.find(fdid);
        demographicNo = eFormData != null ? eFormData.getDemographicId() : null;
    } catch (Exception e) {
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Failed to load eForm data for fdid=" + fdid, e);
        out.print("<em>Error loading attachments</em>");
        return;
    }
    if (demographicNo == null) {
        out.print("<em>No attachments</em>");
        return;
    }

    DocumentAttachmentManager attachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    boolean canReadDocuments = securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null);
    boolean canReadLabs = securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null);
    boolean canReadHrm = securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null);
    boolean canReadEforms = securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null);
    boolean canReadForms = securityInfoManager.hasPrivilege(loggedInInfo, "_form", "r", null);

    List<String> docIds;
    List<String> labIds;
    List<String> hrmIds;
    List<String> eformIds;
    List<EctFormData.PatientForm> attachedForms;
    try {
        docIds = canReadDocuments ? attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, demographicNo) : Collections.emptyList();
        labIds = canReadLabs ? attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, demographicNo) : Collections.emptyList();
        hrmIds = canReadHrm ? attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, demographicNo) : Collections.emptyList();
        eformIds = canReadEforms ? attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, demographicNo) : Collections.emptyList();
        attachedForms = canReadForms ? attachmentManager.getFormsAttachedToEForms(loggedInInfo, fdid, DocumentType.FORM, demographicNo) : Collections.emptyList();
    } catch (Exception e) {
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Failed to load attachments for fdid=" + fdid, e);
        out.print("<em>Error loading attachments</em>");
        return;
    }

    boolean hasAttachments = (docIds != null && !docIds.isEmpty())
            || (labIds != null && !labIds.isEmpty())
            || (hrmIds != null && !hrmIds.isEmpty())
            || (eformIds != null && !eformIds.isEmpty())
            || (attachedForms != null && !attachedForms.isEmpty());

    if (!hasAttachments) {
        out.print("<em>No attachments</em>");
        return;
    }
%>
<% if (docIds != null) { for (String id : docIds) { %>
<span class="doc">Doc #<carlos:encode value='<%= id %>' context="html"/></span><br>
<% } } %>
<% if (labIds != null) { for (String id : labIds) { %>
<span class="lab">Lab #<carlos:encode value='<%= id %>' context="html"/></span><br>
<% } } %>
<% if (hrmIds != null) { for (String id : hrmIds) { %>
<span class="hrm">HRM #<carlos:encode value='<%= id %>' context="html"/></span><br>
<% } } %>
<% if (eformIds != null) { for (String id : eformIds) { %>
<span class="eform">EForm #<carlos:encode value='<%= id %>' context="html"/></span><br>
<% } } %>
<% if (attachedForms != null) { for (EctFormData.PatientForm form : attachedForms) { %>
<span class="form">Form #<carlos:encode value='<%= form.getFormId() %>' context="html"/></span><br>
<% } } %>
