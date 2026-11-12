<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%-- Read-only Add/episode view for an authorized patient. The action supplies demographicNo,
     canWrite, episode (existing) or code/description/startDate (new). Only the explicit
     POST form writes; current episodes offer completion and completed episodes are read-only. --%>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Episode,io.github.carlos_emr.carlos.utility.SafeEncode,io.github.carlos_emr.carlos.utility.FormSubmissionGuard" %>
<%
    Episode episode = (Episode) request.getAttribute("episode");
    String patient = (String) request.getAttribute("demographicNo");
    boolean canWrite = Boolean.TRUE.equals(request.getAttribute("canWrite"));
    boolean creating = episode == null;
    boolean editable = canWrite && (creating || "Current".equals(episode.getStatus()));
    String description = creating ? (String) request.getAttribute("description") : episode.getDescription();
    String start = creating ? (String) request.getAttribute("startDate") : episode.getStartDateStr();
    String notes = creating || episode.getNotes() == null ? "" : episode.getNotes();
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head><meta charset="UTF-8"><title>Pregnancy</title>
    <link rel="stylesheet" href="<%=SafeEncode.forHtmlAttribute(request.getContextPath())%>/library/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body class="container p-3">
<h1><%=creating ? "Add pregnancy" : "Pregnancy episode"%></h1>
<p><%=SafeEncode.forHtmlContent(description == null ? "" : description)%></p>
<% if (!creating) { %><p>Status: <%=SafeEncode.forHtmlContent(episode.getStatus())%></p><% } %>
<form method="post" action="<%=SafeEncode.forHtmlAttribute(request.getContextPath())%>/Pregnancy">
    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
    <input type="hidden" name="method" value="<%=creating ? "create" : "complete"%>">
    <input type="hidden" name="demographicNo" value="<%=SafeEncode.forHtmlAttribute(patient)%>">
    <% if (creating) { %>
    <input type="hidden" name="<%= FormSubmissionGuard.PARAMETER %>" value="<%=SafeEncode.forHtmlAttribute((String) request.getAttribute("formSubmissionToken"))%>">
    <input type="hidden" name="code" value="<%=SafeEncode.forHtmlAttribute((String) request.getAttribute("code"))%>">
    <% } else { %>
    <input type="hidden" name="episodeId" value="<%=episode.getId()%>">
    <% } %>
    <div class="mb-3"><label for="startDate">Start date</label>
        <input id="startDate" name="startDate" type="date" required value="<%=SafeEncode.forHtmlAttribute(start)%>" <%=creating && editable ? "" : "readonly"%>>
    </div>
    <% if (!creating) { %>
    <div class="mb-3"><label for="endDate">Completion date</label>
        <input id="endDate" name="endDate" type="date" required value="<%=SafeEncode.forHtmlAttribute(editable && episode.getEndDate() == null ? java.time.LocalDate.now().toString() : episode.getEndDateStr())%>" <%=editable ? "" : "readonly"%>>
    </div>
    <% } %>
    <div class="mb-3"><label for="notes">Notes</label>
        <textarea id="notes" name="notes" rows="6" cols="60" maxlength="65535" <%=editable ? "" : "readonly"%>>
<%=SafeEncode.forHtmlContent(notes)%></textarea>
    </div>
    <% if (editable) { %><button type="submit" class="btn btn-primary"><%=creating ? "Create pregnancy" : "Complete pregnancy"%></button><% } %>
    <a href="<%=SafeEncode.forHtmlAttribute(request.getContextPath())%>/Pregnancy?method=list&amp;demographicNo=<%=SafeEncode.forHtmlAttribute(patient)%>">Pregnancy history</a>
</form>
</body></html>
