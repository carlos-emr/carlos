<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%-- Read-only pregnancy history, forwarded only after patient and module authorization.
     Required action attributes: demographicNo, episodes and canWrite. saved=true marks the
     POST/redirect/GET confirmation and triggers only a same-patient opener navbar refresh. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.List,io.github.carlos_emr.carlos.commn.model.Episode,io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%
    String patient = (String) request.getAttribute("demographicNo");
    List<Episode> episodes = (List<Episode>) request.getAttribute("episodes");
    String route = request.getContextPath() + "/Pregnancy?demographicNo=" + patient;
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head><meta charset="UTF-8"><title>Pregnancy history</title>
    <link rel="stylesheet" href="<%=SafeEncode.forHtmlAttribute(request.getContextPath())%>/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <script src="<%=SafeEncode.forHtmlAttribute(request.getContextPath())%>/js/pregnancy.js" defer></script>
</head>
<body class="container p-3" data-patient="<%=SafeEncode.forHtmlAttribute(patient)%>" data-saved="<%="true".equals(request.getParameter("saved"))%>">
<h1>Pregnancy history</h1>
<% if ("true".equals(request.getParameter("saved"))) { %><p role="status">Pregnancy saved.</p><% } %>
<% if (Boolean.TRUE.equals(request.getAttribute("canWrite"))) { %>
<nav aria-label="Add pregnancy">
<% String[][] types = {{"72892002", "Normal"}, {"47200007", "High Risk"}, {"16356006", "Multiple"}, {"34801009", "Ectopic"}};
   for (String[] type : types) { %>
    <a href="<%=SafeEncode.forHtmlAttribute(route + "&method=new&code=" + type[0])%>">Add <%=SafeEncode.forHtmlContent(type[1])%></a>
<% } %>
</nav>
<% } %>
<% if (episodes.isEmpty()) { %><p>No pregnancy episodes recorded.</p><% } else { %>
<table class="table"><caption>Current and completed pregnancies</caption>
    <thead><tr><th scope="col">Type</th><th scope="col">Status</th><th scope="col">Start date</th><th scope="col">Completion date</th></tr></thead>
    <tbody>
    <% for (Episode episode : episodes) { %>
    <tr data-episode-id="<%=episode.getId()%>">
        <td><a href="<%=SafeEncode.forHtmlAttribute(route + "&method=edit&episodeId=" + episode.getId())%>"><%=SafeEncode.forHtmlContent(episode.getDescription() == null ? "" : episode.getDescription())%></a></td>
        <td><%=SafeEncode.forHtmlContent(episode.getStatus())%></td>
        <td><%=SafeEncode.forHtmlContent(episode.getStartDateStr())%></td>
        <td><%=SafeEncode.forHtmlContent(episode.getEndDateStr())%></td>
    </tr>
    <% } %>
    </tbody>
</table>
<% } %>
</body></html>
