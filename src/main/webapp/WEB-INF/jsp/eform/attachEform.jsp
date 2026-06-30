<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.FormsManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
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
<%!
    private String buildDomId(String prefix, String... parts) {
        StringBuilder builder = new StringBuilder(prefix);
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            builder.append('-');
            for (int i = 0; i < part.length(); i++) {
                char ch = part.charAt(i);
                builder.append(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':' || ch == '.' ? ch : '_');
            }
        }
        return builder.toString();
    }
%>
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
    boolean canReadForms = securityInfoManager.hasPrivilege(loggedInInfo, "_form", "r", null);
    List<EctFormData.PatientForm> allForms = canReadForms
            ? formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, false, true)
            : new ArrayList<>();
    List<EctFormData.PatientForm> allFormVersions = canReadForms
            ? formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, true)
            : new ArrayList<>();
    List<EFormData> allEForms = securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)
            ? (fdid != null ? attachmentManager.getAllEFormsExpectFdid(loggedInInfo, demographicNo, fdid) : io.github.carlos_emr.carlos.eform.EFormUtil.listPatientEformsCurrent(demographicNo, true))
            : new ArrayList<>();

    Set<String> attachedDocIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, demographicNo)) : Collections.emptySet();
    Set<String> attachedLabIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, demographicNo)) : Collections.emptySet();
    Set<String> attachedHrmIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, demographicNo)) : Collections.emptySet();
    Set<String> attachedEFormIds = fdid != null ? new HashSet<>(attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, demographicNo)) : Collections.emptySet();
    Set<String> attachedFormIds = new HashSet<>();
    Set<String> currentFormIds = new HashSet<>();
    List<EctFormData.PatientForm> attachedOlderForms = new ArrayList<>();
    Map<String, EctFormData.PatientForm> allFormVersionsById = new HashMap<>();
    for (EctFormData.PatientForm form : allForms) {
        currentFormIds.add(form.getFormId());
    }
    for (EctFormData.PatientForm form : allFormVersions) {
        allFormVersionsById.put(form.getFormId(), form);
    }
    if (fdid != null) {
        for (EctFormData.PatientForm form : attachmentManager.getFormsAttachedToEForms(loggedInInfo, fdid, DocumentType.FORM, demographicNo)) {
            String attachedFormId = form.getFormId();
            attachedFormIds.add(attachedFormId);
            if (!currentFormIds.contains(attachedFormId)) {
                EctFormData.PatientForm attachedOlderForm = allFormVersionsById.get(attachedFormId);
                if (attachedOlderForm != null) {
                    attachedOlderForms.add(attachedOlderForm);
                }
            }
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
                <% } else { for (EDoc document : allDocuments) { String documentId = String.valueOf(document.getDocId()); String documentCheckboxId = buildDomId("docNo", documentId); %>
                <div class="item">
                    <label for="<%= SafeEncode.forHtmlAttribute(documentCheckboxId) %>">
                        <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(documentCheckboxId) %>" name="docNo" value="<%= SafeEncode.forHtmlAttribute(documentId) %>" <%= attachedDocIds.contains(documentId) ? "checked" : "" %>>
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
                <% } else { for (AttachmentLabResultData lab : allLabsSortedByVersions) { String labSegmentId = lab.getSegmentID(); String labCheckboxId = buildDomId("labNo", labSegmentId); String labLabelId = buildDomId("labLabel", labSegmentId); String labDateId = buildDomId("labDate", labSegmentId); %>
                <div class="item">
                    <label for="<%= SafeEncode.forHtmlAttribute(labCheckboxId) %>">
                        <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(labCheckboxId) %>" name="labNo" value="<%= SafeEncode.forHtmlAttribute(labSegmentId) %>" aria-labelledby="<%= SafeEncode.forHtmlAttribute(labLabelId + " " + labDateId) %>" <%= attachedLabIds.contains(labSegmentId) ? "checked" : "" %>>
                        <span id="<%= SafeEncode.forHtmlAttribute(labLabelId) %>"><%= SafeEncode.forHtml(lab.getLabName()) %></span>
                        <span id="<%= SafeEncode.forHtmlAttribute(labDateId) %>" class="muted"><%= SafeEncode.forHtml(lab.getLabDateFormated()) %></span>
                    </label>
                </div>
                <% for (Map.Entry<String, String> version : lab.getLabVersionIds().entrySet()) { String labVersionId = version.getKey(); String labVersionCheckboxId = buildDomId("labNo", labSegmentId, labVersionId); String labVersionLabelId = buildDomId("labLabel", labSegmentId, labVersionId); String labVersionDateId = buildDomId("labDate", labSegmentId, labVersionId); %>
                <div class="item" style="padding-left: 18px;">
                    <label for="<%= SafeEncode.forHtmlAttribute(labVersionCheckboxId) %>">
                        <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(labVersionCheckboxId) %>" name="labNo" value="<%= SafeEncode.forHtmlAttribute(labVersionId) %>" aria-labelledby="<%= SafeEncode.forHtmlAttribute(labVersionLabelId + " " + labVersionDateId) %>" <%= attachedLabIds.contains(labVersionId) ? "checked" : "" %>>
                        <span id="<%= SafeEncode.forHtmlAttribute(labVersionDateId) %>" class="muted">Earlier version</span>
                        <span id="<%= SafeEncode.forHtmlAttribute(labVersionLabelId) %>"><%= SafeEncode.forHtml(version.getValue()) %></span>
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
                <% } else { for (HashMap<String, ? extends Object> hrm : allHRMDocuments) { String id = String.valueOf(hrm.get("id")); String hrmCheckboxId = buildDomId("hrmNo", id); String hrmLabelId = buildDomId(hrmCheckboxId, "label"); String hrmDateId = buildDomId(hrmCheckboxId, "date"); %>
                <div class="item">
                    <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(hrmCheckboxId) %>" name="hrmNo" value="<%= SafeEncode.forHtmlAttribute(id) %>" aria-labelledby="<%= SafeEncode.forHtmlAttribute(hrmLabelId + " " + hrmDateId) %>" <%= attachedHrmIds.contains(id) ? "checked" : "" %>>
                    <span id="<%= SafeEncode.forHtmlAttribute(hrmLabelId) %>">
                        <carlos:encode value='<%= String.valueOf(hrm.get("name")) %>' context="html"/>
                    </span>
                    <span class="muted" id="<%= SafeEncode.forHtmlAttribute(hrmDateId) %>"><carlos:encode value='<%= String.valueOf(hrm.get("report_date")) %>' context="html"/></span>
                </div>
                <% } } %>
            </div>
        </div>

        <div class="section">
            <h4 class="eform">eForms</h4>
            <div class="list">
                <% if (allEForms.isEmpty()) { %>
                <em class="muted">No eForms available</em>
                <% } else { for (EFormData eForm : allEForms) { String eformId = String.valueOf(eForm.getId()); String eformCheckboxId = buildDomId("eFormNo", eformId); String eformLabelId = buildDomId(eformCheckboxId, "label"); String displayName = eForm.getSubject() == null || eForm.getSubject().isEmpty() ? eForm.getFormName() : eForm.getSubject(); %>
                <div class="item">
                    <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(eformCheckboxId) %>" name="eFormNo" value="<%= SafeEncode.forHtmlAttribute(eformId) %>" aria-labelledby="<%= SafeEncode.forHtmlAttribute(eformLabelId) %>" <%= attachedEFormIds.contains(eformId) ? "checked" : "" %>>
                    <span id="<%= SafeEncode.forHtmlAttribute(eformLabelId) %>">
                        <carlos:encode value='<%= displayName %>' context="html"/>
                    </span>
                </div>
                <% } } %>
            </div>
        </div>

        <div class="section">
            <h4 class="form">Forms Current Only</h4>
            <div class="list">
                <% if (allForms.isEmpty() && attachedOlderForms.isEmpty()) { %>
                <em class="muted">No encounter forms available</em>
                <% } else { for (EctFormData.PatientForm form : allForms) { String formId = form.getFormId(); String formCheckboxId = buildDomId("formNo", formId); String formLabelId = buildDomId(formCheckboxId, "label"); String formDateId = buildDomId(formCheckboxId, "date"); %>
                <div class="item">
                    <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(formCheckboxId) %>" name="formNo" value="<%= SafeEncode.forHtmlAttribute(formId) %>" aria-labelledby="<%= SafeEncode.forHtmlAttribute(formLabelId + " " + formDateId) %>" <%= attachedFormIds.contains(formId) ? "checked" : "" %>>
                    <span id="<%= SafeEncode.forHtmlAttribute(formLabelId) %>">
                        <carlos:encode value='<%= form.getFormName() %>' context="html"/>
                    </span>
                    <span class="muted" id="<%= SafeEncode.forHtmlAttribute(formDateId) %>"><carlos:encode value='<%= form.getEdited() %>' context="html"/></span>
                </div>
                <% }
                   for (EctFormData.PatientForm form : attachedOlderForms) { String formId = form.getFormId(); String formCheckboxId = buildDomId("formNo", formId); String formLabelId = buildDomId(formCheckboxId, "label"); String formDateId = buildDomId(formCheckboxId, "date"); %>
                <div class="item">
                    <input type="checkbox" id="<%= SafeEncode.forHtmlAttribute(formCheckboxId) %>" name="formNo" value="<%= SafeEncode.forHtmlAttribute(formId) %>" aria-labelledby="<%= SafeEncode.forHtmlAttribute(formLabelId + " " + formDateId) %>" checked>
                    <span class="muted">Earlier version</span>
                    <span id="<%= SafeEncode.forHtmlAttribute(formLabelId) %>">
                        <carlos:encode value='<%= form.getFormName() %>' context="html"/>
                    </span>
                    <span class="muted" id="<%= SafeEncode.forHtmlAttribute(formDateId) %>"><carlos:encode value='<%= form.getEdited() %>' context="html"/></span>
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
