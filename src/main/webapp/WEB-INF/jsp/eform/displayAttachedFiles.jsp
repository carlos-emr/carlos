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
    EFormData eFormData = eFormDataDao.find(fdid);
    Integer demographicNo = eFormData != null ? eFormData.getDemographicId() : null;
    if (demographicNo == null) {
        out.print("<em>No attachments</em>");
        return;
    }

    DocumentAttachmentManager attachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);

    List<String> docIds;
    List<String> labIds;
    List<String> hrmIds;
    List<String> eformIds;
    List<EctFormData.PatientForm> attachedForms;
    try {
        docIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, demographicNo);
        labIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, demographicNo);
        hrmIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, demographicNo);
        eformIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, demographicNo);
        attachedForms = attachmentManager.getFormsAttachedToEForms(loggedInInfo, fdid, DocumentType.FORM, demographicNo);
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
