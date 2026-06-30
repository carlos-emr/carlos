<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.FormsManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EFormData" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.data.AttachmentLabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return;
    }

    String demoNo = request.getParameter("demo");
    String requestId = request.getParameter("requestId");
    if (!StringUtils.isInteger(demoNo)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic number");
        return;
    }

    Integer demographicNo = Integer.valueOf(demoNo);
    Integer fdid = StringUtils.isInteger(requestId) ? Integer.valueOf(requestId) : null;

    DocumentAttachmentManager attachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    FormsManager formsManager = SpringUtils.getBean(FormsManager.class);

    List<EDoc> allDocuments = securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)
            ? EDocUtil.listDocs(loggedInInfo, "demographic", demoNo, null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE)
            : new ArrayList<>();
    ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)
            ? HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, demoNo, false)
            : new ArrayList<>();
    List<AttachmentLabResultData> allLabsSortedByVersions = securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)
            ? attachmentManager.getAllLabsSortedByVersions(loggedInInfo, demoNo)
            : new ArrayList<>();
    List<EctFormData.PatientForm> allForms = securityInfoManager.hasPrivilege(loggedInInfo, "_form", "r", null)
            ? formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, false, true)
            : new ArrayList<>();
    List<EFormData> allEForms = securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)
            ? (fdid != null ? attachmentManager.getAllEFormsExpectFdid(loggedInInfo, demographicNo, fdid) : io.github.carlos_emr.carlos.eform.EFormUtil.listPatientEformsCurrent(demographicNo, true))
            : new ArrayList<>();

    Set<String> attachedDocIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, demographicNo)) : Collections.emptySet();
    Set<String> attachedLabIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, demographicNo)) : Collections.emptySet();
    Set<String> attachedHrmIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, demographicNo)) : Collections.emptySet();
    Set<String> attachedEFormIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, demographicNo)) : Collections.emptySet();
    Set<String> attachedFormIds = new HashSet<>();
    if (fdid != null) {
        for (EctFormData.PatientForm form : attachmentManager.getFormsAttachedToEForms(loggedInInfo, fdid, DocumentType.FORM, demographicNo)) {
            attachedFormIds.add(form.getFormId());
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <meta charset="UTF-8">
    <title>Attach Files to Letter</title>
    <style>
        body { font-family: Helvetica, Arial, sans-serif; font-size: 12px; margin: 10px; }
        h3 { margin: 0 0 8px; }
        h4 { margin: 12px 0 4px; }
        .section { margin-bottom: 12px; }
        .list { border: 1px solid #ccc; max-height: 180px; overflow-y: auto; padding: 6px; }
        .item { padding: 2px 0; }
        .muted { color: #666; }
        .actions { margin-top: 12px; text-align: center; }
        .btn { padding: 4px 12px; margin: 0 4px; cursor: pointer; }
        .doc { color: blue; }
        .lab { color: #cc0099; }
        .hrm { color: red; }
        .eform { color: green; }
        .form { color: #8a5b00; }
    </style>
</head>
<body>
    <h3>Attach Files to Letter</h3>
    <p>Patient Demographic: <carlos:encode value='<%= demoNo %>' context="html"/></p>

    <form method="post" action="../eform/attachDoc">
        <input type="hidden" name="demoNo" value="<carlos:encode value='<%= demoNo %>' context="htmlAttribute"/>">
        <% if (fdid != null) { %>
        <input type="hidden" name="requestId" value="<%= fdid %>">
        <% } %>

        <div class="section">
            <h4 class="doc">Documents</h4>
            <div class="list">
                <% if (allDocuments.isEmpty()) { %>
                <em class="muted">No documents available</em>
                <% } else { for (EDoc document : allDocuments) { String documentId = String.valueOf(document.getDocId()); String documentCheckboxId = "docNo-" + documentId; %>
                <div class="item">
                    <label for="<%= documentCheckboxId %>">
                        <input type="checkbox" id="<%= documentCheckboxId %>" name="docNo" value="<%= documentId %>" <%= attachedDocIds.contains(documentId) ? "checked" : "" %>>
                        <carlos:encode value='<%= document.getDescription() %>' context="html"/>
                        <% if (document.getObservationDate() != null) { %>
                        <span class="muted"><carlos:encode value='<%= document.getObservationDate() %>' context="html"/></span>
                        <% } %>
                    </label>
                </div>
                <% } } %>
            </div>
        </div>

        <div class="section">
            <h4 class="lab">Labs</h4>
            <div class="list">
                <% if (allLabsSortedByVersions.isEmpty()) { %>
                <em class="muted">No labs available</em>
                <% } else { for (AttachmentLabResultData lab : allLabsSortedByVersions) { String labSegmentId = lab.getSegmentID(); String labCheckboxId = "labNo-" + labSegmentId; %>
                <div class="item">
                    <label for="<%= labCheckboxId %>">
                        <input type="checkbox" id="<%= labCheckboxId %>" name="labNo" value="<%= labSegmentId %>" <%= attachedLabIds.contains(labSegmentId) ? "checked" : "" %>>
                        <carlos:encode value='<%= lab.getLabName() %>' context="html"/>
                        <span class="muted"><carlos:encode value='<%= lab.getLabDateFormated() %>' context="html"/></span>
                    </label>
                </div>
                <% for (Map.Entry<String, String> version : lab.getLabVersionIds().entrySet()) { String labVersionId = version.getKey(); String labVersionCheckboxId = "labNo-" + labSegmentId + "-" + labVersionId; %>
                <div class="item" style="padding-left: 18px;">
                    <label for="<%= labVersionCheckboxId %>">
                        <input type="checkbox" id="<%= labVersionCheckboxId %>" name="labNo" value="<%= labVersionId %>" <%= attachedLabIds.contains(labVersionId) ? "checked" : "" %>>
                        <span class="muted">Earlier version</span>
                        <carlos:encode value='<%= version.getValue() %>' context="html"/>
                    </label>
                </div>
                <% } } } %>
            </div>
        </div>

        <div class="section">
            <h4 class="hrm">HRM</h4>
            <div class="list">
                <% if (allHRMDocuments.isEmpty()) { %>
                <em class="muted">No HRM documents available</em>
                <% } else { for (HashMap<String, ? extends Object> hrm : allHRMDocuments) { String id = String.valueOf(hrm.get("id")); String hrmCheckboxId = "hrmNo-" + id; %>
                <div class="item">
                    <input type="checkbox" id="<%= hrmCheckboxId %>" name="hrmNo" value="<%= id %>" <%= attachedHrmIds.contains(id) ? "checked" : "" %>>
                    <label for="<%= hrmCheckboxId %>">
                        <carlos:encode value='<%= String.valueOf(hrm.get("name")) %>' context="html"/>
                        <span class="muted"><carlos:encode value='<%= String.valueOf(hrm.get("report_date")) %>' context="html"/></span>
                    </label>
                </div>
                <% } } %>
            </div>
        </div>

        <div class="section">
            <h4 class="eform">eForms</h4>
            <div class="list">
                <% if (allEForms.isEmpty()) { %>
                <em class="muted">No eForms available</em>
                <% } else { for (EFormData eForm : allEForms) { String eformId = String.valueOf(eForm.getId()); String eformCheckboxId = "eFormNo-" + eformId; String displayName = eForm.getSubject() == null || eForm.getSubject().isEmpty() ? eForm.getFormName() : eForm.getSubject(); %>
                <div class="item">
                    <input type="checkbox" id="<%= eformCheckboxId %>" name="eFormNo" value="<%= eformId %>" <%= attachedEFormIds.contains(eformId) ? "checked" : "" %>>
                    <label for="<%= eformCheckboxId %>">
                        <carlos:encode value='<%= displayName %>' context="html"/>
                    </label>
                </div>
                <% } } %>
            </div>
        </div>

        <div class="section">
            <h4 class="form">Forms Current Only</h4>
            <div class="list">
                <% if (allForms.isEmpty()) { %>
                <em class="muted">No encounter forms available</em>
                <% } else { for (EctFormData.PatientForm form : allForms) { String formId = form.getFormId(); String formCheckboxId = "formNo-" + formId; %>
                <div class="item">
                    <input type="checkbox" id="<%= formCheckboxId %>" name="formNo" value="<%= formId %>" <%= attachedFormIds.contains(formId) ? "checked" : "" %>>
                    <label for="<%= formCheckboxId %>">
                        <carlos:encode value='<%= form.getFormName() %>' context="html"/>
                        <span class="muted"><carlos:encode value='<%= form.getEdited() %>' context="html"/></span>
                    </label>
                </div>
                <% } } %>
            </div>
        </div>

        <div class="actions">
            <input type="submit" class="btn" value="Attach Selected">
            <input type="button" class="btn" value="Close" onclick="window.close();">
        </div>
    </form>
</body>
</html>
